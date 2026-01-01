package com.jnd.aikit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jnd.aikit.ui.gallery.GalleryApp
import com.jnd.aikit.ui.theme.AIKitTheme

// Theme state data class
data class ThemeState(
    val isDarkMode: Boolean,
    val onThemeChanged: (Boolean) -> Unit
)

// Composition local for theme state
val LocalThemeState = androidx.compose.runtime.staticCompositionLocalOf<ThemeState> {
    error("ThemeState not provided")
}

class MainActivity : ComponentActivity() {

    private var hasRequestedPermission = false

    @Composable
    private fun AppThemeWrapper() {
        val context = LocalContext.current
        var isDarkMode by remember { mutableStateOf(false) }
        var themeLoaded by remember { mutableStateOf(false) }

        // Load theme preference on launch
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            isDarkMode = prefs.getBoolean("dark_mode", false)
            themeLoaded = true
        }

        AIKitTheme(darkTheme = isDarkMode) {
            // Provide theme state to the entire app
            androidx.compose.runtime.CompositionLocalProvider(
                LocalThemeState provides ThemeState(
                    isDarkMode = isDarkMode,
                    onThemeChanged = { newDarkMode ->
                        isDarkMode = newDarkMode
                        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("dark_mode", newDarkMode).apply()
                    }
                )
            ) {
                GalleryAppWithPermissions()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppThemeWrapper()
        }
    }

    @Composable
    private fun GalleryAppWithPermissions() {
        val context = LocalContext.current
        var hasStoragePermission by remember { mutableStateOf(checkStoragePermission()) }
        var hasManageExternalPermission by remember { mutableStateOf(checkManageExternalStoragePermission()) }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            hasStoragePermission = isGranted
            if (!isGranted) {
                Toast.makeText(context, "Storage permission is required to access images", Toast.LENGTH_LONG).show()
            }
        }

        // Launcher for MANAGE_EXTERNAL_STORAGE permission (Android 11+)
        val manageExternalLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            hasManageExternalPermission = checkManageExternalStoragePermission()
            if (!hasManageExternalPermission) {
                Toast.makeText(context, "All files access is recommended for complete folder listing", Toast.LENGTH_LONG).show()
            }
        }

        // Request permissions on first launch
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (!hasStoragePermission && !hasRequestedPermission) {
                hasRequestedPermission = true
                permissionLauncher.launch(getStoragePermission())
            }

            // Request MANAGE_EXTERNAL_STORAGE permission if needed (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasManageExternalPermission) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    manageExternalLauncher.launch(intent)
                } catch (e: Exception) {
                    // Fallback if the intent fails
                    Toast.makeText(context, "Please grant 'All files access' permission manually in settings", Toast.LENGTH_LONG).show()
                }
            }
        }

        if (hasStoragePermission) {
            // Check for missing models and show toast
            androidx.compose.runtime.LaunchedEffect(Unit) {
                checkMissingModelsAndNotify(context)
            }
            GalleryApp()
        } else {
            PermissionRequiredScreen(
                onRequestPermission = {
                    permissionLauncher.launch(getStoragePermission())
                }
            )
        }
    }

    private fun checkStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            getStoragePermission()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getStoragePermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun checkManageExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, check if we have MANAGE_EXTERNAL_STORAGE permission
            android.os.Environment.isExternalStorageManager()
        } else {
            true // Not needed for older versions
        }
    }

    private fun checkMissingModelsAndNotify(context: android.content.Context) {
        val modelManager = com.jnd.aikit.model.ModelManager.getInstance(context)
        val missingModels = com.jnd.aikit.model.ModelConfig.ALL_MODELS.filter { config ->
            !modelManager.isModelAvailable(config.type)
        }

        if (missingModels.isNotEmpty()) {
            val modelNames = missingModels.joinToString(", ") { it.name }
            Toast.makeText(
                context,
                "Missing AI models: $modelNames. Go to Model Management to upload them.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
private fun PermissionRequiredScreen(onRequestPermission: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.PhotoAlbum,
                contentDescription = null,
                modifier = androidx.compose.ui.Modifier.size(80.dp),
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
            )

            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(24.dp))

            androidx.compose.material3.Text(
                text = "Storage Permission Required",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))

            androidx.compose.material3.Text(
                text = "This app needs access to your photos to create AI-powered image search. Please grant storage permission to continue.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(32.dp))

            androidx.compose.material3.Button(
                onClick = onRequestPermission,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(0.6f)
            ) {
                androidx.compose.material3.Text("Grant Permission")
            }
        }
    }
}
