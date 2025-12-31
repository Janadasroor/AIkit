package com.jnd.aikit.embedding

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.*
import com.jnd.aikit.model.ModelManager
import com.jnd.aikit.model.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.util.*

class CLIPImageEncoder(private val context: Context) {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private val modelManager = ModelManager(context)

    companion object {
        private const val INPUT_SIZE = 224
    }

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (session != null) return@withContext Result.success(Unit)

            // Load model dynamically
            val modelResult = modelManager.loadModel(ModelType.CLIP_VISION)
            modelResult.fold(
                onSuccess = { modelBytes ->
                    val sessionOptions = OrtSession.SessionOptions().apply {
                        setIntraOpNumThreads(2)
                        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    }
                    session = env.createSession(modelBytes, sessionOptions)
                    android.util.Log.d("CLIPImageEncoder", "Initialized with dynamic model loading")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    android.util.Log.e("CLIPImageEncoder", "Failed to load model: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("CLIPImageEncoder", "Failed to initialize: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getEmbedding(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        val currentSession = session ?: throw IllegalStateException("Model not initialized")
        val inputTensor = preprocessImage(bitmap)
        
        val inputs = Collections.singletonMap("pixel_values", inputTensor)
        currentSession.run(inputs).use { results ->
            // Prefer index 0 if it's 2D, else look for named node
            var selectedValue: Any? = null
            var nodeName = "index_0"
            
            val firstOutput = results.get(0)
            val firstInfo = firstOutput.info as? TensorInfo
            if (firstInfo != null && firstInfo.shape.size == 2) {
                selectedValue = firstOutput.value
            } else {
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
                android.util.Log.d("CLIPImageEncoder", "No 2D output found, using index 0")
                selectedValue = results.get(0).value
            } else {
                android.util.Log.d("CLIPImageEncoder", "Using 2D output: $nodeName")
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
                            else throw IllegalStateException("Unexpected output type in 3D array")
                        }
                        else -> throw IllegalStateException("Unexpected output type in 2D array")
                    }
                }
                else -> throw IllegalStateException("Unexpected output type: ${selectedValue?.javaClass?.simpleName}")
            }
            
            normalizeEmbedding(embedding)
            inputTensor.close()
            embedding
        }
    }
    
    private fun preprocessImage(bitmap: Bitmap): OnnxTensor {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val floatBuffer = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
        
        val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
        
        for (c in 0 until 3) {
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    val pixel = resized.getPixel(x, y)
                    val value = when(c) {
                        0 -> (pixel shr 16 and 0xFF)
                        1 -> (pixel shr 8 and 0xFF)
                        else -> (pixel and 0xFF)
                    }
                    floatBuffer.put((value / 255.0f - mean[c]) / std[c])
                }
            }
        }
        
        floatBuffer.rewind()
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        return OnnxTensor.createTensor(env, floatBuffer, shape)
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
    }
}
