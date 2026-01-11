package ai.rever.boss.components.plugin.panels.bottom.console

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks

/**
 * Console panel info
 * Displays captured stdout/stderr logs in a side panel
 */
object ConsoleInfo : PanelInfo {
    override val id = PanelId("console", 16) // After Git Log (15)
    override val displayName = "Console"
    override val icon = Icons.Outlined.Info
    override val defaultSlotPosition = left.bottom
}

/**
 * Console panel component
 * Shows real-time application logs captured from System.out and System.err
 */
class ConsoleComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = ai.rever.boss.components.plugin.panels.bottom.console.ConsoleViewModel()

    init {
        // Dispose view model when panel closes
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
        ai.rever.boss.components.plugin.panels.bottom.console.ConsoleView(viewModel)
    }
}

/**
 * Register Console panel with the plugin system (desktop implementation)
 */
actual fun DefaultPlugin.registerConsole() = panelRegistry.registerPanel(ConsoleInfo) {
    ctx, panelInfo -> ConsoleComponent(ctx, panelInfo)
}
