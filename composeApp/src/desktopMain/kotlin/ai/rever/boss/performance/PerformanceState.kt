package ai.rever.boss.performance

import ai.rever.boss.components.events.PanelEventBus
import ai.rever.boss.components.registery.PanelId
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Desktop implementation of PerformanceState.
 * Uses PerformanceMonitor and PerformanceSettingsManager to provide state.
 */
actual object PerformanceState {
    private val scope = CoroutineScope(Dispatchers.Main)

    @Composable
    actual fun currentSnapshot(): PerformanceSnapshot? {
        val snapshot by PerformanceMonitor.currentSnapshot.collectAsState()
        return snapshot
    }

    @Composable
    actual fun currentHealth(): PerformanceHealth {
        val health by PerformanceMonitor.currentHealth.collectAsState()
        return health
    }

    @Composable
    actual fun shouldShowIndicator(): Boolean {
        val settings by PerformanceSettingsManager.currentSettings.collectAsState()
        return settings.showIndicator && settings.enabled
    }

    actual fun openPerformancePanel() {
        scope.launch {
            PanelEventBus.openPanel(PanelId("performance", 15))
        }
    }
}
