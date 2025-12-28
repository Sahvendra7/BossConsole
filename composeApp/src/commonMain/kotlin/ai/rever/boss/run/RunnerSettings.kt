package ai.rever.boss.run

import kotlinx.serialization.Serializable

/**
 * Target panel for runner terminal output.
 *
 * Issue #347: Configurable terminal target for runner output
 */
@Serializable
enum class RunnerTerminalTarget {
    /**
     * Open runner terminals in the sidebar panel (left.bottom).
     * This is the default behavior, similar to VS Code's integrated terminal.
     */
    SIDEBAR_PANEL,

    /**
     * Open runner terminals in the main panel (center area).
     * This provides more space for output and is similar to IntelliJ's run window.
     */
    MAIN_PANEL
}

/**
 * Settings for the runner system.
 *
 * Issue #347: Runner configuration settings
 */
@Serializable
data class RunnerSettings(
    /**
     * Where to open runner terminal tabs.
     * Default: SIDEBAR_PANEL
     */
    val terminalTarget: RunnerTerminalTarget = RunnerTerminalTarget.SIDEBAR_PANEL,

    /**
     * Whether to automatically focus the terminal when a runner starts.
     * Default: true
     */
    val focusOnRun: Boolean = true,

    /**
     * Whether to clear the terminal before re-running a configuration.
     * Note: Currently not supported - re-run creates a new terminal.
     * Default: true
     */
    val clearOnRerun: Boolean = true,

    /**
     * Whether to show a notification when a runner process exits.
     * Default: false
     */
    val notifyOnExit: Boolean = false,

    /**
     * Delay in milliseconds between sending Ctrl+C and the new command during re-run.
     * This gives the shell time to handle the interrupt and show its prompt.
     * Default: 1000ms. Range: 0-2000ms
     */
    val rerunDelayMs: Long = 1000
)
