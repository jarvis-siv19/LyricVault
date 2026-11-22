# AI matching — architecture

LyricVault's matching runs two engines side by side: the original keyword
matcher (`OfflineVaultMatcher`, TF-IDF + concept clusters + trigrams +
rhyme) always runs, and **vector search runs alongside it** when AI
matching is enabled and an embedder is ready. The two engines' results are
kept in **separate sections** (issue #8) — an AI section and a keyword
section — instead of being merged into one list. If the embedder is missing
(AI off, model not downloaded, no API key), matching behaves exactly as
before (keyword section only).

All code lives in `app/src/main/java/com/example/ai/`.

## Pieces

| Piece | File | What it does |
|---|---|---|
| Model catalog | `ModelCatalog.kt` | Every model: HF repo, file names, exact byte sizes + SHA-256 (verified against the HF tree API 2026-11-20), RAM floors, speed notes |
| Device tiers | `DeviceTiers.kt` | Permission-free spec detection (`totalMem`, `availMem`, cores, SoC) → LOW/MID/HIGH tier → recommended LLM |
| Model store | `ModelStore.kt` | Models dir under `getExternalFilesDir("models")`; installed checks, free-space checks |
| Downloader | `ModelDownloader.kt`, `ModelDownloadWorker.kt` | OkHttp HTTP-Range resume (`.part` files), SHA-256 verify, atomic rename; WorkManager job (Wi-Fi-only by default) |
| Vector math | `VectorMath.kt` | Cosine similarity, L2 normalize, float ↔ BLOB (little-endian) |
| Embedders | `Embedder.kt`, `LitertEmbedder.kt`, `MiniLmEmbedder.kt`, `GeminiEmbedder.kt` | Common interface; EmbeddingGemma 2 (LiteRT-LM, 256 dims via MRL), MiniLM int8 ONNX (384 dims, pure-Kotlin WordPiece tokenizer), Gemini API (768 dims) |
| LLMs | `LlmEngine.kt`, `LitertLlmEngine.kt`, `GeminiLlmEngine.kt` | One job: a one-sentence "why this fits" explanation per match card. LiteRT-LM Conversation API; Gemini REST |
| Gemini client | `GeminiClient.kt` | `gemini-embedding-001` + `generateContent`; key in the `x-goog-api-key` header, never in a URL |
| Key storage | `ApiKeyStore.kt` | EncryptedSharedPreferences (AES256_GCM); plain-prefs fallback with an honest UI flag if KeyStore is unavailable |
| Settings | `AiSettings.kt` | Plain prefs: enabled, embedder, LLM, backend, Wi-Fi-only, Gemini model name |
| Orchestrator | `AiMatchingService.kt` | Split match (AI + keyword sections), vault embedding, engine caching, GPU→CPU fallback, RAM load gate |
| Vector store | `IdeaEmbeddingEntity` + DAO + `LyricVaultDatabase` v2 | `idea_embeddings` table (ideaId FK CASCADE, embedding BLOB, engineId, dimensions, updatedAt) |

## Embeddings are engine-specific

Each embedding row records the engine that produced it. The matcher only
reads rows for the **active** engine; switching embedders re-embeds the vault
(old rows are kept, so switching back is instant). Re-embedding is
incremental: ideas whose embedding exists with matching dimensions and a
newer-or-equal timestamp are skipped.

## Model catalog

| Model | Size | RAM floor | Role |
|---|---|---|---|
| Gemma 4 E2B (`.litertlm`) | 2.41 GiB | 6 GB total / 2 GB free | Default LLM on HIGH tier (≥8 GB RAM) |
| Qwen3 1.7B int4 | 932 MB | 4 GB / 1.5 GB | Default LLM on MID tier |
| Qwen3 0.6B int4 | 328 MB | 2 GB / 1 GB | Default LLM on LOW tier |
| EmbeddingGemma 2 text 270M | 157 MB | 2 GB / 500 MB | Best on-device embedder (256 dims) |
| MiniLM int8 ONNX + vocab | 23 MB | 1 GB / 200 MB | Default embedder; downloads in seconds |
| Gemini (BYOK) | no download | — | `gemini-embedding-001` (768 dims) + Flash LLM |

Nothing is bundled in the APK: every model downloads from Hugging Face on
first use, so the install stays small. (MiniLM is 23 MB — small enough to
bundle, but a binary blob in git complicates the fork/PR flow, and a 23 MB
download on first enable takes seconds.)

## Runtime: LiteRT-LM, not MediaPipe

The on-device runtime is **LiteRT-LM 0.19.1**
(`com.google.ai.edge.litertlm:litertlm-android`, Google Maven). MediaPipe LLM
Inference is maintenance-only and is not used. The LiteRT-LM API used here
(`Engine` → `createConversation` → `sendMessage`, `EmbeddingEngine` →
`computeEmbedding` with `EmbeddingOptions.outputSize`) was verified against
the 0.19.1 AAR's classes.

Fallback if the Conversation API ever rejects a call: the same engine exposes
`Session.generateContent(List<InputData>)`, a blocking one-shot call that
takes the same prompt. The Conversation path is preferred because it applies
the model's chat template and supports `ThinkingConfig` (disabled for Qwen3,
whose thinking mode is off by design here).

