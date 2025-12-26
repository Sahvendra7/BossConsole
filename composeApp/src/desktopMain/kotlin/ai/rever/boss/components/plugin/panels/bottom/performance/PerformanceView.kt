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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
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
            // Health summary card
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        MetricItem("Memory", "${snapshot.memory.heapUsagePercent.toInt()}%", health.memoryStatus)
                        MetricItem("CPU", "${snapshot.cpu.processLoadPercent.toInt()}%", health.cpuStatus)
                        MetricItem("Threads", "${snapshot.cpu.activeThreadCount}", HealthStatus.GOOD)
                        MetricItem("GC Count", "${snapshot.gc.collectionCount}", HealthStatus.GOOD)
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
            // Resources summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resources", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        ResourceItem("Browser Tabs", snapshot.resources.browserTabCount)
                        ResourceItem("Terminals", snapshot.resources.terminalCount)
                        ResourceItem("Editor Tabs", snapshot.resources.editorTabCount)
                        ResourceItem("Panels", snapshot.resources.panelCount)
                        ResourceItem("Windows", snapshot.resources.windowCount)
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

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Application Resources", fontWeight = FontWeight.Bold, color = BossDarkTextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    ResourceRow("Browser Tabs", snapshot.resources.browserTabCount, Icons.Outlined.Web)
                    ResourceRow("Terminal Sessions", snapshot.resources.terminalCount, Icons.Outlined.Terminal)
                    ResourceRow("Editor Tabs", snapshot.resources.editorTabCount, Icons.Default.Edit)
                    ResourceRow("Open Panels", snapshot.resources.panelCount, Icons.Outlined.ViewSidebar)
                    ResourceRow("Windows", snapshot.resources.windowCount, Icons.Outlined.Window)
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
