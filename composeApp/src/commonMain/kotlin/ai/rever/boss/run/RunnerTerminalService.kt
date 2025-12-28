package ai.rever.boss.run

import kotlinx.coroutines.flow.StateFlow

/**
 * Expect declaration for RunnerTerminalService.
 * Manages runner terminals with configuration tracking.
 *
 * Features:
 * - One terminal tab per run configuration (reused on re-run)
 * - Track running state per configuration
 * - Support for stop (Ctrl+C) and re-run operations
 *
 * Issue #347: Runner should open in terminal sidebar panel with run/stop state management
 */
expect object RunnerTerminalService {
    /**
     * Map of configId to terminalTabId for tracking which terminal belongs to which config.
     */
    val configToTerminal: StateFlow<Map<String, String>>

    /**
     * Set of currently running configuration IDs.
     */
    val runningConfigs: StateFlow<Set<String>>

    /**
     * Check if a specific configuration is currently running.
     */
    fun isConfigRunning(configId: String): Boolean

    /**
     * Open or reuse a runner terminal for the given configuration.
     * If a terminal already exists for this config, it will be reused.
     *
     * @param config The run configuration to execute
     * @param onTerminalCreated Callback when terminal tab is created, receives terminal ID
     * @return The terminal tab ID
     */
    suspend fun openRunnerTerminal(
        config: RunConfiguration,
        onTerminalCreated: (String) -> Unit = {}
    ): String

    /**
     * Stop the runner for a configuration by sending Ctrl+C.
     *
     * @param configId The configuration ID to stop
     * @return True if stop signal was sent, false if config not running
     */
    suspend fun stopRunner(configId: String): Boolean

    /**
     * Re-run a configuration: stop current process (if running) and run again.
     *
     * @param config The run configuration to re-run
     * @param onTerminalCreated Callback when terminal tab is created
     * @return The terminal tab ID
     */
    suspend fun rerunRunner(
        config: RunConfiguration,
        onTerminalCreated: (String) -> Unit = {}
    ): String

    /**
     * Mark a runner terminal as stopped (process exited).
     * Called when the terminal process exits.
     *
     * @param terminalId The terminal tab ID
     */
    fun markTerminalStopped(terminalId: String)

    /**
     * Remove tracking for a terminal tab (when tab is closed).
     *
     * @param terminalId The terminal tab ID
     */
    fun removeTerminal(terminalId: String)

    /**
     * Get the configuration ID associated with a terminal tab.
     *
     * @param terminalId The terminal tab ID
     * @return The configuration ID, or null if not a runner terminal
     */
    fun getConfigForTerminal(terminalId: String): String?

    /**
     * Open a runner command in the sidebar terminal panel.
     * Creates a new tab in the sidebar terminal with the given command.
     * Also updates the configId → terminalId mapping to use SIDEBAR_TERMINAL_ID.
     *
     * @param configId The configuration ID for tracking
     * @param command The command to run
     * @param workingDirectory Optional working directory
     * @param tabTitle The title for the terminal tab
     * @param isRerun If true, sends Ctrl+C first to stop any running process
     * @return True if the sidebar terminal exists and tab was created
     */
    fun openInSidebarTerminal(
        configId: String,
        command: String,
        workingDirectory: String?,
        tabTitle: String,
        isRerun: Boolean = false
    ): Boolean
}
