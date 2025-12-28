package ai.rever.boss.run

import ai.rever.boss.components.events.RunnerTerminalEventBus
import ai.rever.boss.components.plugin.panels.bottom.terminal.SIDEBAR_TERMINAL_ID
import ai.rever.boss.components.plugin.panels.bottom.terminal.TabbedTerminalStateRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Mutex for atomic state updates across configToTerminal, terminalToConfigs, and runningConfigs
    private val stateMutex = Mutex()

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
        // Build the command outside lock (no state access needed)
        val command = buildFullCommand(config)

        // Atomic state update under mutex
        val (terminalId, isRerun) = stateMutex.withLock {
            val existingTerminalId = _configToTerminal.value[config.id]
            val newTerminalId = existingTerminalId ?: "runner-${config.id}-${System.currentTimeMillis()}"

            // Update all state atomically
            _configToTerminal.update { it + (config.id to newTerminalId) }
            addConfigToTerminal(newTerminalId, config.id)
            _runningConfigs.update { it + config.id }

            newTerminalId to (existingTerminalId != null)
        }

        // Emit event outside lock (avoid holding lock during I/O)
        println("[Runner] Opening terminal for config: ${config.name}")
        println("[Runner] Command: $command")
        RunnerTerminalEventBus.openRunnerTerminal(
            terminalId = terminalId,
            command = command,
            configId = config.id,
            configName = config.name,
            workingDirectory = config.workingDirectory.ifBlank { null },
            isRerun = isRerun
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
        // Get terminal ID and validate under mutex
        val terminalId = stateMutex.withLock {
            if (!isConfigRunning(configId)) {
                println("[Runner] Config $configId is not running")
                return false
            }

            val id = _configToTerminal.value[configId]
            if (id == null) {
                println("[Runner] No terminal found for config $configId")
                return false
            }

            // Clear tracking atomically
            _configToTerminal.update { it - configId }
            removeConfigFromTerminal(id, configId)
            _runningConfigs.update { it - configId }

            id
        }

        // Perform I/O operations outside lock
        val closed = TabbedTerminalStateRegistry.closeActiveTab(terminalId)
        if (closed) {
            println("[Runner] Closed terminal tab: $terminalId (config: $configId)")
        } else {
            println("[Runner] Failed to close tab - terminal not found: $terminalId")
        }

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

        // Build command outside lock
        val command = buildFullCommand(config)

        // Atomic state update under mutex
        val (terminalId, existingTerminalId) = stateMutex.withLock {
            val existingId = _configToTerminal.value[config.id]

            // Stop existing process and close terminal (only for main panel mode)
            if (existingId != null && !usesSidebar) {
                removeConfigFromTerminal(existingId, config.id)
            }

            // Create new terminal with fresh ID
            val newTerminalId = "runner-${config.id}-${System.currentTimeMillis()}"

            // Update all state atomically
            _configToTerminal.update { it + (config.id to newTerminalId) }
            addConfigToTerminal(newTerminalId, config.id)
            _runningConfigs.update { it + config.id }

            newTerminalId to existingId
        }

        // Perform I/O operations outside lock
        if (existingTerminalId != null && !usesSidebar) {
            // Send Ctrl+C to stop the running process
            val sent = TabbedTerminalStateRegistry.sendCtrlC(existingTerminalId)
            if (sent) {
                println("[Runner] Sent Ctrl+C to stop existing process")
            }
            // Close the terminal tab
            RunnerTerminalEventBus.closeRunnerTerminal(existingTerminalId)
        }

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
     * Note: This is called from non-suspend context, uses synchronized block.
     */
    actual fun markTerminalStopped(terminalId: String) {
        // Use synchronized for non-suspend context
        synchronized(this) {
            val configIds = terminalToConfigs[terminalId]?.toSet() ?: emptySet()
            if (configIds.isNotEmpty()) {
                _runningConfigs.update { it - configIds }
                println("[Runner] Terminal stopped: $terminalId (configs: $configIds)")
            }
        }
    }

    /**
     * Remove tracking for a terminal tab (when tab is closed).
     * For terminals with multiple configs (sidebar), removes all.
     * Note: This is called from non-suspend context, uses synchronized block.
     */
    actual fun removeTerminal(terminalId: String) {
        // Use synchronized for non-suspend context
        synchronized(this) {
            val configIds = terminalToConfigs.remove(terminalId)?.toSet() ?: emptySet()
            if (configIds.isNotEmpty()) {
                _configToTerminal.update { current ->
                    current.filterKeys { it !in configIds }
                }
                _runningConfigs.update { it - configIds }
                println("[Runner] Terminal removed: $terminalId (configs: $configIds)")
            }
        }

        // Clear sidebar tab tracking outside lock (I/O operation)
        if (terminalId == SIDEBAR_TERMINAL_ID) {
            TabbedTerminalStateRegistry.clearSidebarConfigTracking()
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
     * Note: This is called from non-suspend context, uses synchronized block.
     */
    actual fun openInSidebarTerminal(
        configId: String,
        command: String,
        workingDirectory: String?,
        tabTitle: String,
        isRerun: Boolean
    ): Boolean {
        // Perform terminal operation first (I/O outside lock)
        val success = TabbedTerminalStateRegistry.newSidebarTab(
            command = command,
            workingDirectory = workingDirectory,
            configId = configId,
            isRerun = isRerun
        )

        if (success) {
            // Update state atomically
            synchronized(this) {
                _configToTerminal.update { it + (configId to SIDEBAR_TERMINAL_ID) }
                addConfigToTerminal(SIDEBAR_TERMINAL_ID, configId)
                _runningConfigs.update { it + configId }
            }
            println("[Runner] Opened command in sidebar terminal: $tabTitle (mapped to $SIDEBAR_TERMINAL_ID, isRerun=$isRerun)")
        } else {
            println("[Runner] Failed to open in sidebar terminal - panel may not be open")
        }
        return success
    }

    /**
     * Build the full command including cd to working directory.
     * Working directory is quoted and escaped to handle paths with spaces,
     * quotes, and other special characters safely.
     */
    private fun buildFullCommand(config: RunConfiguration): String {
        return if (config.workingDirectory.isNotBlank()) {
            // Escape double quotes in the path to prevent command injection
            val escapedDir = config.workingDirectory.replace("\"", "\\\"")
            "cd \"$escapedDir\" && ${config.command}"
        } else {
            config.command
        }
    }
}
