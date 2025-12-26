package ai.rever.boss.components.plugin.panels.bottom.performance

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import BossDarkError
import BossDarkSuccess
import BossDarkSurface
import BossDarkTextPrimary
import BossDarkTextSecondary
import ai.rever.boss.components.bars.horizontal.BossDarkWarning
import ai.rever.boss.performance.HealthStatus
import ai.rever.boss.performance.PerformanceHealth
import ai.rever.boss.performance.PerformanceSettings
import ai.rever.boss.performance.PerformanceSnapshot
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material.icons.outlined.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Performance panel view with tabs.
 */
@Composable
fun PerformanceView(viewModel: PerformanceViewModel) {
    val snapshot by viewModel.currentSnapshot.collectAsState()
    val history by viewModel.history.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground)
    ) {
        // Tab bar
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            backgroundColor = BossDarkBackground,
            contentColor = BossDarkAccent
        ) {
            PerformanceViewModel.Tab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        Text(
                            text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (selectedTab == tab) BossDarkTextPrimary else BossDarkTextSecondary
                        )
                    }
                )
            }
        }

        Divider(color = BossDarkBorder)

        // Export result notification
        exportResult?.let { path ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                color = BossDarkSuccess.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Exported to: $path",
                        color = BossDarkSuccess,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { viewModel.clearExportResult() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = BossDarkSurface)
                    ) {
                        Text("Dismiss", fontSize = 12.sp)
                    }
                }
            }
        }

        // Tab content
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            when (selectedTab) {
                PerformanceViewModel.Tab.OVERVIEW -> OverviewTab(snapshot, settings, viewModel)
                PerformanceViewModel.Tab.MEMORY -> MemoryTab(snapshot)
                PerformanceViewModel.Tab.CPU -> CpuTab(snapshot)
                PerformanceViewModel.Tab.TIMINGS -> TimingsTab(snapshot)
                PerformanceViewModel.Tab.RESOURCES -> ResourcesTab(snapshot)
            }
        }
    }
}

