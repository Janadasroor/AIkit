package com.jnd.aikit.model

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for managing model download, storage, and status
 */
class ModelViewModel(application: Application) : AndroidViewModel(application) {

    private val modelManager = ModelManager(application)
    private val modelDownloader = ModelDownloader(application)

    // UI state
    private val _uiState = MutableStateFlow(ModelUiState())
    val uiState: StateFlow<ModelUiState> = _uiState.asStateFlow()

    // Model states from manager
    val modelStates = modelManager.modelStates

    init {
        loadModelStates()
    }

    /**
     * Load current model states
     */
    private fun loadModelStates() {
        viewModelScope.launch {
            modelManager.modelStates.collect { states ->
                _uiState.update { currentState ->
                    currentState.copy(
                        modelStates = states,
                        totalStorageUsed = modelManager.getTotalStorageUsed()
                    )
                }
            }
        }
    }

    /**
     * Download a model
     */
    fun downloadModel(config: ModelConfig) {
        if (_uiState.value.isDownloading) return // Prevent multiple downloads

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, currentDownload = config) }

            try {
                modelDownloader.downloadModel(config)
                    .collect { result ->
                        when (result) {
                            is DownloadResult.Started -> {
                                _uiState.update {
                                    it.copy(
                                        downloadProgress = 0f,
                                        downloadStatus = "Starting download..."
                                    )
                                }
                            }
                            is DownloadResult.Progress -> {
                                _uiState.update {
                                    it.copy(
                                        downloadProgress = result.progress,
                                        downloadStatus = "Downloading... ${(result.progress * 100).toInt()}%"
                                    )
                                }
                            }
                            is DownloadResult.Completed -> {
                                // Validate and save the model
                                if (modelDownloader.validateModel(config, result.file)) {
                                    // Use streaming to save the model to avoid OOM
                                    result.file.inputStream().use { input ->
                                        val saveResult = modelManager.saveModelFromStream(config.type, input)
                                        saveResult.onSuccess {
                                            _uiState.update {
                                                it.copy(
                                                    downloadStatus = "Model saved successfully",
                                                    downloadProgress = 1f
                                                )
                                            }
                                        }.onFailure { error ->
                                            _uiState.update {
                                                it.copy(
                                                    downloadStatus = "Failed to save model: ${error.message}",
                                                    errorMessage = error.message
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    _uiState.update {
                                        it.copy(
                                            downloadStatus = "Downloaded file is invalid",
                                            errorMessage = "Model validation failed"
                                        )
                                    }
                                }

                                // Clean up temp file
                                result.file.delete()
                            }
                            is DownloadResult.Error -> {
                                _uiState.update {
                                    it.copy(
                                        downloadStatus = "Download failed",
                                        errorMessage = result.exception.message
                                    )
                                }
                            }
                        }
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        downloadStatus = "Download failed",
                        errorMessage = e.message
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        currentDownload = null
                    )
                }

                // Cleanup temp files after a delay
                kotlinx.coroutines.delay(2000)
                modelDownloader.cleanupTempFiles()
            }
        }
    }

    /**
     * Upload a model from file URI
     */
    fun uploadModel(fileUri: android.net.Uri) {
        if (_uiState.value.isUploading) return // Prevent multiple uploads

        _uiState.update { it.copy(isUploading = true, uploadStatus = "Uploading model...") }

        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadStatus = "Failed to open file",
                            errorMessage = "Could not read selected file"
                        )
                    }
                    return@launch
                }

                // Try to determine model type from filename
                val cursor = context.contentResolver.query(fileUri, null, null, null, null)
                var filename = "unknown.onnx"
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            filename = it.getString(nameIndex)
                        }
                    }
                }

                // Determine model type from filename
                val modelType = when {
                    filename.contains("clip_text") || filename.contains("text") -> ModelType.CLIP_TEXT
                    filename.contains("clip_vision") || filename.contains("vision") -> ModelType.CLIP_VISION
                    filename.contains("vit") -> ModelType.VIT_BASE
                    else -> null
                }

                if (modelType == null) {
                    inputStream.close()
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadStatus = "Unknown model type",
                            errorMessage = "Could not determine model type from filename: $filename"
                        )
                    }
                    return@launch
                }

                // Save the model using streaming to avoid OOM
                val saveResult = modelManager.saveModelFromStream(modelType, inputStream)
                saveResult.onSuccess {
                    _uiState.update {
                        it.copy(
                            uploadStatus = "Model uploaded successfully",
                            uploadProgress = 1f,
                            isUploading = false,
                            errorMessage = null
                        )
                    }
                    loadModelStates()
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadStatus = "Failed to save model: ${error.message}",
                            errorMessage = error.message
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(tag, "Upload error", e)
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadStatus = "Upload failed: ${e.message}",
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    /**
     * Delete a model
     */
    fun deleteModel(type: ModelType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }

            val result = modelManager.deleteModel(type)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        deleteStatus = "Model deleted successfully",
                        isDeleting = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        deleteStatus = "Failed to delete model",
                        errorMessage = error.message,
                        isDeleting = false
                    )
                }
            }
        }
    }

    /**
     * Load a model into cache - USE WITH CAUTION for large models
     */
    fun loadModel(type: ModelType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModel = true) }

            val result = modelManager.loadModel(type)
            result.onSuccess { bytes ->
                _uiState.update {
                    it.copy(
                        loadStatus = "Model loaded (${bytes.size} bytes)",
                        isLoadingModel = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loadStatus = "Failed to load model",
                        errorMessage = error.message,
                        isLoadingModel = false
                    )
                }
            }
        }
    }

    /**
     * Clear model cache
     */
    fun clearCache() {
        modelManager.clearCache()
        _uiState.update {
            it.copy(clearCacheStatus = "Cache cleared")
        }
    }

    /**
     * Get model information
     */
    fun getModelInfo(type: ModelType): ModelInfo? {
        return modelManager.getModelInfo(type)
    }

    /**
     * Reset error state
     */
    fun resetError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        modelManager.close()
    }

    companion object {
        private const val tag = "ModelViewModel"

        private fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            return java.text.DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
        }
    }
}

/**
 * UI state for model management
 */
data class ModelUiState(
    val modelStates: Map<ModelType, ModelState> = emptyMap(),
    val totalStorageUsed: Long = 0L,
    val isDownloading: Boolean = false,
    val currentDownload: ModelConfig? = null,
    val downloadProgress: Float = 0f,
    val downloadStatus: String = "",
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val uploadStatus: String = "",
    val isDeleting: Boolean = false,
    val deleteStatus: String = "",
    val isLoadingModel: Boolean = false,
    val loadStatus: String = "",
    val clearCacheStatus: String = "",
    val errorMessage: String? = null
)
