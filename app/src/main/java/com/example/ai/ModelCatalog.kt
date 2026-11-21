package com.example.ai

/**
 * Catalog of every model LyricVault can use, on-device or via the Gemini API.
 *
 * All on-device files are downloaded from Hugging Face on first use — nothing
 * is bundled in the APK, so the install stays small. Every file carries its
 * exact byte size and SHA-256 (verified against the Hugging Face tree API on
 * 2026-11-20); the downloader refuses a file that does not match.
 *
 * Sizes and hashes change when a model repo republishes a file. If a download
 * starts failing its hash check, re-fetch the values from
 * https://huggingface.co/api/models/<repo>/tree/main and update them here.
 */

enum class ModelKind { LLM, EMBEDDER }

/** One downloadable file of a model (most models have exactly one). */
data class ModelFile(
    /** File name inside the Hugging Face repo, e.g. "gemma-4-E2B-it.litertlm". */
    val repoPath: String,
    /** Exact byte size from the Hugging Face tree API. */
    val sizeBytes: Long,
    /** SHA-256 hex from the Hugging Face LFS metadata, or null for small git
     *  files (verified by size only, e.g. vocab.txt). */
    val sha256: String?
) {
    /** Local file name under the models directory. */
    val localName: String get() = repoPath.substringAfterLast('/')
}

data class AiModel(
    val id: String,
    val kind: ModelKind,
    val displayName: String,
    /** One-line description shown in the model picker. */
    val description: String,
    val repo: String,
    val files: List<ModelFile>,
    /** Minimum total device RAM for this model to be usable. */
    val minTotalRamBytes: Long,
    /** Free RAM that must be available when the model is loaded. */
    val minFreeRamBytes: Long,
    /** Expected decode speed note, shown in the picker (LLMs only). */
    val speedNote: String = "",
    /** Embedding dimensions (embedders only). */
    val dimensions: Int = 0,
    /** True when the model needs no download (bundled, or API-backed). */
    val noDownload: Boolean = false
) {
    val totalSizeBytes: Long get() = files.sumOf { it.sizeBytes }

    fun downloadUrl(file: ModelFile): String =
        "https://huggingface.co/$repo/resolve/main/${file.repoPath}"

    fun formatSize(): String = formatBytes(totalSizeBytes)
}

/** Human-readable size for any byte count (download progress, RAM, storage). */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

private const val GB = 1_000_000_000L
private const val MB = 1_000_000L

object ModelCatalog {

    // ---- On-device LLMs (LiteRT-LM .litertlm) ----

    val GEMMA_4_E2B = AiModel(
        id = "gemma-4-e2b",
        kind = ModelKind.LLM,
        displayName = "Gemma 4 E2B",
        description = "Google's current on-device model. Best quality, needs a recent phone.",
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        files = listOf(
            ModelFile(
                repoPath = "gemma-4-E2B-it.litertlm",
                sizeBytes = 2_588_147_712L,
                sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
            )
        ),
        minTotalRamBytes = 6 * GB,
        minFreeRamBytes = 2 * GB,
        speedNote = "~30–50 tok/s on a flagship"
    )

    val QWEN3_1_7B = AiModel(
        id = "qwen3-1.7b",
        kind = ModelKind.LLM,
        displayName = "Qwen3 1.7B (int4)",
        description = "Strong mid-size model. Good balance of quality and download size.",
        repo = "litert-community/Qwen3-1.7B",
        files = listOf(
            ModelFile(
                repoPath = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
                sizeBytes = 977_184_032L,
                sha256 = "2a72330b504a6596d53a2c00923c57173b10f1b83e6a541213ffec08d1fc5de8"
            )
        ),
        minTotalRamBytes = 4 * GB,
        minFreeRamBytes = 1_500 * MB,
        speedNote = "~15–30 tok/s on a mid-range phone"
    )

    val QWEN3_0_6B = AiModel(
        id = "qwen3-0.6b",
        kind = ModelKind.LLM,
        displayName = "Qwen3 0.6B (int4)",
        description = "Smallest LLM. For phones with 4 GB RAM or less.",
        repo = "litert-community/Qwen3-0.6B",
        files = listOf(
            ModelFile(
                repoPath = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
                sizeBytes = 343_490_560L,
                sha256 = "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0"
            )
        ),
        minTotalRamBytes = 2 * GB,
        minFreeRamBytes = 1 * GB,
        speedNote = "~10–15 tok/s on a budget phone"
    )

    // ---- On-device embedders ----

    val EMBEDDING_GEMMA_2 = AiModel(
        id = "embeddinggemma2",
        kind = ModelKind.EMBEDDER,
        displayName = "EmbeddingGemma 2",
        description = "Best on-device match quality. Shares the Gemma 4 stack.",
        repo = "litert-community/embeddinggemma-2-text-270m-litert-lm",
        files = listOf(
            ModelFile(
                repoPath = "embeddinggemma-2-text-270m.litertlm",
                sizeBytes = 164_626_432L,
                sha256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb"
            )
        ),
        minTotalRamBytes = 2 * GB,
        minFreeRamBytes = 500 * MB,
        dimensions = 256
    )

    val MINILM = AiModel(
        id = "minilm",
        kind = ModelKind.EMBEDDER,
        displayName = "MiniLM (default)",
        description = "Tiny and fast. Works on any phone, downloads in seconds.",
        repo = "sentence-transformers/all-MiniLM-L6-v2",
        files = listOf(
            ModelFile(
                repoPath = "onnx/model_qint8_arm64.onnx",
                sizeBytes = 23_026_053L,
                sha256 = "4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474"
            ),
            ModelFile(
                repoPath = "vocab.txt",
                sizeBytes = 231_508L,
                sha256 = null
            )
        ),
        minTotalRamBytes = 1 * GB,
        minFreeRamBytes = 200 * MB,
        dimensions = 384
    )

    // ---- Gemini API (bring your own key — no download) ----

    val GEMINI_EMBEDDER = AiModel(
        id = "gemini",
        kind = ModelKind.EMBEDDER,
        displayName = "Gemini API (your key)",
        description = "Google's embedding model over the API. Needs a free API key.",
        repo = "",
        files = emptyList(),
        minTotalRamBytes = 0L,
        minFreeRamBytes = 0L,
        dimensions = 768,
        noDownload = true
    )

    val llms: List<AiModel> = listOf(GEMMA_4_E2B, QWEN3_1_7B, QWEN3_0_6B)
    val embedders: List<AiModel> = listOf(MINILM, EMBEDDING_GEMMA_2, GEMINI_EMBEDDER)
    val downloadable: List<AiModel> = llms + listOf(EMBEDDING_GEMMA_2, MINILM)

    fun byId(id: String): AiModel? = (llms + embedders).firstOrNull { it.id == id }

    /** The LLM LiteRT-LM should load for a Qwen3 model: thinking mode off. */
    fun isQwen(model: AiModel): Boolean = model.id.startsWith("qwen3")
}
