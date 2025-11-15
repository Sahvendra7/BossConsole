package ai.rever.boss.window

import ai.rever.boss.components.registery.TabInfo

/**
 * Desktop implementation of window operations
 *
 * Provides full multi-window support using WindowManager
 */
actual object WindowOperations {
    /**
     * Open a tab in a new window
     *
     * Note: Tab management moved to BossApp/SplitViewState.
     * Currently creates an empty window. The "Open in New Window" feature
     * needs to be reimplemented using SplitViewState.
     *
     * TODO: Implement tab moving through SplitViewState/BossApp
     *
     * @param tabInfo The tab to open in the new window (currently ignored)
     */
    actual fun openTabInNewWindow(tabInfo: TabInfo) {
        println("Creating new window (tab moving not yet reimplemented)")
        WindowManager.createNewWindow()
        // TODO: Implement proper tab moving through BossApp/SplitViewState
    }

    /**
     * Desktop platforms support multiple windows
     *
     * @return Always returns true for desktop
     */
    actual fun isMultiWindowSupported(): Boolean = true

    /**
     * Close window if it has no tabs
     *
     * Closes the window if all panels are empty
     *
     * @param windowId The window ID to potentially close
     */
    @Deprecated("Tabs are managed by BossApp/SplitViewState, not WindowManager")
    actual fun closeWindowIfEmpty(windowId: String) {
        WindowManager.closeWindowIfEmpty(windowId)
    }

    /**
     * Create a new empty window
     */
    actual fun createNewWindow() {
        WindowManager.createNewWindow()
    }

    /**
     * Force close a window by ID
     *
     * @param windowId The window ID to close
     */
    actual fun closeWindow(windowId: String) {
        WindowManager.closeWindow(windowId)
    }
}
