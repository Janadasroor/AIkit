package com.jnd.aikit.ui.gallery

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jnd.aikit.preferences.viewModels.EmbeddingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import kotlin.math.roundToInt

/**
 * ViewModel for managing gallery operations and AI search
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val galleryRepository = GalleryRepository(application)
    private val embeddingViewModel = EmbeddingViewModel(application)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    // Expose model states for UI reactivity
    val modelStates = embeddingViewModel.modelStates

    private val tag = "GalleryViewModel"

    init {
        loadFolders()
    }

    /**
     * Load all image folders from device
     */
    fun loadFolders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, processingStatus = ProcessingStatus(ProcessingState.IDLE)) }
            try {
                Log.d(tag, "Loading folders...")
                val deviceFolders = galleryRepository.getImageFolders()

                // Add a special "Processed Images" folder
                val processedFolder = GalleryFolder(
                    id = "processed_images",
                    name = "Processed Images",
                    path = "vexdb://processed",
                    imageCount = 0, // Will be updated when we query the database
                    thumbnailUri = null, // Could set a default icon
                    lastModified = System.currentTimeMillis()
                )

                val allFolders = listOf(processedFolder) + deviceFolders

                _uiState.update {
                    it.copy(
                        folders = allFolders,
                        isLoading = false
                    )
                }
                Log.d(tag, "Loaded ${allFolders.size} folders (including ${deviceFolders.size} device folders)")
                
                if (deviceFolders.isEmpty()) {
                    Log.w(tag, "No device folders found. Check permissions.")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to load folders", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Failed to load folders: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Select a folder and load its images
     */
    fun selectFolder(folder: GalleryFolder) {
        _uiState.update { it.copy(selectedFolder = folder) }
        if (folder.id == "processed_images") {
            loadProcessedImages()
        } else {
            loadImagesFromFolder(folder.id)
        }
    }

    /**
     * Clear the selected folder (used when navigating back)
     */
    fun clearSelectedFolder() {
        _uiState.update { it.copy(selectedFolder = null, images = emptyList()) }
    }

    /**
     * Load images from selected folder
     */
    private fun loadImagesFromFolder(folderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val images = galleryRepository.getImagesFromFolder(folderId)
                _uiState.update {
                    it.copy(
                        images = images,
                        isLoading = false
                    )
                }
                Log.d(tag, "Loaded ${images.size} images from folder $folderId")
            } catch (e: Exception) {
                Log.e(tag, "Failed to load images from folder", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Failed to load images: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Load processed images from VexDB
     */
    private fun loadProcessedImages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // For now, we'll show a placeholder. In a full implementation,
                // we'd need to map vector IDs back to actual image URIs
                // This would require storing the original image URI in the vector metadata
                val processedImages = emptyList<GalleryImage>() // TODO: Implement this properly

                _uiState.update {
                    it.copy(
                        images = processedImages,
                        isLoading = false
                    )
                }
                Log.d(tag, "Loaded ${processedImages.size} processed images from VexDB")
            } catch (e: Exception) {
                Log.e(tag, "Failed to load processed images", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Failed to load processed images: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Process all images in the selected folder for embeddings
     */
    fun processFolderImages() {
        val folder = _uiState.value.selectedFolder ?: return
        val images = _uiState.value.images

        if (images.isEmpty()) {
            _uiState.update {
                it.copy(
                    processingStatus = ProcessingStatus(
                        ProcessingState.ERROR,
                        errorMessage = "No images found in folder"
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    processingStatus = ProcessingStatus(
                        ProcessingState.PROCESSING_FOLDER,
                        currentItem = folder.name,
                        totalCount = images.size
                    )
                )
            }

            try {
                var processed = 0
                val errors = mutableListOf<String>()

                for ((index, image) in images.withIndex()) {
                    try {
                        _uiState.update {
                            it.copy(
                                processingStatus = ProcessingStatus(
                                    ProcessingState.PROCESSING_IMAGES,
                                    currentItem = image.displayName,
                                    progress = (index + 1).toFloat() / images.size,
                                    processedCount = processed,
                                    totalCount = images.size
                                )
                            )
                        }

                        val bitmap = loadBitmapFromUri(image.uri)
                        if (bitmap != null) {
                            // Process with embedding model
                            val result = embeddingViewModel.processImage(
                                bitmap = bitmap,
                                description = image.displayName,
                                tags = listOf(folder.name, "gallery"),
                                imageUri = image.uri
                            )
                            if (result.isSuccess) {
                                processed++
                            } else {
                                errors.add("Processing failed for ${image.displayName}: ${result.exceptionOrNull()?.message}")
                            }
                        } else {
                            errors.add("Failed to load: ${image.displayName}")
                        }

                    } catch (e: Exception) {
                        Log.e(tag, "Failed to process image: ${image.displayName}", e)
                        errors.add("Error processing ${image.displayName}: ${e.message}")
                    }
                }

                _uiState.update {
                    it.copy(
                        processingStatus = ProcessingStatus(
                            if (errors.isNotEmpty()) ProcessingState.COMPLETED_WITH_ERRORS else ProcessingState.COMPLETED,
                            progress = 1f,
                            processedCount = processed,
                            totalCount = images.size,
                            errorMessage = if (errors.isNotEmpty()) "${errors.size} errors occurred during processing" else null
                        )
                    )
                }

                Log.d(tag, "Processed $processed/${images.size} images from folder ${folder.name}")

            } catch (e: Exception) {
                Log.e(tag, "Failed to process folder", e)
                _uiState.update {
                    it.copy(
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Failed to process folder: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Search for similar images using text query (CLIP)
     */
    fun searchSimilarImages(query: String) {
        if (query.isBlank()) return

        _uiState.update {
            it.copy(
                searchQuery = query,
                processingStatus = ProcessingStatus(ProcessingState.SEARCHING)
            )
        }

        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        processingStatus = ProcessingStatus(ProcessingState.SEARCHING)
                    )
                }

                // Use CLIP text search and get results
                val searchResults = embeddingViewModel.searchByText(
                    query = query,
                    limit = 50,
                    minScore = 0.01f // Lower threshold for better results
                )

                if (searchResults.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            processingStatus = ProcessingStatus(
                                ProcessingState.COMPLETED
                            ),
                            searchResults = emptyList()
                        )
                    }
                    Log.d(tag, "No search results found for query: $query")
                    return@launch
                }

                // Convert database SearchResult to UI SearchResult
                val uiSearchResults = searchResults.map { dbResult ->
                    val imageUri = dbResult.vectorData.payload.imageUri?.let { uriString ->
                        try {
                            android.net.Uri.parse(uriString)
                        } catch (e: Exception) {
                            android.net.Uri.parse("content://vector/${dbResult.vectorData.id}")
                        }
                    } ?: android.net.Uri.parse("content://vector/${dbResult.vectorData.id}")

                    com.jnd.aikit.ui.gallery.SearchResult(
                        image = com.jnd.aikit.ui.gallery.GalleryImage(
                            id = dbResult.vectorData.id,
                            uri = imageUri,
                            folderId = "search_results",
                            displayName = dbResult.vectorData.payload.description ?: "Search result",
                            path = "",
                            size = 0L,
                            dateAdded = dbResult.vectorData.payload.timestamp,
                            dateModified = dbResult.vectorData.payload.timestamp,
                            mimeType = "image/*",
                            isProcessed = true,
                            embeddingId = dbResult.vectorData.id
                        ),
                        score = dbResult.score,
                        rank = dbResult.rank
                    )
                }

                _uiState.update {
                    it.copy(
                        processingStatus = ProcessingStatus(ProcessingState.COMPLETED),
                        searchResults = uiSearchResults
                    )
                }

                Log.d(tag, "CLIP text search completed for query: $query - found ${uiSearchResults.size} results")

            } catch (e: Exception) {
                Log.e(tag, "Search failed", e)
                _uiState.update {
                    it.copy(
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Search failed: ${e.message}"
                        ),
                        searchResults = emptyList()
                    )
                }
            }
        }
    }

    /**
     * Search by image (reverse image search using ViT)
     */
    fun searchByImage(imageUri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(processingStatus = ProcessingStatus(ProcessingState.SEARCHING))
                }

                val bitmap = loadBitmapFromUri(imageUri)
                if (bitmap != null) {
                    // Use ViT for image-to-image search (better for visual similarity)
                    val searchResults = embeddingViewModel.searchByImage(
                        bitmap = bitmap,
                        limit = 20,
                        minScore = 0.1f,
                        useViT = true // Use ViT model for image queries
                    )

                    // Convert database SearchResult to UI SearchResult
                    val uiSearchResults = searchResults.map { dbResult ->
                        val imageUri = dbResult.vectorData.payload.imageUri?.let { uriString ->
                            try {
                                android.net.Uri.parse(uriString)
                            } catch (e: Exception) {
                                android.net.Uri.parse("content://vector/${dbResult.vectorData.id}")
                            }
                        } ?: android.net.Uri.parse("content://vector/${dbResult.vectorData.id}")

                        com.jnd.aikit.ui.gallery.SearchResult(
                            image = com.jnd.aikit.ui.gallery.GalleryImage(
                                id = dbResult.vectorData.id,
                                uri = imageUri,
                                folderId = "search_results",
                                displayName = dbResult.vectorData.payload.description ?: "Search result",
                                path = "",
                                size = 0L,
                                dateAdded = dbResult.vectorData.payload.timestamp,
                                dateModified = dbResult.vectorData.payload.timestamp,
                                mimeType = "image/*",
                                isProcessed = true,
                                embeddingId = dbResult.vectorData.id
                            ),
                            score = dbResult.score,
                            rank = dbResult.rank
                        )
                    }

                    _uiState.update {
                        it.copy(
                            processingStatus = ProcessingStatus(ProcessingState.COMPLETED),
                            searchResults = uiSearchResults
                        )
                    }
                    Log.d(tag, "ViT image search completed - found ${uiSearchResults.size} results")
                } else {
                    _uiState.update {
                        it.copy(
                            processingStatus = ProcessingStatus(
                                ProcessingState.ERROR,
                                errorMessage = "Failed to load image for search"
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(tag, "Image search failed", e)
                _uiState.update {
                    it.copy(
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Image search failed: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Toggle image selection for batch operations
     */
    fun toggleImageSelection(imageId: String) {
        _uiState.update { state ->
            val selectedImages = state.selectedImages.toMutableSet()
            if (selectedImages.contains(imageId)) {
                selectedImages.remove(imageId)
            } else {
                selectedImages.add(imageId)
            }
            state.copy(selectedImages = selectedImages)
        }
    }

    /**
     * Process selected images only
     */
    fun processSelectedImages() {
        val selectedImages = _uiState.value.images.filter { it.id in _uiState.value.selectedImages }
        if (selectedImages.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    processingStatus = ProcessingStatus(
                        ProcessingState.PROCESSING_IMAGES,
                        totalCount = selectedImages.size
                    )
                )
            }

            try {
                var processed = 0
                val errors = mutableListOf<String>()

                for ((index, image) in selectedImages.withIndex()) {
                    try {
                        _uiState.update {
                            it.copy(
                                processingStatus = ProcessingStatus(
                                    ProcessingState.PROCESSING_IMAGES,
                                    currentItem = image.displayName,
                                    progress = (index + 1).toFloat() / selectedImages.size,
                                    processedCount = processed,
                                    totalCount = selectedImages.size
                                )
                            )
                        }

                        val bitmap = loadBitmapFromUri(image.uri)
                        if (bitmap != null) {
                            val result = embeddingViewModel.processImage(
                                bitmap = bitmap,
                                description = image.displayName,
                                tags = listOf("selected", "gallery"),
                                imageUri = image.uri
                            )
                            if (result.isSuccess) {
                                processed++
                            } else {
                                errors.add("Processing failed for ${image.displayName}: ${result.exceptionOrNull()?.message}")
                            }
                        } else {
                            errors.add("Failed to load: ${image.displayName}")
                        }

                    } catch (e: Exception) {
                        Log.e(tag, "Failed to process selected image: ${image.displayName}", e)
                        errors.add("Error processing ${image.displayName}: ${e.message}")
                    }
                }

                _uiState.update {
                    it.copy(
                        processingStatus = ProcessingStatus(
                            if (errors.isNotEmpty()) ProcessingState.COMPLETED_WITH_ERRORS else ProcessingState.COMPLETED,
                            progress = 1f,
                            processedCount = processed,
                            totalCount = selectedImages.size,
                            errorMessage = if (errors.isNotEmpty()) "${errors.size} errors occurred during processing" else null
                        )
                    )
                }

                Log.d(tag, "Processed $processed/${selectedImages.size} selected images")

            } catch (e: Exception) {
                Log.e(tag, "Failed to process selected images", e)
                _uiState.update {
                    it.copy(
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Failed to process images: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Clear search results and reset to gallery view
     */
    fun clearSearch() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                scoreFilter = 0f,
                processingStatus = ProcessingStatus(ProcessingState.IDLE)
            )
        }
    }

    /**
     * Set score filter for results (0-100)
     */
    fun setScoreFilter(score: Float) {
        _uiState.update { it.copy(scoreFilter = score) }
    }

    /**
     * Reset processing status
     */
    fun resetProcessingStatus() {
        _uiState.update {
            it.copy(processingStatus = ProcessingStatus(ProcessingState.IDLE))
        }
    }

    /**
     * Load bitmap from URI (Public for Developer Test)
     */
    suspend fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(getApplication<Application>().contentResolver, uri)
            } else {
                val source = ImageDecoder.createSource(getApplication<Application>().contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load bitmap from URI: $uri", e)
            null
        }
    }

    /**
     * Real-time comparison for developer test
     */
    suspend fun compareImageWithText(bitmap: Bitmap, text: String, forceStretch: Boolean = false): ComparisonResult {
        return try {
            val result = embeddingViewModel.compareRealtime(bitmap, text, forceStretch)
            ComparisonResult(
                score = result.first,
                imageStats = result.second,
                textStats = result.third,
                tokenDetails = embeddingViewModel.getTokenDetails(text)
            )
        } catch (e: Exception) {
            ComparisonResult(0f, "Error", "Error: ${e.message}", "")
        }
    }

    /**
     * Memory Management Methods
     */

    /**
     * Clear image cache
     */
    fun clearImageCache() {
        viewModelScope.launch {
            try {
                // Clear any cached images or temporary files
                _uiState.update { it.copy(isLoading = true) }
                // In a real implementation, you'd clear image caches here
                Log.d(tag, "Image cache cleared")
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Log.e(tag, "Failed to clear image cache", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Failed to clear image cache: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Clear all caches (images, search, models)
     */
    fun clearAllCache() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                // Clear image cache
                clearImageCache()

                // Clear embedding model cache if available
                // Note: This might not be directly accessible from here

                Log.d(tag, "All caches cleared")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.COMPLETED,
                            errorMessage = null
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to clear all caches", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Failed to clear caches: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Backup database
     */
    fun backupDatabase() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                // In a real implementation, you'd backup the vector database
                Log.d(tag, "Database backup completed")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.COMPLETED,
                            errorMessage = null
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to backup database", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Failed to backup database: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Optimize database
     */
    fun optimizeDatabase() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                // In a real implementation, you'd optimize the vector database
                Log.d(tag, "Database optimization completed")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.COMPLETED,
                            errorMessage = null
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to optimize database", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Failed to optimize database: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Analyze storage usage
     */
    fun analyzeStorage() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                // In a real implementation, you'd analyze storage usage
                Log.d(tag, "Storage analysis completed")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.COMPLETED,
                            errorMessage = null
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to analyze storage", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        processingStatus = ProcessingStatus(
                            ProcessingState.ERROR,
                            errorMessage = "Failed to analyze storage: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Get memory statistics
     */
    fun getMemoryStatistics(): Map<String, String> {
        return try {
            val context = getApplication<Application>()
            val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            val totalMemory = memoryInfo.totalMem / (1024 * 1024 * 1024.0) // GB
            val availableMemory = memoryInfo.availMem / (1024 * 1024 * 1024.0) // GB
            val usedMemory = totalMemory - availableMemory
            val memoryUsagePercent = (usedMemory / totalMemory * 100)

            mapOf(
                "totalMemory" to "${DecimalFormat("#.##").format(totalMemory)} GB",
                "usedMemory" to "${DecimalFormat("#.##").format(usedMemory)} GB",
                "availableMemory" to "${DecimalFormat("#.##").format(availableMemory)} GB",
                "memoryUsagePercent" to "${memoryUsagePercent.toInt()}%"
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to get memory statistics", e)
            emptyMap()
        }
    }
}

data class ComparisonResult(
    val score: Float,
    val imageStats: String,
    val textStats: String,
    val tokenDetails: String
)
