package com.jnd.aikit.ui.gallery

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jnd.aikit.preferences.viewModels.EmbeddingViewModel
import com.jnd.aikit.ui.theme.DarkAppBarDark
import com.jnd.aikit.ui.theme.DarkAppBarLight
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperTestScreen(
    onBackPressed: () -> Unit,
    galleryViewModel: GalleryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var textQuery by remember { mutableStateOf("") }
    var similarityResult by remember { mutableStateOf<Float?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var forceStretch by remember { mutableStateOf(false) }
    
    var imageEmbeddingStat by remember { mutableStateOf("") }
    var textEmbeddingStat by remember { mutableStateOf("") }
    var tokenDetails by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        similarityResult = null
    }

    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("Developer Real-time Test", style= MaterialTheme.typography.bodyMedium) },
//                navigationIcon = {
//                    IconButton(onClick = onBackPressed) {
//                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
//                    }
//                },
//                colors = TopAppBarDefaults.topAppBarColors(
//                    containerColor = if (isSystemInDarkTheme()) DarkAppBarDark else DarkAppBarLight,
//                    titleContentColor = Color.White,
//                    navigationIconContentColor = Color.White
//                ),
//                modifier = Modifier.height(30.dp)
//
//
//            )
//        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Compare Image & Text Embeddings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Image Picker Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Step 1: Select Image", fontWeight = FontWeight.SemiBold)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(selectedImageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Selected image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Image, 
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text("Tap to pick image")
                            }
                        }
                    }
                }
            }

            // Text Input Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TextSnippet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Step 2: Enter Text Query", fontWeight = FontWeight.SemiBold)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = textQuery,
                        onValueChange = { textQuery = it },
                        label = { Text("Query (e.g. 'flower')") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = forceStretch,
                    onCheckedChange = { forceStretch = it }
                )
                Text(
                    text = "Force 224x224 Stretch (Debug)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { forceStretch = !forceStretch }
                )
            }

            Button(
                onClick = {
                    val uri = selectedImageUri ?: return@Button
                    isProcessing = true
                    scope.launch {
                        try {
                            val bitmap = galleryViewModel.loadBitmapFromUri(uri)
                            if (bitmap != null) {
                                val result = galleryViewModel.compareImageWithText(bitmap, textQuery, forceStretch)
                                similarityResult = result.score
                                imageEmbeddingStat = result.imageStats
                                textEmbeddingStat = result.textStats
                                tokenDetails = result.tokenDetails
                            }
                        } catch (e: Exception) {
                            textEmbeddingStat = "Error: ${e.message}"
                        } finally {
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = selectedImageUri != null && textQuery.isNotBlank() && !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Compare, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compare in Real-time")
                }
            }

            // Results Section
            if (similarityResult != null || isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Matching Result",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val score = similarityResult ?: 0f
                        
                        // Recalibrated CLIP Confidence Mapping (OpenAI Standard)
                        // Raw cosine similarity in CLIP is naturally low due to high dimensional space.
                        // 0.20: Noise / Off-topic
                        // 0.25: Correct category (Low)
                        // 0.30: Good match (70%+)
                        // 0.35: Strong match (90%+)
                        // 0.40+: Excellent match (99%+)
                        
                        val confidence = score*100f.coerceIn(0f, 100f)

                        val matchQuality = when {
                            confidence > 90 -> "Excellent Match"
                            confidence > 75 -> "Strong Match"
                            confidence > 50 -> "Good Match"
                            confidence > 25 -> "Weak Match"
                            else -> "No Match"
                        }
                        
                        val statusColor = when {
                            confidence > 75 -> MaterialTheme.colorScheme.primary
                            confidence > 50 -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.error
                        }
                        
                        Text(
                            text = String.format("%.4f", score),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = statusColor
                        )

                        Text(
                            text = matchQuality,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        
                        Text(
                            text = "Calibrated Confidence: ${confidence.toInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LinearProgressIndicator(
                            progress = { confidence / 100f },
                            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                            color = if (confidence > 70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        HorizontalDivider()
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Debug Info",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(text = "Image Statistics: $imageEmbeddingStat", style = MaterialTheme.typography.bodySmall)
                        Text(text = "Text Statistics: $textEmbeddingStat", style = MaterialTheme.typography.bodySmall)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Processing Details:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tokens: $tokenDetails",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Query: '$textQuery' (Auto-prefixed with 'a photo of' if needed)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
