package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.panels.bottom.terminal.TerminalContent
import ai.rever.boss.components.registery.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

object TerminalTab : TabTypeInfo {
    override val typeId = TabTypeId("terminal")
    override val icon = Icons.Outlined.Terminal
    override val displayName = "Terminal"
}

data class TerminalTabInfo(
    override val id: String,
    override val typeId: TabTypeId,
    override val title: String = "Terminal",
    override val icon: androidx.compose.ui.graphics.vector.ImageVector = TerminalTab.icon,
    override val tabIcon: TabIcon = TabIcon.Vector(icon),
    val initialCommand: String? = null
) : TabInfo

/**
 * Terminal tab component using BossTerm library for terminal emulation.
 */
class TerminalTabComponent(
    override val config: TabInfo,
    private val componentContext: ComponentContext,
    private val onClose: () -> Unit
) : TabComponentWithUI, ComponentContext by componentContext {

    override val tabTypeInfo = TerminalTab
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.subscribe(
            callbacks = object : Lifecycle.Callbacks {
                override fun onDestroy() {
                    coroutineScope.cancel()
                }
            }
        )
    }

    @Composable
    override fun Content() {
        val terminalConfig = config as? TerminalTabInfo

        TerminalContent(
            terminalId = config.id,  // Use tab ID to persist state across composition changes
            initialCommand = terminalConfig?.initialCommand,
            onExit = { onClose() }
        )
    }
}

fun DefaultPlugin.registerTerminalTab() = tabRegistry.registerTabType(TerminalTab) { config, context ->
    // Get the parent tab component through the component tree
    var parentTabsComponent: ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent? = null

    // The context passed here is the BossTabsComponent itself
    if (context is ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent) {
        parentTabsComponent = context
    }

    TerminalTabComponent(
        config = config,
        componentContext = context,
        onClose = {
            // Find and remove this tab
            parentTabsComponent?.let { tabs ->
                val index = tabs.tabsState.value.tabs.indexOfFirst { it.id == config.id }
                if (index >= 0) {
                    tabs.removeTab(index)
                }
            }
        }
    )
}
