package com.example.domain.matching

import com.example.data.local.IdeaNoteWithDetails
import kotlin.math.ln
import kotlin.math.sqrt

enum class MatchMode {
    WHOLE_SONG_VIBE,
    SPECIFIC_LINE
}

data class VaultMatchItem(
    val ideaDetail: IdeaNoteWithDetails,
    val scorePercent: Int,
    val matchReason: String,
    val matchedTerms: List<String>,
    val bestSnippet: String
)

data class VaultMatchResult(
    val freshUnusedMatches: List<VaultMatchItem> = emptyList(),
    val alreadyUsedMatches: List<VaultMatchItem> = emptyList(),
    val querySummary: String = ""
) {
    val totalCount: Int get() = freshUnusedMatches.size + alreadyUsedMatches.size
}

/**
 * Matches split by engine (issue #8): the AI (vector) matches and the
 * keyword matches are kept in separate sections instead of being merged
 * into one list. The same idea can appear in both — each section shows
 * what its own engine found, with its own score and reason.
 */
data class SplitMatchResult(
    val aiMatches: VaultMatchResult = VaultMatchResult(),
    val keywordMatches: VaultMatchResult = VaultMatchResult()
) {
    val totalCount: Int get() = aiMatches.totalCount + keywordMatches.totalCount
}

/**
 * 100% On-Device Offline Similarity Engine for LyricVault.
 * Strictly grounded in the songwriter's own saved notes — never invents new lyrics.
 *
 * Combines:
 * 1. TF-IDF cosine similarity with lightweight English stemming.
 * 2. Local songwriting concept/mood cluster expansion (metaphor & psychology clusters).
 * 3. Character trigram similarity for partial phrases and word stems.
 * 4. End-rhyme & vowel-assonance boost when matching a specific lyric line.
 */
