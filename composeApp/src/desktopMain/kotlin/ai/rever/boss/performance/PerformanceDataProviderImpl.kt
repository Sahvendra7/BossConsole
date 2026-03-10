package ai.rever.boss.performance

import ai.rever.boss.plugin.api.ChildProcessData
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
            },
            childProcesses = collectChildProcesses()
        )
    }

    /**
     * Collect metrics from out-of-process plugin child JVMs.
     * Uses the process registry from KernelBootstrap (if running in KERNEL mode).
     */
    private fun collectChildProcesses(): List<ChildProcessData> {
        return try {
            val bootstrapCls = Class.forName("ai.rever.boss.kernel.KernelBootstrap")
            val companionCls = Class.forName("ai.rever.boss.kernel.KernelBootstrap\$Companion")
            val companion = bootstrapCls.getDeclaredField("Companion").get(null)
            val getInstance = companionCls.getMethod("getInstance")
            val kernel = getInstance.invoke(companion) ?: return emptyList()

            val registry = bootstrapCls.getMethod("getProcessRegistry").invoke(kernel) ?: return emptyList()
            val registryCls = registry::class.java
            val getAllProcesses = registryCls.getMethod("getAllProcesses")
            @Suppress("UNCHECKED_CAST")
            val processes = getAllProcesses.invoke(registry) as? List<*> ?: return emptyList()

            processes.mapNotNull { process ->
                try {
                    val processCls = process!!::class.java
                    val config = processCls.getMethod("getConfig").invoke(process)
                    val configCls = config::class.java

                    val processType = configCls.getMethod("getProcessType").invoke(config).toString()
                    if (processType != "PLUGIN") return@mapNotNull null

                    val processId = configCls.getMethod("getProcessId").invoke(config) as String
                    val displayName = configCls.getMethod("getDisplayName").invoke(config) as String
                    val pid = (processCls.getMethod("getPid").invoke(process) as? Long) ?: -1L
                    val isAlive = processCls.getMethod("isAlive").invoke(process) as Boolean
                    val lastMetrics = try {
                        processCls.getMethod("getLastHealthMetrics").invoke(process)
                    } catch (_: Exception) { null }

                    val heapUsed = try {
                        lastMetrics?.let { it::class.java.getMethod("getHeapUsedBytes").invoke(it) as Long } ?: 0L
                    } catch (_: Exception) { 0L }

                    val heapMax = try {
                        lastMetrics?.let { it::class.java.getMethod("getHeapMaxBytes").invoke(it) as Long } ?: 0L
                    } catch (_: Exception) { 0L }

                    val activeThreads = try {
                        lastMetrics?.let { it::class.java.getMethod("getActiveThreads").invoke(it) as Int } ?: 0
                    } catch (_: Exception) { 0 }

                    val uptimeMs = try {
                        lastMetrics?.let { it::class.java.getMethod("getUptimeMs").invoke(it) as Long } ?: 0L
                    } catch (_: Exception) { 0L }

                    ChildProcessData(
                        processId = processId,
                        pluginId = processId.removePrefix("plugin-"),
                        displayName = displayName,
                        pid = pid,
                        state = if (isAlive) "RUNNING" else "STOPPED",
                        heapUsedBytes = heapUsed,
                        heapMaxBytes = heapMax,
                        activeThreads = activeThreads,
                        uptimeMs = uptimeMs,
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: ClassNotFoundException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
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
