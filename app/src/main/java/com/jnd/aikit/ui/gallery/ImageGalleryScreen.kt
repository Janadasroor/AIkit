package com.jnd.aikit.ui.gallery

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.jnd.aikit.ui.theme.DarkAppBarDark
import com.jnd.aikit.ui.theme.DarkAppBarLight
import com.jnd.aikit.ui.theme.ShadowLight
import com.jnd.aikit.ui.theme.ShadowMedium
import kotlinx.coroutines.delay

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
    val gridState = rememberLazyGridState()

    // Check if all images are processed (hide processing UI if all are processed)
    val allImagesProcessed = uiState.images.all { it.isProcessed }

    // Animation states
    var screenVisible by remember { mutableStateOf(false) }
    val fadeInAnim = remember { Animatable(0f) }
    val slideInAnim = remember { Animatable(50f) }

    LaunchedEffect(Unit) {
        screenVisible = true
        fadeInAnim.animateTo(1f, animationSpec = tween(600, easing = EaseOutCubic))
        slideInAnim.animateTo(0f, animationSpec = tween(500, easing = EaseOutCubic))
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = screenVisible,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(400, easing = EaseOutCubic)
                ) + fadeIn()
            ) {
                TopAppBar(
                    title = {
                        Column {
//                            Text(
//                                text = folder.name,
//                                style = MaterialTheme.typography.headlineSmall,
//                                fontWeight = FontWeight.SemiBold
//                            )
                            AnimatedVisibility(
                                visible = screenVisible,
                                enter = fadeIn(animationSpec = tween(800, delayMillis = 200))
                            ) {
                                Text(
                                    text = "${folder.imageCount} images",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Search button with animation
                        AnimatedVisibility(
                            visible = screenVisible,
                            enter = slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(400, delayMillis = 100, easing = EaseOutCubic)
                            )
                        ) {
                            IconButton(onClick = onNavigateToSearch) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }

                        // Process folder button (only show if not all images are processed)
                        AnimatedVisibility(
                            visible = screenVisible && !allImagesProcessed,
                            enter = slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(400, delayMillis = 200, easing = EaseOutCubic)
                            )
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.processFolderImages() },
                                modifier = Modifier.padding(end = 8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Process All", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        // Process selected button (only show if images are selected and not all images are processed)
                        AnimatedVisibility(
                            visible = uiState.selectedImages.isNotEmpty() && !allImagesProcessed,
                            enter = scaleIn(animationSpec = tween(300, easing = EaseOutBack)) + fadeIn(),
                            exit = scaleOut(animationSpec = tween(200)) + fadeOut()
                        ) {
                            Button(
                                onClick = { viewModel.processSelectedImages() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Process ${uiState.selectedImages.size}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isSystemInDarkTheme()) DarkAppBarDark else DarkAppBarLight,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.selectedImages.isNotEmpty() && !allImagesProcessed,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = EaseOutCubic)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200)
                )
            ) {
                BottomAppBar(
                    containerColor = if (isSystemInDarkTheme()) DarkAppBarDark else DarkAppBarLight,
                    tonalElevation = 8.dp
                ) {
                    Text(
                        text = "${uiState.selectedImages.size} selected",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedButton(
                        onClick = {
                            viewModel.uiState.value.images.forEach { image ->
                                viewModel.toggleImageSelection(image.id)
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Select All", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = {
                            // Clear selection
                            uiState.selectedImages.forEach { imageId ->
                                viewModel.toggleImageSelection(imageId)
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .graphicsLayer {
                    alpha = fadeInAnim.value
                    translationY = slideInAnim.value
                }
        ) {

            // Enhanced floating action button for search
            AnimatedVisibility(
                visible = screenVisible && uiState.selectedImages.isEmpty(),
                enter = scaleIn(
                    animationSpec = tween(400, delayMillis = 600, easing = EaseOutBack)
                ) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = onNavigateToSearch,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp)),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 12.dp
                    )
                ) {
                    Icon(
                        Icons.Default.ImageSearch,
                        contentDescription = "AI Search",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                uiState.images.isEmpty() -> {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(500, delayMillis = 200))
                    ) {
                        EmptyState(
                            modifier = Modifier.align(Alignment.Center),
                            message = "No images found",
                            subMessage = "This folder appears to be empty"
                        )
                    }
                }
                else -> {
                    AnimatedVisibility(
                        visible = screenVisible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 300))
                    ) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            contentPadding = PaddingValues(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.images,
                                key = { it.id }
                            ) { image ->
                                var itemVisible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    delay(50) // Stagger animation
                                    itemVisible = true
                                }

                                AnimatedVisibility(
                                    visible = itemVisible,
                                    enter = scaleIn(
                                        animationSpec = tween(400, easing = EaseOutBack)
                                    ) + fadeIn()
                                ) {
                                    GalleryImageItem(
                                        image = image,
                                        isSelected = image.id in uiState.selectedImages && !allImagesProcessed,
                                        onImageClick = { if (!allImagesProcessed) viewModel.toggleImageSelection(image.id) },
                                        onImageLongClick = { onSearchByImage(image) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Enhanced processing overlay
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
 * Individual image item in the gallery grid with modern design
 */
@Composable
private fun GalleryImageItem(
    image: GalleryImage,
    isSelected: Boolean,
    onImageClick: () -> Unit,
    onImageLongClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isPressed) 12.dp else if (isSelected) 8.dp else 4.dp,
        animationSpec = tween(200)
    )

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(16.dp),
                ambientColor = ShadowMedium,
                spotColor = ShadowMedium
            )
            .combinedClickable(
                onClick = onImageClick,
                onLongClick = onImageLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 6.dp else 2.dp,
            pressedElevation = 12.dp,
            hoveredElevation = 8.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.1f),
                                    Color.Black.copy(alpha = 0.4f)
                                )
                            )
                        )
                ) {
                    // Selection checkmark
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(28.dp)
                            .shadow(4.dp, RoundedCornerShape(50))
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                RoundedCornerShape(50)
                            )
                            .padding(4.dp)
                    )
                }
            }

            // Processing indicator
            if (image.isProcessed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .shadow(2.dp, RoundedCornerShape(50))
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(50)
                        )
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Processed",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Subtle gradient overlay for better text contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.1f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Enhanced processing overlay with modern design and animations
 */
