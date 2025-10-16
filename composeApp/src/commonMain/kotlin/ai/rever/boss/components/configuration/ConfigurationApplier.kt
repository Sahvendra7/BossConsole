package ai.rever.boss.components.configuration

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
 * Applies a layout configuration to the split view
 */
suspend fun applyConfiguration(
    configuration: LayoutConfiguration,
    splitViewState: SplitViewState
) {
    // Generate ID if missing
    val configId = configuration.id.ifEmpty { LayoutConfiguration.generateId() }
    
    // Try to restore preserved state first
    if (splitViewState.restorePreservedState(configId)) {
        // State restored successfully
        return
    }
    
    // No preserved state, apply configuration from scratch
    splitViewState.clearAllPanels()
    
    // Apply the configuration recursively
    applyConfigNode(configuration.layout, splitViewState, "main")
}

private suspend fun applyConfigNode(
    node: SplitConfig,
    splitViewState: SplitViewState,
    currentPanelId: String
) {
    when (node) {
        is SplitConfig.SinglePanel -> {
            // Add tabs to current panel
            val tabsComponent = splitViewState.getPanelTabsComponent(currentPanelId)
            node.panel.tabs.forEach { tabConfig ->
                val tab = createTabFromConfig(tabConfig)
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
                        val tab = createTabFromConfig(tabConfig)
                        tabsComponent?.addTab(tab)
                    }
                }
                else -> {
                    // Recursively apply left config
                    applyConfigNode(leftNode, splitViewState, currentPanelId)
                }
            }
            
            // Then create vertical split for right side
            val firstRightTab = getFirstTab(node.right)
            if (firstRightTab != null) {
                val rightPanelId = splitViewState.splitPanel(
                    panelId = currentPanelId,
                    orientation = SplitOrientation.VERTICAL,
                    tabToMove = createTabFromConfig(firstRightTab)
                )
                
                // Add remaining tabs or process splits for right side
                when (val rightNode = node.right) {
                    is SplitConfig.SinglePanel -> {
                        // Add remaining tabs
                        val tabsComponent = splitViewState.getPanelTabsComponent(rightPanelId)
                        rightNode.panel.tabs.drop(1).forEach { tabConfig ->
                            val tab = createTabFromConfig(tabConfig)
                            tabsComponent?.addTab(tab)
                        }
                    }
                    else -> {
                        // Recursively apply right config
                        applyConfigNode(rightNode, splitViewState, rightPanelId)
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
                        val tab = createTabFromConfig(tabConfig)
                        tabsComponent?.addTab(tab)
                    }
                }
                else -> {
                    // Recursively apply top config
                    applyConfigNode(topNode, splitViewState, currentPanelId)
                }
            }
            
            // Then create horizontal split for bottom side
            val firstBottomTab = getFirstTab(node.bottom)
            if (firstBottomTab != null) {
                val bottomPanelId = splitViewState.splitPanel(
                    panelId = currentPanelId,
                    orientation = SplitOrientation.HORIZONTAL,
                    tabToMove = createTabFromConfig(firstBottomTab)
                )
                
                // Add remaining tabs or process splits for bottom side
                when (val bottomNode = node.bottom) {
                    is SplitConfig.SinglePanel -> {
                        // Add remaining tabs
                        val tabsComponent = splitViewState.getPanelTabsComponent(bottomPanelId)
                        bottomNode.panel.tabs.drop(1).forEach { tabConfig ->
                            val tab = createTabFromConfig(tabConfig)
                            tabsComponent?.addTab(tab)
                        }
                    }
                    else -> {
                        // Recursively apply bottom config
                        applyConfigNode(bottomNode, splitViewState, bottomPanelId)
                    }
                }
            }
        }
    }
}

private fun getFirstTab(config: SplitConfig): TabConfig? {
    return when (config) {
        is SplitConfig.SinglePanel -> config.panel.tabs.firstOrNull()
        is SplitConfig.VerticalSplit -> getFirstTab(config.left)
        is SplitConfig.HorizontalSplit -> getFirstTab(config.top)
    }
}

private fun createTabFromConfig(tabConfig: TabConfig): TabInfo {
    return when (tabConfig.type) {
        "browser" -> FluckTabInfo(
            id = "browser-${Random.nextLong()}",
            typeId = Fluck.typeId,
            _title = tabConfig.title,
            url = tabConfig.url ?: "about:blank"
        )
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
