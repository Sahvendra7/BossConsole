package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.panels.bottom.terminal.PersistentTabbedTerminalContent
import ai.rever.boss.components.registery.*
import ai.rever.boss.plugin.tab.terminal.TerminalTabInfo
import ai.rever.boss.plugin.tab.terminal.TerminalTabType
import ai.rever.boss.run.RUNNER_TERMINAL_PREFIX
import ai.rever.boss.run.RunnerTerminalService
import ai.rever.boss.window.LocalWindowId
import ai.rever.boss.window.MenuActionsHandler
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Terminal tab component using BossTerm library for terminal emulation.
 */
class TerminalTabComponent(
    override val config: TabInfo,
    private val componentContext: ComponentContext,
    private val onClose: () -> Unit,
    private val onTitleUpdate: (String) -> Unit
) : TabComponentWithUI, ComponentContext by componentContext {

    override val tabTypeInfo = TerminalTabType
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
        val windowId = LocalWindowId.current

        // Get initial command and working directory from config if it's a TerminalTabInfo
        val terminalConfig = config as? TerminalTabInfo
        val initialCommand = terminalConfig?.initialCommand
        val workingDirectory = terminalConfig?.workingDirectory

        PersistentTabbedTerminalContent(
            terminalId = config.id,
            initialCommand = initialCommand,
            workingDirectory = workingDirectory,
            onExit = { onClose() },
            onShowSettings = {
                windowId?.let { MenuActionsHandler.triggerOpenSettings(it, "TERMINAL") }
            },
            onTitleChange = { newTitle ->
                onTitleUpdate(newTitle)
            }
        )
    }
}

fun DefaultPlugin.registerTerminalTab() = tabRegistry.registerTabType(TerminalTabType) { config, context ->
    // Get the parent tab component through the component tree
    val parentTabsComponent = context as? ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent

    TerminalTabComponent(
        config = config,
        componentContext = context,
        onClose = {
            // If this is a runner terminal, notify the service that the terminal process
            // has stopped. This updates the Run/Stop button state.
            if (config.id.startsWith(RUNNER_TERMINAL_PREFIX)) {
                RunnerTerminalService.markTerminalStopped(config.id)
            }

            // Find and remove this tab
            parentTabsComponent?.let { tabs ->
                val index = tabs.tabsState.value.tabs.indexOfFirst { it.id == config.id }
                if (index >= 0) {
                    tabs.removeTab(index)
                }
            }
        },
        onTitleUpdate = { newTitle ->
            // Update the tab title when terminal window title changes
            parentTabsComponent?.let { parent ->
                val tabs = parent.tabsState.value.tabs
                val tabIndex = tabs.indexOfFirst { it.id == config.id }

                if (tabIndex >= 0) {
                    val currentTab = tabs[tabIndex]
                    if (currentTab is TerminalTabInfo) {
                        parent.updateTab(tabIndex, currentTab.updateTitle(newTitle))
                    }
                }
            }
        }
    )
}
