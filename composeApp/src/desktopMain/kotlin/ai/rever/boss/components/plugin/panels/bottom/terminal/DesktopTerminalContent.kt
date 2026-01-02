package ai.rever.boss.components.plugin.panels.bottom.terminal

import ai.rever.boss.components.events.TerminalLinkEventBus
import ai.rever.boss.components.events.URLEventBus
import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.hyperlinks.HyperlinkInfo
import ai.rever.bossterm.compose.hyperlinks.HyperlinkType
import ai.rever.bossterm.compose.EmbeddableTerminalState
import ai.rever.bossterm.compose.TabbedTerminal
import ai.rever.bossterm.compose.TabbedTerminalState
import ai.rever.boss.run.RunnerTerminalService
import ai.rever.bossterm.compose.rememberEmbeddableTerminalState
import ai.rever.bossterm.compose.settings.SettingsManager
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.settings.TerminalSettingsOverride
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import ai.rever.boss.run.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** ID for the sidebar terminal panel's persistent state */
const val SIDEBAR_TERMINAL_ID = "sidebar-terminal"

/**
 * Holds pending command info for sidebar terminal.
 * Set this BEFORE opening the panel so TabbedTerminal can use it on first render.
 */
data class PendingRunnerCommand(
    val command: String,
    val workingDirectory: String?,
    val configId: String? = null
)

/** Pending command to run when sidebar terminal first renders (thread-safe) */
private val pendingRunnerCommand = AtomicReference<PendingRunnerCommand?>(null)

/**
 * Set a pending command to run when the sidebar terminal panel opens.
 * This should be called BEFORE opening the panel.
 * Thread-safe via AtomicReference.
 */
fun setPendingSidebarCommand(command: String, workingDirectory: String?, configId: String? = null) {
    pendingRunnerCommand.set(PendingRunnerCommand(command, workingDirectory, configId))
}

/**
 * Get and clear the pending command (called by TabbedTerminalContent on render).
 * Thread-safe via AtomicReference.getAndSet().
 */
fun consumePendingSidebarCommand(): PendingRunnerCommand? {
    return pendingRunnerCommand.getAndSet(null)
}

/**
 * Desktop implementation of TabbedTerminalContent using BossTerm's TabbedTerminal.
 *
 * Provides full-featured terminal with:
 * - Multiple tabs within the panel
 * - Split panes (horizontal/vertical)
 * - Tab management keyboard shortcuts
 * - Settings integration (opens BOSS Settings)
 *
 * Uses persistent state so runner commands can create tabs in this terminal.
 */