## Gemini BYOK

The user pastes their own key in AI settings; the screen walks them through
getting one at aistudio.google.com/apikey (sign in → Create API key → copy →
paste → Save & test). The key is tested with `GET /v1beta/models?pageSize=1`.

Gemini can be the **embedder**, the **explanation LLM**, or both — the user's
choice. LLM model names move fast (Gemini 2.5 models are deprecated with
routing from Dec 1, 2026), so the picker offers `gemini-3.8-flash` (default),
`gemini-3.5-flash-lite`, and a custom model-name field. Embeddings always use
`gemini-embedding-001`. The free tier needs no card; limits are per-model
RPM/TPM/RPD shown in AI Studio.

## Custom matching instruction (issue #5)

The user can write a free-text instruction telling the AI how to judge
matches — e.g. "Match by mood and imagery, not rhyme". Two levels:

- **Global** — set in AI settings (`AiSettings.customInstruction`, capped at
  500 chars). Applies to every song.
- **Per-song** — stored on the song (`SongEntity.customInstruction`, nullable;
  DB v3, `MIGRATION_2_3` adds the column). Overrides the global instruction
  for that song when set; blank/null falls back to the global one. The field
  lives in the songwriting screen's vibe card and round-trips through JSON
  backup (old backups without the key import as null).

Resolution is `AiMatchingService.resolveInstruction(song, global)` (pure,
unit-tested). The effective instruction steers two things:

1. **Vector matching** — folded into the embedded query
   (`instructionQuery()`: `"Instruction: …\n\n<query>"`). Instruction-tuned
   embedders (EmbeddingGemma) are built for exactly this; for MiniLM/Gemini
   it still steers the query representation. The vault is NOT re-embedded:
   the instruction rides on the query side only. Keyword matching is
   term-based and cannot take instructions, so it always sees the raw query.
2. **"Why this fits" explanations** — injected into the LLM prompt, so the
   explanation judges the fit against the user's instruction.

The songwriting screen shows the instruction actually in effect under the
match-mode chips ("Matching with instruction: …"), and matches re-run live
when either the per-song or the global instruction changes.

## Split by matching type (issue #8)

`AiMatchingService.match()` returns a `SplitMatchResult` with two
`VaultMatchResult`s — `aiMatches` (vector) and `keywordMatches` — instead
of one merged list. The split itself is the pure function
`splitResults(keyword, vector)`: keyword results pass through untouched,
vector results are sorted by score and split fresh/used with the same caps
as before (25 fresh, 15 used).

There is deliberately **no cross-engine dedupe**: an idea matched by both
engines appears in both sections, each with its own score and reason, so
the songwriter can see *why* each engine surfaced it. The suggestions tab
renders an "AI MATCHES" section first, then "KEYWORD MATCHES"; each keeps
the fresh/already-used sub-grouping. With AI off there is no AI section at
all. (The old `mergeResults` hybrid merge was removed with this change.)

## Known limits

- **Runtime behavior on a real phone is unverified from this VM.** The code
  compiles and the pure logic is unit-tested, but LiteRT-LM model loading,
  GPU delegate init, and decode speed need a device test (Galaxy S23 Ultra).
- **APK size grows a lot.** Debug APK went from 18.9 MB to **147 MB**
  (Nov 20, 2026). The native libs are the bulk: LiteRT-LM's
  `liblitertlm_jni.so` (~23 MB arm64 / ~27 MB x86_64) plus ONNX Runtime's
  `libonnxruntime.so` (~18 MB arm64, shipped for all four ABIs), plus
  unminified debug DEX (~44 MB for the main dex). A release build with ABI
  splits (one APK per ABI) and R8 minification would cut this substantially
  — an arm64-only release APK should land near 50–60 MB — but that is a
  follow-up: the Build APK workflow currently assumes a single APK file, so
  splits would need a workflow change too.
- **Qwen3 thinking mode is off** (`ThinkingConfig(enableThinking=false)`):
  the explanation prompt wants a single short sentence, not a reasoning
  trace. A `<think>...</think>` strip is applied anyway as a safety net.
- **EmbeddingGemma 2 prompt format** is applied in the embedders
  (`task: search result | query: ...` for queries; `title: ... | text: ...`
  for documents, via `AiMatchingService.documentText`). Harmless for MiniLM
  and Gemini.
- **16 KB page alignment**: LiteRT-LM 0.19.1's native libs are 16 KB
  aligned (verified). ONNX Runtime Android 1.22.0 ships 16 KB-aligned libs
  per its release notes; if a future NDK change breaks that, the build will
  fail loudly at install time on 16 KB-page devices.
- **Model hashes go stale** when a repo republishes a file. If a download
  starts failing its SHA-256 check, re-fetch sizes/hashes from
  `https://huggingface.co/api/models/<repo>/tree/main` and update
  `ModelCatalog.kt`.
