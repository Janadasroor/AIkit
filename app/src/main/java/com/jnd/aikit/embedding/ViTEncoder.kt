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

class ViTEncoder(private val context: Context) {
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
            val modelResult = modelManager.loadModel(ModelType.VIT_BASE)
            modelResult.fold(
                onSuccess = { modelBytes ->
                    val sessionOptions = OrtSession.SessionOptions().apply {
                        setIntraOpNumThreads(2)
                        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    }
                    session = env.createSession(modelBytes, sessionOptions)
                    android.util.Log.d("ViTEncoder", "Initialized with dynamic model loading")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    android.util.Log.e("ViTEncoder", "Failed to load model: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("ViTEncoder", "Failed to initialize: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun getEmbedding(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        val currentSession = session ?: throw IllegalStateException("ViT Model not initialized or file missing")
        val inputTensor = preprocessImage(bitmap)
        
        val inputs = Collections.singletonMap("pixel_values", inputTensor)
        currentSession.run(inputs).use { results ->
            val outputValue = results[0].value
            
            val embedding = when (outputValue) {
                is Array<*> -> {
                    val firstLevel = outputValue[0]
                    when (firstLevel) {
                        is FloatArray -> firstLevel.clone()
                        is Array<*> -> {
                            val secondLevel = firstLevel[0]
                            if (secondLevel is FloatArray) secondLevel.clone()
                            else throw IllegalStateException("Unexpected ViT output type in 3D array")
                        }
                        else -> throw IllegalStateException("Unexpected ViT output type in 2D array")
                    }
                }
                else -> throw IllegalStateException("Unexpected ViT output type: ${outputValue?.javaClass?.simpleName}")
            }
            
            inputTensor.close()
            embedding
        }
    }
    
    private fun preprocessImage(bitmap: Bitmap): OnnxTensor {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val floatBuffer = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
        
        val mean = floatArrayOf(0.5f, 0.5f, 0.5f)
        val std = floatArrayOf(0.5f, 0.5f, 0.5f)
        
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
    
    fun close() {
        session?.close()
        session = null
    }
}
