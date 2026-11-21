package com.example.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal Gemini Developer API client (REST, no SDK).
 * Endpoints: generativelanguage.googleapis.com/v1beta.
 *
 * The API key travels only in the `x-goog-api-key` header — never in a URL
 * query string, so it cannot leak into logs or history. The key itself lives
 * in EncryptedSharedPreferences (see ApiKeyStore) and is never logged.
 */
class GeminiClient(
    private val apiKey: String,
    client: OkHttpClient? = null
) {
    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    class GeminiException(message: String) : IOException(message)

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val EMBEDDING_MODEL = "gemini-embedding-001"
        const val DEFAULT_LLM_MODEL = "gemini-3.8-flash"
        const val LIGHT_LLM_MODEL = "gemini-3.5-flash-lite"
        const val EMBEDDING_DIMS = 768

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    /** True when the key is accepted by the API (cheap models.list call). */
    suspend fun testKey(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/models?pageSize=1")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> true
                400, 401, 403 -> throw GeminiException(
                    "That key was rejected by Google. Check it was copied " +
                        "in full from AI Studio."
                )
                429 -> throw GeminiException(
                    "Rate limit hit — try again in a minute."
                )
                else -> throw GeminiException(
                    "Google returned HTTP ${response.code}. Try again later."
                )
            }
        }
    }

    /**
     * Embed [text] with gemini-embedding-001, truncated to 768 dims.
     * [isQuery] selects the RETRIEVAL_QUERY task type, otherwise
     * RETRIEVAL_DOCUMENT.
     */
    suspend fun embedContent(text: String, isQuery: Boolean): FloatArray =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("model", "models/$EMBEDDING_MODEL")
                put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text))))
                put("taskType", if (isQuery) "RETRIEVAL_QUERY" else "RETRIEVAL_DOCUMENT")
                put("outputDimensionality", EMBEDDING_DIMS)
            }
            val request = Request.Builder()
                .url("$BASE_URL/models/$EMBEDDING_MODEL:embedContent")
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw friendlyError(response.code, payload)
                val values = JSONObject(payload)
                    .getJSONObject("embedding")
                    .getJSONArray("values")
                FloatArray(values.length()) { values.getDouble(it).toFloat() }
            }
        }

    /** One-shot text generation with [model]. */
    suspend fun generateContent(
        prompt: String,
        model: String,
        maxTokens: Int,
        temperature: Double = 0.7
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", temperature)
                    put("maxOutputTokens", maxTokens)
                }
            )
        }
        val request = Request.Builder()
            .url("$BASE_URL/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw friendlyError(response.code, payload)
            val root = JSONObject(payload)
            val candidates = root.optJSONArray("candidates") ?: return@use ""
            if (candidates.length() == 0) return@use ""
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return@use ""
            val out = StringBuilder()
            for (i in 0 until parts.length()) {
                out.append(parts.getJSONObject(i).optString("text", ""))
            }
            out.toString().trim()
        }
    }

    private fun friendlyError(code: Int, payload: String): GeminiException {
        val detail = runCatching {
            JSONObject(payload).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when (code) {
            400, 401, 403 -> GeminiException(
                "Google rejected the request (${code}). " +
                    "Your API key may be wrong or lack access. $detail".trim()
            )
            404 -> GeminiException(
                "That Gemini model name was not found (${code}). Pick a " +
                    "current model in AI settings. $detail".trim()
            )
            429 -> GeminiException(
                "Gemini free-tier limit reached — wait a minute and try again."
            )
            else -> GeminiException(
                "Gemini request failed (HTTP $code). $detail".trim()
            )
        }
    }
}
