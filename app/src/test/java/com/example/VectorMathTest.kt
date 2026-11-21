package com.example

import com.example.ai.VectorMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VectorMathTest {

    @Test
    fun cosine_identicalVectors_isOne() {
        val a = floatArrayOf(1f, 2f, 3f)
        assertEquals(1.0f, VectorMath.cosine(a, a), 1e-6f)
    }

    @Test
    fun cosine_orthogonalVectors_isZero() {
        assertEquals(
            0.0f,
            VectorMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)),
            1e-6f
        )
    }

    @Test
    fun cosine_oppositeVectors_isMinusOne() {
        assertEquals(
            -1.0f,
            VectorMath.cosine(floatArrayOf(1f, 2f), floatArrayOf(-1f, -2f)),
            1e-6f
        )
    }

    @Test
    fun cosine_zeroVector_isZero() {
        assertEquals(
            0.0f,
            VectorMath.cosine(floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 2f, 3f)),
            0f
        )
    }

    @Test
    fun cosine_knownAngle_matches() {
        // 45 degrees between (1, 0) and (1, 1): cos = 1/sqrt(2).
        assertEquals(
            0.70710678f,
            VectorMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(1f, 1f)),
            1e-6f
        )
    }

    @Test
    fun normalizeInPlace_unitLength() {
        val v = floatArrayOf(3f, 4f)
        VectorMath.normalizeInPlace(v)
        assertEquals(0.6f, v[0], 1e-6f)
        assertEquals(0.8f, v[1], 1e-6f)
    }

    @Test
    fun normalizeInPlace_zeroVector_unchanged() {
        val v = floatArrayOf(0f, 0f)
        VectorMath.normalizeInPlace(v)
        assertArrayEquals(floatArrayOf(0f, 0f), v, 0f)
    }

    @Test
    fun toBytes_fromBytes_roundTrip() {
        val v = floatArrayOf(0.1f, -2.5f, 3.14159f, 0f, 12345.678f)
        val bytes = VectorMath.toBytes(v)
        assertEquals(v.size * 4, bytes.size)
        assertArrayEquals(v, VectorMath.fromBytes(bytes), 0f)
    }
}
