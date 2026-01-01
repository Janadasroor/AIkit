package com.jnd.aikit.ui.gallery

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.jnd.aikit.LocalThemeState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * Navigation drawer item data class
 */
data class DrawerItem(
    val title: String,
    val icon: ImageVector,
    val screen: GalleryScreen,
    val badge: String? = null
)

/**
 * Main drawer navigation composable with beautiful animations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerNavigation(
    viewModel: GalleryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Navigation state
    val currentScreen = remember { mutableStateOf<GalleryScreen>(GalleryScreen.FolderSelection) }

    // Update screen based on view model state
    LaunchedEffect(uiState.selectedFolder) {
        if (uiState.selectedFolder != null && currentScreen.value is GalleryScreen.FolderSelection) {
            currentScreen.value = GalleryScreen.ImageGallery(uiState.selectedFolder!!)
        }
    }

    val drawerItems = listOf(
        DrawerItem("Gallery", Icons.Default.PhotoLibrary, GalleryScreen.FolderSelection),
        DrawerItem("Search", Icons.Default.Search, GalleryScreen.Search),
        DrawerItem("Performance Monitor", Icons.Default.Memory, GalleryScreen.MemoryManagement),
        DrawerItem("Models", Icons.Default.Settings, GalleryScreen.ModelManagement),
        DrawerItem("Settings", Icons.Default.Settings, GalleryScreen.Settings),
        DrawerItem("Developer Tools", Icons.Default.BugReport, GalleryScreen.DeveloperTest)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                items = drawerItems,
                currentScreen = currentScreen.value,
                onItemClick = { screen ->
                    currentScreen.value = screen
                    scope.launch { drawerState.close() }
                },
                onClose = {
                    scope.launch { drawerState.close() }
                }
            )
        },
        content = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = getScreenTitle(currentScreen.value),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
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
                                },
                                onNavigateToDeveloperTest = {
                                    currentScreen.value = GalleryScreen.DeveloperTest
                                }
                            )
                        }

                        is GalleryScreen.ImageGallery -> {
                            ImageGalleryScreen(
                                folder = screen.folder,
                                onBackPressed = {
                                    // Clear selected folder to prevent automatic navigation back to ImageGallery
                                    viewModel.clearSelectedFolder()
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

                        is GalleryScreen.MemoryManagement -> {
                            MemoryManagementScreen(
                                onBackPressed = {
                                    currentScreen.value = GalleryScreen.FolderSelection
                                }
                            )
                        }

                        is GalleryScreen.ModelManagement -> {
                            androidx.compose.runtime.CompositionLocalProvider(
                                androidx.lifecycle.compose.LocalLifecycleOwner provides androidx.compose.ui.platform.LocalLifecycleOwner.current
                            ) {
                                com.jnd.aikit.ui.model.ModelManagementScreen(
                                    onBackPressed = {
                                        currentScreen.value = GalleryScreen.FolderSelection
                                    }
                                )
                            }
                        }

                        is GalleryScreen.DeveloperTest -> {
                            DeveloperTestScreen(
                                onBackPressed = {
                                    currentScreen.value = GalleryScreen.FolderSelection
                                }
                            )
                        }

                        is GalleryScreen.Settings -> {
                            SettingsScreen(
                                onBackPressed = {
                                    currentScreen.value = GalleryScreen.FolderSelection
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * Beautiful drawer content with animations and gradients
 */
@Composable
private fun DrawerContent(
    items: List<DrawerItem>,
    currentScreen: GalleryScreen,
    onItemClick: (GalleryScreen) -> Unit,
    onClose: () -> Unit
) {
    var drawerVisible by remember { mutableStateOf(false) }
    val fadeInAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        drawerVisible = true
        fadeInAnim.animateTo(1f, animationSpec = tween(600, easing = EaseOutCubic))
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                )
            )
            .graphicsLayer { alpha = fadeInAnim.value }
    ) {
        // Header
        DrawerHeader()

        // Menu items
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { index, item ->
                key(item.title) {
                    var itemVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(drawerVisible) {
                        if (drawerVisible) {
                            kotlinx.coroutines.delay(index * 50L)
                            itemVisible = true
                        }
                    }

                    AnimatedVisibility(
                        visible = itemVisible,
                        enter = slideInHorizontally(
                            initialOffsetX = { -300 },
                            animationSpec = tween(400, easing = EaseOutCubic)
                        ) + fadeIn()
                    ) {
                        DrawerMenuItem(
                            item = item,
                            isSelected = isScreenSelected(currentScreen, item.screen),
                            onClick = { onItemClick(item.screen) }
                        )
                    }
                }
            }
        }

        // Footer
        DrawerFooter()
    }
}

/**
 * Animated drawer header with app branding
 */
@Composable
private fun DrawerHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App icon with animated scale
            val infiniteTransition = rememberInfiniteTransition()
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(8.dp, CircleShape)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "AI Kit",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "AI Kit Gallery",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = "AI-Powered Image Search",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Animated drawer menu item with hover effects
 */
@Composable
private fun DrawerMenuItem(
    item: DrawerItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(300)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon with animation
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Title and badge
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        // Selection indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        // Badge if present
        item.badge?.let { badge ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Drawer footer with version info and theme toggle
 */
@Composable
private fun DrawerFooter() {
    val themeState = LocalThemeState.current

    Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Divider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quick theme toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .clickable {
                    themeState.onThemeChanged(!themeState.isDarkMode)
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (themeState.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = "Theme",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (themeState.isDarkMode) "Dark Mode" else "Light Mode",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "More settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Version 1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "© 2024 AI Kit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// Helper functions

private fun getScreenTitle(screen: GalleryScreen): String {
    return when (screen) {
        is GalleryScreen.FolderSelection -> "AI Image Gallery"
        is GalleryScreen.ImageGallery -> screen.folder.name
        is GalleryScreen.Search -> "AI Search"
        is GalleryScreen.MemoryManagement -> "Performance Monitor"
        is GalleryScreen.ModelManagement -> "Model Management"
        is GalleryScreen.Settings -> "Settings"
        is GalleryScreen.DeveloperTest -> "Developer Test"
    }
}

private fun isScreenSelected(currentScreen: GalleryScreen, targetScreen: GalleryScreen): Boolean {
    return when {
        currentScreen is GalleryScreen.ImageGallery && targetScreen is GalleryScreen.FolderSelection -> true
        currentScreen::class == targetScreen::class -> true
        else -> currentScreen == targetScreen
    }
}

// Constants for SharedPreferences
private const val PREFS_NAME = "settings"
private const val DARK_MODE_KEY = "dark_mode"

