package ai.rever.boss.components.window_panel

import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.plugin.api.Panel
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.components.model.TabDraggableComponent
import ai.rever.boss.components.model.TabDropResult
import ai.rever.boss.components.registery.PanelComponentStore
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.components.window_panel.components.BossResizablePanel
import ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
import ai.rever.boss.components.window_panel.components.side_panel.SidePanel
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BossDraggableComponent.BossWindow(
    modifier: Modifier = Modifier,
    tabsComponent: BossTabsComponent,
    panelComponentStore: PanelComponentStore,
    splitViewState: SplitViewState? = null,
    tabDragComponent: TabDraggableComponent? = null,
    onTabDropResult: (TabDropResult) -> Unit = {},
    onShowSettings: (() -> Unit)? = null,
    onOpenProjectDialog: (() -> Unit)? = null,
    onNewProject: (() -> Unit)? = null
) {
    // Process any pending panel opens (for two-phase transitions)
    // This is critical for JxBrowser-based plugins to avoid BrowserViewState conflicts
    ProcessPendingPanelOpen()

    // State for split panels - use provided or create new
    val actualSplitViewState = splitViewState ?: rememberSplitViewState(
        tabRegistry = tabsComponent.tabRegistry,
        windowId = tabsComponent.windowId,
        initialTabsComponent = tabsComponent
    )

    @Composable
    fun WithPanel(panel: Panel,
                  isPanelVisible: Boolean = isVisible(panel),
                  isMainVisible: Boolean = true,
                  isRelative: Boolean = false,
                  panelContent: @Composable BoxScope.() -> Unit = { SidePanel(panel, panelComponentStore) },
                  mainContent: (@Composable BoxScope.() -> Unit)? = null) {
        BossResizablePanel(
            modifier = modifier,
            panel = panel,
            isPanelVisible = isPanelVisible,
            isMainVisible = isMainVisible,
            isRelative = isRelative,
            sideContent = panelContent,
            mainContent = mainContent
        )
    }

    @Composable
    fun WithNestedPanel(panel: Panel,
                        secondaryPanel: Panel = bottom,
                        isFirstPanelVisible: Boolean = isVisible(panel.bottom),
                        isLastPanelVisible: Boolean = isVisible(panel.top),
                        isNestedRelative: Boolean = true,
                        firstPanel: @Composable BoxScope.() -> Unit = { SidePanel(panel.bottom, panelComponentStore) },
                        lastPanel: @Composable BoxScope.() -> Unit = { SidePanel(panel.top, panelComponentStore) },
                        mainContent: @Composable BoxScope.() -> Unit) {
        WithPanel(panel,
            panelContent = {
                WithPanel(secondaryPanel,
                    isPanelVisible = isFirstPanelVisible,
                    isMainVisible = isLastPanelVisible,
                    isRelative = isNestedRelative,
                    panelContent = firstPanel,
                    mainContent = lastPanel
                )},
            mainContent = mainContent)
    }

    WithPanel(bottom) {
        WithNestedPanel(left) {
            WithNestedPanel(right) {
                // Use the new split view panel
                SplitViewPanel(
                    splitViewState = actualSplitViewState,
                    tabDragComponent = tabDragComponent,
                    onTabDropResult = onTabDropResult,
                    onShowSettings = onShowSettings,
                    onOpenProjectDialog = onOpenProjectDialog,
                    onNewProject = onNewProject
                )
            }
        }
    }
}

