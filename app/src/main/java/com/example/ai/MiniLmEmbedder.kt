package com.example.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * all-MiniLM-L6-v2 (int8 ONNX, 23 MB) via ONNX Runtime — the default
 * embedder. Tiny, fast (~20–40 ms per embed on CPU), works on any phone.
 *
 * The model is a BERT-style encoder: WordPiece-tokenize, run the ONNX graph,
 * mean-pool the last hidden state over the non-padding tokens, L2-normalize.
 * Output is 384 dimensions.
 */
class MiniLmEmbedder(
    modelFile: File,
    vocabFile: File
) : Embedder {

    override val id: String = "minilm"
    override val displayName: String = "MiniLM"
    override val dimensions: Int = 384

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    // OrtSession has no public constructor in ONNX Runtime 1.22 — sessions
    // come from OrtEnvironment.createSession. The path overload avoids
    // reading the 23 MB model into a byte array first.
    private val session: OrtSession = environment.createSession(modelFile.absolutePath)
    private val tokenizer: WordPieceTokenizer = WordPieceTokenizer.loadFromFile(vocabFile)

    override suspend fun embed(text: String, isQuery: Boolean): FloatArray =
        withContext(Dispatchers.Default) {
            val encoded = tokenizer.encode(text)
            val declaredInputs = session.inputNames
            val tensors = HashMap<String, OnnxTensor>()
            try {
                if ("input_ids" in declaredInputs) {
                    tensors["input_ids"] = OnnxTensor.createTensor(
                        environment,
                        arrayOf(encoded.inputIds)
                    )
                }
                if ("attention_mask" in declaredInputs) {
                    tensors["attention_mask"] = OnnxTensor.createTensor(
                        environment,
                        arrayOf(encoded.attentionMask)
                    )
                }
                if ("token_type_ids" in declaredInputs) {
                    tensors["token_type_ids"] = OnnxTensor.createTensor(
                        environment,
                        arrayOf(encoded.tokenTypeIds)
                    )
                }
                session.run(tensors).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val hidden = result[0].value as Array<Array<FloatArray>>
                    meanPool(hidden[0], encoded.attentionMask)
                }
            } finally {
                tensors.values.forEach { runCatching { it.close() } }
            }
        }

    /** Mean-pool token vectors over non-padding tokens, then L2-normalize. */
    private fun meanPool(tokenVectors: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        val dims = tokenVectors.firstOrNull()?.size ?: dimensions
        val sum = FloatArray(dims)
        var count = 0
        for (i in tokenVectors.indices) {
            if (i >= attentionMask.size || attentionMask[i] == 0L) continue
            val vec = tokenVectors[i]
            for (d in 0 until dims) sum[d] += vec[d]
            count++
        }
        if (count == 0) return FloatArray(dims)
        for (d in 0 until dims) sum[d] /= count.toFloat()
        return VectorMath.normalizeInPlace(sum)
    }

    override fun close() {
        runCatching { session.close() }
    }
}
