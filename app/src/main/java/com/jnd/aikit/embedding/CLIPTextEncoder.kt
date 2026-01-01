package com.jnd.aikit.embedding

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import com.jnd.aikit.model.ModelManager
import com.jnd.aikit.model.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import java.util.*
import kotlin.math.sqrt

class CLIPTextEncoder(private val context: Context) {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private val modelManager = ModelManager.getInstance(context)
    private val tokenizer = Tokenizer(context)

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (session != null) return@withContext Result.success(Unit)

            val modelResult = modelManager.loadModel(ModelType.CLIP_TEXT)
            modelResult.fold(
                onSuccess = { modelBytes ->
                    val sessionOptions = OrtSession.SessionOptions().apply {
                        val cores = Runtime.getRuntime().availableProcessors()
                        setIntraOpNumThreads(cores.coerceIn(2, 4))
                        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    }
                    session = env.createSession(modelBytes, sessionOptions)
                    Log.d("CLIPTextEncoder", "Model loaded. Outputs: ${session?.outputNames?.joinToString(", ")}")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e("CLIPTextEncoder", "Failed to load model: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e("CLIPTextEncoder", "Initialization error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        val currentSession = session ?: throw IllegalStateException("CLIP Text Model not initialized")
        
        // 1. Get Ensemble of tokens with per-prompt lengths
        val ensemble = tokenizer.tokenizeEnsemble(text)
        val averagedEmbedding = FloatArray(512) { 0f }
        val seqLen = 77
        val shape = longArrayOf(1, seqLen.toLong())

        // 2. Sequential Inference for each prompt variation
        for ((tokens, actualLen) in ensemble) {
            val inputIds = LongArray(seqLen) { tokens[it].toLong() }
            val attentionMask = LongArray(seqLen) { if (it < actualLen) 1L else 0L }
            
            val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
            val attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)
            
            val inputNames = currentSession.inputNames.toList()
            val inputs = mutableMapOf<String, OnnxTensor>()
            val tensorsToClose = mutableListOf<OnnxTensor>(inputIdsTensor, attentionMaskTensor)
            
            inputNames.forEach { name ->
                when {
                    name.contains("position") -> {
                        val positions = LongArray(seqLen) { it.toLong() }
                        val posTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(positions), shape)
                        inputs[name] = posTensor
                        tensorsToClose.add(posTensor)
                    }
                    name.contains("mask") -> inputs[name] = attentionMaskTensor
                    name.contains("ids") -> inputs[name] = inputIdsTensor
                }
            }
            
            if (inputs.isEmpty() && inputNames.isNotEmpty()) inputs[inputNames[0]] = inputIdsTensor

            currentSession.run(inputs).use { results ->
                var currentEmbedding: FloatArray? = null
                val validOutputNames = listOf("text_embeds", "output", "output_0", "pooler_output")
                
                for (name in validOutputNames) {
                    if (currentSession.outputNames.contains(name)) {
                        val value = results.get(name).get().value
                        if (value is Array<*> && value[0] is FloatArray) {
                            val candidate = value[0] as FloatArray
                            if (candidate.size == 512) {
                                currentEmbedding = candidate
                                break
                            }
                        }
                    }
                }
                
                currentEmbedding?.let { 
                    for (i in 0 until 512) averagedEmbedding[i] += it[i]
                } ?: Log.w("CLIPTextEncoder", "Skipping variation: No 512-dim output found.")
            }
            
            tensorsToClose.forEach { it.close() }
        }

        // 3. Average and final normalization
        for (i in 0 until 512) averagedEmbedding[i] /= ensemble.size
        val normalized = normalize(averagedEmbedding)
        
        val finalNorm = sqrt(normalized.map { it * it }.sum())
        Log.d("CLIPTextEncoder", "Full Ensemble generated. Norm: $finalNorm")
        return@withContext normalized
    }

    private fun normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm > 0) {
            for (i in v.indices) v[i] /= norm
        }
        return v
    }

    fun getTokenDetails(text: String): String {
        return tokenizer.getTokenDetails(text)
    }

    fun close() {
        session?.close()
        session = null
    }
}
