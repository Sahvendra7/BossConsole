package ai.rever.boss.components.workspaces

import ai.rever.boss.cache.loadFaviconFromCache
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.plugin.tab_types.fluck.Fluck
import ai.rever.boss.components.plugin.tab_types.EditorTabInfo
import ai.rever.boss.components.plugin.tab_types.CodeEditor
import ai.rever.boss.components.plugin.tab_types.TerminalTabInfo
import ai.rever.boss.components.plugin.tab_types.TerminalTab
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.window_panel.SplitViewState
import ai.rever.boss.components.window_panel.SplitOrientation
import kotlin.random.Random

/**
 * Applies a layout workspace to the split view
 */
suspend fun applyWorkspace(
    workspace: LayoutWorkspace,
    splitViewState: SplitViewState
) {
    // Generate ID if missing
    val workspaceId = workspace.id.ifEmpty { LayoutWorkspace.generateId() }
    
    // Try to restore preserved state first
    if (splitViewState.restorePreservedState(workspaceId)) {
        // State restored successfully
        return
    }
    
    // No preserved state, apply workspace from scratch
    splitViewState.clearAllPanels()
    
    // Apply the workspace recursively
    applyWorkspaceNode(workspace.layout, splitViewState, "main")
}

private suspend fun applyWorkspaceNode(
    node: SplitConfig,
    splitViewState: SplitViewState,
    currentPanelId: String
) {
    when (node) {
        is SplitConfig.SinglePanel -> {
            // Add tabs to current panel
            val tabsComponent = splitViewState.getPanelTabsComponent(currentPanelId)
            node.panel.tabs.forEach { tabConfig ->
                val tab = createTabFromWorkspaceConfig(tabConfig)
                tabsComponent?.addTab(tab)
            }
        }
        
        is SplitConfig.VerticalSplit -> {
            // First process left side in current panel
            when (val leftNode = node.left) {
                is SplitConfig.SinglePanel -> {
                    // Add tabs to current panel
                    val tabsComponent = splitViewState.getPanelTabsComponent(currentPanelId)
                    leftNode.panel.tabs.forEach { tabConfig ->
                        val tab = createTabFromWorkspaceConfig(tabConfig)
                        tabsComponent?.addTab(tab)
                    }
                }
                else -> {
                    // Recursively apply left workspace config
                    applyWorkspaceNode(leftNode, splitViewState, currentPanelId)
                }
            }
            
            // Then create vertical split for right side
            val firstRightTab = getFirstTab(node.right)
            if (firstRightTab != null) {
                val rightPanelId = splitViewState.splitPanel(
                    panelId = currentPanelId,
                    orientation = SplitOrientation.VERTICAL,
                    tabToMove = createTabFromWorkspaceConfig(firstRightTab)
                )
                
                // Add remaining tabs or process splits for right side
                when (val rightNode = node.right) {
                    is SplitConfig.SinglePanel -> {
                        // Add remaining tabs
                        val tabsComponent = splitViewState.getPanelTabsComponent(rightPanelId)
                        rightNode.panel.tabs.drop(1).forEach { tabConfig ->
                            val tab = createTabFromWorkspaceConfig(tabConfig)
                            tabsComponent?.addTab(tab)
                        }
                    }
                    else -> {
                        // Recursively apply right workspace config
                        applyWorkspaceNode(rightNode, splitViewState, rightPanelId)
                    }
                }
            }
        }
        
        is SplitConfig.HorizontalSplit -> {
            // First process top side in current panel
            when (val topNode = node.top) {
                is SplitConfig.SinglePanel -> {
                    // Add tabs to current panel
                    val tabsComponent = splitViewState.getPanelTabsComponent(currentPanelId)
                    topNode.panel.tabs.forEach { tabConfig ->
                        val tab = createTabFromWorkspaceConfig(tabConfig)
                        tabsComponent?.addTab(tab)
                    }
                }
                else -> {
                    // Recursively apply top workspace config
                    applyWorkspaceNode(topNode, splitViewState, currentPanelId)
                }
            }
            
            // Then create horizontal split for bottom side
            val firstBottomTab = getFirstTab(node.bottom)
            if (firstBottomTab != null) {
                val bottomPanelId = splitViewState.splitPanel(
                    panelId = currentPanelId,
                    orientation = SplitOrientation.HORIZONTAL,
                    tabToMove = createTabFromWorkspaceConfig(firstBottomTab)
                )
                
                // Add remaining tabs or process splits for bottom side
                when (val bottomNode = node.bottom) {
                    is SplitConfig.SinglePanel -> {
                        // Add remaining tabs
                        val tabsComponent = splitViewState.getPanelTabsComponent(bottomPanelId)
                        bottomNode.panel.tabs.drop(1).forEach { tabConfig ->
                            val tab = createTabFromWorkspaceConfig(tabConfig)
                            tabsComponent?.addTab(tab)
                        }
                    }
                    else -> {
                        // Recursively apply bottom workspace config
                        applyWorkspaceNode(bottomNode, splitViewState, bottomPanelId)
                    }
                }
            }
        }
    }
}

private fun getFirstTab(workspaceConfig: SplitConfig): TabConfig? {
    return when (workspaceConfig) {
        is SplitConfig.SinglePanel -> workspaceConfig.panel.tabs.firstOrNull()
        is SplitConfig.VerticalSplit -> getFirstTab(workspaceConfig.left)
        is SplitConfig.HorizontalSplit -> getFirstTab(workspaceConfig.top)
    }
}

private fun createTabFromWorkspaceConfig(tabConfig: TabConfig): TabInfo {
    return when (tabConfig.type) {
        "browser" -> {
            // Load favicon from cache if available (Issue #160)
            val cachedFavicon = loadFaviconFromCache(tabConfig.faviconCacheKey)

            FluckTabInfo(
                id = "browser-${Random.nextLong()}",
                typeId = Fluck.typeId,
                _title = tabConfig.title,
                _tabIcon = cachedFavicon,
                url = tabConfig.url ?: "about:blank",
                faviconCacheKey = tabConfig.faviconCacheKey
            )
        }
        "terminal" -> TerminalTabInfo(
            id = "terminal-${Random.nextLong()}",
            typeId = TerminalTab.typeId,
            title = tabConfig.title
        )
        "editor" -> EditorTabInfo(
            id = "editor-${Random.nextLong()}",
            typeId = CodeEditor.typeId,
            title = tabConfig.title,
            filePath = tabConfig.filePath ?: ""
        )
        else -> throw IllegalArgumentException("Unknown tab type: ${tabConfig.type}")
    }
}
