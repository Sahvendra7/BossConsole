package ai.rever.boss.components.bars.horizontal

import BossDarkError
import BossDarkSuccess
import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.performance.HealthStatus
import ai.rever.boss.performance.PerformanceHealth
import ai.rever.boss.performance.PerformanceSnapshot
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warning color (yellow/orange)
val BossDarkWarning = Color(0xFFFFA726)

/**
 * Compact performance indicator for the status bar.
 * Shows memory and CPU usage with color-coded health status.
 *
 * Format: "256M/512M 45%" (memory usage / max, CPU %)
 */
@Composable
fun PerformanceIndicator(
    snapshot: PerformanceSnapshot?,
    health: PerformanceHealth,
    onClick: () -> Unit
) {
    if (snapshot == null) return

    val color = when (health.overall) {
        HealthStatus.GOOD -> BossDarkSuccess
        HealthStatus.WARNING -> BossDarkWarning
        HealthStatus.CRITICAL -> BossDarkError
    }

    val memoryText = "${snapshot.memory.heapUsedMB.toInt()}M/${snapshot.memory.heapMaxMB.toInt()}M"
    val cpuText = "${snapshot.cpu.processLoadPercent.toInt()}%"

    BossActionButton(
        text = "$memoryText $cpuText",
        color = color,
        hintText = buildHintText(snapshot, health),
        onClick = onClick
    )
}

private fun buildHintText(snapshot: PerformanceSnapshot, health: PerformanceHealth): String {
    return buildString {
        appendLine("Memory: ${snapshot.memory.heapUsedMB.toInt()}MB / ${snapshot.memory.heapMaxMB.toInt()}MB (${snapshot.memory.heapUsagePercent.toInt()}%)")
        appendLine("CPU: ${snapshot.cpu.processLoadPercent.toInt()}% (${snapshot.cpu.activeThreadCount} threads)")
        appendLine("GC: ${snapshot.gc.collectionCount} collections")
        append("Resources: ${snapshot.resources.browserTabCount} browser, ${snapshot.resources.terminalCount} terminal, ${snapshot.resources.editorTabCount} editor tabs")
    }
}
