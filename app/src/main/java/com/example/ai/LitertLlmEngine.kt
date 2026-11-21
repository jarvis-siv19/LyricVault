package com.example.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device LLM via LiteRT-LM (Engine + Conversation).
 *
 * The engine loads lazily on the first [generate] call — model load takes
 * seconds, so it never happens on the main thread. One fresh Conversation per
 * call: LyricVault's generations are one-shot explanations, not chats, so no
 * history is carried between calls.
 *
 * Qwen3 models run with thinking disabled (ThinkingConfig): the thinking
 * trace would waste time and tokens on a one-sentence answer. As a safety
 * net, any <think>...</think> block is stripped from the output anyway.
 */
class LitertLlmEngine(
    private val model: AiModel,
    modelFile: File,
    cacheDir: File,
    backend: Backend
) : LlmEngine {

    override val id: String = model.id
    override val displayName: String = model.displayName

    private val engine = Engine(
        EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = backend,
            maxNumTokens = 2048,
            cacheDir = cacheDir.absolutePath
        )
    )
    private val initialized = AtomicBoolean(false)

    private fun ensureInitialized() {
        if (!initialized.getAndSet(true)) {
            engine.initialize()
        }
    }

    override suspend fun generate(prompt: String, maxTokens: Int): String =
        withContext(Dispatchers.Default) {
            ensureInitialized()
            val thinking = if (ModelCatalog.isQwen(model)) {
                ThinkingConfig(enableThinking = false)
            } else {
                null
            }
            val conversation: Conversation = engine.createConversation()
            conversation.use { conv ->
                // Named args only — every other sendMessage parameter has a
                // default, and extraContext is non-nullable, so positional
                // nulls do not type-check.
                val message = conv.sendMessage(
                    text = prompt,
                    maxOutputToken = maxTokens,
                    thinkingConfig = thinking
                )
                val text = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                stripThinkBlocks(text).trim()
            }
        }

    override fun close() {
        runCatching { engine.close() }
    }

    companion object {
        private val THINK_BLOCK = Regex("(?s)<think>.*?</think>")

        fun stripThinkBlocks(text: String): String =
            text.replace(THINK_BLOCK, "").trim()
    }
}
