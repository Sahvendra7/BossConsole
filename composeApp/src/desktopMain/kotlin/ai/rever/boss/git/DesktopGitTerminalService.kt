package ai.rever.boss.git

import ai.rever.boss.components.plugin.panels.bottom.terminal.TabbedTerminalStateRegistry

/**
 * Desktop implementation of GitTerminalService.
 * Uses TabbedTerminalStateRegistry to open git commands in the sidebar terminal.
 */
actual object GitTerminalService {

    /**
     * Open a git command in the sidebar terminal panel.
     * Creates a new tab in the sidebar terminal with the given command.
     *
     * @param windowId The window ID for window-scoped terminal state
     * @param command The git command to run
     * @param workingDirectory The working directory for the terminal
     * @param operationName Human-readable name for the operation (used for tab title)
     * @return True if the sidebar terminal exists and tab was created
     */
    actual fun openInSidebarTerminal(
        windowId: String,
        command: String,
        workingDirectory: String,
        operationName: String
    ): Boolean {
        // Generate a unique tab ID for this git operation
        val tabId = "git-${operationName.lowercase().replace(" ", "-")}-${System.currentTimeMillis()}"

        val success = TabbedTerminalStateRegistry.newSidebarTab(
            windowId = windowId,
            command = command,
            workingDirectory = workingDirectory,
            configId = tabId,
            isRerun = false
        )

        if (success) {
            println("[GitTerminal] Opened command in sidebar terminal: $operationName (window: $windowId)")
        } else {
            println("[GitTerminal] Failed to open in sidebar terminal - panel may not be open (window: $windowId)")
        }

        return success
    }
}
