package com.example.ai

/**
 * Gemini API embedder (bring your own key). Uses gemini-embedding-001 at
 * 768 dimensions — the vectors are NOT interchangeable with on-device
 * embeddings, so switching to or from Gemini re-embeds the vault.
 */
class GeminiEmbedder(
    private val client: GeminiClient
) : Embedder {

    override val id: String = "gemini"
    override val displayName: String = "Gemini API"
    override val dimensions: Int = GeminiClient.EMBEDDING_DIMS

    override suspend fun embed(text: String, isQuery: Boolean): FloatArray {
        // Gemini embeddings are already L2-normalized by the API.
        return client.embedContent(text, isQuery)
    }

    override fun close() = Unit
}
