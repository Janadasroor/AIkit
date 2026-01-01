package com.jnd.aikit.ui.gallery

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DecimalFormat

/**
 * Memory management screen with beautiful animations and comprehensive memory statistics
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryManagementScreen(
    onBackPressed: () -> Unit,
    viewModel: GalleryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
                    Text(
                        text = "Performance Monitor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.loadFolders() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { viewModel.clearAllCache() }) {
                            Icon(Icons.Default.CleaningServices, contentDescription = "Clear All Cache")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
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
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.loadFolders() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Memory overview card
                    item {
                        AnimatedVisibility(
                            visible = screenVisible,
                            enter = scaleIn(
                                animationSpec = tween(500, delayMillis = 200, easing = EaseOutBack)
                            ) + fadeIn()
                        ) {
                            MemoryOverviewCard()
                        }
                    }

                    // Cache statistics
                    item {
                        AnimatedVisibility(
                            visible = screenVisible,
                            enter = slideInVertically(
                                initialOffsetY = { 50 },
                                animationSpec = tween(500, delayMillis = 300)
                            ) + fadeIn()
                        ) {
                            CacheStatisticsCard(viewModel = viewModel)
                        }
                    }

                    // Database statistics
                    item {
                        AnimatedVisibility(
                            visible = screenVisible,
                            enter = slideInVertically(
                                initialOffsetY = { 50 },
                                animationSpec = tween(500, delayMillis = 400)
                            ) + fadeIn()
                        ) {
                            DatabaseStatisticsCard(viewModel = viewModel)
                        }
                    }

                    // Quick actions
                    item {
                        AnimatedVisibility(
                            visible = screenVisible,
                            enter = slideInVertically(
                                initialOffsetY = { 50 },
                                animationSpec = tween(500, delayMillis = 500)
                            ) + fadeIn()
                        ) {
                            QuickActionsCard(viewModel = viewModel)
                        }
                    }

                    // Memory usage chart
                    item {
                        AnimatedVisibility(
                            visible = screenVisible,
                            enter = scaleIn(
                                animationSpec = tween(500, delayMillis = 600, easing = EaseOutBack)
                            ) + fadeIn()
                        ) {
                            MemoryUsageChart()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Beautiful memory overview card with animated progress indicators and real-time updates
 */
