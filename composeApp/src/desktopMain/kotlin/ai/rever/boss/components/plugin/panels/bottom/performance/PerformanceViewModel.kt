package ai.rever.boss.components.plugin.panels.bottom.performance

import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.performance.PerformanceMonitor
import ai.rever.boss.performance.PerformanceSettings
import ai.rever.boss.performance.PerformanceSettingsManager
import ai.rever.boss.performance.PerformanceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Performance panel.
 */
class PerformanceViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val currentSnapshot: StateFlow<PerformanceSnapshot?> = PerformanceMonitor.currentSnapshot
    val history: StateFlow<List<PerformanceSnapshot>> = PerformanceMonitor.history
    val settings: StateFlow<PerformanceSettings> = PerformanceSettingsManager.currentSettings

    // Selected tab in panel
    enum class Tab {
        OVERVIEW, MEMORY, CPU, TIMINGS, RESOURCES
    }

    private val _selectedTab = MutableStateFlow(Tab.OVERVIEW)
    val selectedTab: StateFlow<Tab> = _selectedTab.asStateFlow()

    // Export state: Success path (contains file path), Error (contains message), or null (idle)
    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    fun selectTab(tab: Tab) {
        _selectedTab.value = tab
    }

    fun requestGC() {
        PerformanceMonitor.requestGC()
    }

    fun exportMetrics() {
        scope.launch {
            _isExporting.value = true
            _exportError.value = null

            PerformanceMonitor.exportMetrics()
                .onSuccess { filePath ->
                    _exportResult.value = filePath
                    // Open the exported file in a new editor tab
                    FileEventBus.openFile(filePath)
                }
                .onFailure { error ->
                    _exportError.value = error.message ?: "Failed to export metrics"
                }

            _isExporting.value = false
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    fun clearExportError() {
        _exportError.value = null
    }

    fun updateSettings(newSettings: PerformanceSettings) {
        scope.launch {
            PerformanceSettingsManager.updateSettings(newSettings)
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