@Composable
private fun ProcessingOverlay(
    status: ProcessingStatus,
    onDismiss: () -> Unit
) {
    // Animated background
    val backgroundAlpha by animateFloatAsState(
        targetValue = 0.6f,
        animationSpec = tween(400)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        // Animated card entrance
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(
                animationSpec = tween(400, easing = EaseOutBack)
            ) + fadeIn()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .shadow(16.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (status.state) {
                        ProcessingState.PROCESSING_FOLDER -> {
                            // Animated progress indicator
                            val infiniteTransition = rememberInfiniteTransition()
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2000, easing = LinearEasing)
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .graphicsLayer { rotationZ = rotation }
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.fillMaxSize(),
                                    strokeWidth = 4.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Processing folder...",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        ProcessingState.PROCESSING_IMAGES -> {
                            // Enhanced progress with animation
                            val progressAnim by animateFloatAsState(
                                targetValue = status.progress,
                                animationSpec = tween(300)
                            )

                            CircularProgressIndicator(
                                progress = { progressAnim },
                                modifier = Modifier.size(64.dp),
                                strokeWidth = 4.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Processing images...",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            AnimatedVisibility(
                                visible = status.currentItem.isNotEmpty(),
                                enter = fadeIn() + slideInVertically()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = status.currentItem,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${status.processedCount}/${status.totalCount}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        ProcessingState.SEARCHING -> {
                            val infiniteTransition = rememberInfiniteTransition()
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = EaseInOut),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )

                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(64.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale },
                                strokeWidth = 4.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Searching...",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        ProcessingState.COMPLETED -> {
                            // Success animation
                            val successScale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )

                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(64.dp)
                                    .graphicsLayer { scaleX = successScale; scaleY = successScale }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Processing Complete!",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Processed ${status.processedCount} images",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (status.errorMessage != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = status.errorMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("OK", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        ProcessingState.COMPLETED_WITH_ERRORS -> {
                            val warningScale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                )
                            )

                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Completed with errors",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .size(64.dp)
                                    .graphicsLayer { scaleX = warningScale; scaleY = warningScale }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Processing Completed with Issues",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Processed ${status.processedCount}/${status.totalCount} images",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (status.errorMessage != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = status.errorMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("OK", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        ProcessingState.ERROR -> {
                            val errorShake by animateFloatAsState(
                                targetValue = 0f,
                                animationSpec = repeatable(
                                    iterations = 3,
                                    animation = tween(100),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )

                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(64.dp)
                                    .graphicsLayer { translationX = errorShake }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = status.errorMessage ?: "Unknown error occurred",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("OK", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        ProcessingState.IDLE -> {}
                    }
                }
            }
        }
    }
}