object OfflineVaultMatcher {

    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "been", "but", "by", "can", "could",
        "did", "do", "does", "for", "from", "got", "had", "has", "have", "he", "her",
        "here", "him", "his", "how", "i", "if", "im", "in", "into", "is", "it", "its",
        "just", "like", "me", "my", "no", "not", "now", "of", "on", "one", "or", "our",
        "out", "over", "she", "so", "some", "that", "the", "their", "them", "then",
        "there", "these", "they", "this", "to", "too", "up", "us", "very", "was", "we",
        "were", "what", "when", "where", "which", "who", "why", "will", "with", "would",
        "you", "your", "about", "really", "song", "idea", "use", "want", "write", "writing"
    )

    // Curated offline songwriting theme & psychological concept clusters
    private val conceptClusters: Map<String, Set<String>> = mapOf(
        "solitude" to setOf(
            "alone", "lonely", "solitude", "empty", "quiet", "silence", "silent", "ghost",
            "hollow", "isolated", "drift", "drifting", "stranger", "nobody", "echo", "midnight", "room"
        ),
        "transit" to setOf(
            "metro", "train", "subway", "station", "platform", "window", "commute", "ride",
            "city", "street", "neon", "concrete", "crowd", "rush", "passing", "tunnel", "tracks", "midnight"
        ),
        "psychology" to setOf(
            "mind", "ego", "mask", "mirror", "shadow", "spiral", "loop", "overthink", "overthinking",
            "anxiety", "anxious", "guilt", "shame", "trauma", "subconscious", "illusion", "facade",
            "pretend", "persona", "paradox", "cognitive", "dissonance", "numb", "dissociate", "pattern"
        ),
        "heartbreak" to setOf(
            "love", "heart", "heartbreak", "break", "broken", "scar", "ache", "pain", "miss",
            "missing", "goodbye", "leave", "left", "fade", "fading", "memory", "memories",
            "tears", "cry", "cold", "distance", "apart", "betray", "hurt", "loss", "lost"
        ),
        "time" to setOf(
            "time", "clock", "hour", "hours", "minute", "seconds", "yesterday", "tomorrow",
            "past", "future", "old", "age", "rust", "dust", "fleeting", "forever", "never", "late", "wait", "waiting"
        ),
        "ambition" to setOf(
            "fire", "flame", "burn", "climb", "crown", "king", "rise", "hunger", "hungry",
            "grind", "chase", "dream", "dreams", "gold", "shine", "win", "fight", "battle", "spark", "power"
        ),
        "hope" to setOf(
            "hope", "dawn", "morning", "sunrise", "light", "bright", "breathe", "breath", "bloom",
            "grow", "heal", "healing", "horizon", "open", "sky", "warm", "warmth", "peace", "calm", "home"
        ),
        "storm" to setOf(
            "storm", "rain", "thunder", "lightning", "wind", "chaos", "static", "noise", "loud",
            "rage", "anger", "angry", "crash", "shatter", "wreck", "flood", "drown", "heavy", "dark", "darkness"
        ),
        "night" to setOf(
            "night", "midnight", "moon", "stars", "sleep", "insomnia", "awake", "3am", "2am",
            "lamp", "streetlight", "shadow", "shadows", "velvet", "black", "dim", "late"
        ),
        "nostalgia" to setOf(
            "nostalgia", "childhood", "home", "summer", "vintage", "polaroid", "cassette", "tape",
            "rewind", "remember", "forgot", "forget", "photograph", "melody", "youth", "back"
        )
    )

    private val wordToClusters: Map<String, List<String>> by lazy {
        val map = mutableMapOf<String, MutableList<String>>()
        conceptClusters.forEach { (clusterName, words) ->
            words.forEach { w ->
                val stemmed = stem(w)
                map.getOrPut(w) { mutableListOf() }.add(clusterName)
                if (stemmed != w) {
                    map.getOrPut(stemmed) { mutableListOf() }.add(clusterName)
                }
            }
        }
        map
    }

    fun matchVault(
        ideas: List<IdeaNoteWithDetails>,
        queryText: String,
        mode: MatchMode,
        maxFresh: Int = 25,
        maxUsed: Int = 15
    ): VaultMatchResult {
        val cleanedQuery = queryText.trim()
        if (cleanedQuery.isBlank() || ideas.isEmpty()) {
            return VaultMatchResult(querySummary = cleanedQuery)
        }

        val queryTokens = tokenize(cleanedQuery)
        val queryStems = queryTokens.map { stem(it) }
        val queryClusters = extractClusters(queryTokens)
        val queryTrigrams = buildTrigrams(cleanedQuery.lowercase())
        val queryEndingRhyme = if (mode == MatchMode.SPECIFIC_LINE) extractRhymeTail(cleanedQuery) else null

        if (queryTokens.isEmpty() && queryTrigrams.isEmpty()) {
            return VaultMatchResult(querySummary = cleanedQuery)
        }

        // Precompute document token lists and IDF across the user's vault
        val docTokenLists = ideas.map { detail ->
            val tokens = tokenize(detail.searchableText)
            tokens to tokens.map { stem(it) }
        }
        val docCount = ideas.size.toDouble()
        val df = mutableMapOf<String, Int>()
        docTokenLists.forEach { (_, stems) ->
            stems.toSet().forEach { s -> df[s] = (df[s] ?: 0) + 1 }
        }
        fun idf(stemWord: String): Double {
            val count = df[stemWord] ?: 0
            return ln((docCount + 1.0) / (count + 1.0)) + 1.0
        }

        val queryTfIdf = buildTfIdfVector(queryStems, ::idf)
        val matches = mutableListOf<VaultMatchItem>()

        ideas.forEachIndexed { idx, detail ->
            val rawText = detail.searchableText
            if (rawText.isBlank()) return@forEachIndexed

            val (docTokens, docStems) = docTokenLists[idx]
            val docTfIdf = buildTfIdfVector(docStems, ::idf)
            val cosine = cosineSimilarity(queryTfIdf, docTfIdf)

            // Exact & stemmed token overlap
            val docStemSet = docStems.toSet()
            val overlappingQueryWords = queryTokens.filter { stem(it) in docStemSet }.distinct()

            // Concept cluster overlap
            val docClusters = extractClusters(docTokens)
            val sharedClusters = queryClusters.intersect(docClusters)
            val conceptScore = if (queryClusters.isEmpty() || docClusters.isEmpty()) {
                0.0
            } else {
                sharedClusters.size.toDouble() / sqrt((queryClusters.size * docClusters.size).toDouble())
            }

            // Character trigram overlap (great for partial words, phrases, Indian/slang words)
            val docTrigrams = buildTrigrams(rawText.lowercase())
            val trigramScore = jaccardOverlap(queryTrigrams, docTrigrams)

            // Direct phrase/substring bonus
            val lowerDoc = rawText.lowercase()
            val lowerQuery = cleanedQuery.lowercase()
            val phraseBonus = when {
                lowerQuery.length >= 4 && lowerDoc.contains(lowerQuery) -> 0.35
                else -> {
                    val bigrams = queryTokens.zipWithNext { a, b -> "$a $b" }
                    val hitBigrams = bigrams.count { lowerDoc.contains(it) }
                    if (bigrams.isNotEmpty() && hitBigrams > 0) (hitBigrams.toDouble() / bigrams.size) * 0.25 else 0.0
                }
            }

            // Rhyme / phonetic tail match for Specific Line mode
            var rhymeMatchedWord: String? = null
            val rhymeBonus = if (mode == MatchMode.SPECIFIC_LINE && !queryEndingRhyme.isNullOrBlank()) {
                val candidateLines = detail.combinedLyricText.lines().filter { it.isNotBlank() }
                var bestRhyme = 0.0
                for (line in candidateLines) {
                    val lineTail = extractRhymeTail(line)
                    val lastWord = extractLastWord(line)
                    val queryLastWord = extractLastWord(cleanedQuery)
                    if (!lineTail.isNullOrBlank() && lastWord != queryLastWord) {
                        if (lineTail == queryEndingRhyme && lineTail.length >= 2) {
                            bestRhyme = 0.32
                            rhymeMatchedWord = lastWord
                            break
                        } else if (lineTail.length >= 2 && queryEndingRhyme.length >= 2 &&
                            lineTail.takeLast(2) == queryEndingRhyme.takeLast(2)
                        ) {
                            if (bestRhyme < 0.18) {
                                bestRhyme = 0.18
                                rhymeMatchedWord = lastWord
                            }
                        }
                    }
                }
                bestRhyme
            } else {
                0.0
            }

            // Context note boost (Mr. Music explicitly writes "where I want to use it in the future")
            val contextStems = tokenize(detail.idea.contextNote).map { stem(it) }.toSet()
            val tagStems = tokenize(detail.idea.tags).map { stem(it) }.toSet()
            val contextOverlap = queryStems.count { it in contextStems || it in tagStems }
            val contextBonus = if (contextOverlap > 0) (contextOverlap.toDouble() / queryStems.size.coerceAtLeast(1)) * 0.22 else 0.0

            val combinedScore = when (mode) {
                MatchMode.WHOLE_SONG_VIBE -> {
                    (cosine * 0.50) + (conceptScore * 0.34) + (trigramScore * 0.16) + phraseBonus + contextBonus
                }
                MatchMode.SPECIFIC_LINE -> {
                    (cosine * 0.42) + (trigramScore * 0.24) + (conceptScore * 0.18) + phraseBonus + rhymeBonus + (contextBonus * 0.6)
                }
            }

            val threshold = if (mode == MatchMode.WHOLE_SONG_VIBE) 0.065 else 0.075
            if (combinedScore >= threshold) {
                val normalizedPercent = (combinedScore.coerceIn(0.0, 1.0) * 100.0).toInt().coerceIn(12, 99)
                val reason = buildReasonLabel(
                    mode = mode,
                    overlappingWords = overlappingQueryWords,
                    sharedClusters = sharedClusters,
                    rhymeWord = rhymeMatchedWord,
                    contextHit = contextOverlap > 0
                )
                val snippet = selectBestSnippet(detail, queryStems, sharedClusters)
                matches.add(
                    VaultMatchItem(
                        ideaDetail = detail,
                        scorePercent = normalizedPercent,
                        matchReason = reason,
                        matchedTerms = (overlappingQueryWords + sharedClusters).distinct().take(4),
                        bestSnippet = snippet
                    )
                )
            }
        }

        val sorted = matches.sortedWith(
            compareByDescending<VaultMatchItem> { it.scorePercent }
                .thenByDescending { it.ideaDetail.idea.updatedAt }
        )

        val fresh = sorted.filter { it.ideaDetail.idea.usedCount == 0 }.take(maxFresh)
        val used = sorted.filter { it.ideaDetail.idea.usedCount > 0 }.take(maxUsed)

        return VaultMatchResult(
            freshUnusedMatches = fresh,
            alreadyUsedMatches = used,
            querySummary = cleanedQuery
        )
    }

    private fun buildReasonLabel(
        mode: MatchMode,
        overlappingWords: List<String>,
        sharedClusters: Set<String>,
        rhymeWord: String?,
        contextHit: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (overlappingWords.isNotEmpty()) {
            parts.add("Matches ${overlappingWords.take(2).joinToString(", ") { "'$it'" }}")
        }
        if (sharedClusters.isNotEmpty()) {
            parts.add("Theme: ${sharedClusters.take(2).joinToString(" & ")}")
        }
        if (!rhymeWord.isNullOrBlank()) {
            parts.add("Rhymes with '$rhymeWord'")
        }
        if (parts.isEmpty() && contextHit) {
            parts.add("Matches your usage note")
        }
        if (parts.isEmpty()) {
            parts.add(if (mode == MatchMode.WHOLE_SONG_VIBE) "Similar vibe & phrasing" else "Similar phrase texture")
        }
        return parts.take(2).joinToString(" • ")
    }

    private fun selectBestSnippet(
        detail: IdeaNoteWithDetails,
        queryStems: List<String>,
        sharedClusters: Set<String>
    ): String {
        val querySet = queryStems.toSet()
        val lines = detail.combinedLyricText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return detail.idea.contextNote.ifBlank { detail.displayHeadline }
        }

        val scoredLine = lines.maxByOrNull { line ->
            val tokens = tokenize(line)
            val stemHits = tokens.count { stem(it) in querySet } * 3
            val clusterHits = extractClusters(tokens).count { it in sharedClusters } * 2
            stemHits + clusterHits
        }
        return scoredLine ?: lines.first()
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9'\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim('\'') }
            .filter { it.length >= 2 && it !in stopWords }
    }

    private fun stem(word: String): String {
        var w = word.lowercase()
        if (w.length <= 3) return w
        val suffixes = listOf("ingly", "edly", "ness", "ment", "tion", "sion", "ing", "ied", "ies", "ful", "less", "ous", "ive", "ers", "est", "ed", "es", "ly", "s")
        for (suffix in suffixes) {
            if (w.endsWith(suffix) && w.length - suffix.length >= 3) {
                w = w.dropLast(suffix.length)
                break
            }
        }
        return w
    }

    private fun extractClusters(tokens: List<String>): Set<String> {
        val result = mutableSetOf<String>()
        tokens.forEach { token ->
            wordToClusters[token]?.let { result.addAll(it) }
            wordToClusters[stem(token)]?.let { result.addAll(it) }
        }
        return result
    }

    private fun buildTfIdfVector(stems: List<String>, idfFn: (String) -> Double): Map<String, Double> {
        if (stems.isEmpty()) return emptyMap()
        val counts = mutableMapOf<String, Int>()
        stems.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val total = stems.size.toDouble()
        return counts.mapValues { (term, count) ->
            (count.toDouble() / total) * idfFn(term)
        }
    }

    private fun cosineSimilarity(v1: Map<String, Double>, v2: Map<String, Double>): Double {
        if (v1.isEmpty() || v2.isEmpty()) return 0.0
        var dot = 0.0
        var mag1 = 0.0
        var mag2 = 0.0
        v1.forEach { (k, a) ->
            mag1 += a * a
            v2[k]?.let { b -> dot += a * b }
        }
        v2.values.forEach { b -> mag2 += b * b }
        if (mag1 == 0.0 || mag2 == 0.0) return 0.0
        return dot / (sqrt(mag1) * sqrt(mag2))
    }

    private fun buildTrigrams(text: String): Set<String> {
        val compact = text.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        if (compact.length < 3) return emptySet()
        val set = mutableSetOf<String>()
        for (i in 0..compact.length - 3) {
            val tri = compact.substring(i, i + 3)
            if (!tri.contains("  ")) set.add(tri)
        }
        return set
    }

    private fun jaccardOverlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var intersection = 0
        val smaller = if (a.size <= b.size) a else b
        val larger = if (a.size <= b.size) b else a
        for (item in smaller) {
            if (item in larger) intersection++
        }
        if (intersection == 0) return 0.0
        // Weighted toward query coverage so long notes aren't penalized
        return intersection.toDouble() / a.size.toDouble()
    }

    private fun extractLastWord(line: String): String {
        return line.lowercase()
            .replace(Regex("[^a-z\\s]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .lastOrNull()
            .orEmpty()
    }

    private fun extractRhymeTail(line: String): String? {
        val word = extractLastWord(line)
        if (word.length < 2) return null
        val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')
        val lastVowelIndex = word.indexOfLast { it in vowels }
        return if (lastVowelIndex >= 0) {
            word.substring(lastVowelIndex)
        } else {
            word.takeLast(2)
        }
    }
}
