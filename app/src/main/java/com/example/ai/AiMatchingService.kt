package com.example.ai

import android.content.Context
import com.example.data.local.IdeaEmbeddingEntity
import com.example.data.local.IdeaNoteWithDetails
import com.example.data.local.LyricVaultDao
import com.example.domain.matching.MatchMode
import com.example.domain.matching.OfflineVaultMatcher
import com.example.domain.matching.SplitMatchResult
import com.example.domain.matching.VaultMatchItem
import com.example.domain.matching.VaultMatchResult
import com.google.ai.edge.litertlm.Backend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Orchestrates AI matching: keyword + vector search, embedding the
 * vault on save and on engine switches, and one-sentence LLM explanations.
 *
 * Design rules:
 * - Keyword matching always runs. Vector search runs alongside it; when no
 *   embedder is ready (AI off, model not downloaded, no API key), matching
 *   behaves exactly as before.
 * - The two engines' results are kept SEPARATE (issue #8): [match] returns
 *   a [SplitMatchResult] with an AI section and a keyword section, so the
 *   UI shows which engine found what. An idea matched by both appears in
 *   both sections, each with its own score and reason.
 * - Embeddings are engine-specific. The matcher only reads rows for the
 *   active engine, and switching engines re-embeds the vault.
 * - Embedding failures never break saving or matching: they are swallowed
 *   here (the UI surfaces the embedder status separately).
 */
