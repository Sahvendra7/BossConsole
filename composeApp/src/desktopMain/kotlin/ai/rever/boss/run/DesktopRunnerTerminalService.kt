package ai.rever.boss.run

import ai.rever.boss.components.events.RunnerTerminalEventBus
import ai.rever.boss.components.plugin.panels.bottom.terminal.SIDEBAR_TERMINAL_ID
import ai.rever.boss.components.plugin.panels.bottom.terminal.TabbedTerminalStateRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop implementation of RunnerTerminalService.
 * Manages runner terminals with configuration tracking.
 *
 * Features:
 * - Stop closes the terminal tab and clears tracking
 * - Re-run sends Ctrl+C, waits, then runs new command in same tab
 * - Multiple configurations can run in sidebar mode (each tracked separately)
 *
 * Issue #347: Runner should open in terminal sidebar panel with run/stop state management
 */
actual object RunnerTerminalService {

    // Map: configId → terminalTabId
    private val _configToTerminal = MutableStateFlow<Map<String, String>>(emptyMap())
    actual val configToTerminal: StateFlow<Map<String, String>> = _configToTerminal.asStateFlow()

    // Reverse map: terminalTabId → Set<configId> (supports multiple configs per terminal, e.g., sidebar)
    // Thread-safe: uses ConcurrentHashMap with concurrent sets
    private val terminalToConfigs = ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<String, Boolean>>()

    // Set of currently running configuration IDs
    private val _runningConfigs = MutableStateFlow<Set<String>>(emptySet())
    actual val runningConfigs: StateFlow<Set<String>> = _runningConfigs.asStateFlow()

    /**
     * Add a config to a terminal's tracking set.
     */
    private fun addConfigToTerminal(terminalId: String, configId: String) {
        terminalToConfigs.computeIfAbsent(terminalId) {
            ConcurrentHashMap.newKeySet()
        }.add(configId)
    }

    /**
     * Remove a config from a terminal's tracking set.
     */
    private fun removeConfigFromTerminal(terminalId: String, configId: String) {
        terminalToConfigs[terminalId]?.remove(configId)
    }

    /**
     * Check if a specific configuration is currently running.
     */
    actual fun isConfigRunning(configId: String): Boolean {
        return configId in _runningConfigs.value
    }

    /**
     * Open or reuse a runner terminal for the given configuration.
     */
    actual suspend fun openRunnerTerminal(
        config: RunConfiguration,
        onTerminalCreated: (String) -> Unit
    ): String {
        // Check if we already have a terminal for this config
        val existingTerminalId = _configToTerminal.value[config.id]

        // Build the command
        val command = buildFullCommand(config)

        // Generate terminal ID
        val terminalId = existingTerminalId ?: "runner-${config.id}-${System.currentTimeMillis()}"

        // Update mappings
        _configToTerminal.update { it + (config.id to terminalId) }
        addConfigToTerminal(terminalId, config.id)

        // Mark as running
        _runningConfigs.update { it + config.id }

        // Emit event to create/open terminal
        println("[Runner] Opening terminal for config: ${config.name}")
        println("[Runner] Command: $command")
        RunnerTerminalEventBus.openRunnerTerminal(
            terminalId = terminalId,
            command = command,
            configId = config.id,
            configName = config.name,
            workingDirectory = config.workingDirectory.ifBlank { null },
            isRerun = existingTerminalId != null
        )

        onTerminalCreated(terminalId)
        return terminalId
    }

    /**
     * Stop the runner for a configuration by closing its terminal tab.
     *
     * This closes the active tab which terminates the running process.
     */
    actual suspend fun stopRunner(configId: String): Boolean {
        if (!isConfigRunning(configId)) {
            println("[Runner] Config $configId is not running")
            return false
        }

        val terminalId = _configToTerminal.value[configId]
        if (terminalId == null) {
            println("[Runner] No terminal found for config $configId")
            return false
        }

        // Close the active tab in the terminal (terminates the process)
        val closed = TabbedTerminalStateRegistry.closeActiveTab(terminalId)
        if (closed) {
            println("[Runner] Closed terminal tab: $terminalId (config: $configId)")
        } else {
            println("[Runner] Failed to close tab - terminal not found: $terminalId")
        }

        // Clear tracking
        _configToTerminal.update { it - configId }
        removeConfigFromTerminal(terminalId, configId)
        _runningConfigs.update { it - configId }

        // Clear sidebar tab tracking if this was a sidebar config
        if (terminalId == SIDEBAR_TERMINAL_ID) {
            TabbedTerminalStateRegistry.removeSidebarConfigTracking(configId)
        }

        // Emit stop event for any additional UI handling
        RunnerTerminalEventBus.stopRunnerTerminal(terminalId, configId)

        return closed
    }

    /**
     * Re-run a configuration: stop current process (if running) and run again.
     *
     * For main panel: Sends Ctrl+C to stop the current process, closes the old terminal,
     * and creates a new one with the same command.
     * For sidebar panel: Ctrl+C is handled by openInSidebarTerminal via the isRerun flag.
     */
    actual suspend fun rerunRunner(
        config: RunConfiguration,
        onTerminalCreated: (String) -> Unit
    ): String {
        println("[Runner] Re-running config: ${config.name}")

        // Check if using sidebar mode - Ctrl+C will be handled by openInSidebarTerminal
        val usesSidebar = RunnerSettingsManager.currentSettings.value.terminalTarget == RunnerTerminalTarget.SIDEBAR_PANEL

        // Get the existing terminal ID (if any)
        val existingTerminalId = _configToTerminal.value[config.id]

        // Stop existing process and close terminal (only for main panel mode)
        if (existingTerminalId != null && !usesSidebar) {
            // Send Ctrl+C to stop the running process
            val sent = TabbedTerminalStateRegistry.sendCtrlC(existingTerminalId)
            if (sent) {
                println("[Runner] Sent Ctrl+C to stop existing process")
            }
            // Close the terminal tab
            RunnerTerminalEventBus.closeRunnerTerminal(existingTerminalId)
            // Remove old mapping
            removeConfigFromTerminal(existingTerminalId, config.id)
        }

        // Create new terminal with fresh ID
        val command = buildFullCommand(config)
        val terminalId = "runner-${config.id}-${System.currentTimeMillis()}"

        // Update mappings
        _configToTerminal.update { it + (config.id to terminalId) }
        addConfigToTerminal(terminalId, config.id)

        // Mark as running
        _runningConfigs.update { it + config.id }

        // Emit event to create terminal
        RunnerTerminalEventBus.openRunnerTerminal(
            terminalId = terminalId,
            command = command,
            configId = config.id,
            configName = config.name,
            workingDirectory = config.workingDirectory.ifBlank { null },
            isRerun = true
        )

        onTerminalCreated(terminalId)
        return terminalId
    }

    /**
     * Mark a runner terminal as stopped (process exited).
     * For terminals with multiple configs (sidebar), marks all as stopped.
     */
    actual fun markTerminalStopped(terminalId: String) {
        val configIds = terminalToConfigs[terminalId]?.toSet() ?: emptySet()
        if (configIds.isNotEmpty()) {
            _runningConfigs.update { it - configIds }
            println("[Runner] Terminal stopped: $terminalId (configs: $configIds)")
        }
    }

    /**
     * Remove tracking for a terminal tab (when tab is closed).
     * For terminals with multiple configs (sidebar), removes all.
     */
    actual fun removeTerminal(terminalId: String) {
        val configIds = terminalToConfigs.remove(terminalId)?.toSet() ?: emptySet()
        if (configIds.isNotEmpty()) {
            _configToTerminal.update { current ->
                current.filterKeys { it !in configIds }
            }
            _runningConfigs.update { it - configIds }
            // Clear sidebar tab tracking if this was the sidebar terminal
            if (terminalId == SIDEBAR_TERMINAL_ID) {
                TabbedTerminalStateRegistry.clearSidebarConfigTracking()
            }
            println("[Runner] Terminal removed: $terminalId (configs: $configIds)")
        }
    }

    /**
     * Get a configuration ID associated with a terminal tab.
     * For terminals with multiple configs (sidebar), returns any one of them.
     */
    actual fun getConfigForTerminal(terminalId: String): String? {
        return terminalToConfigs[terminalId]?.firstOrNull()
    }

    /**
     * Open a runner command in the sidebar terminal panel.
     * Creates a new tab in the sidebar terminal with the given command.
     * Updates tracking to map configId → SIDEBAR_TERMINAL_ID so stop works correctly.
     */
    actual fun openInSidebarTerminal(
        configId: String,
        command: String,
        workingDirectory: String?,
        tabTitle: String,
        isRerun: Boolean
    ): Boolean {
        val success = TabbedTerminalStateRegistry.newSidebarTab(
            command = command,
            workingDirectory = workingDirectory,
            configId = configId,
            isRerun = isRerun
        )
        if (success) {
            // Update mapping to point to SIDEBAR_TERMINAL_ID so stop works correctly
            _configToTerminal.update { it + (configId to SIDEBAR_TERMINAL_ID) }
            addConfigToTerminal(SIDEBAR_TERMINAL_ID, configId)
            _runningConfigs.update { it + configId }
            println("[Runner] Opened command in sidebar terminal: $tabTitle (mapped to $SIDEBAR_TERMINAL_ID, isRerun=$isRerun)")
        } else {
            println("[Runner] Failed to open in sidebar terminal - panel may not be open")
        }
        return success
    }

    /**
     * Build the full command including cd to working directory.
     * Working directory is quoted to handle paths with spaces and special characters.
     */
    private fun buildFullCommand(config: RunConfiguration): String {
        return if (config.workingDirectory.isNotBlank()) {
            "cd \"${config.workingDirectory}\" && ${config.command}"
        } else {
            config.command
        }
    }
}
