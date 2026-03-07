package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.tab.terminal.TerminalTabType
import ai.rever.boss.plugin.tab.terminal.TerminalTabInfo
import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.components.window_panel.SplitViewState
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.plugin.api.SplitViewOperations
import ai.rever.boss.plugin.api.TabsComponent
import ai.rever.boss.plugin.workspace.LayoutWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Implementation of SplitViewOperations that wraps SplitViewState.
 * This allows plugins to interact with the split view without direct coupling.
 */
class SplitViewOperationsImpl(
    private val splitViewState: SplitViewState,
    private val windowId: String
) : SplitViewOperations {

    private val logger = BossLogger.forComponent("SplitViewOperationsImpl")

    // Coroutine scope for launching background operations
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun openUrlInActivePanel(url: String, title: String, forceNewTab: Boolean) {
        splitViewState.openUrlInActivePanel(url, title, forceNewTab)
    }

    override fun openFileInActivePanel(filePath: String, fileName: String) {
        splitViewState.openFileInActivePanel(filePath, fileName)
    }

    override fun openFileInBrowser(filePath: String, fileName: String) {
        splitViewState.openUrlInActivePanel(SplitViewState.toFileUrl(filePath), fileName)
    }

    override fun openFileInEditor(filePath: String, fileName: String) {
        splitViewState.openFileInEditorTab(filePath, fileName)
    }

    override fun openFileAtPosition(filePath: String, fileName: String, line: Int, column: Int) {
        // Use FileEventBus to open file with position - BossApp listens and handles NavigationTargetBus
        logger.debug(LogCategory.UI, "openFileAtPosition: $filePath:$line:$column", mapOf("windowId" to windowId))
        scope.launch {
            FileEventBus.openFile(filePath, line, column, sourceWindowId = windowId)
            logger.debug(LogCategory.UI, "FileEventBus.openFile called")
        }
    }

    override fun setActivePanel(panelId: String) {
        splitViewState.setActivePanel(panelId)
    }

    override fun preserveCurrentState(workspaceId: String, workspaceName: String) {
        splitViewState.preserveCurrentState(workspaceId, workspaceName)
    }

    override fun getActiveTabsComponent(): TabsComponent? {
        val bossTabsComponent = splitViewState.getActiveTabsComponent() ?: return null
        return TabsComponentWrapper(bossTabsComponent)
    }

    override fun applyWorkspace(workspace: LayoutWorkspace) {
        // Launch the suspend function in a coroutine
        // The workspace is already the correct type (plugin LayoutWorkspace == composeApp LayoutWorkspace via typealias)
        scope.launch {
            ai.rever.boss.components.workspaces.applyWorkspace(workspace, splitViewState)
        }
    }

    override fun selectTabInPanel(tabId: String, panelId: String) {
        splitViewState.selectTabInPanel(tabId, panelId)
    }
}

/**
 * Wrapper around BossTabsComponent to implement the plugin TabsComponent interface.
 */
private class TabsComponentWrapper(
    private val bossTabsComponent: ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
) : TabsComponent {

    override fun addTerminalTab(id: String, title: String, workingDirectory: String?, initialCommand: String?) {
        val terminalTabInfo = TerminalTabInfo(
            id = id,
            typeId = TerminalTabType.typeId,
            title = title,
            initialCommand = initialCommand,
            workingDirectory = workingDirectory
        )
        bossTabsComponent.addTab(terminalTabInfo)
    }
}