@Composable
private fun MemoryOverviewCard() {
    val context = LocalContext.current
    val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager

    // Real-time memory data
    var memoryInfo by remember { mutableStateOf(android.app.ActivityManager.MemoryInfo()) }
    var cpuUsage by remember { mutableStateOf(0f) }
    var lastCpuTime by remember { mutableLongStateOf(0L) }
    var lastWallTime by remember { mutableLongStateOf(0L) }
    var initialCpuTime by remember { mutableLongStateOf(0L) }
    var initialWallTime by remember { mutableLongStateOf(0L) }

    // Update memory and CPU info every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            val newMemoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(newMemoryInfo)
            memoryInfo = newMemoryInfo

            // Calculate CPU usage (approximate process CPU usage)
            try {
                val currentWallTime = android.os.SystemClock.elapsedRealtime()
                val currentCpuTime = android.os.Process.getElapsedCpuTime()

                // Initialize baseline on first run
                if (initialWallTime == 0L) {
                    initialWallTime = currentWallTime
                    initialCpuTime = currentCpuTime
                    lastWallTime = currentWallTime
                    lastCpuTime = currentCpuTime
                    return@LaunchedEffect
                }

                if (lastWallTime > 0 && lastCpuTime > 0) {
                    val wallTimeDiff = currentWallTime - lastWallTime
                    val cpuTimeDiff = currentCpuTime - lastCpuTime

                    if (wallTimeDiff > 0 && cpuTimeDiff >= 0) {
                        // Calculate CPU usage percentage
                        // This gives the percentage of available CPU cores being used by this process
                        val usagePercent = (cpuTimeDiff.toFloat() / wallTimeDiff.toFloat()) * 100f

                        // On multi-core systems, this can exceed 100%
                        // Cap at a reasonable maximum (e.g., 8 cores * 100% = 800%)
                        val maxCores = Runtime.getRuntime().availableProcessors()
                        val maxUsage = maxCores * 100f
                        cpuUsage = minOf(usagePercent, maxUsage)
                    }
                }

                lastWallTime = currentWallTime
                lastCpuTime = currentCpuTime
            } catch (e: Exception) {
                // CPU monitoring might not work on all devices
                cpuUsage = 0f
            }

            kotlinx.coroutines.delay(2000) // Update every 2 seconds
        }
    }

    val totalMemory = memoryInfo.totalMem / (1024 * 1024 * 1024.0) // GB
    val availableMemory = memoryInfo.availMem / (1024 * 1024 * 1024.0) // GB
    val usedMemory = totalMemory - availableMemory
    val memoryUsagePercent = if (totalMemory > 0) (usedMemory / totalMemory * 100).toFloat() else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "System Performance",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Memory Usage Circle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Memory",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier.size(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val animatedProgress by animateFloatAsState(
                            targetValue = memoryUsagePercent / 100f,
                            animationSpec = tween(1000, easing = EaseOutCubic)
                        )

                        // Get colors outside Canvas
                        val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val secondaryColor = MaterialTheme.colorScheme.secondary

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Background circle
                            drawArc(
                                color = backgroundColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                                size = Size(size.width, size.height)
                            )

                            // Progress circle
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        primaryColor,
                                        secondaryColor
                                    )
                                ),
                                startAngle = -90f,
                                sweepAngle = animatedProgress * 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                                size = Size(size.width, size.height)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${memoryUsagePercent.toInt()}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Used",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // CPU Usage Circle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "CPU",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier.size(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val animatedCpuProgress by animateFloatAsState(
                            targetValue = cpuUsage / 100f,
                            animationSpec = tween(1000, easing = EaseOutCubic)
                        )

                        // Get colors outside Canvas
                        val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                        val tertiaryColor = MaterialTheme.colorScheme.tertiary
                        val errorColor = MaterialTheme.colorScheme.error

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Background circle
                            drawArc(
                                color = backgroundColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                                size = Size(size.width, size.height)
                            )

                            // Progress circle
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        tertiaryColor,
                                        errorColor
                                    )
                                ),
                                startAngle = -90f,
                                sweepAngle = animatedCpuProgress * 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                                size = Size(size.width, size.height)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (cpuUsage > 0f) "${cpuUsage.toInt()}%" else "N/A",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (cpuUsage > 0f) "Used" else "Unavailable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Memory Statistics
            Text(
                text = "Memory Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MemoryStatItem(
                    label = "Total",
                    value = "${DecimalFormat("#.##").format(totalMemory)} GB",
                    color = MaterialTheme.colorScheme.onSurface
                )
                MemoryStatItem(
                    label = "Used",
                    value = "${DecimalFormat("#.##").format(usedMemory)} GB",
                    color = MaterialTheme.colorScheme.primary
                )
                MemoryStatItem(
                    label = "Free",
                    value = "${DecimalFormat("#.##").format(availableMemory)} GB",
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // CPU Statistics
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CPU Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CpuStatItem(
                    label = "Current Usage",
                    value = if (cpuUsage > 0f) "${cpuUsage.toInt()}%" else "N/A",
                    color = if (cpuUsage > 0f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                CpuStatItem(
                    label = "Cores Available",
                    value = "${Runtime.getRuntime().availableProcessors()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CpuStatItem(
                    label = "Status",
                    value = if (cpuUsage > 0f) {
                        when {
                            cpuUsage > 60f -> "High"
                            cpuUsage > 30f -> "Medium"
                            else -> "Low"
                        }
                    } else "Unavailable",
                    color = if (cpuUsage > 0f) {
                        when {
                            cpuUsage > 60f -> MaterialTheme.colorScheme.error
                            cpuUsage > 30f -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    } else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Cache statistics card
 */
@Composable
private fun CacheStatisticsCard(viewModel: GalleryViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Cache Statistics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Placeholder cache stats - in a real app you'd get these from the ViewModel
            CacheStatItem(
                icon = Icons.Default.Image,
                label = "Image Cache",
                value = "45.2 MB",
                description = "Processed image embeddings"
            )

            CacheStatItem(
                icon = Icons.Default.Search,
                label = "Search Cache",
                value = "12.8 MB",
                description = "Search results and queries"
            )

            CacheStatItem(
                icon = Icons.Default.Memory,
                label = "Model Cache",
                value = "234.1 MB",
                description = "AI model weights and data"
            )
        }
    }
}

/**
 * Database statistics card
 */
@Composable
private fun DatabaseStatisticsCard(viewModel: GalleryViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Database Statistics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Placeholder database stats
            DatabaseStatItem(
                label = "Total Vectors",
                value = "2,847",
                description = "Stored image embeddings"
            )

            DatabaseStatItem(
                label = "Index Size",
                value = "156.3 MB",
                description = "Vector database size"
            )

            DatabaseStatItem(
                label = "Last Backup",
                value = "2 hours ago",
                description = "Automatic backup status"
            )
        }
    }
}

/**
 * Quick actions card with animated buttons
 */
@Composable
private fun QuickActionsCard(viewModel: GalleryViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.CleaningServices,
                    label = "Clear Cache",
                    onClick = { viewModel.clearImageCache() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                QuickActionButton(
                    icon = Icons.Default.Backup,
                    label = "Backup DB",
                    onClick = { viewModel.backupDatabase() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Restore,
                    label = "Optimize DB",
                    onClick = { viewModel.optimizeDatabase() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )

                QuickActionButton(
                    icon = Icons.Default.Analytics,
                    label = "Analyze",
                    onClick = { viewModel.analyzeStorage() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }
    }
}

/**
 * Memory usage chart with beautiful gradients
 */
@Composable
private fun MemoryUsageChart() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Memory Usage Trend",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Get colors outside Canvas
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val dataPoints = listOf(0.3f, 0.45f, 0.52f, 0.38f, 0.61f, 0.55f, 0.7f)

                // Draw background grid
                for (i in 0..4) {
                    val y = height * i / 4
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                // Draw the chart line with gradient
                val path = androidx.compose.ui.graphics.Path()
                val pointDistance = width / (dataPoints.size - 1)

                dataPoints.forEachIndexed { index, value ->
                    val x = index * pointDistance
                    val y = height * (1 - value)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryColor,
                            secondaryColor
                        )
                    ),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw data points
                dataPoints.forEachIndexed { index, value ->
                    val x = index * pointDistance
                    val y = height * (1 - value)

                    drawCircle(
                        color = primaryColor,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

// Helper composables

@Composable
private fun MemoryStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CpuStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CacheStatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DatabaseStatItem(label: String, value: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        colors = colors,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
