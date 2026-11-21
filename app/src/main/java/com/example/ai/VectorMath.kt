package com.example.ai

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Pure vector math for the Room vector store. No Android dependencies, so it
 * is covered by plain JVM unit tests.
 */
object VectorMath {

    /** Cosine similarity in [-1, 1]. Returns 0 when either vector is zero. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0f
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0
        for (i in 0 until n) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            magA += x * x
            magB += y * y
        }
        if (magA == 0.0 || magB == 0.0) return 0f
        return (dot / (sqrt(magA) * sqrt(magB))).toFloat()
    }

    /** L2-normalize in place. A zero vector is left unchanged. */
    fun normalizeInPlace(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x.toDouble() * x.toDouble()
        val mag = sqrt(sum)
        if (mag == 0.0) return v
        for (i in v.indices) v[i] = (v[i] / mag).toFloat()
        return v
    }

    /** Serialize a float vector to a Room BLOB (little-endian). */
    fun toBytes(v: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(v)
        return buffer.array()
    }

    /** Deserialize a Room BLOB back to a float vector. */
    fun fromBytes(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(out)
        return out
    }
}
