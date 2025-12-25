package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.panels.bottom.terminal.PersistentTabbedTerminalContent
import ai.rever.boss.components.registery.*
import ai.rever.boss.window.MenuActionsHandler
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
) : TabInfo {
    companion object {
        /** Maximum length for terminal tab titles - fits typical "user@hostname:/path" patterns */
        const val MAX_TITLE_LENGTH = 64
    }

    /**
     * Returns a copy of this tab info with an updated title.
     * Used when terminal window title changes via escape sequences (OSC 0/1/2).
     * Title is truncated to [MAX_TITLE_LENGTH] characters.
     */
    fun updateTitle(newTitle: String): TerminalTabInfo {
        val truncatedTitle = if (newTitle.length > MAX_TITLE_LENGTH) {
            newTitle.take(MAX_TITLE_LENGTH)
        } else {
            newTitle
        }
        return copy(title = truncatedTitle)
    }
}

/**
 * Terminal tab component using BossTerm library for terminal emulation.
 */
class TerminalTabComponent(
    override val config: TabInfo,
    private val componentContext: ComponentContext,
    private val onClose: () -> Unit,
    private val onTitleUpdate: (String) -> Unit
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
        PersistentTabbedTerminalContent(
            terminalId = config.id,
            onExit = { onClose() },
            onShowSettings = {
                MenuActionsHandler.triggerGlobalOpenSettings("TERMINAL")
            },
            onTitleChange = { newTitle ->
                onTitleUpdate(newTitle)
            }
        )
    }
}

fun DefaultPlugin.registerTerminalTab() = tabRegistry.registerTabType(TerminalTab) { config, context ->
    // Get the parent tab component through the component tree
    val parentTabsComponent = context as? ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent

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
