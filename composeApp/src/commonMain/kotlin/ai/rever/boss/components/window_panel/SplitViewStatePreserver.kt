package ai.rever.boss.components.window_panel

import ai.rever.boss.components.registery.TabComponentWithUI
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent

/**
 * Stores the state of all tabs and their components before clearing
 */
data class TabStateSnapshot(
    val tabInfo: TabInfo,
    val component: TabComponentWithUI?,
    val isActive: Boolean
)

/**
 * Preserves tab component state across layout changes
 */
class SplitViewStatePreserver {
    // Map of tab key to actual component instance
    private val preservedComponents = mutableMapOf<String, TabComponentWithUI>()
    
    /**
     * Take a snapshot of all tabs and their components
     */
    fun snapshotState(splitViewState: SplitViewState): List<TabStateSnapshot> {
        val snapshots = mutableListOf<TabStateSnapshot>()
        
        // Collect all tabs from all panels
        splitViewState.getAllPanels().forEach { panel ->
            val tabsComponent = panel.tabsComponent
            val tabs = tabsComponent.tabsState.value.tabs
            val activeIndex = tabsComponent.tabsState.value.activeIndex
            
            tabs.forEachIndexed { index, tab ->
                // Get the actual component for this tab
                val component = tabsComponent.getTabComponent(tab)
                
                snapshots.add(TabStateSnapshot(
                    tabInfo = tab,
                    component = component,
                    isActive = index == activeIndex
                ))
                
                // Store component for later reuse
                if (component != null) {
                    val key = createTabKey(tab)
                    preservedComponents[key] = component
                }
            }
        }
        
        return snapshots
    }
    
    /**
     * Get a preserved component for a tab
     */
    fun getPreservedComponent(tab: TabInfo): TabComponentWithUI? {
        val key = createTabKey(tab)
        return preservedComponents[key]
    }
    
    /**
     * Clear preserved components that are no longer needed
     */
    fun cleanup(keepKeys: Set<String>) {
        val keysToRemove = preservedComponents.keys - keepKeys
        keysToRemove.forEach { key ->
            preservedComponents.remove(key)
        }
    }
    
    /**
     * Create a unique key for a tab
     */
    private fun createTabKey(tab: TabInfo): String {
        return when (tab) {
            is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo -> "browser:${tab.url}"
            is ai.rever.boss.components.plugin.tab_types.EditorTabInfo -> "editor:${tab.filePath}"
            is ai.rever.boss.components.plugin.tab_types.TerminalTabInfo -> "terminal:${tab.title}:${tab.id}"
            else -> "unknown:${tab.title}:${tab.id}"
        }
    }
}

/**
 * Extension function to get a tab component
 */
fun BossTabsComponent.getTabComponent(tab: TabInfo): TabComponentWithUI? {
    // This would need to be implemented in BossTabsComponent to expose the actual components
    // For now, return null - this needs to be connected to the actual component storage
    return null
}