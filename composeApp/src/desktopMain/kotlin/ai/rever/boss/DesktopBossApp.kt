package ai.rever.boss

import ai.rever.boss.components.window_panel.SplitViewState
import ai.rever.boss.components.plugin.tab_types.fluck.FluckEngine

/**
 * Desktop-specific implementation for setting up download tab close callback.
 * Called when BossApp initializes on desktop platform.
 */
actual fun setupDownloadTabCloseCallback(splitViewState: SplitViewState) {
    FluckEngine.setCloseMostRecentTabCallback {
        println("BossApp: Received request to close most recent tab")
        // Close most recent tab in all panels
        splitViewState.getAllPanels().forEach { panel ->
            val tabsComp = splitViewState.getPanelTabsComponent(panel.id)
            tabsComp?.closeMostRecentTab()
        }
    }
}
