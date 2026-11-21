package com.example.ai

/**
 * A text-generation engine: on-device (LiteRT-LM) or the Gemini API.
 * In LyricVault the LLM has one job: explain, in one short sentence, why a
 * saved idea fits what the songwriter is writing. It never invents lyrics.
 */
interface LlmEngine : AutoCloseable {
    val id: String
    val displayName: String

    /** One-shot generation. Returns the model's text, trimmed. */
    suspend fun generate(prompt: String, maxTokens: Int = 160): String
}
