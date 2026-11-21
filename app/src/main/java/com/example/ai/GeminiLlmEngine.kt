package com.example.ai

/**
 * Gemini API LLM (bring your own key). The model name is user-configurable
 * because Google renames models often — the default is the current free-tier
 * Flash model, with a lighter Flash-Lite option and a custom-name field.
 */
class GeminiLlmEngine(
    private val client: GeminiClient,
    private val modelName: String
) : LlmEngine {

    override val id: String = "gemini"
    override val displayName: String = "Gemini API ($modelName)"

    override suspend fun generate(prompt: String, maxTokens: Int): String =
        client.generateContent(prompt, modelName, maxTokens)

    override fun close() = Unit
}