class AiMatchingService(
    appContext: Context,
    private val dao: LyricVaultDao,
    private val settings: AiSettings,
    private val apiKeyStore: ApiKeyStore,
    private val store: ModelStore
) {
    private val appContext: Context = appContext.applicationContext

    val specs: DeviceSpecs = DeviceTiers.detectSpecs(appContext)
    val tier: DeviceTier = DeviceTiers.tierFor(specs)
    val recommendedLlm: AiModel = DeviceTiers.recommendedLlm(tier)

    data class ReembedProgress(val done: Int, val total: Int, val phase: String)
    private val _reembedProgress = MutableStateFlow<ReembedProgress?>(null)
    val reembedProgress: StateFlow<ReembedProgress?> = _reembedProgress.asStateFlow()

    private val engineMutex = Mutex()
    private var cachedEmbedder: Embedder? = null
    private var cachedEmbedderId: String? = null
    private var cachedLlm: LlmEngine? = null
    private var cachedLlmId: String? = null
    private var gpuFailedOnce = false
    /** Last error from building the selected LLM, kept so the UI can name it. */
    private var lastLlmBuildError: Throwable? = null

    private val litertCacheDir: File
        get() = File(appContext.cacheDir, "litertlm").also { it.mkdirs() }

    // ---- Matching ----

    suspend fun match(
        ideas: List<IdeaNoteWithDetails>,
        query: String,
        mode: MatchMode,
        instruction: String = ""
    ): SplitMatchResult {
        val keyword = OfflineVaultMatcher.matchVault(ideas = ideas, queryText = query, mode = mode)
        val embedder = activeEmbedder() ?: return SplitMatchResult(keywordMatches = keyword)
        if (query.isBlank()) return SplitMatchResult(keywordMatches = keyword)
        // The instruction steers the vector search by riding inside the
        // embedded query; keyword matching is term-based and cannot take
        // instructions, so it always sees the raw query.
        val vectorItems = runCatching {
            vectorMatch(ideas, instructionQuery(query, instruction), embedder)
        }.getOrElse { return SplitMatchResult(keywordMatches = keyword) }
        return splitResults(keyword, vectorItems)
    }

    private suspend fun vectorMatch(
        ideas: List<IdeaNoteWithDetails>,
        query: String,
        embedder: Embedder
    ): List<VaultMatchItem> {
        val queryVec = embedder.embed(query, isQuery = true)
        val stored = dao.getEmbeddingsForEngine(embedder.id).associateBy { it.ideaId }
        val items = ArrayList<VaultMatchItem>()
        for (detail in ideas) {
            val row = stored[detail.idea.id] ?: continue
            if (row.dimensions != embedder.dimensions) continue
            val sim = VectorMath.cosine(queryVec, VectorMath.fromBytes(row.embedding))
            if (sim < VECTOR_THRESHOLD) continue
            items.add(
                VaultMatchItem(
                    ideaDetail = detail,
                    scorePercent = (sim * 100).toInt().coerceIn(12, 99),
                    matchReason = "Similar meaning & mood",
                    matchedTerms = emptyList(),
                    bestSnippet = bestSnippet(detail)
                )
            )
        }
        return items.sortedByDescending { it.scorePercent }.take(MAX_VECTOR_MATCHES)
    }

    private fun bestSnippet(detail: IdeaNoteWithDetails): String {
        val firstLine = detail.combinedLyricText
            .lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
        return firstLine ?: detail.idea.contextNote.ifBlank { detail.displayHeadline }
    }

    // ---- Embedding the vault ----

    /** Embed (or re-embed) one idea after it is saved. Best-effort. */
    suspend fun embedIdea(detail: IdeaNoteWithDetails) {
        val embedder = activeEmbedder() ?: return
        val text = detail.searchableText
        if (text.isBlank()) return
        runCatching {
            val vector = embedder.embed(documentText(detail), isQuery = false)
            dao.upsertEmbedding(
                IdeaEmbeddingEntity(
                    ideaId = detail.idea.id,
                    embedding = VectorMath.toBytes(vector),
                    engineId = embedder.id,
                    dimensions = embedder.dimensions
                )
            )
        }
    }

    /**
     * Embed every idea that has no embedding for the active engine, or whose
     * note changed after its embedding was written. Reports progress so the
     * UI can show "Embedding your vault: 12/48".
     */
    suspend fun reembedVault(ideas: List<IdeaNoteWithDetails>): Int {
        val embedder = activeEmbedder() ?: return 0
        val existing = dao.getEmbeddingsForEngine(embedder.id).associateBy { it.ideaId }
        val pending = ideas.filter { detail ->
            if (detail.searchableText.isBlank()) return@filter false
            val row = existing[detail.idea.id]
            row == null || row.dimensions != embedder.dimensions ||
                detail.idea.updatedAt > row.updatedAt
        }
        var done = 0
        _reembedProgress.value = ReembedProgress(0, pending.size, "Embedding")
        try {
            for (detail in pending) {
                runCatching {
                    val vector = embedder.embed(documentText(detail), isQuery = false)
                    dao.upsertEmbedding(
                        IdeaEmbeddingEntity(
                            ideaId = detail.idea.id,
                            embedding = VectorMath.toBytes(vector),
                            engineId = embedder.id,
                            dimensions = embedder.dimensions
                        )
                    )
                }
                done++
                _reembedProgress.value = ReembedProgress(done, pending.size, "Embedding")
            }
        } finally {
            _reembedProgress.value = null
        }
        return done
    }

    suspend fun embeddedCount(): Int {
        if (!settings.aiEnabled) return 0
        // Count rows for the selected engine without building it — building
        // an engine just to count would load the model for no reason.
        return dao.countEmbeddingsForEngine(settings.embedderId)
    }

    // ---- LLM explanations ----

    /**
     * One short sentence explaining why [detail] fits [query]. Throws when
     * no LLM is selected or ready — the caller turns that into UI text.
     *
     * The error names the REAL reason: "no model selected" was previously
     * shown for every failure (download unfinished, low free RAM, load
     * crash), which sent users back to settings when the model was already
     * selected and installed.
     */
    suspend fun explainMatch(
        detail: IdeaNoteWithDetails,
        query: String,
        instruction: String = ""
    ): String {
        val llm = activeLlm()
            ?: throw IllegalStateException(llmFailureReason())
        val prompt = buildString {
            appendLine(
                "You are a songwriting assistant. Answer in ONE short sentence " +
                    "(under 25 words). Do not invent lyrics. Do not quote the idea back."
            )
            if (instruction.isNotBlank()) {
                appendLine()
                appendLine(
                    "The songwriter's matching instruction: \"$instruction\" " +
                        "Judge the fit against that instruction."
                )
            }
            appendLine()
            appendLine("The songwriter is writing about: \"$query\"")
            appendLine()
            appendLine("Saved idea \"${detail.displayHeadline}\":")
            appendLine(detail.combinedLyricText.take(600))
            appendLine()
            append("Why does this idea fit what they are writing?")
        }
        val answer = llm.generate(prompt, maxTokens = 120)
        if (answer.isBlank()) throw IllegalStateException("The model returned an empty answer.")
        return answer
    }

    // ---- Engine selection ----

    suspend fun activeEmbedder(): Embedder? = engineMutex.withLock {
        if (!settings.aiEnabled) return null
        cachedEmbedder?.let { if (cachedEmbedderId == settings.embedderId) return it }
        closeEmbedderLocked()
        val built = runCatching { buildEmbedder(settings.embedderId) }.getOrNull() ?: return null
        cachedEmbedder = built
        cachedEmbedderId = settings.embedderId
        built
    }

    suspend fun activeLlm(): LlmEngine? = engineMutex.withLock {
        val llmId = settings.llmId
        if (llmId == AiSettings.LLM_NONE) return null
        cachedLlm?.let {
            if (cachedLlmId == llmCacheKey(llmId)) return it
        }
        closeLlmLocked()
        val built = runCatching { buildLlm(llmId) }
            .onFailure { lastLlmBuildError = it }
            .getOrNull() ?: return null
        lastLlmBuildError = null
        cachedLlm = built
        cachedLlmId = llmCacheKey(llmId)
        built
    }

    /**
     * Human-readable reason the selected explanation model is not usable
     * right now. Checked without building an engine, so it is safe to call
     * from the error path of [explainMatch].
     */
    fun llmFailureReason(): String {
        val llmId = settings.llmId
        if (llmId == AiSettings.LLM_NONE) {
            return "No explanation model selected. Pick one in AI settings " +
                "(download an on-device model or add a Gemini API key)."
        }
        if (llmId == "gemini") {
            return if (apiKeyStore.getGeminiKey() == null) {
                "Gemini is selected, but no Gemini API key is saved. " +
                    "Add your key in AI settings."
            } else {
                lastLlmBuildError?.message?.let {
                    "Gemini failed to start: $it"
                } ?: "Gemini is selected but could not start. " +
                    "Check your key in AI settings and retry."
            }
        }
        val model = ModelCatalog.byId(llmId)
            ?: return "The selected explanation model is no longer available. " +
                "Pick another one in AI settings."
        if (!store.isInstalled(model)) {
            return "${model.displayName} is selected but not fully downloaded yet. " +
                "Finish the download in AI settings — downloads are Wi-Fi only " +
                "by default, so check you are on Wi-Fi."
        }
        val current = DeviceTiers.detectSpecs(appContext)
        if (!DeviceTiers.canLoadNow(model, current)) {
            return "Not enough free RAM right now to load ${model.displayName} " +
                "(needs ${formatBytes(model.minFreeRamBytes)} free, " +
                "${formatBytes(current.availRamBytes)} available). " +
                "Close other apps and retry, or pick a smaller model."
        }
        return lastLlmBuildError?.message?.let {
            "${model.displayName} failed to load: $it Try deleting it and " +
                "re-downloading in AI settings, or pick a smaller model."
        } ?: "${model.displayName} is selected but could not load. " +
            "Try deleting it and re-downloading in AI settings."
    }

    private fun llmCacheKey(llmId: String): String =
        if (llmId == "gemini") "gemini:${settings.geminiModel}" else llmId

    private fun buildEmbedder(embedderId: String): Embedder? {
        return when (embedderId) {
            ModelCatalog.EMBEDDING_GEMMA_2.id -> {
                val model = ModelCatalog.EMBEDDING_GEMMA_2
                if (!store.isInstalled(model)) return null
                LitertEmbedder(
                    store.fileFor(model, model.files[0]),
                    litertCacheDir,
                    resolveBackend(model)
                )
            }
            ModelCatalog.GEMINI_EMBEDDER.id -> {
                val key = apiKeyStore.getGeminiKey() ?: return null
                GeminiEmbedder(GeminiClient(key))
            }
            else -> {
                val model = ModelCatalog.MINILM
                if (!store.isInstalled(model)) return null
                val onnx = model.files.first { it.localName.endsWith(".onnx") }
                val vocab = model.files.first { it.localName == "vocab.txt" }
                MiniLmEmbedder(store.fileFor(model, onnx), store.fileFor(model, vocab))
            }
        }
    }

    private fun buildLlm(llmId: String): LlmEngine? {
        if (llmId == "gemini") {
            val key = apiKeyStore.getGeminiKey() ?: return null
            return GeminiLlmEngine(GeminiClient(key), settings.geminiModel)
        }
        val model = ModelCatalog.byId(llmId) ?: return null
        if (!store.isInstalled(model)) return null
        // Load-time RAM gate: never OOM-crash the app.
        val current = DeviceTiers.detectSpecs(appContext)
        if (!DeviceTiers.canLoadNow(model, current)) return null
        val file = store.fileFor(model, model.files[0])
        return try {
            LitertLlmEngine(model, file, litertCacheDir, resolveBackend(model))
        } catch (e: Exception) {
            // GPU init failed (driver/model quirk): fall back to CPU once.
            if (!gpuFailedOnce) {
                gpuFailedOnce = true
                LitertLlmEngine(model, file, litertCacheDir, Backend.CPU())
            } else {
                throw e
            }
        }
    }

    private fun resolveBackend(model: AiModel): Backend {
        return when (settings.backendPreference) {
            AiSettings.BackendPreference.CPU -> Backend.CPU()
            AiSettings.BackendPreference.GPU -> Backend.GPU()
            AiSettings.BackendPreference.AUTO -> {
                // GPU only for the big model on a high-end phone; everything
                // else is fast enough on CPU and more compatible there.
                if (!gpuFailedOnce && tier == DeviceTier.HIGH && model.id == ModelCatalog.GEMMA_4_E2B.id) {
                    Backend.GPU()
                } else {
                    Backend.CPU()
                }
            }
        }
    }

    suspend fun setEmbedder(embedderId: String) = engineMutex.withLock {
        if (settings.embedderId != embedderId) {
            settings.embedderId = embedderId
            closeEmbedderLocked()
        }
    }

    suspend fun setLlm(llmId: String) = engineMutex.withLock {
        if (settings.llmId != llmId || llmId == "gemini") {
            settings.llmId = llmId
            closeLlmLocked()
        }
    }

    fun refreshLlmModel() {
        // Gemini model name changed: drop the cached engine so the next call
        // rebuilds it with the new name.
        cachedLlm?.let { runCatching { it.close() } }
        cachedLlm = null
        cachedLlmId = null
    }

    suspend fun closeEngines() = engineMutex.withLock {
        closeEmbedderLocked()
        closeLlmLocked()
    }

    private fun closeEmbedderLocked() {
        cachedEmbedder?.let { runCatching { it.close() } }
        cachedEmbedder = null
        cachedEmbedderId = null
    }

    private fun closeLlmLocked() {
        cachedLlm?.let { runCatching { it.close() } }
        cachedLlm = null
        cachedLlmId = null
    }

    companion object {
        /** Minimum cosine similarity for a vector match to surface. */
        const val VECTOR_THRESHOLD = 0.35f
        const val MAX_VECTOR_MATCHES = 25

        /**
         * Effective matching instruction: the per-song instruction wins when
         * it is set; otherwise the global instruction from AI settings;
         * otherwise none (""). Pure — covered by unit tests.
         */
        fun resolveInstruction(songInstruction: String?, globalInstruction: String): String {
            if (!songInstruction.isNullOrBlank()) return songInstruction.trim()
            return globalInstruction.trim()
        }

        /**
         * Query text for the embedder with the instruction folded in.
         * Instruction-tuned embedders (EmbeddingGemma) are built for exactly
         * this; for the others the instruction still steers the query
         * representation. Blank instruction returns the query unchanged.
         * Pure — covered by unit tests.
         */
        fun instructionQuery(query: String, instruction: String): String {
            if (instruction.isBlank()) return query
            return "Instruction: ${instruction.trim()}\n\n$query"
        }

        /**
         * Document text for embedding, in the EmbeddingGemma 2 format
         * (`title: ... | text: ...`). Harmless for the other engines.
         */
        fun documentText(detail: IdeaNoteWithDetails): String {
            val title = detail.idea.title.trim().ifBlank { "none" }
            return "title: $title | text: ${detail.searchableText}"
        }

        /**
         * Split by engine (issue #8): keyword results pass through untouched
         * into their own section, and vector results form the AI section,
         * split fresh/used with the same caps as before. No cross-engine
         * dedupe — an idea matched by both engines appears in both sections,
         * each with its own score and reason. Pure — covered by unit tests.
         */
        fun splitResults(
            keyword: VaultMatchResult,
            vector: List<VaultMatchItem>
        ): SplitMatchResult {
            val sorted = vector.sortedByDescending { it.scorePercent }
            return SplitMatchResult(
                aiMatches = VaultMatchResult(
                    freshUnusedMatches = sorted.filter { it.ideaDetail.idea.usedCount == 0 }.take(25),
                    alreadyUsedMatches = sorted.filter { it.ideaDetail.idea.usedCount > 0 }.take(15),
                    querySummary = keyword.querySummary
                ),
                keywordMatches = keyword
            )
        }
    }
}
