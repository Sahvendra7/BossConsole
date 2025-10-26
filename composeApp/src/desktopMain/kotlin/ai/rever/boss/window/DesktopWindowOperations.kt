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
     * Creates a new window instance and adds the tab to it.
     *
     * @param tabInfo The tab to open in the new window
     */
    actual fun openTabInNewWindow(tabInfo: TabInfo) {
        println("Opening tab '${tabInfo.title}' in new window")
        WindowManager.createNewWindow(initialTab = tabInfo)
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
