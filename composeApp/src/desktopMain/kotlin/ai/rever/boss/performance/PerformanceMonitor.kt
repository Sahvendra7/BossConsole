package ai.rever.boss.performance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.MemoryPoolMXBean
import java.lang.management.OperatingSystemMXBean
import java.lang.management.ThreadMXBean
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Global singleton for performance monitoring.
 *
 * Usage:
 * - main.kt: Call PerformanceMonitor.start() at app startup
 * - Status bar: Observe PerformanceMonitor.currentSnapshot
 * - Panel: Access PerformanceMonitor.history for charts
 */
object PerformanceMonitor {
    private val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()
    private val memoryPoolMXBeans: List<MemoryPoolMXBean> = ManagementFactory.getMemoryPoolMXBeans()
    private val osMXBean: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean()
    private val threadMXBean: ThreadMXBean = ManagementFactory.getThreadMXBean()
    private val gcMXBeans: List<GarbageCollectorMXBean> = ManagementFactory.getGarbageCollectorMXBeans()

    // Sun/Oracle specific for process CPU load and GC info
    private val sunOSBean: com.sun.management.OperatingSystemMXBean? =
        osMXBean as? com.sun.management.OperatingSystemMXBean
    private val sunGcBeans: List<com.sun.management.GarbageCollectorMXBean> =
        gcMXBeans.mapNotNull { it as? com.sun.management.GarbageCollectorMXBean }

    private val _currentSnapshot = MutableStateFlow<PerformanceSnapshot?>(null)
    val currentSnapshot: StateFlow<PerformanceSnapshot?> = _currentSnapshot.asStateFlow()

    private val _currentHealth = MutableStateFlow(
        PerformanceHealth(HealthStatus.GOOD, HealthStatus.GOOD, HealthStatus.GOOD)
    )
    val currentHealth: StateFlow<PerformanceHealth> = _currentHealth.asStateFlow()

    // Max history entries: 30 min retention with 1s intervals = 1800 entries
    // Add buffer for slightly longer retention
    private const val MAX_HISTORY_SIZE = 2000

    // Use ArrayDeque as a circular buffer for efficient history management
    private val historyBuffer = ArrayDeque<PerformanceSnapshot>(MAX_HISTORY_SIZE)
    private val _history = MutableStateFlow<List<PerformanceSnapshot>>(emptyList())
    val history: StateFlow<List<PerformanceSnapshot>> = _history.asStateFlow()

    // Resource count providers (registered by BossApp)
    private var browserTabCountProvider: (() -> Int)? = null
    private var terminalCountProvider: (() -> Int)? = null
    private var editorTabCountProvider: (() -> Int)? = null
    private var panelCountProvider: (() -> Int)? = null
    private var windowCountProvider: (() -> Int)? = null

    // Detailed resource providers (registered by BossApp)
    private var browserTabsProvider: (() -> List<BrowserTabInfo>)? = null
    private var terminalsProvider: (() -> List<TerminalInfo>)? = null
    private var editorTabsProvider: (() -> List<EditorTabResourceInfo>)? = null

    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastGcTime: Long = 0

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Start performance monitoring.
     * Should be called once in main.kt after GlobalLogCapture.start()
     */
    fun start() {
        if (monitoringJob != null) return

        println("[Performance] Starting performance monitor")

        monitoringJob = scope.launch {
            var memoryTick = 0L
            var cpuTick = 0L
            var resourceTick = 0L
            var gcTick = 0L

            while (isActive) {
                val settings = PerformanceSettingsManager.currentSettings.value

                if (!settings.enabled) {
                    delay(1000)
                    continue
                }

                val now = System.currentTimeMillis()
                var memory = _currentSnapshot.value?.memory
                var cpu = _currentSnapshot.value?.cpu
                var gc = _currentSnapshot.value?.gc
                var resources = _currentSnapshot.value?.resources

                // Sample memory
                if (now - memoryTick >= settings.memorySampleIntervalMs || memory == null) {
                    memory = collectMemoryMetrics()
                    memoryTick = now
                }

                // Sample CPU
                if (now - cpuTick >= settings.cpuSampleIntervalMs || cpu == null) {
                    cpu = collectCpuMetrics()
                    cpuTick = now
                }

                // Sample GC
                if (now - gcTick >= settings.gcSampleIntervalMs || gc == null) {
                    gc = collectGcMetrics()
                    gcTick = now
                }

                // Sample resources
                if (now - resourceTick >= settings.resourceSampleIntervalMs || resources == null) {
                    resources = collectResourceMetrics()
                    resourceTick = now
                }

                val snapshot = PerformanceSnapshot(
                    timestamp = now,
                    memory = memory,
                    cpu = cpu,
                    gc = gc,
                    resources = resources
                )

                // Only update if values changed to avoid unnecessary recomposition
                val current = _currentSnapshot.value
                if (current == null || hasSignificantChange(current, snapshot)) {
                    _currentSnapshot.value = snapshot
                    _currentHealth.value = PerformanceHealth.fromSnapshot(snapshot, settings)
                }

                // Update history using circular buffer (efficient, no GC pressure)
                val cutoff = now - (settings.historyRetentionMinutes * 60 * 1000)

                // Remove old entries from front
                while (historyBuffer.isNotEmpty() && historyBuffer.first().timestamp < cutoff) {
                    historyBuffer.removeFirst()
                }

                // Add new snapshot
                historyBuffer.addLast(snapshot)

                // Enforce max size (shouldn't happen often with proper retention)
                while (historyBuffer.size > MAX_HISTORY_SIZE) {
                    historyBuffer.removeFirst()
                }

                // Update StateFlow only if needed (creates immutable snapshot)
                _history.value = historyBuffer.toList()

                delay(
                    minOf(
                        settings.memorySampleIntervalMs,
                        settings.cpuSampleIntervalMs
                    )
                )
            }
        }
    }

