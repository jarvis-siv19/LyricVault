package com.example.ai

import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Resumable file downloads from Hugging Face.
 *
 * Writes to a `.part` file, sends `Range: bytes=<already-downloaded>-`,
 * handles HTTP 206 (resume) vs 200 (server ignored Range: restart from
 * scratch), then verifies the exact byte size and SHA-256 before renaming
 * the part file into place. Cancellation keeps the part file, so a later
 * download resumes instead of starting over.
 */
object ModelDownloader {

    private const val BUFFER_SIZE = 256 * 1024

    class HashMismatchException(message: String) : IOException(message)

    /**
     * Downloads every file of [model] that is not already installed.
     * [onProgress] receives (downloadedBytes, totalBytes) across all files.
     * Returns normally when all files are present and verified.
     */
    suspend fun download(
        model: AiModel,
        store: ModelStore,
        client: OkHttpClient,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        val total = model.totalSizeBytes
        var completedBefore = 0L
        onProgress(store.downloadedBytes(model), total)

        for (file in model.files) {
            val finalFile = store.fileFor(model, file)
            if (finalFile.exists() && finalFile.length() == file.sizeBytes) {
                completedBefore += file.sizeBytes
                continue
            }
            val base = completedBefore
            downloadOneFile(
                url = model.downloadUrl(file),
                file = file,
                finalFile = finalFile,
                partFile = store.partFileFor(model, file),
                client = client,
                onProgress = { fileDownloaded ->
                    onProgress(base + fileDownloaded, total)
                }
            )
            completedBefore += file.sizeBytes
            onProgress(completedBefore, total)
        }
    }

    private suspend fun downloadOneFile(
        url: String,
        file: ModelFile,
        finalFile: File,
        partFile: File,
        client: OkHttpClient,
        onProgress: suspend (fileDownloadedBytes: Long) -> Unit
    ) {
        finalFile.parentFile?.mkdirs()
        var downloaded = if (partFile.exists()) partFile.length() else 0L
        if (downloaded > file.sizeBytes) {
            // Stale/corrupt part file from an older catalog entry.
            partFile.delete()
            downloaded = 0L
        }
        if (downloaded == file.sizeBytes) {
            if (verify(partFile, file)) {
                partFile.renameTo(finalFile)
                onProgress(downloaded)
                return
            }
            partFile.delete()
            downloaded = 0L
        }

        val requestBuilder = Request.Builder().url(url)
        if (downloaded > 0L) {
            requestBuilder.header("Range", "bytes=$downloaded-")
        }
        val response = client.newCall(requestBuilder.build()).execute()
        response.use { resp ->
            if (resp.code == 200 && downloaded > 0L) {
                // Server ignored Range: restart from scratch.
                partFile.delete()
                downloaded = 0L
            } else if (resp.code != 200 && resp.code != 206) {
                throw IOException("Download failed: HTTP ${resp.code} for $url")
            }
            val body = resp.body ?: throw IOException("Empty response body for $url")
            val append = downloaded > 0L
            java.io.FileOutputStream(partFile, append).use { out ->
                val source = body.byteStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                var lastReport = System.currentTimeMillis()
                while (source.read(buffer).also { read = it } != -1) {
                    coroutineContext.ensureActive()
                    out.write(buffer, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastReport >= 200L) {
                        onProgress(downloaded)
                        lastReport = now
                    }
                }
            }
        }

        onProgress(downloaded)
        if (downloaded != file.sizeBytes) {
            throw IOException(
                "Download incomplete for ${file.localName}: " +
                    "got $downloaded of ${file.sizeBytes} bytes"
            )
        }
        if (!verify(partFile, file)) {
            partFile.delete()
            throw HashMismatchException(
                "SHA-256 mismatch for ${file.localName}. The file on the server " +
                    "may have changed — the catalog entry needs updating."
            )
        }
        if (!partFile.renameTo(finalFile)) {
            throw IOException("Could not move ${file.localName} into place")
        }
    }

    /** Size check always; SHA-256 when the catalog carries one. */
    private fun verify(file: File, expected: ModelFile): Boolean {
        if (!file.exists() || file.length() != expected.sizeBytes) return false
        val sha = expected.sha256 ?: return true
        return sha256Hex(file).equals(sha, ignoreCase = true)
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
