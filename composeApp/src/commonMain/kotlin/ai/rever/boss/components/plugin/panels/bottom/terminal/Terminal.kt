package ai.rever.boss.components.plugin.panels.bottom.terminal

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.components.events.PanelEventBus
import ai.rever.boss.window.LocalWindowId
import ai.rever.boss.window.LocalWindowProjectState
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

    /**
     * Called when user clicks Reset in the panel's more menu.
     * Resets all terminal states to fix persistent issues.
     */
    override fun onBeforeReset() {
        resetTerminals()
    }

    @Composable
    override fun Content() {
        val windowId = LocalWindowId.current
        val windowProjectState = LocalWindowProjectState.current
        // Per-window project state (required for multi-window support)
        val projectPath = windowProjectState?.selectedProject?.value?.path ?: ""
        TabbedTerminalContent(
            workingDirectory = projectPath.ifEmpty { null },
            onExit = {
                windowId?.let { wid ->
                    coroutineScope.launch {
                        PanelEventBus.closePanel(panelInfo.id, sourceWindowId = wid)
                    }
                }
            },
            onShowSettings = {
                windowId?.let { MenuActionsHandler.triggerOpenSettings(it, "TERMINAL") }
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
 * @param workingDirectory Optional working directory for the terminal (defaults to home directory)
 * @param onExit Called when the last terminal tab is closed
 * @param onShowSettings Called when user requests settings (right-click menu)
 */
@Composable
expect fun TabbedTerminalContent(
    workingDirectory: String? = null,
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
 * @param workingDirectory Optional working directory for the terminal (defaults to home directory)
 * @param onExit Called when terminal process exits
 */
@Composable
expect fun TerminalContent(
    terminalId: String? = null,
    initialCommand: String? = null,
    workingDirectory: String? = null,
    onExit: () -> Unit = {}
)

/**
 * Platform-specific tabbed terminal with persistent state.
 * Desktop: Uses BossTerm's TabbedTerminal with TabbedTerminalStateRegistry
 * Other platforms: Shows placeholder (terminal not supported)
 *
 * This provides full-featured terminal (splits, multiple tabs) with state
 * persistence across composition changes (e.g., when switching parent tabs).
 *
 * @param terminalId Unique ID for this terminal instance, used as key in state registry
 * @param initialCommand Optional command to run after terminal starts (only for new terminals)
 * @param workingDirectory Optional working directory for the terminal (defaults to home directory)
 * @param onExit Called when the last terminal tab is closed
 * @param onShowSettings Called when user requests settings
 * @param onTitleChange Called when terminal window title changes via escape sequences (OSC 0/1/2)
 */
@Composable
expect fun PersistentTabbedTerminalContent(
    terminalId: String,
    initialCommand: String? = null,
    workingDirectory: String? = null,
    onExit: () -> Unit = {},
    onShowSettings: () -> Unit = {},
    onTitleChange: ((String) -> Unit)? = null
)

/**
 * Platform-specific function to reset all terminal states.
 * Called when user triggers reset from panel's more menu.
 */
expect fun resetTerminals()

fun DefaultPlugin.registerTerminal() = panelRegistry.registerPanel(TerminalInfo) {
    ctx, panelInfo -> TerminalComponent(ctx, panelInfo)
}
