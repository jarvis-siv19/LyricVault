package com.example.ai

import android.content.Context
import android.os.StatFs
import java.io.File

/**
 * Where models live on disk: getExternalFilesDir("models").
 * No permission needed, persists until uninstall, supports mmap.
 * Never getCacheDir — the system may clear it under storage pressure.
 */
class ModelStore(context: Context) {

    private val appContext = context.applicationContext

    val modelsDir: File
        get() = File(appContext.getExternalFilesDir(null), "models").also { it.mkdirs() }

    fun fileFor(model: AiModel, file: ModelFile): File =
        File(File(modelsDir, model.id), file.localName)

    fun partFileFor(model: AiModel, file: ModelFile): File =
        File(File(modelsDir, model.id), file.localName + ".part")

    /** Installed means every file exists with exactly the catalogued size. */
    fun isInstalled(model: AiModel): Boolean {
        if (model.noDownload) return false
        return model.files.all { file ->
            val local = fileFor(model, file)
            local.exists() && local.length() == file.sizeBytes
        }
    }

    /** Bytes already downloaded (complete files + partial files). */
    fun downloadedBytes(model: AiModel): Long {
        var total = 0L
        for (file in model.files) {
            val local = fileFor(model, file)
            val part = partFileFor(model, file)
            total += when {
                local.exists() -> local.length()
                part.exists() -> part.length()
                else -> 0L
            }
        }
        return total
    }

    fun delete(model: AiModel) {
        File(modelsDir, model.id).deleteRecursively()
    }

    /** Delete only the in-progress part files (cancel a download). */
    fun deletePartFiles(model: AiModel) {
        val dir = File(modelsDir, model.id)
        dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    /** Free bytes on the volume holding the models directory. */
    fun freeBytes(): Long {
        return try {
            val stat = StatFs(modelsDir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            0L
        }
    }

    fun hasRoomFor(model: AiModel): Boolean {
        // Keep a 100 MB safety margin so the download never fills the disk.
        val needed = model.totalSizeBytes - downloadedBytes(model) + 100L * 1_000_000L
        return freeBytes() >= needed
    }

    fun shortfallBytes(model: AiModel): Long {
        val needed = model.totalSizeBytes - downloadedBytes(model) + 100L * 1_000_000L
        return (needed - freeBytes()).coerceAtLeast(0L)
    }
}