@Composable
private fun OverviewTab(
    snapshot: PerformanceSnapshot?,
    settings: PerformanceSettings,
    viewModel: PerformanceViewModel
) {
    if (snapshot == null) {
        EmptyState("Waiting for metrics...")
        return
    }

    val health = PerformanceHealth.fromSnapshot(snapshot, settings)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            // Health summary card with gauges
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("System Health", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                        HealthBadge(health.overall)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Circular gauges row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CircularGauge(
                            value = snapshot.memory.heapUsagePercent / 100f,
                            label = "Memory",
                            valueText = "${snapshot.memory.heapUsagePercent.toInt()}%",
                            status = health.memoryStatus
                        )
                        CircularGauge(
                            value = snapshot.cpu.processLoadPercent / 100f,
                            label = "CPU",
                            valueText = "${snapshot.cpu.processLoadPercent.toInt()}%",
                            status = health.cpuStatus
                        )
                        CircularGauge(
                            value = (snapshot.cpu.activeThreadCount.toFloat() / 100f).coerceAtMost(1f),
                            label = "Threads",
                            valueText = "${snapshot.cpu.activeThreadCount}",
                            status = HealthStatus.GOOD,
                            showAsCount = true
                        )
                        CircularGauge(
                            value = (snapshot.gc.collectionCount.toFloat() / 50f).coerceAtMost(1f),
                            label = "GC",
                            valueText = "${snapshot.gc.collectionCount}",
                            status = HealthStatus.GOOD,
                            showAsCount = true
                        )
                    }
                }
            }
        }

        item {
            // Quick actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.requestGC() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = BossDarkAccent)
                ) {
                    Icon(Icons.Default.Delete, "GC", modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Request GC", color = Color.White)
                }

                Button(
                    onClick = { viewModel.exportMetrics() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = BossDarkSurface)
                ) {
                    Icon(Icons.Default.SaveAlt, "Export", modifier = Modifier.size(16.dp), tint = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export Metrics", color = BossDarkTextPrimary)
                }
            }
        }

        item {
            // Resources summary with visual cards
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resources", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ResourceCard(
                            modifier = Modifier.weight(1f),
                            label = "Browser",
                            count = snapshot.resources.browserTabCount,
                            icon = Icons.Outlined.Web,
                            color = BossDarkAccent
                        )
                        ResourceCard(
                            modifier = Modifier.weight(1f),
                            label = "Terminal",
                            count = snapshot.resources.terminalCount,
                            icon = Icons.Outlined.Terminal,
                            color = BossDarkSuccess
                        )
                        ResourceCard(
                            modifier = Modifier.weight(1f),
                            label = "Editor",
                            count = snapshot.resources.editorTabCount,
                            icon = Icons.Default.Edit,
                            color = BossDarkWarning
                        )
                        ResourceCard(
                            modifier = Modifier.weight(1f),
                            label = "Panels",
                            count = snapshot.resources.panelCount,
                            icon = Icons.Outlined.ViewSidebar,
                            color = Color(0xFF9C27B0)
                        )
                        ResourceCard(
                            modifier = Modifier.weight(1f),
                            label = "Windows",
                            count = snapshot.resources.windowCount,
                            icon = Icons.Outlined.Window,
                            color = Color(0xFF00BCD4)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryTab(snapshot: PerformanceSnapshot?) {
    if (snapshot == null) {
        EmptyState("Waiting for memory metrics...")
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            // Heap usage bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Heap Memory", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    ProgressBar(
                        progress = snapshot.memory.heapUsagePercent / 100f,
                        label = "${snapshot.memory.heapUsedMB.toInt()}MB / ${snapshot.memory.heapMaxMB.toInt()}MB"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Committed: ${snapshot.memory.heapCommittedMB.toInt()}MB",
                        color = BossDarkTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            // Non-heap memory
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Non-Heap Memory", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Used: ${snapshot.memory.nonHeapUsedMB.toInt()}MB",
                        color = BossDarkTextPrimary
                    )
                    Text(
                        "Committed: ${snapshot.memory.nonHeapCommittedMB.toInt()}MB",
                        color = BossDarkTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CpuTab(snapshot: PerformanceSnapshot?) {
    if (snapshot == null) {
        EmptyState("Waiting for CPU metrics...")
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Process CPU", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    ProgressBar(
                        progress = snapshot.cpu.processLoadPercent / 100f,
                        label = "${snapshot.cpu.processLoadPercent.toInt()}%"
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System CPU", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    ProgressBar(
                        progress = snapshot.cpu.systemLoadPercent / 100f,
                        label = "${snapshot.cpu.systemLoadPercent.toInt()}%"
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Threads", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        MetricItem("Active", "${snapshot.cpu.activeThreadCount}", HealthStatus.GOOD)
                        MetricItem("Processors", "${snapshot.cpu.availableProcessors}", HealthStatus.GOOD)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingsTab(snapshot: PerformanceSnapshot?) {
    if (snapshot == null) {
        EmptyState("Waiting for GC metrics...")
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Garbage Collection", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        MetricItem("Total Collections", "${snapshot.gc.collectionCount}", HealthStatus.GOOD)
                        MetricItem("Total Time", "${snapshot.gc.collectionTimeMs}ms", HealthStatus.GOOD)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Collectors:", color = BossDarkTextSecondary, fontSize = 12.sp)
                    snapshot.gc.gcCollectors.forEach { collector ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(collector.name, color = BossDarkTextPrimary, fontSize = 12.sp)
                            Text(
                                "${collector.collectionCount} (${collector.collectionTimeMs}ms)",
                                color = BossDarkTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourcesTab(snapshot: PerformanceSnapshot?) {
    if (snapshot == null) {
        EmptyState("Waiting for resource metrics...")
        return
    }

    val totalResources = snapshot.resources.browserTabCount +
                         snapshot.resources.terminalCount +
                         snapshot.resources.editorTabCount +
                         snapshot.resources.panelCount +
                         snapshot.resources.windowCount

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            // Total resources summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total Resources", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                        Text("Active application components", color = BossDarkTextSecondary, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(BossDarkAccent.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$totalResources",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = BossDarkAccent
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resource Breakdown", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(16.dp))

                    ResourceBarRow(
                        label = "Browser Tabs",
                        count = snapshot.resources.browserTabCount,
                        maxCount = maxOf(10, totalResources),
                        icon = Icons.Outlined.Web,
                        color = BossDarkAccent
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ResourceBarRow(
                        label = "Terminal Sessions",
                        count = snapshot.resources.terminalCount,
                        maxCount = maxOf(10, totalResources),
                        icon = Icons.Outlined.Terminal,
                        color = BossDarkSuccess
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ResourceBarRow(
                        label = "Editor Tabs",
                        count = snapshot.resources.editorTabCount,
                        maxCount = maxOf(10, totalResources),
                        icon = Icons.Default.Edit,
                        color = BossDarkWarning
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ResourceBarRow(
                        label = "Open Panels",
                        count = snapshot.resources.panelCount,
                        maxCount = maxOf(10, totalResources),
                        icon = Icons.Outlined.ViewSidebar,
                        color = Color(0xFF9C27B0)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ResourceBarRow(
                        label = "Windows",
                        count = snapshot.resources.windowCount,
                        maxCount = maxOf(10, totalResources),
                        icon = Icons.Outlined.Window,
                        color = Color(0xFF00BCD4)
                    )
                }
            }
        }
    }
}

// Helper composables

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Speed,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = BossDarkTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = BossDarkTextSecondary)
        }
    }
}

@Composable
private fun HealthBadge(status: HealthStatus) {
    val color = when (status) {
        HealthStatus.GOOD -> BossDarkSuccess
        HealthStatus.WARNING -> BossDarkWarning
        HealthStatus.CRITICAL -> BossDarkError
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = status.name,
            color = color,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String, status: HealthStatus) {
    Column {
        Text(value, fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
        Text(label, color = BossDarkTextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ResourceItem(label: String, count: Int) {
    Column {
        Text("$count", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
        Text(label, color = BossDarkTextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ResourceRow(label: String, count: Int, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = BossDarkTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = BossDarkTextPrimary)
        }
        Text("$count", color = BossDarkTextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressBar(progress: Float, label: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = BossDarkTextPrimary, fontSize = 12.sp)
            Text("${(progress * 100).toInt()}%", color = BossDarkTextSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            backgroundColor = BossDarkBorder,
            color = when {
                progress >= 0.9f -> BossDarkError
                progress >= 0.75f -> BossDarkWarning
                else -> BossDarkSuccess
            }
        )
    }
}

/**
 * Circular gauge showing a percentage or count value with animated arc.
 */
@Composable
private fun CircularGauge(
    value: Float,
    label: String,
    valueText: String,
    status: HealthStatus,
    showAsCount: Boolean = false
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500)
    )

    val color = if (showAsCount) {
        BossDarkAccent
    } else {
        when (status) {
            HealthStatus.GOOD -> BossDarkSuccess
            HealthStatus.WARNING -> BossDarkWarning
            HealthStatus.CRITICAL -> BossDarkError
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background arc
            Canvas(modifier = Modifier.size(72.dp)) {
                val strokeWidth = 8.dp.toPx()
                val arcSize = size.minDimension - strokeWidth
                drawArc(
                    color = BossDarkBorder,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Foreground arc (animated)
            Canvas(modifier = Modifier.size(72.dp)) {
                val strokeWidth = 8.dp.toPx()
                val arcSize = size.minDimension - strokeWidth
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedValue,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Center text
            Text(
                text = valueText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = BossDarkTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            color = BossDarkTextSecondary
        )
    }
}

/**
 * Compact resource card with icon, count, and colored accent.
 */
@Composable
private fun ResourceCard(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    icon: ImageVector,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = BossDarkBackground
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, BossDarkBorder, RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$count",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = BossDarkTextPrimary
            )

            Text(
                text = label,
                fontSize = 10.sp,
                color = BossDarkTextSecondary
            )
        }
    }
}

/**
 * Resource row with icon, label, count, and animated bar.
 */
@Composable
private fun ResourceBarRow(
    label: String,
    count: Int,
    maxCount: Int,
    icon: ImageVector,
    color: Color
) {
    val progress = if (maxCount > 0) count.toFloat() / maxCount else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500)
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        modifier = Modifier.size(14.dp),
                        tint = color
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, color = BossDarkTextPrimary, fontSize = 13.sp)
            }

            Text(
                "$count",
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Animated bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BossDarkBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
