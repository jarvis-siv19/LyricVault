package com.example.ai

/**
 * Turns text into a dense vector. Implementations: on-device (EmbeddingGemma 2
 * via LiteRT-LM, MiniLM via ONNX Runtime) or the Gemini API (bring your own
 * key).
 *
 * Embeddings are engine-specific: vectors from one engine must never be
 * compared with vectors from another. The vector store records the engine id
 * with every embedding, and switching engines re-embeds the vault.
 */
interface Embedder : AutoCloseable {
    /** Stable id used in the vector store ("minilm", "embeddinggemma2", "gemini"). */
    val id: String
    val displayName: String
    val dimensions: Int

    /**
     * Embed [text]. [isQuery] marks a search query (as opposed to a stored
     * note): engines that distinguish the two (EmbeddingGemma 2 task prefixes,
     * Gemini task types) use it.
     */
    suspend fun embed(text: String, isQuery: Boolean = false): FloatArray
}
