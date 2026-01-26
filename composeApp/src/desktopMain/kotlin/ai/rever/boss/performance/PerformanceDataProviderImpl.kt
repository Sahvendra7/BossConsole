package ai.rever.boss.performance

import ai.rever.boss.plugin.api.GcCollectorData
import ai.rever.boss.plugin.api.MemoryPoolData
import ai.rever.boss.plugin.api.PerformanceDataProvider
import ai.rever.boss.plugin.api.PerformanceSettingsData
import ai.rever.boss.plugin.api.PerformanceSnapshotData
import ai.rever.boss.plugin.api.ThreadData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Implementation of PerformanceDataProvider that adapts PerformanceMonitor
 * and PerformanceSettingsManager to the plugin interface.
 *
 * This adapter converts the internal performance types to the plugin API types,
 * allowing the Performance panel to be extracted as a separate module.
 */
class PerformanceDataProviderImpl : PerformanceDataProvider {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentSnapshot = MutableStateFlow<PerformanceSnapshotData?>(null)
    override val currentSnapshot: StateFlow<PerformanceSnapshotData?> = _currentSnapshot.asStateFlow()

    private val _history = MutableStateFlow<List<PerformanceSnapshotData>>(emptyList())
    override val history: StateFlow<List<PerformanceSnapshotData>> = _history.asStateFlow()

    private val _settings = MutableStateFlow(PerformanceSettingsData())
    override val settings: StateFlow<PerformanceSettingsData> = _settings.asStateFlow()

    init {
        // Observe PerformanceMonitor.currentSnapshot and convert to PerformanceSnapshotData
        scope.launch {
            PerformanceMonitor.currentSnapshot.collect { snapshot ->
                _currentSnapshot.value = snapshot?.toSnapshotData()
            }
        }

        // Observe PerformanceMonitor.history and convert to List<PerformanceSnapshotData>
        scope.launch {
            PerformanceMonitor.history.collect { snapshots ->
                _history.value = snapshots.map { it.toSnapshotData() }
            }
        }

        // Observe PerformanceSettingsManager.currentSettings and convert to PerformanceSettingsData
        scope.launch {
            PerformanceSettingsManager.currentSettings.collect { settings ->
                _settings.value = settings.toSettingsData()
            }
        }
    }

    override fun requestGC() {
        PerformanceMonitor.requestGC()
    }

    override suspend fun exportMetrics(): Result<String> {
        return PerformanceMonitor.exportMetrics()
    }

    override suspend fun updateSettings(settings: PerformanceSettingsData) {
        val internalSettings = PerformanceSettings(
            enabled = settings.enabled,
            showIndicator = settings.showIndicator,
            memoryWarningThresholdPercent = settings.memoryWarningThresholdPercent,
            memoryCriticalThresholdPercent = settings.memoryCriticalThresholdPercent,
            cpuWarningThresholdPercent = settings.cpuWarningThresholdPercent,
            cpuCriticalThresholdPercent = settings.cpuCriticalThresholdPercent,
            memorySampleIntervalMs = settings.memorySampleIntervalMs,
            cpuSampleIntervalMs = settings.cpuSampleIntervalMs,
            historyRetentionMinutes = settings.historyRetentionMinutes
        )
        PerformanceSettingsManager.updateSettings(internalSettings)
    }

    /**
     * Convert internal PerformanceSnapshot to plugin PerformanceSnapshotData.
     */
    private fun PerformanceSnapshot.toSnapshotData(): PerformanceSnapshotData {
        return PerformanceSnapshotData(
            timestamp = timestamp,
            heapUsedBytes = memory.heapUsedBytes,
            heapMaxBytes = memory.heapMaxBytes,
            heapUsagePercent = memory.heapUsagePercent,
            nonHeapUsedBytes = memory.nonHeapUsedBytes,
            processLoadPercent = cpu.processLoadPercent,
            systemLoadPercent = cpu.systemLoadPercent,
            activeThreadCount = cpu.activeThreadCount,
            gcCollectionCount = gc.collectionCount,
            gcCollectionTimeMs = gc.collectionTimeMs,
            browserTabCount = resources.browserTabCount,
            terminalCount = resources.terminalCount,
            editorTabCount = resources.editorTabCount,
            panelCount = resources.panelCount,
            windowCount = resources.windowCount,
            memoryPools = memory.memoryPools.map { pool ->
                MemoryPoolData(
                    name = pool.name,
                    type = pool.type,
                    usedBytes = pool.usedBytes,
                    maxBytes = pool.maxBytes,
                    committedBytes = pool.committedBytes
                )
            },
            threads = cpu.threads.map { thread ->
                ThreadData(
                    id = thread.id,
                    name = thread.name,
                    state = thread.state,
                    cpuTimeMs = thread.cpuTimeMs,
                    userTimeMs = thread.userTimeMs,
                    blockedCount = thread.blockedCount,
                    waitedCount = thread.waitedCount
                )
            },
            gcCollectors = gc.gcCollectors.map { collector ->
                GcCollectorData(
                    name = collector.name,
                    collectionCount = collector.collectionCount,
                    collectionTimeMs = collector.collectionTimeMs
                )
            }
        )
    }

    /**
     * Convert internal PerformanceSettings to plugin PerformanceSettingsData.
     */
    private fun PerformanceSettings.toSettingsData(): PerformanceSettingsData {
        return PerformanceSettingsData(
            enabled = enabled,
            showIndicator = showIndicator,
            memoryWarningThresholdPercent = memoryWarningThresholdPercent,
            memoryCriticalThresholdPercent = memoryCriticalThresholdPercent,
            cpuWarningThresholdPercent = cpuWarningThresholdPercent,
            cpuCriticalThresholdPercent = cpuCriticalThresholdPercent,
            memorySampleIntervalMs = memorySampleIntervalMs,
            cpuSampleIntervalMs = cpuSampleIntervalMs,
            historyRetentionMinutes = historyRetentionMinutes
        )
    }
}
