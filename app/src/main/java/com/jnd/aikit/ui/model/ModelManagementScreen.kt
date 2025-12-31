package com.jnd.aikit.ui.model

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jnd.aikit.model.*
import java.text.DecimalFormat

/**
 * Model management screen for downloading and managing ONNX models
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    onBackPressed: () -> Unit,
    viewModel: ModelViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState(ModelUiState())
    val modelStates by viewModel.modelStates.collectAsState(emptyMap())
    val context = LocalContext.current

    // File picker for model uploads
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { selectedUri ->
            // Handle the selected file
            viewModel.uploadModel(selectedUri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Model Management",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearCache() }) {
                        Icon(Icons.Default.CleaningServices, contentDescription = "Clear Cache")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Storage info
            StorageInfoCard(
                totalStorageUsed = uiState.totalStorageUsed,
                modifier = Modifier.padding(16.dp)
            )

            // Model list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ModelConfig.ALL_MODELS) { config ->
                    val state = modelStates[config.type] ?: ModelState(config, ModelStatus.NOT_DOWNLOADED)
                    ModelCard(
                        config = config,
                        state = state,
                        isUploading = uiState.isUploading,
                        uploadProgress = uiState.uploadProgress,
                        onUpload = { filePickerLauncher.launch("*/*") }, // Launch file picker for any file
                        onDelete = { viewModel.deleteModel(config.type) },
                        onLoad = { viewModel.loadModel(config.type) },
                        viewModel = viewModel
                    )
                }
            }

            // Status messages
            if (uiState.downloadStatus.isNotEmpty()) {
                StatusCard(
                    message = uiState.downloadStatus,
                    isError = uiState.errorMessage != null,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/**
 * Storage information card
 */
@Composable
private fun StorageInfoCard(
    totalStorageUsed: Long,
    modifier: Modifier = Modifier
) {
    val formatter = DecimalFormat("#.##")

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Model Storage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${formatter.format(totalStorageUsed / (1024.0 * 1024.0))} MB used",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Individual model card
 */
@Composable
private fun ModelCard(
    config: ModelConfig,
    state: ModelState,
    isUploading: Boolean,
    uploadProgress: Float,
    onUpload: (android.net.Uri) -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    viewModel: ModelViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = config.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status indicator
                StatusIndicator(state.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Model info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "v${config.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                    // Show actual file size if available, otherwise show model info
                    val modelInfo = viewModel.getModelInfo(config.type)
                    if (modelInfo != null && modelInfo.isDownloaded && modelInfo.fileSize > 0) {
                        Text(
                            text = formatFileSize(modelInfo.fileSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "ONNX Model",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar for uploading
            if (isUploading && uploadProgress > 0f) {
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state.status) {
                    ModelStatus.NOT_DOWNLOADED -> {
                        Button(
                            onClick = { onUpload(Uri.EMPTY) }, // Launch file picker
                            enabled = !isUploading
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.FileOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("Upload")
                        }
                    }

                    ModelStatus.DOWNLOADED -> {
                        OutlinedButton(onClick = onLoad) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Load")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }

                    ModelStatus.READY -> {
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }

                    ModelStatus.DOWNLOADING,
                    ModelStatus.LOADING,
                    ModelStatus.ERROR -> {
                        // Show status, no actions
                        Text(
                            text = state.status.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (state.status) {
                                ModelStatus.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }

            // Error message
            state.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Status indicator for model state
 */
@Composable
private fun StatusIndicator(status: ModelStatus) {
    val (icon, color) = when (status) {
        ModelStatus.NOT_DOWNLOADED -> Icons.Default.CloudDownload to MaterialTheme.colorScheme.outline
        ModelStatus.DOWNLOADING -> Icons.Default.Downloading to MaterialTheme.colorScheme.primary
        ModelStatus.DOWNLOADED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.secondary
        ModelStatus.LOADING -> Icons.Default.Refresh to MaterialTheme.colorScheme.primary
        ModelStatus.READY -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        ModelStatus.ERROR -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        tint = color,
        modifier = Modifier.size(24.dp)
    )
}

/**
 * Status card for messages
 */
@Composable
private fun StatusCard(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError)
                MaterialTheme.colorScheme.onErrorContainer
            else
                MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Format file size for display
 */
private fun formatFileSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0

    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }

    return String.format("%.1f %s", size, units[unitIndex])
}
