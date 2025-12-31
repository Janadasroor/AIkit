package com.jnd.aikit.ui.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jnd.aikit.R

/**
 * Image gallery screen - shows images from selected folder
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGalleryScreen(
    folder: GalleryFolder,
    onBackPressed: () -> Unit,
    onSearchByImage: (GalleryImage) -> Unit,
    onNavigateToSearch: () -> Unit,
    viewModel: GalleryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${folder.imageCount} images",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Search button
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }

                    // Process folder button
                    FilledTonalButton(
                        onClick = { viewModel.processFolderImages() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Process All")
                    }

                    // Process selected button (only show if images are selected)
                    if (uiState.selectedImages.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.processSelectedImages() }
                        ) {
                            Text("Process ${uiState.selectedImages.size}")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            if (uiState.selectedImages.isNotEmpty()) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${uiState.selectedImages.size} selected",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedButton(
                        onClick = {
                            viewModel.uiState.value.images.forEach { image ->
                                viewModel.toggleImageSelection(image.id)
                            }
                        }
                    ) {
                        Text("Select All")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = {
                            // Clear selection
                            uiState.selectedImages.forEach { imageId ->
                                viewModel.toggleImageSelection(imageId)
                            }
                        }
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // Floating action button for search
            FloatingActionButton(
                onClick = onNavigateToSearch,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "AI Search")
            }
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.images.isEmpty()) {
                EmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    message = "No images found",
                    subMessage = "This folder appears to be empty"
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.images) { image ->
                        GalleryImageItem(
                            image = image,
                            isSelected = image.id in uiState.selectedImages,
                            onImageClick = { viewModel.toggleImageSelection(image.id) },
                            onImageLongClick = { onSearchByImage(image) }
                        )
                    }
                }
            }

            // Processing overlay
            if (uiState.processingStatus.state != ProcessingState.IDLE) {
                ProcessingOverlay(
                    status = uiState.processingStatus,
                    onDismiss = { viewModel.resetProcessingStatus() }
                )
            }
        }
    }
}

/**
 * Individual image item in the gallery grid
 */
@Composable
private fun GalleryImageItem(
    image: GalleryImage,
    isSelected: Boolean,
    onImageClick: () -> Unit,
    onImageLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.LightGray
            )
            .clickable(onClick = onImageClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(image.uri)
                .crossfade(true)
                .build(),
            contentDescription = image.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            placeholder = painterResource(R.drawable.ic_launcher_foreground),
            error = painterResource(R.drawable.ic_launcher_foreground)
        )

        // Selection overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(alpha = 0.3f)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                )
            }
        }

        // Processing indicator
        if (image.isProcessed) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Processed",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        RoundedCornerShape(50)
                    )
                    .padding(2.dp)
            )
        }
    }
}

/**
 * Processing overlay with progress indicator
 */
@Composable
private fun ProcessingOverlay(
    status: ProcessingStatus,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (status.state) {
                    ProcessingState.PROCESSING_FOLDER -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Processing folder...",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    ProcessingState.PROCESSING_IMAGES -> {
                        CircularProgressIndicator(progress = { status.progress })
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Processing images...",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = status.currentItem,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${status.processedCount}/${status.totalCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ProcessingState.SEARCHING -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Searching...",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    ProcessingState.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Processing Complete!",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Processed ${status.processedCount} images",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (status.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = status.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismiss) {
                            Text("OK")
                        }
                    }
                    ProcessingState.COMPLETED_WITH_ERRORS -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Completed with errors",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Processing Completed with Issues",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Processed ${status.processedCount}/${status.totalCount} images",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (status.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = status.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismiss) {
                            Text("OK")
                        }
                    }
                    ProcessingState.ERROR -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = status.errorMessage ?: "Unknown error occurred",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismiss) {
                            Text("OK")
                        }
                    }
                    ProcessingState.IDLE -> {}
                }
            }
        }
    }
}
