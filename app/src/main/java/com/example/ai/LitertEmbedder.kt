package com.example.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * EmbeddingGemma 2 (text-only, 270M) via LiteRT-LM's EmbeddingEngine.
 *
 * Embeddings are truncated to 256 dimensions via the model's Matryoshka
 * (MRL) support — EmbeddingOptions.outputSize — which keeps most of the
 * retrieval quality at a third of the storage (1 KB per note).
 *
 * Prompt format follows the official EmbeddingGemma 2 model card:
 * queries are prefixed `task: search result | query: ...`; documents arrive
 * already formatted as `title: ... | text: ...` by the matching service.
 */
class LitertEmbedder(
    modelFile: File,
    cacheDir: File,
    backend: Backend = Backend.CPU()
) : Embedder {

    override val id: String = "embeddinggemma2"
    override val displayName: String = "EmbeddingGemma 2"
    override val dimensions: Int = 256

    private val engine = EmbeddingEngine(
        EmbeddingEngineConfig(
            modelPath = modelFile.absolutePath,
            backend = backend,
            cacheDir = cacheDir.absolutePath,
            maxInputLength = 512
        )
    )
    private val initialized = AtomicBoolean(false)

    private fun ensureInitialized() {
        if (!initialized.getAndSet(true)) {
            engine.initialize()
        }
    }

    override suspend fun embed(text: String, isQuery: Boolean): FloatArray =
        withContext(Dispatchers.Default) {
            ensureInitialized()
            val prompt = if (isQuery) {
                "task: search result | query: $text"
            } else {
                text
            }
            val response = engine.computeEmbedding(
                listOf(InputData.Text(prompt)),
                EmbeddingOptions(normalize = true, outputSize = dimensions)
            )
            val embedding = response.embedding
            // Defensive: the engine should return exactly `dimensions` values.
            if (embedding.size == dimensions) embedding else embedding.copyOf(dimensions)
        }

    override fun close() {
        runCatching { engine.close() }
    }
}
