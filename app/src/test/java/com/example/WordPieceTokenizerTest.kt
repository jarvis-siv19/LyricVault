package com.example

import com.example.ai.WordPieceTokenizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordPieceTokenizerTest {

    // Tiny fake vocab: [PAD]=0 [UNK]=100 [CLS]=101 [SEP]=102, then words.
    private val vocab = mapOf(
        "[PAD]" to 0, "[UNK]" to 100, "[CLS]" to 101, "[SEP]" to 102,
        "hello" to 200, "world" to 201, "song" to 202, "writ" to 203,
        "##ing" to 204, "mid" to 205, "##night" to 206, "," to 207
    )

    @Test
    fun encode_wrapsWithClsAndSep() {
        val enc = WordPieceTokenizer(vocab).encode("hello world")
        assertArrayEquals(
            longArrayOf(101, 200, 201, 102),
            enc.inputIds
        )
        assertArrayEquals(longArrayOf(1, 1, 1, 1), enc.attentionMask)
        assertArrayEquals(longArrayOf(0, 0, 0, 0), enc.tokenTypeIds)
    }

    @Test
    fun encode_lowercasesAndStripsAccents() {
        val enc = WordPieceTokenizer(vocab).encode("HELLO Wörld")
        // "wörld" -> "world" after accent stripping.
        assertArrayEquals(longArrayOf(101, 200, 201, 102), enc.inputIds)
    }

    @Test
    fun encode_splitsSubwords() {
        val enc = WordPieceTokenizer(vocab).encode("writing midnight")
        assertArrayEquals(
            longArrayOf(101, 203, 204, 205, 206, 102),
            enc.inputIds
        )
    }

    @Test
    fun encode_unknownWord_becomesUnk() {
        val enc = WordPieceTokenizer(vocab).encode("hello zqxj")
        assertArrayEquals(longArrayOf(101, 200, 100, 102), enc.inputIds)
    }

    @Test
    fun encode_punctuation_isOwnToken() {
        val enc = WordPieceTokenizer(vocab).encode("hello, world")
        assertArrayEquals(longArrayOf(101, 200, 207, 201, 102), enc.inputIds)
    }

    @Test
    fun encode_truncatesToMaxTokens() {
        val enc = WordPieceTokenizer(vocab, maxTokens = 4).encode("hello world song midnight")
        // [CLS] + 2 word tokens + [SEP].
        assertEquals(4, enc.inputIds.size)
        assertEquals(101L, enc.inputIds[0])
        assertEquals(102L, enc.inputIds[3])
    }

    @Test
    fun encode_emptyText_isJustClsSep() {
        val enc = WordPieceTokenizer(vocab).encode("   ")
        assertArrayEquals(longArrayOf(101, 102), enc.inputIds)
    }

    @Test
    fun loadFromFile_lineNumberIsId() {
        val file = kotlin.io.path.createTempFile("vocab", ".txt").toFile()
        file.writeText("[PAD]\n[UNK]\n[CLS]\n[SEP]\nhello\n")
        val tokenizer = WordPieceTokenizer.loadFromFile(file)
        val enc = tokenizer.encode("hello")
        assertArrayEquals(longArrayOf(2, 4, 3), enc.inputIds)
        assertTrue(file.delete())
    }
}