    /**
     * Stop performance monitoring.
     */
    fun stop() {
        monitoringJob?.cancel()
        monitoringJob = null
        println("[Performance] Stopped performance monitor")
    }

    /**
     * Register resource count providers from BossApp.
     * Call clearResourceProviders() on disposal to prevent memory leaks.
     */
    fun registerResourceProviders(
        browserTabs: () -> Int,
        terminals: () -> Int,
        editorTabs: () -> Int,
        panels: () -> Int,
        windows: () -> Int
    ) {
        browserTabCountProvider = browserTabs
        terminalCountProvider = terminals
        editorTabCountProvider = editorTabs
        panelCountProvider = panels
        windowCountProvider = windows
    }

    /**
     * Register detailed resource providers from BossApp.
     * These provide detailed information about each resource for the Resources tab.
     */
    fun registerDetailedResourceProviders(
        browserTabs: () -> List<BrowserTabInfo>,
        terminals: () -> List<TerminalInfo>,
        editorTabs: () -> List<EditorTabResourceInfo>
    ) {
        browserTabsProvider = browserTabs
        terminalsProvider = terminals
        editorTabsProvider = editorTabs
    }

    /**
     * Clear resource providers to prevent memory leaks.
     * Should be called when BossApp is disposed.
     */
    fun clearResourceProviders() {
        browserTabCountProvider = null
        terminalCountProvider = null
        editorTabCountProvider = null
        panelCountProvider = null
        windowCountProvider = null
        browserTabsProvider = null
        terminalsProvider = null
        editorTabsProvider = null
    }

    private fun collectMemoryMetrics(): MemoryMetrics {
        val heapUsage = memoryMXBean.heapMemoryUsage
        val nonHeapUsage = memoryMXBean.nonHeapMemoryUsage

        // Collect memory pool details
        val memoryPools = memoryPoolMXBeans.map { pool ->
            val usage = pool.usage
            MemoryPoolInfo(
                name = pool.name,
                type = pool.type.name,
                usedBytes = usage?.used ?: 0L,
                maxBytes = usage?.max ?: -1L,
                committedBytes = usage?.committed ?: 0L
            )
        }

        return MemoryMetrics(
            heapUsedBytes = heapUsage.used,
            heapMaxBytes = heapUsage.max,
            heapCommittedBytes = heapUsage.committed,
            nonHeapUsedBytes = nonHeapUsage.used,
            nonHeapCommittedBytes = nonHeapUsage.committed,
            memoryPools = memoryPools
        )
    }

