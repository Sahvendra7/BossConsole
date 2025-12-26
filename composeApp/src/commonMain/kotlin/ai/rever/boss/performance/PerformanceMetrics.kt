package ai.rever.boss.performance

import kotlinx.serialization.Serializable

/**
 * Performance settings for monitoring configuration.
 * Persisted to ~/.boss/performance-settings.json
 */
@Serializable
data class PerformanceSettings(
    val enabled: Boolean = true,
    val showIndicator: Boolean = true,
    val memoryWarningThresholdPercent: Int = 75,
    val memoryCriticalThresholdPercent: Int = 90,
    val cpuWarningThresholdPercent: Int = 70,
    val cpuCriticalThresholdPercent: Int = 90,
    val memorySampleIntervalMs: Long = 1000,
    val cpuSampleIntervalMs: Long = 2000,
    val resourceSampleIntervalMs: Long = 5000,
    val gcSampleIntervalMs: Long = 10000,
    val historyRetentionMinutes: Int = 30
)

/**
 * Current snapshot of performance metrics.
 */
@Serializable
data class PerformanceSnapshot(
    val timestamp: Long,
    val memory: MemoryMetrics,
    val cpu: CpuMetrics,
    val gc: GcMetrics,
    val resources: ResourceMetrics
)

/**
 * Memory metrics from JVM.
 */
@Serializable
data class MemoryMetrics(
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val heapCommittedBytes: Long,
    val nonHeapUsedBytes: Long,
    val nonHeapCommittedBytes: Long
) {
    val heapUsagePercent: Float
        get() = if (heapMaxBytes > 0) (heapUsedBytes.toFloat() / heapMaxBytes) * 100f else 0f

    val heapUsedMB: Float
        get() = heapUsedBytes / (1024f * 1024f)

    val heapMaxMB: Float
        get() = heapMaxBytes / (1024f * 1024f)

    val heapCommittedMB: Float
        get() = heapCommittedBytes / (1024f * 1024f)

    val nonHeapUsedMB: Float
        get() = nonHeapUsedBytes / (1024f * 1024f)

    val nonHeapCommittedMB: Float
        get() = nonHeapCommittedBytes / (1024f * 1024f)
}

/**
 * CPU metrics from JVM and OS.
 */
@Serializable
data class CpuMetrics(
    val processLoad: Double,      // 0.0-1.0, JVM process CPU usage
    val systemLoad: Double,       // 0.0-1.0, overall system CPU usage
    val availableProcessors: Int,
    val activeThreadCount: Int
) {
    val processLoadPercent: Float
        get() = (processLoad * 100).toFloat()

    val systemLoadPercent: Float
        get() = (systemLoad * 100).toFloat()
}

/**
 * Garbage collection metrics.
 */
@Serializable
data class GcMetrics(
    val collectionCount: Long,
    val collectionTimeMs: Long,
    val lastGcDurationMs: Long,
    val gcCollectors: List<GcCollectorInfo>
)

/**
 * Information about a single GC collector.
 */
@Serializable
data class GcCollectorInfo(
    val name: String,
    val collectionCount: Long,
    val collectionTimeMs: Long
)

/**
 * Resource counts (browser tabs, terminals, etc.).
 */
@Serializable
data class ResourceMetrics(
    val browserTabCount: Int,
    val terminalCount: Int,
    val editorTabCount: Int,
    val panelCount: Int,
    val windowCount: Int
)

/**
 * Health status for indicators.
 */
enum class HealthStatus {
    GOOD,       // Green
    WARNING,    // Yellow/Orange
    CRITICAL    // Red
}

/**
 * Combined health status for status bar indicator.
 */
data class PerformanceHealth(
    val memoryStatus: HealthStatus,
    val cpuStatus: HealthStatus,
    val overall: HealthStatus
) {
    companion object {
        fun fromSnapshot(snapshot: PerformanceSnapshot, settings: PerformanceSettings): PerformanceHealth {
            val memoryPercent = snapshot.memory.heapUsagePercent
            val cpuPercent = snapshot.cpu.processLoadPercent

            val memoryStatus = when {
                memoryPercent >= settings.memoryCriticalThresholdPercent -> HealthStatus.CRITICAL
                memoryPercent >= settings.memoryWarningThresholdPercent -> HealthStatus.WARNING
                else -> HealthStatus.GOOD
            }

            val cpuStatus = when {
                cpuPercent >= settings.cpuCriticalThresholdPercent -> HealthStatus.CRITICAL
                cpuPercent >= settings.cpuWarningThresholdPercent -> HealthStatus.WARNING
                else -> HealthStatus.GOOD
            }

            val overall = when {
                memoryStatus == HealthStatus.CRITICAL || cpuStatus == HealthStatus.CRITICAL -> HealthStatus.CRITICAL
                memoryStatus == HealthStatus.WARNING || cpuStatus == HealthStatus.WARNING -> HealthStatus.WARNING
                else -> HealthStatus.GOOD
            }

            return PerformanceHealth(memoryStatus, cpuStatus, overall)
        }
    }
}
