package ai.rever.boss.plugin.panel.terminal

import ai.rever.boss.plugin.api.LocalWindowIdProvider
import ai.rever.boss.plugin.api.LocalWindowProjectStateProvider
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    override val panelInfo: PanelInfo,
    private val terminalContentProvider: TerminalContentProvider,
    private val panelEventProvider: PanelEventProvider,
    private val settingsProvider: SettingsProvider
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
        terminalContentProvider.resetTerminals()
    }

    @Composable
    override fun Content() {
        val windowIdProvider = LocalWindowIdProvider.current
        val windowProjectStateProvider = LocalWindowProjectStateProvider.current
        val windowId = windowIdProvider?.getWindowId()
        val projectPath = windowProjectStateProvider?.getSelectedProjectPath() ?: ""

        terminalContentProvider.TabbedTerminalContent(
            workingDirectory = projectPath.ifEmpty { null },
            onExit = {
                windowId?.let { wid ->
                    coroutineScope.launch {
                        panelEventProvider.closePanel(panelInfo.id, wid)
                    }
                }
            },
            onShowSettings = {
                windowId?.let { settingsProvider.openSettings(it, "TERMINAL") }
            }
        )
    }
}

/**
 * Provider interface for terminal content - platform-specific implementation.
 */
interface TerminalContentProvider {
    /**
     * Display tabbed terminal content.
     */
    @Composable
    fun TabbedTerminalContent(
        workingDirectory: String? = null,
        onExit: () -> Unit = {},
        onShowSettings: () -> Unit = {}
    )

    /**
     * Reset all terminal states.
     */
    fun resetTerminals()
}

/**
 * Provider interface for panel events.
 */
interface PanelEventProvider {
    /**
     * Close the panel.
     */
    suspend fun closePanel(panelId: ai.rever.boss.plugin.api.PanelId, windowId: String)
}

/**
 * Provider interface for opening settings.
 */
interface SettingsProvider {
    /**
     * Open settings at specific section.
     */
    fun openSettings(windowId: String, section: String)
}