    private fun collectCpuMetrics(): CpuMetrics {
        val processLoad = sunOSBean?.processCpuLoad ?: -1.0
        val systemLoad = sunOSBean?.systemCpuLoad ?: osMXBean.systemLoadAverage

        // Collect thread details - top 20 by CPU time
        val threadIds = threadMXBean.allThreadIds
        val threadInfos = threadMXBean.getThreadInfo(threadIds)

        val threads = threadIds.zip(threadInfos.toList())
            .filter { it.second != null }
            .map { (id, info) ->
                val cpuTime = if (threadMXBean.isThreadCpuTimeSupported) {
                    threadMXBean.getThreadCpuTime(id) / 1_000_000 // nanoseconds to milliseconds
                } else {
                    0L
                }
                val userTime = if (threadMXBean.isThreadCpuTimeSupported) {
                    threadMXBean.getThreadUserTime(id) / 1_000_000
                } else {
                    0L
                }
                ThreadInfo(
                    id = id,
                    name = info!!.threadName,
                    state = info.threadState.name,
                    cpuTimeMs = cpuTime,
                    userTimeMs = userTime,
                    blockedCount = info.blockedCount,
                    waitedCount = info.waitedCount
                )
            }
            .sortedByDescending { it.cpuTimeMs }
            .take(20)

        return CpuMetrics(
            processLoad = if (processLoad >= 0) processLoad else 0.0,
            systemLoad = if (systemLoad >= 0) systemLoad else 0.0,
            availableProcessors = osMXBean.availableProcessors,
            activeThreadCount = threadMXBean.threadCount,
            threads = threads
        )
    }

    private fun collectGcMetrics(): GcMetrics {
        // Create a map of last GC info by collector name
        val lastGcInfoMap = sunGcBeans.associate { gc ->
            val gcInfo = gc.lastGcInfo
            gc.name to gcInfo?.let { info ->
                val memoryBefore = info.memoryUsageBeforeGc.values.sumOf { it.used }
                val memoryAfter = info.memoryUsageAfterGc.values.sumOf { it.used }
                LastGcInfo(
                    startTime = info.startTime,
                    durationMs = info.duration,
                    memoryBeforeBytes = memoryBefore,
                    memoryAfterBytes = memoryAfter
                )
            }
        }

        val collectors = gcMXBeans.map { gc ->
            GcCollectorInfo(
                name = gc.name,
                collectionCount = gc.collectionCount,
                collectionTimeMs = gc.collectionTime,
                lastGcInfo = lastGcInfoMap[gc.name]
            )
        }

        val totalCount = collectors.sumOf { it.collectionCount }
        val totalTime = collectors.sumOf { it.collectionTimeMs }
        val gcTimeSinceLastSample = totalTime - lastGcTime

        lastGcTime = totalTime

        return GcMetrics(
            collectionCount = totalCount,
            collectionTimeMs = totalTime,
            gcTimeSinceLastSampleMs = gcTimeSinceLastSample,
            gcCollectors = collectors
        )
    }

    private fun collectResourceMetrics(): ResourceMetrics {
        return ResourceMetrics(
            browserTabCount = browserTabCountProvider?.invoke() ?: 0,
            terminalCount = terminalCountProvider?.invoke() ?: 0,
            editorTabCount = editorTabCountProvider?.invoke() ?: 0,
            panelCount = panelCountProvider?.invoke() ?: 0,
            windowCount = windowCountProvider?.invoke() ?: 0,
            browserTabs = browserTabsProvider?.invoke() ?: emptyList(),
            terminals = terminalsProvider?.invoke() ?: emptyList(),
            editorTabs = editorTabsProvider?.invoke() ?: emptyList()
        )
    }

    /**
     * Check if there's a significant change between snapshots to avoid unnecessary updates.
     * Thresholds: memory 1MB, CPU 1%, GC count change, resource count change
     */
    private fun hasSignificantChange(old: PerformanceSnapshot, new: PerformanceSnapshot): Boolean {
        // Memory: 1MB threshold
        val memoryDelta = kotlin.math.abs(old.memory.heapUsedBytes - new.memory.heapUsedBytes)
        if (memoryDelta > 1024 * 1024) return true

        // CPU: 1% threshold
        val cpuDelta = kotlin.math.abs(old.cpu.processLoadPercent - new.cpu.processLoadPercent)
        if (cpuDelta > 1.0f) return true

        // GC count changed
        if (old.gc.collectionCount != new.gc.collectionCount) return true

        // Resource counts changed
        if (old.resources != new.resources) return true

        return false
    }

    /**
     * Force garbage collection (for debugging only)
     */
    fun requestGC() {
        System.gc()
        println("[Performance] GC requested")
    }

    /**
     * Export metrics history to a JSON file.
     * Returns Result with file path on success, or error on failure.
     */
    suspend fun exportMetrics(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
            val exportFile = File(System.getProperty("user.home"), ".boss/performance-export-$timestamp.json")
            exportFile.parentFile?.mkdirs()

            val historyData = _history.value
            if (historyData.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No metrics data to export"))
            }

            val content = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(PerformanceSnapshot.serializer()),
                historyData
            )
            exportFile.writeText(content)

            Result.success(exportFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
