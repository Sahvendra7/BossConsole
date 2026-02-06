package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable

/**
 * Provider interface for terminal content - platform-specific implementation.
 * This allows the terminal panel to be loaded as a dynamic plugin.
 */
interface TerminalContentProvider {
    /**
     * Display tabbed terminal content.
     */
    @Composable
    fun TabbedTerminalContent(
        workingDirectory: String? = null,
        onExit: () -> Unit = {},
        onShowSettings: () -> Unit = {}
    )

    /**
     * Reset all terminal states.
     */
    fun resetTerminals()
}

/**
 * Provider interface for tab-specific terminal rendering.
 * This allows terminal tabs to be loaded as a dynamic plugin.
 *
 * Unlike TerminalContentProvider which is for the sidebar terminal panel,
 * this interface provides persistent terminal content for individual tab instances.
 */
interface TerminalTabContentProvider {
    /**
     * Display persistent tabbed terminal content for a specific terminal tab.
     *
     * @param terminalId Unique ID for this terminal instance, used as key in state registry
     * @param initialCommand Optional command to run after terminal starts (only for new terminals)
     * @param workingDirectory Optional working directory for the terminal
     * @param onExit Called when the last terminal tab is closed
     * @param onTitleChange Called when terminal window title changes via escape sequences
     */
    @Composable
    fun PersistentTabbedTerminalContent(
        terminalId: String,
        initialCommand: String? = null,
        workingDirectory: String? = null,
        onExit: () -> Unit = {},
        onTitleChange: ((String) -> Unit)? = null
    )

    /**
     * Check if a terminal state exists for the given window and terminal ID.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @return true if the terminal state exists
     */
    fun hasTerminalState(windowId: String, terminalId: String): Boolean

    /**
     * Remove a terminal state for the given window and terminal ID.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     */
    fun removeTerminalState(windowId: String, terminalId: String)
}

/**
 * Provider interface for panel events.
 * Allows plugins to trigger panel operations.
 */
interface PanelEventProvider {
    /**
     * Close the panel.
     */
    suspend fun closePanel(panelId: PanelId, windowId: String)
}

/**
 * Provider interface for opening settings.
 * Allows plugins to open the settings dialog.
 */
interface SettingsProvider {
    /**
     * Open settings at specific section.
     */
    fun openSettings(windowId: String, section: String)
}

/**
 * Provider interface for the Boss Console dashboard content.
 * Allows browser plugins to display the host's dashboard for about:blank pages.
 */
interface DashboardContentProvider {
    /**
     * Display the Boss Console dashboard.
     *
     * @param onNavigate Callback when user wants to navigate to a URL (from search or quick links)
     */
    @Composable
    fun DashboardContent(
        onNavigate: (String) -> Unit
    )
}
