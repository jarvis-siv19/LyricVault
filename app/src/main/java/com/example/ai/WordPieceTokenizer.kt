package com.example.ai

import java.io.File
import java.text.Normalizer

/**
 * BERT WordPiece tokenizer for the MiniLM ONNX model. Pure Kotlin, no Android
 * dependencies, covered by JVM unit tests.
 *
 * Steps (standard BERT): lowercase + strip accents, split on whitespace and
 * punctuation, then greedy longest-match WordPiece with "##" continuations.
 * Output is [CLS] tokens [SEP], truncated to [maxTokens].
 */
class WordPieceTokenizer(
    private val vocab: Map<String, Int>,
    private val maxTokens: Int = 256
) {
    private val unkId: Int = vocab["[UNK]"] ?: 100
    private val clsId: Int = vocab["[CLS]"] ?: 101
    private val sepId: Int = vocab["[SEP]"] ?: 102
    private val padId: Int = vocab["[PAD]"] ?: 0

    data class EncodedInput(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray
    )

    fun encode(text: String): EncodedInput {
        val wordPieces = basicTokenize(text).flatMap { wordPiece(it) }
        val truncated = wordPieces.take(maxTokens - 2)
        val ids = ArrayList<Long>(truncated.size + 2)
        ids.add(clsId.toLong())
        truncated.forEach { ids.add(it.toLong()) }
        ids.add(sepId.toLong())
        val size = ids.size
        return EncodedInput(
            inputIds = ids.toLongArray(),
            attentionMask = LongArray(size) { 1L },
            tokenTypeIds = LongArray(size) { 0L }
        )
    }

    fun padId(): Int = padId

    private fun basicTokenize(text: String): List<String> {
        // Lowercase + strip accents (NFD decompose, drop combining marks).
        val cleaned = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        // Split on whitespace and punctuation, keeping punctuation as tokens.
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                tokens.add(current.toString())
                current.clear()
            }
        }
        for (ch in cleaned) {
            when {
                ch.isWhitespace() -> flush()
                isPunctuation(ch) -> {
                    flush()
                    tokens.add(ch.toString())
                }
                else -> current.append(ch)
            }
        }
        flush()
        return tokens
    }

    private fun isPunctuation(ch: Char): Boolean {
        val type = Character.getType(ch)
        return type in Character.CONNECTOR_PUNCTUATION..Character.OTHER_PUNCTUATION ||
            ch in '\u0021'..'\u002F' || ch in '\u003A'..'\u0040' ||
            ch in '\u005B'..'\u0060' || ch in '\u007B'..'\u007E'
    }

    private fun wordPiece(word: String): List<Int> {
        if (word.length > MAX_WORD_CHARS) return listOf(unkId)
        val ids = ArrayList<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: Int? = null
            while (start < end) {
                val piece = if (start == 0) {
                    word.substring(start, end)
                } else {
                    "##" + word.substring(start, end)
                }
                val id = vocab[piece]
                if (id != null) {
                    found = id
                    break
                }
                end--
            }
            if (found == null) return listOf(unkId)
            ids.add(found)
            start = end
        }
        return ids
    }

    companion object {
        private const val MAX_WORD_CHARS = 100

        /** vocab.txt: one token per line, line number = token id. */
        fun loadFromFile(file: File): WordPieceTokenizer {
            val vocab = HashMap<String, Int>()
            file.bufferedReader().useLines { lines ->
                var index = 0
                for (line in lines) {
                    val token = line.trimEnd()
                    if (token.isNotEmpty()) {
                        vocab[token] = index
                    }
                    index++
                }
            }
            require(vocab.isNotEmpty()) { "Empty vocabulary: ${file.path}" }
            return WordPieceTokenizer(vocab)
        }
    }
}
