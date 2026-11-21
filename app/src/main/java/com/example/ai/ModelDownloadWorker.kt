package com.example.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Background model download. Survives the app going to the background and
 * reports progress the UI observes via WorkInfo. Pause = cancel the work
 * (the .part file stays, so resume continues where it stopped).
 *
 * Input: KEY_MODEL_ID. Progress: KEY_DOWNLOADED / KEY_TOTAL.
 * Output on failure: KEY_ERROR with a human-readable message.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_DOWNLOADED = "downloaded_bytes"
        const val KEY_TOTAL = "total_bytes"
        const val KEY_ERROR = "error_message"

        fun uniqueName(modelId: String) = "download_$modelId"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return@withContext Result.failure(
                Data.Builder().putString(KEY_ERROR, "No model id").build()
            )
        val model = ModelCatalog.byId(modelId)
            ?: return@withContext Result.failure(
                Data.Builder().putString(KEY_ERROR, "Unknown model: $modelId").build()
            )
        val store = ModelStore(applicationContext)
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // large files: no read timeout
            .build()
        try {
            ModelDownloader.download(model, store, client) { downloaded, total ->
                setProgress(
                    Data.Builder()
                        .putLong(KEY_DOWNLOADED, downloaded)
                        .putLong(KEY_TOTAL, total)
                        .build()
                )
            }
            Result.success()
        } catch (e: ModelDownloader.HashMismatchException) {
            Result.failure(
                Data.Builder().putString(KEY_ERROR, e.message ?: "Download failed").build()
            )
        } catch (e: IOException) {
            // Network blip: let WorkManager retry with backoff.
            if (runAttemptCount >= 3) {
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR, "Download failed: ${e.message}")
                        .build()
                )
            } else {
                Result.retry()
            }
        }
    }
}
