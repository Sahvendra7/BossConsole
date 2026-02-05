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
