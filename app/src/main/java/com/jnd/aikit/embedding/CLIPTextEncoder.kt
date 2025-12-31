package com.jnd.aikit.embedding

import android.content.Context
import ai.onnxruntime.*
import com.jnd.aikit.model.ModelManager
import com.jnd.aikit.model.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import java.util.*

class CLIPTextEncoder(private val context: Context) {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private val tokenizer = SimpleTokenizer(context)
    private val modelManager = ModelManager(context)

    companion object {
        private const val MAX_LENGTH = 77
    }

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (session != null) return@withContext Result.success(Unit)

            // Load model dynamically
            val modelResult = modelManager.loadModel(ModelType.CLIP_TEXT)
            modelResult.fold(
                onSuccess = { modelBytes ->
                    val sessionOptions = OrtSession.SessionOptions().apply {
                        setIntraOpNumThreads(2)
                        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    }
                    session = env.createSession(modelBytes, sessionOptions)
                    tokenizer.initialize()
                    android.util.Log.d("CLIPTextEncoder", "Initialized with dynamic model loading")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    android.util.Log.e("CLIPTextEncoder", "Failed to load model: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("CLIPTextEncoder", "Failed to initialize: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        val currentSession = session ?: throw IllegalStateException("Model not initialized")
        val (inputIds, attentionMask) = tokenizer.encode(text, MAX_LENGTH)
        
        val inputIdsTensor = createLongTensor(inputIds)
        val attentionMaskTensor = createLongTensor(attentionMask)
        
        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )
        
        currentSession.run(inputs).use { results ->
            // Prioritize index 0 if it's 2D (batch, dim), then look for named nodes
            var selectedValue: Any? = null
            var nodeName = "index_0"
            
            val firstOutput = results.get(0)
            val firstInfo = firstOutput.info as? TensorInfo
            if (firstInfo != null && firstInfo.shape.size == 2) {
                selectedValue = firstOutput.value
            } else {
                // Search for a 2D output among other nodes
                for (entry in results) {
                    val info = entry.value.info as? TensorInfo
                    if (info != null && info.shape.size == 2) {
                        selectedValue = entry.value.value
                        nodeName = entry.key
                        break
                    }
                }
            }
            
            if (selectedValue == null) {
                android.util.Log.d("CLIPTextEncoder", "No 2D output found, falling back to index 0")
                selectedValue = results.get(0).value
            } else {
                android.util.Log.d("CLIPTextEncoder", "Using 2D output: $nodeName")
            }

            val embedding = when (selectedValue) {
                is FloatArray -> selectedValue.clone()
                is Array<*> -> {
                    val firstLevel = selectedValue[0]
                    when (firstLevel) {
                        is FloatArray -> firstLevel.clone() // float[][] -> outputValue[0] is float[]
                        is Array<*> -> {
                            val secondLevel = firstLevel[0]
                            if (secondLevel is FloatArray) secondLevel.clone() // float[][][] -> outputValue[0][0] is float[]
                            else throw IllegalStateException("Unexpected CLIP text output type in 3D array")
                        }
                        else -> throw IllegalStateException("Unexpected CLIP text output type in 2D array")
                    }
                }
                else -> throw IllegalStateException("Unexpected CLIP text output type: ${selectedValue?.javaClass?.simpleName}")
            }

            normalizeEmbedding(embedding)
            
            inputIdsTensor.close()
            attentionMaskTensor.close()
            
            embedding
        }
    }
    
    private fun createLongTensor(data: LongArray): OnnxTensor {
        val buffer = LongBuffer.wrap(data)
        val shape = longArrayOf(1, data.size.toLong())
        return OnnxTensor.createTensor(env, buffer, shape)
    }
    
    private fun normalizeEmbedding(embedding: FloatArray) {
        var norm = 0f
        for (value in embedding) norm += value * value
        norm = kotlin.math.sqrt(norm)
        if (norm > 1e-6) {
            for (i in embedding.indices) embedding[i] /= norm
        }
    }
    
    fun close() {
        session?.close()
        session = null
        tokenizer.close()
    }
}
