package com.jnd.aikit.ui.gallery

import androidx.compose.runtime.Composable

/**
 * Navigation states for the gallery app
 */
sealed class GalleryScreen {
    object FolderSelection : GalleryScreen()
    data class ImageGallery(val folder: GalleryFolder) : GalleryScreen()
    object Search : GalleryScreen()
    object MemoryManagement : GalleryScreen()
    object ModelManagement : GalleryScreen()
    object DeveloperTest : GalleryScreen()
    object Settings : GalleryScreen()
}

/**
 * Main navigation composable that uses the beautiful drawer navigation
 */
@Composable
fun GalleryApp() {
    DrawerNavigation()
}