@Composable
actual fun TabbedTerminalContent(
    workingDirectory: String?,
    onExit: () -> Unit,
    onShowSettings: () -> Unit
) {
    val settings by SettingsManager.instance.settings.collectAsState()
    val scope = rememberCoroutineScope()

    // Observe reset generation to force recomposition when terminals are reset
    val resetGeneration by TabbedTerminalStateRegistry.resetGeneration.collectAsState()

    // Check if this is a fresh terminal (not in registry yet)
    val isNew = !TabbedTerminalStateRegistry.contains(SIDEBAR_TERMINAL_ID)

    // Use persistent state so runner can send commands to this terminal
    // Key on resetGeneration to force re-creation after reset
    val state = remember(resetGeneration) { TabbedTerminalStateRegistry.getOrCreate(SIDEBAR_TERMINAL_ID) }

    // Check for pending runner command (set before panel opened)
    val pendingCommand = remember { if (isNew) consumePendingSidebarCommand() else null }

    // Override settings to always show tab bar for runner integration
    val sidebarSettings = remember {
        TerminalSettingsOverride(alwaysShowTabBar = true)
    }

    // Register the first tab's ID using session listener (callback-based, no polling)
    androidx.compose.runtime.DisposableEffect(pendingCommand?.configId) {
        if (pendingCommand?.configId != null) {
            val configId = pendingCommand.configId
            val listener = object : ai.rever.bossterm.compose.tabs.TerminalSessionListener {
                override fun onSessionCreated(session: ai.rever.bossterm.compose.TerminalSession) {
                    // Register the first session's ID for this config
                    TabbedTerminalStateRegistry.registerSidebarTabId(configId, session.id)
                    // Remove listener after first session (we only need the initial tab)
                    state.removeSessionListener(this)
                }
            }
            state.addSessionListener(listener)

            onDispose {
                state.removeSessionListener(listener)
            }
        } else {
            onDispose { }
        }
    }

    // Use key() to force complete recreation of terminal when reset happens
    key(resetGeneration) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = settings.defaultBackgroundColor
        ) {
            TabbedTerminal(
                state = state,
                // Pass pending command for first render (runs in default tab)
                initialCommand = pendingCommand?.command,
                workingDirectory = pendingCommand?.workingDirectory ?: workingDirectory,
                settingsOverride = sidebarSettings,
                onExit = {
                    TabbedTerminalStateRegistry.remove(SIDEBAR_TERMINAL_ID)
                    // Clean up all runner configs when sidebar terminal exits
                    RunnerTerminalService.removeTerminal(SIDEBAR_TERMINAL_ID)
                    onExit()
                },
                onTabClose = { tabId ->
                    // When a tab is closed in sidebar terminal, check if it's a runner config
                    // and clean up the runner state for just that config
                    val configId = TabbedTerminalStateRegistry.getConfigIdForSidebarTab(tabId)
                    if (configId != null) {
                        RunnerTerminalService.removeConfig(configId)
                        TabbedTerminalStateRegistry.removeSidebarConfigTracking(configId)
                    }
                },
                onShowSettings = onShowSettings,
                onLinkClick = { info -> handleTerminalLinkClick(info, scope, SIDEBAR_TERMINAL_ID) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * TabbedTerminal with persistent state across composition changes.
 * Uses TabbedTerminalStateRegistry to preserve terminal sessions when switching tabs.
 *
 * @param terminalId Unique ID for this terminal instance, used as key in state registry
 * @param initialCommand Optional command to run after terminal starts (only for new terminals)
 * @param workingDirectory Optional working directory for the terminal (defaults to home directory)
 * @param onExit Called when the last terminal tab is closed
 * @param onShowSettings Called when user requests settings
 * @param onTitleChange Called when terminal window title changes via escape sequences (OSC 0/1/2)
 */
@Composable
actual fun PersistentTabbedTerminalContent(
    terminalId: String,
    initialCommand: String?,
    workingDirectory: String?,
    onExit: () -> Unit,
    onShowSettings: () -> Unit,
    onTitleChange: ((String) -> Unit)?
) {
    // Observe reset generation to force recomposition when terminals are reset
    val resetGeneration by TabbedTerminalStateRegistry.resetGeneration.collectAsState()

    // Check if this is a new terminal (not already in registry)
    val isNew = !TabbedTerminalStateRegistry.contains(terminalId)
    // Key on both terminalId and resetGeneration to force re-creation after reset
    val state = remember(terminalId, resetGeneration) { TabbedTerminalStateRegistry.getOrCreate(terminalId) }
    val settings by SettingsManager.instance.settings.collectAsState()
    val scope = rememberCoroutineScope()

    DisposableEffect(terminalId) {
        onDispose {
            // Don't remove from registry here - cleanup happens when tab is closed
        }
    }

    // Use key() to force complete recreation of terminal when reset happens
    key(resetGeneration) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = settings.defaultBackgroundColor
        ) {
            TabbedTerminal(
                state = state,
                // Only send initial command and working directory for newly created terminals
                initialCommand = if (isNew) initialCommand else null,
                workingDirectory = if (isNew) workingDirectory else null,
                onExit = {
                    TabbedTerminalStateRegistry.remove(terminalId)
                    onExit()
                },
                onShowSettings = onShowSettings,
                onWindowTitleChange = { title -> onTitleChange?.invoke(title) },
                onLinkClick = { info -> handleTerminalLinkClick(info, scope, terminalId) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Registry to store TabbedTerminal states by ID, allowing them to persist across
 * composition tree changes (e.g., when switching tabs).
 */
object TabbedTerminalStateRegistry {
    private val states = mutableMapOf<String, TabbedTerminalState>()

    /**
     * Generation counter that increments on each reset operation.
     *
     * This StateFlow triggers automatic UI recomposition when terminals are reset:
     * 1. Composables observe this via `collectAsState()`
     * 2. When reset increments the counter, composables recompose
     * 3. `remember(resetGeneration)` blocks re-execute to fetch fresh state
     * 4. `key(resetGeneration)` blocks force complete recreation of terminal UI
     *
     * This pattern ensures terminals refresh in place without requiring
     * manual navigation away/back or close/reopen actions.
     */
    private val _resetGeneration = MutableStateFlow(0)
    val resetGeneration: StateFlow<Int> = _resetGeneration.asStateFlow()

    fun getOrCreate(terminalId: String): TabbedTerminalState {
        return states.getOrPut(terminalId) { TabbedTerminalState() }
    }

    fun get(terminalId: String): TabbedTerminalState? = states[terminalId]

    fun remove(terminalId: String) {
        states.remove(terminalId)?.dispose()
    }

    fun contains(terminalId: String): Boolean = terminalId in states

    /**
     * Send input bytes to a terminal by ID.
     * Used for sending control characters like Ctrl+C (0x03).
     *
     * @param terminalId The terminal ID to send input to
     * @param bytes The bytes to send
     * @return true if the terminal exists and input was sent, false otherwise
     */
    fun sendInput(terminalId: String, bytes: ByteArray): Boolean {
        val state = states[terminalId] ?: return false
        state.sendInput(bytes)
        return true
    }

    /**
     * Send Ctrl+C (interrupt signal) to a terminal.
     *
     * @param terminalId The terminal ID to send Ctrl+C to
     * @return true if the terminal exists and Ctrl+C was sent, false otherwise
     */
    fun sendCtrlC(terminalId: String): Boolean {
        return sendInput(terminalId, byteArrayOf(0x03))
    }

    /**
     * Close the active tab in a terminal.
     * This will terminate the running process and close the tab.
     *
     * @param terminalId The terminal ID to close the active tab in
     * @return true if the terminal exists and tab was closed, false otherwise
     */
    fun closeActiveTab(terminalId: String): Boolean {
        val state = states[terminalId] ?: return false
        state.closeActiveTab()
        return true
    }

    /**
     * Run a command in a terminal by sending it as input.
     * This sends the command text followed by Enter to execute it.
     *
     * @param terminalId The terminal ID to send the command to
     * @param command The command to run
     * @return true if the terminal exists and command was sent, false otherwise
     */
    fun runCommand(terminalId: String, command: String): Boolean {
        val state = states[terminalId] ?: return false
        // Send the command followed by Enter (newline)
        val commandWithEnter = "$command\n"
        state.sendInput(commandWithEnter.toByteArray(Charsets.UTF_8))
        return true
    }

    // Track configId → stable tabId for sidebar terminal tabs
    // Uses BossTerm 1.0.61+ stable tab ID API
    private val sidebarConfigToTabId = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Run a command in the sidebar terminal.
     * - First run (panel not open): Sets pending command, panel will use it on render
     * - First run (panel already open): Creates a new tab with configId as stable tabId
     * - Re-run: Sends Ctrl+C to the config's tab by stable tabId, waits, then sends new command
     *
     * Uses BossTerm 1.0.61+ stable tab ID API for reliable tab targeting.
     *
     * @param command The command to run
     * @param workingDirectory Optional working directory for the terminal
     * @param configId The configuration ID (used as stable tabId for the tab)
     * @param isRerun If true, sends Ctrl+C first to stop any running process in the config's tab
     * @return true if command was sent successfully
     */
    fun newSidebarTab(
        command: String,
        workingDirectory: String? = null,
        configId: String? = null,
        isRerun: Boolean = false
    ): Boolean {
        val terminalExists = contains(SIDEBAR_TERMINAL_ID)
        println("[SidebarTerminal] newSidebarTab: isRerun=$isRerun, terminalExists=$terminalExists, configId=$configId, command=$command")

        if (isRerun && configId != null) {
            // Re-run: send Ctrl+C to the config's tab by stable tabId, wait, then send new command
            val state = get(SIDEBAR_TERMINAL_ID) ?: return false
            val tabId = sidebarConfigToTabId[configId]

            if (tabId != null) {
                println("[SidebarTerminal] Re-run: switching to tab '$tabId', sending Ctrl+C, then command after delay")
                state.switchToTab(tabId) // Switch to the tab first so user sees it
                state.sendCtrlC(tabId) // Ctrl+C to specific tab by stable ID

                val delayMs = ai.rever.boss.run.RunnerSettingsManager.currentSettings.value.rerunDelayMs
                val fullCommand = ShellUtils.buildCommandWithWorkingDirectory(command, workingDirectory)
                val capturedTabId = tabId // Capture for lambda
                CoroutineScope(Dispatchers.Default).launch {
                    delay(delayMs)
                    // Check if terminal still exists before sending (prevents sending to disposed terminal)
                    if (contains(SIDEBAR_TERMINAL_ID)) {
                        get(SIDEBAR_TERMINAL_ID)?.sendInput("clear && $fullCommand\n".toByteArray(Charsets.UTF_8), capturedTabId)
                    }
                }
            } else {
                // Fallback: no tabId tracked, send to active tab
                println("[SidebarTerminal] Re-run: no tabId for config, sending to active tab")
                state.sendInput(byteArrayOf(0x03))
                val delayMs = ai.rever.boss.run.RunnerSettingsManager.currentSettings.value.rerunDelayMs
                val fullCommand = ShellUtils.buildCommandWithWorkingDirectory(command, workingDirectory)
                CoroutineScope(Dispatchers.Default).launch {
                    delay(delayMs)
                    // Check if terminal still exists before sending (prevents sending to disposed terminal)
                    if (contains(SIDEBAR_TERMINAL_ID)) {
                        get(SIDEBAR_TERMINAL_ID)?.sendInput("clear && $fullCommand\n".toByteArray(Charsets.UTF_8))
                    }
                }
            }
        } else if (!terminalExists) {
            // First run, panel not open yet: set pending command for TabbedTerminalContent to use
            println("[SidebarTerminal] First run (panel opening): setting pending command with configId=$configId")
            setPendingSidebarCommand(command, workingDirectory, configId)
        } else {
            // Panel already open, new config: create a new tab with configId as stable tabId
            val state = get(SIDEBAR_TERMINAL_ID) ?: return false
            println("[SidebarTerminal] New config (panel open): creating new tab with tabId=$configId")
            state.createTab(workingDir = workingDirectory, initialCommand = command, tabId = configId)
            // Record the mapping (configId is the tabId)
            if (configId != null) {
                sidebarConfigToTabId[configId] = configId
                println("[SidebarTerminal] Recorded tabId '$configId' for config")
            }
        }
        return true
    }

    /**
     * Register the tabId for a config after the first tab is created.
     * Called from TabbedTerminalContent after the initial tab renders.
     */
    fun registerSidebarTabId(configId: String, tabId: String) {
        sidebarConfigToTabId[configId] = tabId
        println("[SidebarTerminal] Registered tabId '$tabId' for config '$configId'")
    }

    /**
     * Remove tab tracking for a config when it's stopped/removed.
     */
    fun removeSidebarConfigTracking(configId: String) {
        sidebarConfigToTabId.remove(configId)
    }

    /**
     * Get the config ID for a sidebar tab ID (reverse lookup).
     * Returns the configId if found, null otherwise.
     */
    fun getConfigIdForSidebarTab(tabId: String): String? {
        return sidebarConfigToTabId.entries.find { it.value == tabId }?.key
    }

    /**
     * Clear all sidebar config tracking (e.g., when sidebar terminal is closed).
     */
    fun clearSidebarConfigTracking() {
        sidebarConfigToTabId.clear()
    }

    /**
     * Reset all terminals by disposing all states and clearing tracking.
     * Used by "Reset Terminal" in Help menu.
     *
     * @return Number of terminal states that were disposed
     */
    fun resetAllTerminals(): Int {
        val count = states.size
        // Dispose all terminal states
        states.values.forEach { state ->
            try {
                state.dispose()
            } catch (e: Exception) {
                println("Error disposing terminal state: ${e.message}")
            }
        }
        states.clear()
        // Clear sidebar config tracking
        sidebarConfigToTabId.clear()
        // Increment generation to trigger UI recomposition
        _resetGeneration.value++
        println("[TabbedTerminalStateRegistry] Reset complete: disposed $count terminal states, generation=${_resetGeneration.value}")
        return count
    }
}

/**
 * Registry to store terminal states by ID, allowing them to persist across
 * composition tree changes (e.g., when splitting panels).
 */
private object TerminalStateRegistry {
    private val states = mutableMapOf<String, EmbeddableTerminalState>()

    /** Generation counter for this registry (incremented on reset but not externally observed) */
    private val _resetGeneration = MutableStateFlow(0)
    val resetGeneration: StateFlow<Int> = _resetGeneration.asStateFlow()

    fun getOrCreate(terminalId: String): EmbeddableTerminalState {
        return states.getOrPut(terminalId) { EmbeddableTerminalState() }
    }

    fun remove(terminalId: String) {
        states.remove(terminalId)?.dispose()
    }

    fun contains(terminalId: String): Boolean = terminalId in states

    fun resetAll(): Int {
        val count = states.size
        states.values.forEach { state ->
            try {
                state.dispose()
            } catch (e: Exception) {
                println("Error disposing embeddable terminal state: ${e.message}")
            }
        }
        states.clear()
        // Increment generation to trigger UI recomposition
        _resetGeneration.value++
        println("[TerminalStateRegistry] Reset complete: disposed $count terminal states, generation=${_resetGeneration.value}")
        return count
    }
}

/**
 * Reset all terminal states across all registries.
 * Called by "Reset Terminal" in Help menu.
 *
 * @return Total number of terminal states disposed
 */
fun resetAllTerminalStates(): Int {
    val tabbedCount = TabbedTerminalStateRegistry.resetAllTerminals()
    val embeddableCount = TerminalStateRegistry.resetAll()
    val total = tabbedCount + embeddableCount
    println("[Terminal] Total reset: disposed $total terminal states (tabbed=$tabbedCount, embeddable=$embeddableCount)")
    return total
}

/**
 * Desktop implementation of resetTerminals.
 * Called when user triggers reset from panel's more menu.
 */
actual fun resetTerminals() {
    resetAllTerminalStates()
}

/**
 * Handles terminal link clicks by emitting HTTP/HTTPS and FILE links to TerminalLinkEventBus
 * for BossApp to handle with user preference (dialog or auto-open). Other link types
 * (FOLDER, EMAIL, etc.) are delegated to BossTerm's default behavior.
 *
 * Issue #346: Terminal link click prompt with remember preference
 *
 * Note: This launches coroutines without structured concurrency. If the terminal is closed
 * immediately after a link click, the event might emit after cleanup. This is low-risk
 * because the event bus is fire-and-forget, and BossApp handles stale events gracefully
 * by verifying panel existence before operations.
 *
 * @param info HyperlinkInfo containing URL, type, and metadata
 * @param scope CoroutineScope to launch async operations
 * @param terminalId Optional terminal tab ID (for detecting source panel when opening in splits)
 * @return true if handled by BOSS, false to use BossTerm's default behavior
 */
private fun handleTerminalLinkClick(info: HyperlinkInfo, scope: CoroutineScope, terminalId: String? = null): Boolean {
    return when (info.type) {
        HyperlinkType.HTTP -> {
            // Emit HTTP/HTTPS links to event bus for BossApp to handle
            // BossApp will show dialog or auto-open based on user preference
            scope.launch {
                TerminalLinkEventBus.emitLinkClick(info.url, terminalId)
            }
            true // Handled - BOSS manages HTTP links
        }
        HyperlinkType.FILE -> {
            // Route file links through TerminalLinkEventBus for consistent dialog/settings behavior
            // (same "where to open" dialog as HTTP links)
            // BossApp will detect file: URL and open in editor instead of browser
            scope.launch {
                TerminalLinkEventBus.emitLinkClick(info.url, terminalId)
            }
            true // Handled - BOSS opens files in editor with same dialog behavior
        }
        else -> {
            // Let BossTerm handle FOLDER, EMAIL, FTP, and other link types
            // with its default behavior (open in Finder/browser)
            false
        }
    }
}

/**
 * Desktop implementation of TerminalContent using BossTerm's EmbeddableTerminal.
 */
@Composable
actual fun TerminalContent(
    terminalId: String?,
    initialCommand: String?,
    workingDirectory: String?,
    onExit: () -> Unit
) {
    // Observe reset generation to force recomposition when terminals are reset
    // Uses TabbedTerminalStateRegistry's generation since both registries are reset together
    val resetGeneration by TabbedTerminalStateRegistry.resetGeneration.collectAsState()

    // If terminalId is provided, use the registry for persistent state
    // Otherwise, use compose's remember (ephemeral state)
    val terminalState = if (terminalId != null) {
        val isNew = !TerminalStateRegistry.contains(terminalId)
        // Key on both terminalId and resetGeneration to force re-creation after reset
        val state = remember(terminalId, resetGeneration) { TerminalStateRegistry.getOrCreate(terminalId) }

        // Clean up when this composable is permanently removed
        DisposableEffect(terminalId) {
            onDispose {
                // Note: We don't remove from registry here because the composable
                // might just be moving in the tree (e.g., during split).
                // Cleanup happens when the tab is actually closed.
            }
        }

        // Only send initial command and working directory for newly created terminals
        isNew to state
    } else {
        // Fallback to ephemeral state for sidebar terminals without ID
        true to rememberEmbeddableTerminalState()
    }

    val (isNew, state) = terminalState
    val settings by SettingsManager.instance.settings.collectAsState()
    val scope = rememberCoroutineScope()

    // Use key() to force complete recreation of terminal when reset happens
    key(resetGeneration) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = settings.defaultBackgroundColor
        ) {
            EmbeddableTerminal(
                state = state,
                // Only send initial command and working directory for newly created terminals
                initialCommand = if (isNew) initialCommand else null,
                workingDirectory = if (isNew) workingDirectory else null,
                onExit = { _ ->
                    // Clean up registry when terminal exits
                    terminalId?.let { TerminalStateRegistry.remove(it) }
                    onExit()
                },
                onLinkClick = { info -> handleTerminalLinkClick(info, scope, terminalId) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
