package com.jnd.aikit.ui.gallery

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jnd.aikit.ui.theme.DarkAppBarDark
import com.jnd.aikit.ui.theme.DarkAppBarLight
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import com.jnd.aikit.model.ModelManager
import com.jnd.aikit.model.ModelType

/**
 * AI-powered search screen for finding similar images
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onImageSelected: (GalleryImage) -> Unit,
    onBackPressed: () -> Unit,
    viewModel: GalleryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf(uiState.searchQuery) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var dismissModelWarning by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Check model availability reactively
    val modelStates by viewModel.modelStates.collectAsState(initial = emptyMap())
    val clipTextAvailable = modelStates[ModelType.CLIP_TEXT]?.status?.let { it == com.jnd.aikit.model.ModelStatus.DOWNLOADED || it == com.jnd.aikit.model.ModelStatus.READY } ?: false
    val clipVisionAvailable = modelStates[ModelType.CLIP_VISION]?.status?.let { it == com.jnd.aikit.model.ModelStatus.DOWNLOADED || it == com.jnd.aikit.model.ModelStatus.READY } ?: false
    val vitAvailable = modelStates[ModelType.VIT_BASE]?.status?.let { it == com.jnd.aikit.model.ModelStatus.DOWNLOADED || it == com.jnd.aikit.model.ModelStatus.READY } ?: false

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        }
    }

    Scaffold(
        topBar = {
//            TopAppBar(
//                title = {
//                    Text(
//                        text = "AI Image Search",
//                        style = MaterialTheme.typography.headlineSmall,
//                        fontWeight = FontWeight.Bold
//                    )
//                },
//                navigationIcon = {
//                    IconButton(onClick = onBackPressed) {
//                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
//                    }
//                },
//                colors = TopAppBarDefaults.topAppBarColors(
//                    containerColor = if (isSystemInDarkTheme()) DarkAppBarDark else DarkAppBarLight,
//                    titleContentColor = Color.White,
//                    navigationIconContentColor = Color.White
//                )
//            )
        }
        ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Model availability warnings
            if (!clipTextAvailable || !clipVisionAvailable || !vitAvailable) {
                AnimatedVisibility(
                    visible = !dismissModelWarning,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "⚠️ Models Not Available",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = { dismissModelWarning = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Dismiss warning",
                                            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "The following AI models need to be downloaded and loaded for full functionality:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val missingModels = mutableListOf<String>()
                                if (!clipTextAvailable) missingModels.add("CLIP Text")
                                if (!clipVisionAvailable) missingModels.add("CLIP Vision")
                                if (!vitAvailable) missingModels.add("ViT Base")

                                missingModels.forEach { model ->
                                    Text(
                                        text = "• $model",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Go to Settings → Model Management to download models",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            // Search input
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "AI-Powered Image Search",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Text Search Section
                    Text(
                        text = "Search by Text (CLIP)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Describe what you're looking for") },
                        placeholder = { Text("e.g., sunset, mountain, beach, car...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Image Search Section
                    Text(
                        text = "Or Search by Image (ViT)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Selected image preview
                    selectedImageUri?.let { uri ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Selected image for search",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                            RoundedCornerShape(50)
                                        )
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Remove image")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Search buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Text search button
                        Button(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    viewModel.searchSimilarImages(searchQuery)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = searchQuery.isNotBlank() && clipTextAvailable
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Text Search")
                        }

                        // Image picker button
                        OutlinedButton(
                            onClick = {
                                // Request permission if needed
                                imagePickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.weight(1f),
                            enabled = clipVisionAvailable || vitAvailable
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick Image")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Image search button (only show if image is selected)
                    selectedImageUri?.let {
                        Button(
                            onClick = {
                                viewModel.searchByImage(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = vitAvailable // Prefer ViT for image search
                        ) {
                            Icon(Icons.Default.ImageSearch, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Search by Image (${if (vitAvailable) "ViT" else if (clipVisionAvailable) "CLIP" else "No Model"})")
                        }
                    }

                    if (uiState.searchResults.isNotEmpty()) {
                        Divider(modifier = Modifier.padding(vertical = 12.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Score Filter: ${uiState.scoreFilter.toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(100.dp)
                            )
                            Slider(
                                value = uiState.scoreFilter,
                                onValueChange = { viewModel.setScoreFilter(it) },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Clear results button
                    if (uiState.searchResults.isNotEmpty() || uiState.searchQuery.isNotEmpty() || selectedImageUri != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.clearSearch()
                                searchQuery = ""
                                selectedImageUri = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear All")
                        }
                    }
                }
            }

            // Search suggestions
            if (uiState.searchQuery.isEmpty()) {
                SearchSuggestions(
                    onSuggestionClick = { suggestion ->
                        searchQuery = suggestion
                        viewModel.searchSimilarImages(suggestion)
                    }
                )
            }

            // Results
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.processingStatus.state == ProcessingState.SEARCHING -> {
                        SearchLoadingState()
                    }
                    uiState.searchResults.isNotEmpty() -> {
                        val filteredResults = uiState.searchResults.filter { 
                            (it.score * 100) >= uiState.scoreFilter 
                        }
                        
                        if (filteredResults.isEmpty()) {
                            EmptySearchState(message = "No results match your score filter (${uiState.scoreFilter.toInt()}%+)")
                        } else {
                            SearchResultsGrid(
                                results = filteredResults,
                                onImageClick = onImageSelected
                            )
                        }
                    }
                    uiState.searchQuery.isNotEmpty() && uiState.processingStatus.state == ProcessingState.IDLE -> {
                        EmptySearchState()
                    }
                    else -> {
                        SearchWelcomeState()
                    }
                }

                // Error message
                uiState.processingStatus.errorMessage?.let { error ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Text(text = error)
                    }
                }
            }
        }
    }
}

/**
 * Search suggestions for common queries
 */
@Composable
private fun SearchSuggestions(onSuggestionClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Popular searches",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            val suggestions = listOf(
                "nature", "landscape", "sunset", "beach", "mountain",
                "car", "city", "food", "animal", "flower", "portrait"
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        text = suggestion,
                        onClick = { onSuggestionClick(suggestion) }
                    )
                }
            }
        }
    }
}

/**
 * Individual suggestion chip
 */
@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Loading state during search
 */
@Composable
private fun SearchLoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Searching with AI...",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Finding images that match your description",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Welcome state when no search has been performed
 */
@Composable
private fun SearchWelcomeState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "AI-Powered Image Search",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Describe any image you're looking for and our AI will find visually similar images from your processed gallery.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Try: \"sunset over mountains\" or \"red sports car\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * Empty state when search returned no results
 */
@Composable
private fun EmptySearchState(message: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No images found",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message ?: "Try different keywords or make sure you've processed some images first.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * Grid displaying search results
 */
@Composable
private fun SearchResultsGrid(
    results: List<SearchResult>,
    onImageClick: (GalleryImage) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(results) { result ->
            SearchResultItem(
                result = result,
                onClick = { onImageClick(result.image) }
            )
        }
    }
}

/**
 * Individual search result item
 */
@Composable
private fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(result.image.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = result.image.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(R.drawable.ic_launcher_foreground),
                error = painterResource(R.drawable.ic_launcher_foreground)
            )

            // Similarity score overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        RoundedCornerShape(bottomStart = 8.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${(result.score * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Rank indicator
            if (result.rank <= 3) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            RoundedCornerShape(bottomEnd = 8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#${result.rank}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}
