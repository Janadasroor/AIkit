package com.jnd.aikit.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * ModelManager handles dynamic ONNX model loading, storage, and management
 */
class ModelManager private constructor(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        @Volatile
        private var INSTANCE: ModelManager? = null

        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context).also { INSTANCE = it }
            }
        }
    }

    private val tag = "ModelManager"

    // Model storage directory
    private val modelsDir = File(appContext.filesDir, "models").apply {
        if (!exists()) mkdirs()
    }

    // Model states
    private val _modelStates = MutableStateFlow<Map<ModelType, ModelState>>(emptyMap())
    val modelStates: MutableStateFlow<Map<ModelType, ModelState>> = _modelStates

    init {
        refreshModelStates()
    }

    /**
     * Refresh model states by checking local storage
     */
    fun refreshModelStates() {
        val states = mutableMapOf<ModelType, ModelState>()

        ModelConfig.ALL_MODELS.forEach { config ->
            val localFile = getModelFile(config)
            val isDownloaded = localFile.exists() && localFile.length() > 0

            val status = if (isDownloaded) {
                ModelStatus.DOWNLOADED
            } else {
                ModelStatus.NOT_DOWNLOADED
            }

            states[config.type] = ModelState(config, status)
        }

        _modelStates.value = states
        Log.d(tag, "Initialized model states: ${states.map { "${it.key} -> ${it.value.status}" }}")
    }

    /**
     * Get model file path
     */
    fun getModelFile(config: ModelConfig): File {
        return File(modelsDir, "${config.filename}")
    }

    /**
     * Check if model is available locally
     */
    fun isModelAvailable(type: ModelType): Boolean {
        val state = _modelStates.value[type]
        return state?.status == ModelStatus.DOWNLOADED || state?.status == ModelStatus.READY
    }

    /**
     * Load model bytes into memory - USE WITH CAUTION for large models
     */
    suspend fun loadModel(type: ModelType): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            updateModelStatus(type, ModelStatus.LOADING)

            val config = ModelConfig.ALL_MODELS.find { it.type == type }
                ?: return@withContext Result.failure(Exception("Unknown model type: $type"))

            val modelFile = getModelFile(config)
            if (!modelFile.exists()) {
                updateModelStatus(type, ModelStatus.NOT_DOWNLOADED)
                return@withContext Result.failure(Exception("Model file not found: ${modelFile.absolutePath}"))
            }

            // For large models (400MB+), this will likely fail with OOM.
            // ONNX Runtime can load models directly from file paths which is much safer.
            val modelBytes = modelFile.readBytes()

            updateModelStatus(type, ModelStatus.READY)
            Result.success(modelBytes)

        } catch (e: Exception) {
            Log.e(tag, "Failed to load model $type", e)
            updateModelStatus(type, ModelStatus.ERROR, e.message)
            Result.failure(e)
        }
    }

    /**
     * Save model from stream to file (Memory efficient)
     */
    suspend fun saveModelFromStream(type: ModelType, inputStream: InputStream): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val config = ModelConfig.ALL_MODELS.find { it.type == type }
                ?: return@withContext Result.failure(Exception("Unknown model type: $type"))

            val modelFile = getModelFile(config)
            
            modelFile.outputStream().use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }

            updateModelStatus(type, ModelStatus.DOWNLOADED)
            Log.d(tag, "Saved model ${type} to ${modelFile.absolutePath}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to save model stream $type", e)
            updateModelStatus(type, ModelStatus.ERROR, e.message)
            Result.failure(e)
        }
    }

    /**
     * Save downloaded model bytes
     */
    suspend fun saveModel(type: ModelType, modelBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val config = ModelConfig.ALL_MODELS.find { it.type == type }
                ?: return@withContext Result.failure(Exception("Unknown model type: $type"))

            val modelFile = getModelFile(config)
            modelFile.writeBytes(modelBytes)
            updateModelStatus(type, ModelStatus.DOWNLOADED)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete model from storage
     */
    suspend fun deleteModel(type: ModelType): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val config = ModelConfig.ALL_MODELS.find { it.type == type }
                ?: return@withContext Result.failure(Exception("Unknown model type: $type"))

            val modelFile = getModelFile(config)

            if (modelFile.exists()) {
                modelFile.delete()
            }

            updateModelStatus(type, ModelStatus.NOT_DOWNLOADED)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getModelInfo(type: ModelType): ModelInfo? {
        val config = ModelConfig.ALL_MODELS.find { it.type == type } ?: return null
        val modelFile = getModelFile(config)

        return ModelInfo(
            config = config,
            localPath = if (modelFile.exists()) modelFile.absolutePath else null,
            isDownloaded = modelFile.exists() && modelFile.length() > 0,
            lastUsed = 0L,
            fileSize = if (modelFile.exists()) modelFile.length() else 0L
        )
    }

    fun getTotalStorageUsed(): Long {
        return modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun clearCache() {
        // No-op now as we removed modelCache to save memory
    }

    private fun updateModelStatus(
        type: ModelType,
        status: ModelStatus,
        errorMessage: String? = null,
        progress: Float = 0f
    ) {
        val currentStates = _modelStates.value.toMutableMap()
        val config = ModelConfig.ALL_MODELS.find { it.type == type } ?: return

        currentStates[type] = ModelState(config, status, progress, errorMessage)
        _modelStates.value = currentStates
    }

    fun close() {
        Log.d(tag, "ModelManager closed")
    }
}


