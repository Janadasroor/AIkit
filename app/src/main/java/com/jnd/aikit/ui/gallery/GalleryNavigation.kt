package com.jnd.aikit.ui.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.jnd.aikit.ui.model.ModelManagementScreen

/**
 * Navigation states for the gallery app
 */
sealed class GalleryScreen {
    object FolderSelection : GalleryScreen()
    data class ImageGallery(val folder: GalleryFolder) : GalleryScreen()
    object Search : GalleryScreen()
    object ModelManagement : GalleryScreen()
}

/**
 * Main navigation composable that handles screen switching
 */
@Composable
fun GalleryApp() {
    val viewModel: GalleryViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Navigation state
    val currentScreen = remember { mutableStateOf<GalleryScreen>(GalleryScreen.FolderSelection) }

    // Update screen based on view model state
    when {
        uiState.selectedFolder != null && currentScreen.value is GalleryScreen.FolderSelection -> {
            currentScreen.value = GalleryScreen.ImageGallery(uiState.selectedFolder!!)
        }
    }

    when (val screen = currentScreen.value) {
        is GalleryScreen.FolderSelection -> {
            FolderSelectionScreen(
                onFolderSelected = { folder ->
                    viewModel.selectFolder(folder)
                    currentScreen.value = GalleryScreen.ImageGallery(folder)
                },
                onNavigateToSearch = {
                    currentScreen.value = GalleryScreen.Search
                },
                onNavigateToModelManagement = {
                    currentScreen.value = GalleryScreen.ModelManagement
                }
            )
        }

        is GalleryScreen.ImageGallery -> {
            ImageGalleryScreen(
                folder = screen.folder,
                onBackPressed = {
                    viewModel.selectFolder(screen.folder) // Clear selection
                    currentScreen.value = GalleryScreen.FolderSelection
                },
                onSearchByImage = { image ->
                    viewModel.searchByImage(image.uri)
                },
                onNavigateToSearch = {
                    currentScreen.value = GalleryScreen.Search
                }
            )
        }

        is GalleryScreen.Search -> {
            SearchScreen(
                onImageSelected = { image ->
                    // Handle image selection from search results
                    // Could navigate to image detail or just show info
                },
                onBackPressed = {
                    currentScreen.value = GalleryScreen.FolderSelection
                }
            )
        }

        is GalleryScreen.ModelManagement -> {
            ModelManagementScreen(
                onBackPressed = {
                    currentScreen.value = GalleryScreen.FolderSelection
                }
            )
        }
    }
}
