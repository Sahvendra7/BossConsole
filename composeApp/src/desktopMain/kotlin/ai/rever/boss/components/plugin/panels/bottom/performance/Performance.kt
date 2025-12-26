package ai.rever.boss.components.plugin.panels.bottom.performance

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks

/**
 * Performance panel info.
 * Displays JVM metrics, CPU, memory, and resource counts.
 */
object PerformanceInfo : PanelInfo {
    override val id = PanelId("performance", 15) // ID 15 (Console is 14)
    override val displayName = "Performance"
    override val icon = Icons.Outlined.Speed
    override val defaultSlotPosition = left.bottom // Same area as Terminal, Console
}

/**
 * Performance panel component.
 * Shows real-time JVM performance metrics with tabs.
 */
class PerformanceComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = PerformanceViewModel()

    init {
        lifecycle.subscribe(
            callbacks = object : Callbacks {
                override fun onDestroy() {
                    viewModel.dispose()
                }
            }
        )
    }

    @Composable
    override fun Content() {
        PerformanceView(viewModel)
    }
}

/**
 * Register Performance panel with the plugin system (desktop implementation).
 */
actual fun DefaultPlugin.registerPerformance() = panelRegistry.registerPanel(PerformanceInfo) { ctx, panelInfo ->
    PerformanceComponent(ctx, panelInfo)
}
