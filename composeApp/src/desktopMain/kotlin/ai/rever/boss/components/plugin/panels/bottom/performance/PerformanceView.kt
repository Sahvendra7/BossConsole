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
import ai.rever.boss.performance.GcCollectorInfo
import ai.rever.boss.performance.HealthStatus
import ai.rever.boss.performance.MemoryPoolInfo
import ai.rever.boss.performance.PerformanceHealth
import ai.rever.boss.performance.PerformanceSettings
import ai.rever.boss.performance.PerformanceSnapshot
import ai.rever.boss.performance.ThreadInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
        // Tab bar - compact
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            backgroundColor = BossDarkBackground,
            contentColor = BossDarkAccent,
            modifier = Modifier.height(32.dp)
        ) {
            PerformanceViewModel.Tab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    modifier = Modifier.height(32.dp),
                    text = {
                        Text(
                            text = tab.displayName,
                            color = if (selectedTab == tab) BossDarkTextPrimary else BossDarkTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                )
            }
        }

        Divider(color = BossDarkBorder, thickness = 1.dp)

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
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
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

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            // Health summary card with gauges
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("System Health", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
                        HealthBadge(health.overall)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Circular gauges row - clickable to navigate to respective tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CircularGauge(
                            value = snapshot.memory.heapUsagePercent / 100f,
                            label = "Memory",
                            valueText = "${snapshot.memory.heapUsagePercent.toInt()}%",
                            status = health.memoryStatus,
                            onClick = { viewModel.selectTab(PerformanceViewModel.Tab.MEMORY) }
                        )
                        CircularGauge(
                            value = snapshot.cpu.processLoadPercent / 100f,
                            label = "CPU",
                            valueText = "${snapshot.cpu.processLoadPercent.toInt()}%",
                            status = health.cpuStatus,
                            onClick = { viewModel.selectTab(PerformanceViewModel.Tab.CPU) }
                        )
                        CircularGauge(
                            value = (snapshot.cpu.activeThreadCount.toFloat() / 100f).coerceAtMost(1f),
                            label = "Threads",
                            valueText = "${snapshot.cpu.activeThreadCount}",
                            status = HealthStatus.GOOD,
                            showAsCount = true,
                            onClick = { viewModel.selectTab(PerformanceViewModel.Tab.CPU) }
                        )
                        CircularGauge(
                            value = (snapshot.gc.collectionCount.toFloat() / 50f).coerceAtMost(1f),
                            label = "GC",
                            valueText = "${snapshot.gc.collectionCount}",
                            status = HealthStatus.GOOD,
                            showAsCount = true,
                            onClick = { viewModel.selectTab(PerformanceViewModel.Tab.TIMINGS) }
                        )
                    }
                }
            }
        }

        item {
            // Quick actions - compact buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { viewModel.requestGC() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = BossDarkAccent),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Delete, "GC", modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Request GC", color = Color.White, fontSize = 11.sp)
                }

                Button(
                    onClick = { viewModel.exportMetrics() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = BossDarkSurface),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.SaveAlt, "Export", modifier = Modifier.size(14.dp), tint = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export Metrics", color = BossDarkTextPrimary, fontSize = 11.sp)
                }
            }
        }

        item {
            // Resources summary with visual cards - clickable to navigate to Resources tab
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectTab(PerformanceViewModel.Tab.RESOURCES) },
                backgroundColor = BossDarkSurface,
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Resources", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            // Heap usage bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Heap Memory", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))

                    ProgressBar(
                        progress = snapshot.memory.heapUsagePercent / 100f,
                        label = "${snapshot.memory.heapUsedMB.toInt()}MB / ${snapshot.memory.heapMaxMB.toInt()}MB"
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "Committed: ${snapshot.memory.heapCommittedMB.toInt()}MB",
                        color = BossDarkTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        item {
            // Memory Pools
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Memory Pools", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pool", fontSize = 10.sp, color = BossDarkTextSecondary, modifier = Modifier.weight(2f))
                        Text("Type", fontSize = 10.sp, color = BossDarkTextSecondary, modifier = Modifier.weight(1f))
                        Text("Usage", fontSize = 10.sp, color = BossDarkTextSecondary, modifier = Modifier.width(100.dp))
                    }

                    Divider(color = BossDarkBorder, thickness = 1.dp)

                    // Memory pool rows
                    snapshot.memory.memoryPools.forEach { pool ->
                        MemoryPoolRow(pool)
                    }

                    if (snapshot.memory.memoryPools.isEmpty()) {
                        Text(
                            "No memory pool data available",
                            fontSize = 10.sp,
                            color = BossDarkTextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        item {
            // Non-heap memory summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Non-Heap Summary", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetricItem("Used", "${snapshot.memory.nonHeapUsedMB.toInt()}MB", HealthStatus.GOOD)
                        MetricItem("Committed", "${snapshot.memory.nonHeapCommittedMB.toInt()}MB", HealthStatus.GOOD)
                    }
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

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Process CPU", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))

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
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("System CPU", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))

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
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Threads (${snapshot.cpu.activeThreadCount} active, ${snapshot.cpu.availableProcessors} processors)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = BossDarkTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Thread list header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Name", fontSize = 10.sp, color = BossDarkTextSecondary, modifier = Modifier.weight(2f))
                        Text("State", fontSize = 10.sp, color = BossDarkTextSecondary, modifier = Modifier.weight(1f))
                        Text("CPU", fontSize = 10.sp, color = BossDarkTextSecondary, modifier = Modifier.width(60.dp))
                    }

                    Divider(color = BossDarkBorder, thickness = 1.dp)

                    // Thread list
                    snapshot.cpu.threads.forEach { thread ->
                        ThreadRow(thread)
                    }

                    if (snapshot.cpu.threads.isEmpty()) {
                        Text(
                            "No thread data available",
                            fontSize = 10.sp,
                            color = BossDarkTextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
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

    val currentTime = System.currentTimeMillis()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            // Summary card
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Garbage Collection Summary", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetricItem("Total Collections", "${snapshot.gc.collectionCount}", HealthStatus.GOOD)
                        MetricItem("Total Time", "${snapshot.gc.collectionTimeMs}ms", HealthStatus.GOOD)
                    }
                }
            }
        }

        item {
            // Collectors with last GC info
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Collectors", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))

                    snapshot.gc.gcCollectors.forEach { collector ->
                        GcCollectorRow(collector, currentTime)
                        if (collector != snapshot.gc.gcCollectors.last()) {
                            Divider(color = BossDarkBorder.copy(alpha = 0.5f), thickness = 1.dp)
                        }
                    }

                    if (snapshot.gc.gcCollectors.isEmpty()) {
                        Text(
                            "No GC collector data available",
                            fontSize = 10.sp,
                            color = BossDarkTextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
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

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            // Total resources summary - compact
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total Resources", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
                        Text("Active components", color = BossDarkTextSecondary, fontSize = 10.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(BossDarkAccent.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$totalResources",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
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
                elevation = 0.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Resource Breakdown", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))

                    ResourceBarRow(
                        label = "Browser Tabs",
                        count = snapshot.resources.browserTabCount,
                        maxCount = maxOf(10, totalResources),
                        icon = Icons.Outlined.Web,
                        color = BossDarkAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ResourceBarRow(
                        label = "Terminal Sessions",
                        count = snapshot.resources.terminalCount,
                        maxCount = maxOf(10, totalResources),
                        icon = Icons.Outlined.Terminal,
                        color = BossDarkSuccess
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ResourceBarRow(
                        label = "Editor Tabs",
                        count = snapshot.resources.editorTabCount,
                        maxCount = maxOf(10, totalResources),
                        icon = Icons.Default.Edit,
                        color = BossDarkWarning
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ResourceBarRow(
                        label = "Open Panels",
                        count = snapshot.resources.panelCount,
                        maxCount = maxOf(10, totalResources),
                        icon = Icons.Outlined.ViewSidebar,
                        color = Color(0xFF9C27B0)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
        shape = RoundedCornerShape(3.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = status.name,
            color = color,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun ThreadRow(thread: ThreadInfo) {
    val stateColor = when (thread.state) {
        "RUNNABLE" -> BossDarkSuccess
        "BLOCKED" -> BossDarkError
        "WAITING", "TIMED_WAITING" -> BossDarkWarning
        else -> BossDarkTextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thread name (truncated if too long)
        Text(
            text = thread.name.take(30) + if (thread.name.length > 30) "..." else "",
            fontSize = 10.sp,
            color = BossDarkTextPrimary,
            modifier = Modifier.weight(2f)
        )

        // State badge
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(2.dp),
            color = stateColor.copy(alpha = 0.2f)
        ) {
            Text(
                text = thread.state,
                fontSize = 9.sp,
                color = stateColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        // CPU time
        Text(
            text = formatCpuTime(thread.cpuTimeMs),
            fontSize = 10.sp,
            color = BossDarkTextSecondary,
            modifier = Modifier.width(60.dp)
        )
    }
}

private fun formatCpuTime(ms: Long): String {
    return when {
        ms >= 60000 -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
        ms >= 1000 -> "${ms / 1000}.${(ms % 1000) / 100}s"
        else -> "${ms}ms"
    }
}

@Composable
private fun MemoryPoolRow(pool: MemoryPoolInfo) {
    val typeColor = if (pool.type == "HEAP") BossDarkAccent else Color(0xFF9C27B0)
    val progress = pool.usagePercent / 100f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pool name
        Text(
            text = pool.name.take(20) + if (pool.name.length > 20) "..." else "",
            fontSize = 10.sp,
            color = BossDarkTextPrimary,
            modifier = Modifier.weight(2f)
        )

        // Type badge
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(2.dp),
            color = typeColor.copy(alpha = 0.2f)
        ) {
            Text(
                text = pool.type,
                fontSize = 9.sp,
                color = typeColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        // Usage bar with percentage
        Row(
            modifier = Modifier.width(100.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(BossDarkBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                progress >= 0.9f -> BossDarkError
                                progress >= 0.75f -> BossDarkWarning
                                else -> BossDarkSuccess
                            }
                        )
                )
            }
            Text(
                text = "${pool.usagePercent.toInt()}%",
                fontSize = 9.sp,
                color = BossDarkTextSecondary
            )
        }
    }
}

@Composable
private fun GcCollectorRow(collector: GcCollectorInfo, currentTime: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Collector name and stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = collector.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = BossDarkTextPrimary
            )
            Text(
                text = "${collector.collectionCount} collections, ${collector.collectionTimeMs}ms",
                fontSize = 10.sp,
                color = BossDarkTextSecondary
            )
        }

        // Last GC info if available
        collector.lastGcInfo?.let { lastGc ->
            val timeAgo = currentTime - lastGc.startTime
            val timeAgoText = formatTimeAgo(timeAgo)

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Last GC: $timeAgoText ago",
                    fontSize = 9.sp,
                    color = BossDarkTextSecondary
                )
                Text(
                    text = "${lastGc.durationMs}ms",
                    fontSize = 9.sp,
                    color = BossDarkWarning
                )
                if (lastGc.memoryReclaimedBytes > 0) {
                    Text(
                        text = "reclaimed ${lastGc.memoryReclaimedMB.toInt()}MB",
                        fontSize = 9.sp,
                        color = BossDarkSuccess
                    )
                }
            }
        }
    }
}

private fun formatTimeAgo(ms: Long): String {
    return when {
        ms >= 3600000 -> "${ms / 3600000}h ${(ms % 3600000) / 60000}m"
        ms >= 60000 -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
        ms >= 1000 -> "${ms / 1000}s"
        else -> "${ms}ms"
    }
}

@Composable
private fun MetricItem(label: String, value: String, status: HealthStatus) {
    Column {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
        Text(label, color = BossDarkTextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun ResourceItem(label: String, count: Int) {
    Column {
        Text("$count", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BossDarkTextPrimary)
        Text(label, color = BossDarkTextSecondary, fontSize = 10.sp)
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
            Text(label, color = BossDarkTextPrimary, fontSize = 11.sp)
            Text("${(progress * 100).toInt()}%", color = BossDarkTextSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
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
    showAsCount: Boolean = false,
    onClick: (() -> Unit)? = null
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

    val gaugeSize = 250.dp
    val strokeWidth = 20.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier.clickable { onClick() }
        } else {
            Modifier
        }
    ) {
        Box(
            modifier = Modifier.size(gaugeSize),
            contentAlignment = Alignment.Center
        ) {
            // Background arc
            Canvas(modifier = Modifier.size(gaugeSize)) {
                val stroke = strokeWidth.toPx()
                val arcSize = size.minDimension - stroke
                drawArc(
                    color = BossDarkBorder,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            // Foreground arc (animated)
            Canvas(modifier = Modifier.size(gaugeSize)) {
                val stroke = strokeWidth.toPx()
                val arcSize = size.minDimension - stroke
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedValue,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            // Center text
            Text(
                text = valueText,
                fontWeight = FontWeight.Bold,
                fontSize = 45.sp,
                color = BossDarkTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = label,
            fontSize = 30.sp,
            color = BossDarkTextSecondary
        )
    }
}

/**
 * Resource card with icon, count, and colored accent.
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
        shape = RoundedCornerShape(4.dp),
        color = BossDarkBackground
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, BossDarkBorder, RoundedCornerShape(4.dp))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(14.dp),
                    tint = color
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$count",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
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
                        .size(20.dp)
                        .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        modifier = Modifier.size(12.dp),
                        tint = color
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, color = BossDarkTextPrimary, fontSize = 12.sp)
            }

            Text(
                "$count",
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Animated bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BossDarkBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
