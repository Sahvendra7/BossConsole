package ai.rever.boss.actions

import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.TabTreeState
import ai.rever.boss.components.plugin.panels.left_top.CodeBaseInfo
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabComponent
import ai.rever.boss.components.window_panel.NavigationDirection
import ai.rever.boss.components.window_panel.SplitViewState
import ai.rever.boss.components.workspaces.WorkspaceManager
import ai.rever.boss.components.workspaces.extractCurrentWorkspace
import ai.rever.boss.keymap.model.KeymapActions
import ai.rever.boss.focusmode.FocusModeSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles keyboard shortcut actions for BossApp.
 * Extracted from BossApp.kt to improve testability and reduce complexity.
 *
 * This class delegates each action to dedicated handler methods, making the code
 * more maintainable and easier to test.
 */
class BossActionHandler(
    private val splitViewState: SplitViewState,
    private val windowId: String,
    private val workspaceManager: WorkspaceManager,
    private val draggablePanelComponent: BossDraggableComponent,
    private val onShowNewTabDialog: () -> Unit,
    private val onShowTopOfMindDialog: () -> Unit,
    private val onShowSaveMessage: (String?) -> Unit,
    private val onShowSettings: () -> Unit,
    private val coroutineScope: CoroutineScope
) {
    fun handleAction(actionId: String): Boolean {
        return when (actionId) {
            KeymapActions.WINDOW_NEW -> handleWindowNew()
            KeymapActions.WINDOW_CLOSE -> handleWindowClose()
            KeymapActions.TAB_NEW -> handleTabNew()
            KeymapActions.TAB_CLOSE -> handleTabClose()
            KeymapActions.BROWSER_RELOAD -> handleBrowserReload()
            KeymapActions.BROWSER_ZOOM_RESET -> handleBrowserZoomReset()
            KeymapActions.BROWSER_ZOOM_IN -> handleBrowserZoomIn()
            KeymapActions.BROWSER_ZOOM_OUT -> handleBrowserZoomOut()
            KeymapActions.PANEL_NAVIGATE_LEFT -> handlePanelNavigateLeft()
            KeymapActions.PANEL_NAVIGATE_RIGHT -> handlePanelNavigateRight()
            KeymapActions.PANEL_NAVIGATE_UP -> handlePanelNavigateUp()
            KeymapActions.PANEL_NAVIGATE_DOWN -> handlePanelNavigateDown()
            KeymapActions.QUICK_SWITCHER_OPEN -> handleQuickSwitcherOpen()
            KeymapActions.WORKSPACE_SAVE -> handleWorkspaceSave()
            KeymapActions.CODEBASE_OPEN -> handleCodebaseOpen()
            KeymapActions.FOCUS_MODE_TOGGLE -> handleFocusModeToggle()
            KeymapActions.SETTINGS_OPEN -> handleSettingsOpen()
            KeymapActions.TEST_EXTERNAL_LINK -> handleTestExternalLink()
            // Editor actions - handled by component-level key handlers (BossEditor, LargeFileViewer)
            KeymapActions.EDITOR_FIND,
            KeymapActions.EDITOR_FIND_NEXT,
            KeymapActions.EDITOR_FIND_PREVIOUS,
            KeymapActions.EDITOR_REPLACE -> false
            else -> false
        }
    }

    // Window Actions

    private fun handleWindowNew(): Boolean {
        ai.rever.boss.window.WindowOperations.createNewWindow()
        return true
    }

    private fun handleWindowClose(): Boolean {
        ai.rever.boss.window.WindowOperations.closeWindow(windowId)
        return true
    }

    // Tab Actions

    private fun handleTabNew(): Boolean {
        onShowNewTabDialog()
        return true
    }

    private fun handleTabClose(): Boolean {
        val allPanels = splitViewState.getAllPanels()
        val totalTabs = allPanels.sumOf { panel ->
            panel.tabsComponent.tabsState.value.tabs.size
        }

        if (totalTabs == 0) {
            ai.rever.boss.window.WindowOperations.closeWindow(windowId)
        } else {
            val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
            if (activeTabsComponent != null) {
                val tabs = activeTabsComponent.tabsState.value.tabs
                val activeIndex = activeTabsComponent.tabsState.value.activeIndex
                if (activeIndex >= 0 && activeIndex < tabs.size) {
                    activeTabsComponent.removeTab(activeIndex)

                    val updatedTotalTabs = allPanels.sumOf { panel ->
                        panel.tabsComponent.tabsState.value.tabs.size
                    }

                    if (updatedTotalTabs == 0) {
                        ai.rever.boss.window.WindowOperations.closeWindow(windowId)
                    }
                }
            }
        }
        return true
    }

    // Browser Actions

    private fun handleBrowserReload(): Boolean {
        val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
        if (activeTabsComponent != null) {
            val activeTab = activeTabsComponent.tabsState.value.activeTab
            if (activeTab is FluckTabInfo) {
                val activeTabComponent = activeTabsComponent.getActiveComponent()
                if (activeTabComponent is FluckTabComponent) {
                    activeTabComponent.reload()
                }
            }
        }
        return true
    }

    private fun handleBrowserZoomReset(): Boolean {
        val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
        val activeTab = activeTabsComponent?.getActiveComponent()
        return if (activeTab is FluckTabComponent) {
            activeTab.actualSize()
            true
        } else {
            false
        }
    }

    private fun handleBrowserZoomIn(): Boolean {
        val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
        val activeTab = activeTabsComponent?.getActiveComponent()
        return if (activeTab is FluckTabComponent) {
            activeTab.zoomIn()
            true
        } else {
            false
        }
    }

    private fun handleBrowserZoomOut(): Boolean {
        val activeTabsComponent = splitViewState.getPanelTabsComponent(splitViewState.activePanelId)
        val activeTab = activeTabsComponent?.getActiveComponent()
        return if (activeTab is FluckTabComponent) {
            activeTab.zoomOut()
            true
        } else {
            false
        }
    }

    // Panel Navigation Actions

    private fun handlePanelNavigateLeft(): Boolean {
        return splitViewState.findPanelInDirection(NavigationDirection.LEFT)?.let { panel ->
            splitViewState.setActivePanel(panel.id)
            true
        } ?: false
    }

    private fun handlePanelNavigateRight(): Boolean {
        return splitViewState.findPanelInDirection(NavigationDirection.RIGHT)?.let { panel ->
            splitViewState.setActivePanel(panel.id)
            true
        } ?: false
    }

    private fun handlePanelNavigateUp(): Boolean {
        return splitViewState.findPanelInDirection(NavigationDirection.UP)?.let { panel ->
            splitViewState.setActivePanel(panel.id)
            true
        } ?: false
    }

    private fun handlePanelNavigateDown(): Boolean {
        return splitViewState.findPanelInDirection(NavigationDirection.DOWN)?.let { panel ->
            splitViewState.setActivePanel(panel.id)
            true
        } ?: false
    }

    // Quick Switcher Action

    private fun handleQuickSwitcherOpen(): Boolean {
        onShowTopOfMindDialog()
        return true
    }

    // Workspace Actions

    private fun handleWorkspaceSave(): Boolean {
        coroutineScope.launch {
            val currentConfig = workspaceManager.currentWorkspace.value
            if (currentConfig != null) {
                val currentLayout = extractCurrentWorkspace(splitViewState)
                val updatedConfig = currentConfig.copy(
                    layout = currentLayout.layout,
                    timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()
                )
                workspaceManager.updateCurrentWorkspace(updatedConfig)
                workspaceManager.saveCurrentWorkspace()
                TabTreeState.markWorkspaceAsSaved(currentConfig.id)
                onShowSaveMessage("Workspace '${currentConfig.name}' saved successfully")
                delay(3000)
                onShowSaveMessage(null)
            } else {
                val currentLayout = extractCurrentWorkspace(splitViewState)
                val newConfig = currentLayout.copy(
                    name = "Workspace ${kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000}",
                    description = "Saved workspace"
                )
                workspaceManager.updateCurrentWorkspace(newConfig)
                workspaceManager.saveCurrentWorkspace()
                onShowSaveMessage("New workspace '${newConfig.name}' saved successfully")
                delay(3000)
                onShowSaveMessage(null)
            }
        }
        return true
    }

    // Panel Actions

    private fun handleCodebaseOpen(): Boolean {
        // Find the CodeBase sidebar item and simulate clicking it
        val codebaseSlot = CodeBaseInfo.defaultSlotPosition // left.top.top
        val codebaseItems = draggablePanelComponent.getItemsForSlot(codebaseSlot)
        val codebaseItem = codebaseItems.find { it.pluginContentId.panelId == "codebase" }

        return if (codebaseItem != null) {
            // Use the draggableComponent's onClick handler
            draggablePanelComponent.onClick.invoke(codebaseItem)
            true
        } else {
            false
        }
    }

    // View/UI Actions

    private fun handleFocusModeToggle(): Boolean {
        coroutineScope.launch {
            FocusModeSettingsManager.toggleFocusMode()
        }
        return true
    }

    private fun handleSettingsOpen(): Boolean {
        onShowSettings()
        return true
    }

    // Test Actions

    private fun handleTestExternalLink(): Boolean {
        coroutineScope.launch {
            ai.rever.boss.components.events.URLEventBus.openURL(
                "https://www.google.com",
                "Google"
            )
        }
        return true
    }
}
