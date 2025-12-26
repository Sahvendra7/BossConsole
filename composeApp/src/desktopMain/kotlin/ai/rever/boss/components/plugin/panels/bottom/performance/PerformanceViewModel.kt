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

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    fun selectTab(tab: Tab) {
        _selectedTab.value = tab
    }

    fun requestGC() {
        PerformanceMonitor.requestGC()
    }

    fun exportMetrics() {
        scope.launch {
            val result = PerformanceMonitor.exportMetrics()
            _exportResult.value = result
            // Open the exported file in a new editor tab
            if (result != null) {
                FileEventBus.openFile(result)
            }
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
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
