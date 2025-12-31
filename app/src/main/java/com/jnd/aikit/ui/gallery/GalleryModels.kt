package com.jnd.aikit.ui.gallery

import android.net.Uri
import androidx.compose.runtime.Immutable

/**
 * Data models for the gallery application
 */

@Immutable
data class GalleryFolder(
    val id: String,
    val name: String,
    val path: String,
    val imageCount: Int,
    val thumbnailUri: Uri?,
    val lastModified: Long
)

@Immutable
data class GalleryImage(
    val id: String,
    val uri: Uri,
    val folderId: String,
    val displayName: String,
    val path: String,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val isProcessed: Boolean = false,
    val embeddingId: String? = null
)

@Immutable
data class SearchResult(
    val image: GalleryImage,
    val score: Float,
    val rank: Int,
    val matchedText: String? = null
)

enum class ProcessingState {
    IDLE,
    PROCESSING_FOLDER,
    PROCESSING_IMAGES,
    SEARCHING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    ERROR
}

@Immutable
data class ProcessingStatus(
    val state: ProcessingState,
    val currentItem: String = "",
    val progress: Float = 0f, // 0.0 to 1.0
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val errorMessage: String? = null
)

@Immutable
data class GalleryUiState(
    val folders: List<GalleryFolder> = emptyList(),
    val selectedFolder: GalleryFolder? = null,
    val images: List<GalleryImage> = emptyList(),
    val searchResults: List<SearchResult> = emptyList(),
    val processingStatus: ProcessingStatus = ProcessingStatus(ProcessingState.IDLE),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedImages: Set<String> = emptySet()
)
