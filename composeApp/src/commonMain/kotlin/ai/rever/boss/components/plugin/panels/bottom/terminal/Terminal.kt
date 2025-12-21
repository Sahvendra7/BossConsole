package ai.rever.boss.components.plugin.panels.bottom.terminal

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.components.events.PanelEventBus
import ai.rever.boss.window.MenuActionsHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object TerminalInfo : PanelInfo {
    override val id = PanelId("terminal", 13)
    override val displayName = "Terminal"
    override val icon = Icons.Outlined.Terminal
    override val defaultSlotPosition = left.bottom
}

/**
 * Terminal panel component using BossTerm's TabbedTerminal for full-featured terminal.
 *
 * Features:
 * - Multiple tabs within the panel
 * - Split panes (horizontal/vertical)
 * - Tab management keyboard shortcuts
 * - Settings integration
 */
class TerminalComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.subscribe(
            callbacks = object : Callbacks {
                override fun onDestroy() {
                    coroutineScope.cancel()
                }
            }
        )
    }

    @Composable
    override fun Content() {
        TabbedTerminalContent(
            onExit = {
                coroutineScope.launch {
                    PanelEventBus.closePanel(panelInfo.id)
                }
            },
            onShowSettings = {
                // Trigger global settings to open BOSS Settings → Terminal tab
                MenuActionsHandler.triggerGlobalOpenSettings("TERMINAL")
            }
        )
    }
}

/**
 * Platform-specific tabbed terminal content composable.
 * Desktop: Uses BossTerm's TabbedTerminal with multi-tab and split support
 * Other platforms: Shows placeholder (terminal not supported)
 *
 * This is the full-featured terminal for the sidebar panel.
 *
 * @param onExit Called when the last terminal tab is closed
 * @param onShowSettings Called when user requests settings (right-click menu)
 */
@Composable
expect fun TabbedTerminalContent(
    onExit: () -> Unit = {},
    onShowSettings: () -> Unit = {}
)

/**
 * Platform-specific embedded terminal content composable.
 * Desktop: Uses BossTerm's EmbeddableTerminal (single terminal instance)
 * Other platforms: Shows placeholder (terminal not supported)
 *
 * This is used for terminal tabs where BOSS manages the tab lifecycle.
 *
 * @param terminalId Unique ID for this terminal instance. Used to preserve state across
 *                   composition tree changes (e.g., when splitting panels). If null, state
 *                   is tied to composition position and may be lost on tree restructuring.
 * @param initialCommand Optional command to run after terminal starts
 * @param onExit Called when terminal process exits
 */
@Composable
expect fun TerminalContent(
    terminalId: String? = null,
    initialCommand: String? = null,
    onExit: () -> Unit = {}
)

fun DefaultPlugin.registerTerminal() = panelRegistry.registerPanel(TerminalInfo) {
    ctx, panelInfo -> TerminalComponent(ctx, panelInfo)
}
