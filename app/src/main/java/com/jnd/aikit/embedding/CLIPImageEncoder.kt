package com.jnd.aikit.embedding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.Log
import ai.onnxruntime.*
import com.jnd.aikit.model.ModelManager
import com.jnd.aikit.model.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Enhanced CLIP Image Encoder with production-grade preprocessing and robust output mapping.
 * Implements official CLIP (OpenAI) specs: Resize shortest side to 224 + Center Crop.
 */
class CLIPImageEncoder(private val context: Context) {
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private val modelManager = ModelManager.getInstance(context)

    companion object {
        private const val INPUT_SIZE = 224
        private const val EMBEDDING_DIM = 512
        
        // Official OpenAI CLIP normalization parameters
        private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (session != null) return@withContext Result.success(Unit)

            val modelResult = modelManager.loadModel(ModelType.CLIP_VISION)
            modelResult.fold(
                onSuccess = { modelBytes ->
                    val sessionOptions = OrtSession.SessionOptions().apply {
                        // Dynamically set threads based on CPU cores (min 2, max 4 for mobile)
                        val cores = Runtime.getRuntime().availableProcessors()
                        setIntraOpNumThreads(cores.coerceIn(2, 4))
                        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    }
                    session = env.createSession(modelBytes, sessionOptions)
                    
                    // Log output nodes once for transparency
                    Log.d("CLIPImageEncoder", "Model loaded. Outputs: ${session?.outputNames?.joinToString(", ")}")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e("CLIPImageEncoder", "Failed to load model: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e("CLIPImageEncoder", "Initialization error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getEmbedding(bitmap: Bitmap, forceStretch: Boolean = false): FloatArray = withContext(Dispatchers.Default) {
        val currentSession = session ?: throw IllegalStateException("CLIP Vision Model not initialized")
        
        // 1. Preprocess image (Resize shortest side + Center Crop)
        val inputTensor = preprocessImage(bitmap, forceStretch)
        
        // 2. Prepare Inputs
        val inputNames = currentSession.inputNames.toList()
        val inputName = inputNames.find { it.contains("pixel") || it.contains("input") || it.contains("image") } ?: inputNames.first()
        val inputs = mapOf(inputName to inputTensor)
        
        // 3. Inference with Priority Selection
        currentSession.run(inputs).use { results ->
            // Priority 1: image_embeds (Projected 512-dim output)
            // Priority 2: base 'output' (If it's 512-dim)
            var finalEmbedding: FloatArray? = null
            
            val validOutputNames = listOf("image_embeds", "output", "output_0", "pooler_output")
            for (name in validOutputNames) {
                if (currentSession.outputNames.contains(name)) {
                    val value = results.get(name).get().value
                    if (value is Array<*> && value[0] is FloatArray) {
                        val candidate = value[0] as FloatArray
                        if (candidate.size == EMBEDDING_DIM) {
                            finalEmbedding = candidate.clone()
                            Log.d("CLIPImageEncoder", "Selected valid 512-dim output: $name")
                            break
                        }
                    }
                }
            }

            // Fallback: If nothing fits exactly 512, try the first token of the first available output
            if (finalEmbedding == null) {
                val firstOutput = results.get(0).value
                if (firstOutput is Array<*> && firstOutput[0] is FloatArray) {
                    val candidate = firstOutput[0] as FloatArray
                    if (candidate.size == EMBEDDING_DIM) {
                        finalEmbedding = candidate.clone()
                    }
                }
            }

            if (finalEmbedding == null) {
                throw IllegalStateException("Could not find a valid 512-dim embedding in model outputs. Checked: ${currentSession.outputNames.joinToString()}")
            }
            
            // 4. Normalize and cleanup
            val normalized = normalize(finalEmbedding)
            inputTensor.close()
            
            val finalNorm = sqrt(normalized.map { it * it }.sum())
            Log.d("CLIPImageEncoder", "Embedding generated. Norm: $finalNorm, Snippet: ${normalized.take(3).joinToString(", ")}")
            return@withContext normalized
        }
    }

    /**
     * Official CLIP Preprocessing:
     * 1. Resize shortest side to 224 (bicubic/bilinear).
     * 2. Center crop to 224x224.
     * 3. Normalize with OpenAI mean/std.
     */
    private fun preprocessImage(bitmap: Bitmap, forceStretch: Boolean): OnnxTensor {
        val finalBitmap = if (forceStretch) {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        } else {
            // 1. Calculate scaling to match shortest side to 224
            val width = bitmap.width
            val height = bitmap.height
            val scale = INPUT_SIZE.toFloat() / Math.min(width, height)
            
            val matrix = Matrix()
            matrix.postScale(scale, scale)
            
            // 2. Initial resize
            val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
            
            // 3. Center Crop 224x224
            val xOffset = (scaledBitmap.width - INPUT_SIZE) / 2
            val yOffset = (scaledBitmap.height - INPUT_SIZE) / 2
            
            Bitmap.createBitmap(scaledBitmap, xOffset, yOffset, INPUT_SIZE, INPUT_SIZE).also {
                if (scaledBitmap != it) scaledBitmap.recycle()
            }
        }

        // Ensure bitmap is software config for pixel access (fix for Android 16+ hardware bitmaps)
        val softwareBitmap = try {
            when {
                finalBitmap.config == Bitmap.Config.HARDWARE -> {
                    // Copy hardware bitmap to software bitmap for pixel access
                    android.util.Log.d("CLIPImageEncoder", "Converting hardware bitmap to software bitmap for pixel access")
                    finalBitmap.copy(Bitmap.Config.ARGB_8888, false).also {
                        finalBitmap.recycle() // Clean up hardware bitmap
                    }
                }
                finalBitmap.config == null -> {
                    // Some bitmaps might have null config, copy to ensure software
                    android.util.Log.d("CLIPImageEncoder", "Bitmap config is null, copying to software bitmap")
                    finalBitmap.copy(Bitmap.Config.ARGB_8888, false)
                }
                else -> {
                    finalBitmap
                }
            }
        } catch (e: Exception) {
            // Fallback: try to copy bitmap if any config issues occur
            android.util.Log.w("CLIPImageEncoder", "Initial bitmap copy failed, trying fallback", e)
            try {
                finalBitmap.copy(Bitmap.Config.ARGB_8888, false)
            } catch (fallbackException: Exception) {
                android.util.Log.e("CLIPImageEncoder", "Bitmap preparation failed", fallbackException)
                throw IllegalStateException("Unable to prepare bitmap for processing: ${e.message}", e)
            }
        }

        // High-performance bulk pixel extraction
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        softwareBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        
        val floatBuffer = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
        
        // CHW format: [Channel, Height, Width]
        for (c in 0 until 3) {
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val value = when(c) {
                    0 -> (pixel shr 16 and 0xFF) // R
                    1 -> (pixel shr 8 and 0xFF)  // G
                    else -> (pixel and 0xFF)      // B
                }
                floatBuffer.put((value / 255.0f - MEAN[c]) / STD[c])
            }
        }
        
        floatBuffer.rewind()
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        return OnnxTensor.createTensor(env, floatBuffer, shape)
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

    fun close() {
        session?.close()
        session = null
    }
}
