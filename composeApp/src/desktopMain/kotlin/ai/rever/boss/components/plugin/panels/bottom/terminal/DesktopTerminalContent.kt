package ai.rever.boss.components.plugin.panels.bottom.terminal

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.components.events.FileValidationResult
import ai.rever.boss.components.events.KeyboardShortcutInterceptor
import ai.rever.boss.components.events.KeyEventSource
import ai.rever.boss.components.events.parseFileReference
import ai.rever.boss.components.events.stripFilePrefix
import ai.rever.boss.components.events.validateFilePath
import ai.rever.boss.components.events.TerminalLinkEventBus
import ai.rever.boss.components.events.URLEventBus
import ai.rever.boss.keymap.model.ShortcutContext
import ai.rever.boss.window.LocalWindowId
import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.hyperlinks.HyperlinkInfo
// BossTerm's HyperlinkType enum: HTTP, FILE, FOLDER, EMAIL, FTP, etc.
// Not to be confused with any local hyperlink types in this codebase
import ai.rever.bossterm.compose.hyperlinks.HyperlinkType
import ai.rever.bossterm.compose.EmbeddableTerminalState
import ai.rever.bossterm.compose.TabbedTerminal
import ai.rever.bossterm.compose.TabbedTerminalState
import ai.rever.boss.run.RunnerTerminalService
import ai.rever.bossterm.compose.rememberEmbeddableTerminalState
import ai.rever.bossterm.compose.settings.SettingsManager
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.settings.TerminalSettingsOverride
import ai.rever.bossterm.compose.onboarding.OnboardingWizard
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private val logger = BossLogger.forComponent("DesktopTerminalContent")

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

/** Pending commands per window to run when sidebar terminal first renders (thread-safe) */
private val pendingRunnerCommands = java.util.concurrent.ConcurrentHashMap<String, PendingRunnerCommand>()

/**
 * Set a pending command to run when the sidebar terminal panel opens for a specific window.
 * This should be called BEFORE opening the panel.
 * Thread-safe via ConcurrentHashMap.
 *
 * @param windowId The window ID to set the command for
 * @param command The command to run
 * @param workingDirectory Optional working directory for the terminal
 * @param configId Optional configuration ID for runner tracking
 */
fun setPendingSidebarCommand(windowId: String, command: String, workingDirectory: String?, configId: String? = null) {
    pendingRunnerCommands[windowId] = PendingRunnerCommand(command, workingDirectory, configId)
}

/**
 * Get and clear the pending command for a specific window (called by TabbedTerminalContent on render).
 * Thread-safe via ConcurrentHashMap.remove().
 *
 * @param windowId The window ID to get the command for
 * @return The pending command, or null if none
 */
fun consumePendingSidebarCommand(windowId: String): PendingRunnerCommand? {
    return pendingRunnerCommands.remove(windowId)
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
    // Get current window ID for per-window terminal isolation (Issue #498)
    val windowId = LocalWindowId.current ?: return

    // Observe reset generation to force recomposition when terminals are reset
    val resetGeneration by TabbedTerminalStateRegistry.resetGeneration.collectAsState()

    // Check if this is a fresh terminal (not in registry yet for this window)
    val isNew = !TabbedTerminalStateRegistry.contains(windowId, SIDEBAR_TERMINAL_ID)

    // Use persistent state so runner can send commands to this terminal
    // Key on resetGeneration to force re-creation after reset
    val state = remember(resetGeneration) { TabbedTerminalStateRegistry.getOrCreate(windowId, SIDEBAR_TERMINAL_ID) }

    // Check for pending runner command (set before panel opened) - window-scoped
    val pendingCommand = remember { if (isNew) consumePendingSidebarCommand(windowId) else null }

    // Override settings to always show tab bar for runner integration
    val sidebarSettings = remember {
        TerminalSettingsOverride(alwaysShowTabBar = true)
    }

    val effectiveWorkingDir = pendingCommand?.workingDirectory ?: workingDirectory

    // Welcome Wizard support - show on first launch when onboarding not completed
    var showWelcomeWizard by remember { mutableStateOf(false) }

    // Check if onboarding should be shown on first launch
    LaunchedEffect(settings.onboardingCompleted) {
        if (!settings.onboardingCompleted) {
            showWelcomeWizard = true
        }
    }

    // Register the first tab's ID using session listener (callback-based, no polling)
    // Capture windowId for use in listener
    val capturedWindowId = windowId
    androidx.compose.runtime.DisposableEffect(pendingCommand?.configId) {
        if (pendingCommand?.configId != null) {
            val configId = pendingCommand.configId
            val listener = object : ai.rever.bossterm.compose.tabs.TerminalSessionListener {
                override fun onSessionCreated(session: ai.rever.bossterm.compose.TerminalSession) {
                    // Register the first session's ID for this config (window-scoped)
                    TabbedTerminalStateRegistry.registerSidebarTabId(capturedWindowId, configId, session.id)
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
            // Normalize initialCommand to ensure auto-execution on Windows
            // BossTerm's TabbedTerminal doesn't append \n on Windows but does on Linux/macOS
            val normalizedPendingCommand = pendingCommand?.command?.let { command ->
                if (ShellUtils.isWindows && command.isNotEmpty()) {
                    if (command.endsWith("\n") || command.endsWith("\r\n")) {
                        command
                    } else {
                        "$command\n"
                    }
                } else {
                    command
                }
            }

            KeyboardShortcutInterceptor(
                windowId = windowId,
                source = KeyEventSource.COMPONENT_TERMINAL,
                context = ShortcutContext.TERMINAL
            ) {
                TabbedTerminal(
                    state = state,
                    // Pass pending command for first render (runs in default tab)
                    initialCommand = normalizedPendingCommand,
                    workingDirectory = effectiveWorkingDir,
                    settingsOverride = sidebarSettings,
                    onExit = {
                        TabbedTerminalStateRegistry.remove(windowId, SIDEBAR_TERMINAL_ID)
                        // Clean up all runner configs for this window's sidebar terminal
                        RunnerTerminalService.removeTerminal(windowId, SIDEBAR_TERMINAL_ID)
                        onExit()
                    },
                    onTabClose = { tabId ->
                        // When a tab is closed in sidebar terminal, check if it's a runner config
                        // and clean up the runner state for just that config (window-scoped)
                        val configId = TabbedTerminalStateRegistry.getConfigIdForSidebarTab(windowId, tabId)
                        if (configId != null) {
                            RunnerTerminalService.removeConfig(windowId, configId)
                            TabbedTerminalStateRegistry.removeSidebarConfigTracking(windowId, configId)
                        }
                    },
                    onShowSettings = onShowSettings,
                    onShowWelcomeWizard = { showWelcomeWizard = true },
                    onLinkClick = { info -> handleTerminalLinkClick(info, scope, SIDEBAR_TERMINAL_ID, windowId) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Welcome Wizard dialog
    if (showWelcomeWizard) {
        OnboardingWizard(
            onDismiss = { showWelcomeWizard = false },
            onComplete = { showWelcomeWizard = false },
            settingsManager = SettingsManager.instance
        )
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

    val settings by SettingsManager.instance.settings.collectAsState()
    val scope = rememberCoroutineScope()
    // Get current window ID for per-window terminal isolation (Issue #498)
    val windowId = LocalWindowId.current ?: return

    // Check if this is a new terminal (not already in registry for this window)
    val isNew = !TabbedTerminalStateRegistry.contains(windowId, terminalId)
    // Key on both terminalId and resetGeneration to force re-creation after reset
    val state = remember(terminalId, resetGeneration) { TabbedTerminalStateRegistry.getOrCreate(windowId, terminalId) }
    val effectiveWorkingDir = if (isNew) workingDirectory else null

    // Welcome Wizard support - show on first launch when onboarding not completed
    var showWelcomeWizard by remember { mutableStateOf(false) }

    // Check if onboarding should be shown on first launch
    LaunchedEffect(settings.onboardingCompleted) {
        if (!settings.onboardingCompleted) {
            showWelcomeWizard = true
        }
    }

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
            // Normalize initialCommand to ensure auto-execution on Windows
            // BossTerm's TabbedTerminal doesn't append \n on Windows but does on Linux/macOS
            val normalizedInitialCommand = if (isNew) {
                if (ShellUtils.isWindows && !initialCommand.isNullOrEmpty()) {
                    // Append newline if not already present
                    if (initialCommand.endsWith("\n") || initialCommand.endsWith("\r\n")) {
                        initialCommand
                    } else {
                        "$initialCommand\n"
                    }
                } else {
                    initialCommand
                }
            } else {
                null
            }

            KeyboardShortcutInterceptor(
                windowId = windowId,
                source = KeyEventSource.COMPONENT_TERMINAL,
                context = ShortcutContext.TERMINAL
            ) {
                TabbedTerminal(
                    state = state,
                    // Only send initial command and working directory for newly created terminals
                    initialCommand = normalizedInitialCommand,
                    workingDirectory = effectiveWorkingDir,
                    onExit = {
                        TabbedTerminalStateRegistry.remove(windowId, terminalId)
                        onExit()
                    },
                    onShowSettings = onShowSettings,
                    onShowWelcomeWizard = { showWelcomeWizard = true },
                    onWindowTitleChange = { title -> onTitleChange?.invoke(title) },
                    onLinkClick = { info -> handleTerminalLinkClick(info, scope, terminalId, windowId) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Welcome Wizard dialog
    if (showWelcomeWizard) {
        OnboardingWizard(
            onDismiss = { showWelcomeWizard = false },
            onComplete = { showWelcomeWizard = false },
            settingsManager = SettingsManager.instance
        )
    }
}

/**
 * Registry to store TabbedTerminal states by window and terminal ID, allowing them to persist across
 * composition tree changes (e.g., when switching tabs).
 *
 * Uses composite keys of format "$windowId:$terminalId" to ensure per-window isolation.
 * This prevents terminal state from being shared across multiple windows (Issue #498).
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

    /** Create a composite key for window-scoped terminal state */
    private fun key(windowId: String, terminalId: String) = "$windowId:$terminalId"

    fun getOrCreate(windowId: String, terminalId: String): TabbedTerminalState {
        return states.getOrPut(key(windowId, terminalId)) { TabbedTerminalState() }
    }

    fun get(windowId: String, terminalId: String): TabbedTerminalState? = states[key(windowId, terminalId)]

    fun remove(windowId: String, terminalId: String) {
        states.remove(key(windowId, terminalId))?.dispose()
    }

    fun contains(windowId: String, terminalId: String): Boolean = key(windowId, terminalId) in states

    /**
     * Remove all terminal states for a specific window.
     * Called when a window is closed.
     *
     * @param windowId The window ID to clean up
     * @return Number of terminal states that were disposed
     */
    fun removeAllForWindow(windowId: String): Int {
        val prefix = "$windowId:"
        val keysToRemove = states.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { key ->
            states.remove(key)?.dispose()
        }
        // Also clean up sidebar config tracking for this window
        clearSidebarConfigTrackingForWindow(windowId)
        logger.debug(LogCategory.TERMINAL, "Removed terminals for window", mapOf("count" to keysToRemove.size, "windowId" to windowId))
        return keysToRemove.size
    }

    /**
     * Send input bytes to a terminal by window and terminal ID.
     * Used for sending control characters like Ctrl+C (0x03).
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID to send input to
     * @param bytes The bytes to send
     * @return true if the terminal exists and input was sent, false otherwise
     */
    fun sendInput(windowId: String, terminalId: String, bytes: ByteArray): Boolean {
        val state = states[key(windowId, terminalId)] ?: return false
        state.sendInput(bytes)
        return true
    }

    /**
     * Send Ctrl+C (interrupt signal) to a terminal.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID to send Ctrl+C to
     * @return true if the terminal exists and Ctrl+C was sent, false otherwise
     */
    fun sendCtrlC(windowId: String, terminalId: String): Boolean {
        return sendInput(windowId, terminalId, byteArrayOf(0x03))
    }

    /**
     * Close the active tab in a terminal.
     * This will terminate the running process and close the tab.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID to close the active tab in
     * @return true if the terminal exists and tab was closed, false otherwise
     */
    fun closeActiveTab(windowId: String, terminalId: String): Boolean {
        val state = states[key(windowId, terminalId)] ?: return false
        state.closeActiveTab()
        return true
    }

    /**
     * Run a command in a terminal by sending it as input.
     * This sends the command text followed by Enter to execute it.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID to send the command to
     * @param command The command to run
     * @return true if the terminal exists and command was sent, false otherwise
     */
    fun runCommand(windowId: String, terminalId: String, command: String): Boolean {
        val state = states[key(windowId, terminalId)] ?: return false
        // Send the command followed by Enter (newline)
        val commandWithEnter = "$command\n"
        state.sendInput(commandWithEnter.toByteArray(Charsets.UTF_8))
        return true
    }

    // Track (windowId, configId) → stable tabId for sidebar terminal tabs
    // Uses BossTerm 1.0.61+ stable tab ID API
    // Key format: "$windowId:$configId"
    private val sidebarConfigToTabId = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Create a composite key for sidebar config tracking */
    private fun sidebarConfigKey(windowId: String, configId: String) = "$windowId:$configId"

    /**
     * Run a command in the sidebar terminal for a specific window.
     * - First run (panel not open): Sets pending command, panel will use it on render
     * - First run (panel already open): Creates a new tab with configId as stable tabId
     * - Re-run: Sends Ctrl+C to the config's tab by stable tabId, waits, then sends new command
     *
     * Uses BossTerm 1.0.61+ stable tab ID API for reliable tab targeting.
     *
     * @param windowId The window ID
     * @param command The command to run
     * @param workingDirectory Optional working directory for the terminal
     * @param configId The configuration ID (used as stable tabId for the tab)
     * @param isRerun If true, sends Ctrl+C first to stop any running process in the config's tab
     * @return true if command was sent successfully
     */
    fun newSidebarTab(
        windowId: String,
        command: String,
        workingDirectory: String? = null,
        configId: String? = null,
        isRerun: Boolean = false
    ): Boolean {
        val terminalExists = contains(windowId, SIDEBAR_TERMINAL_ID)
        logger.debug(LogCategory.TERMINAL, "newSidebarTab", mapOf("windowId" to windowId, "isRerun" to isRerun, "terminalExists" to terminalExists, "configId" to (configId ?: "none"), "command" to command))

        if (isRerun && configId != null) {
            // Re-run: send Ctrl+C to the config's tab by stable tabId, wait, then send new command
            val state = get(windowId, SIDEBAR_TERMINAL_ID) ?: return false
            val configKey = sidebarConfigKey(windowId, configId)
            val tabId = sidebarConfigToTabId[configKey]

            if (tabId != null) {
                logger.debug(LogCategory.TERMINAL, "Re-run: switching to tab, sending Ctrl+C, then command after delay", mapOf("tabId" to tabId))
                state.switchToTab(tabId) // Switch to the tab first so user sees it
                state.sendCtrlC(tabId) // Ctrl+C to specific tab by stable ID

                val delayMs = ai.rever.boss.run.RunnerSettingsManager.currentSettings.value.rerunDelayMs
                val fullCommand = ShellUtils.buildCommandWithWorkingDirectory(command, workingDirectory)
                val capturedTabId = tabId // Capture for lambda
                val capturedWindowId = windowId // Capture for lambda
                CoroutineScope(Dispatchers.Default).launch {
                    delay(delayMs)
                    // Check if terminal still exists before sending (prevents sending to disposed terminal)
                    if (contains(capturedWindowId, SIDEBAR_TERMINAL_ID)) {
                        val clearCommand = ShellUtils.chainCommands("clear", fullCommand)
                        get(capturedWindowId, SIDEBAR_TERMINAL_ID)?.sendInput("$clearCommand\n".toByteArray(Charsets.UTF_8), capturedTabId)
                    }
                }
            } else {
                // Fallback: no tabId tracked, send to active tab
                logger.debug(LogCategory.TERMINAL, "Re-run: no tabId for config, sending to active tab")
                state.sendInput(byteArrayOf(0x03))
                val delayMs = ai.rever.boss.run.RunnerSettingsManager.currentSettings.value.rerunDelayMs
                val fullCommand = ShellUtils.buildCommandWithWorkingDirectory(command, workingDirectory)
                val capturedWindowId = windowId // Capture for lambda
                CoroutineScope(Dispatchers.Default).launch {
                    delay(delayMs)
                    // Check if terminal still exists before sending (prevents sending to disposed terminal)
                    if (contains(capturedWindowId, SIDEBAR_TERMINAL_ID)) {
                        val clearCommand = ShellUtils.chainCommands("clear", fullCommand)
                        get(capturedWindowId, SIDEBAR_TERMINAL_ID)?.sendInput("$clearCommand\n".toByteArray(Charsets.UTF_8))
                    }
                }
            }
        } else if (!terminalExists) {
            // First run, panel not open yet: set pending command for TabbedTerminalContent to use
            logger.debug(LogCategory.TERMINAL, "First run (panel opening): setting pending command", mapOf("configId" to (configId ?: "none"), "windowId" to windowId))
            setPendingSidebarCommand(windowId, command, workingDirectory, configId)
        } else {
            // Panel already open, new config: create a new tab with configId as stable tabId
            val state = get(windowId, SIDEBAR_TERMINAL_ID) ?: return false
            logger.debug(LogCategory.TERMINAL, "New config (panel open): creating new tab", mapOf("tabId" to (configId ?: "none")))

            // Normalize initialCommand to ensure auto-execution on Windows
            // BossTerm's createTab doesn't append \n on Windows but does on Linux/macOS
            val normalizedCommand = if (ShellUtils.isWindows && command.isNotEmpty()) {
                if (command.endsWith("\n") || command.endsWith("\r\n")) {
                    command
                } else {
                    "$command\n"
                }
            } else {
                command
            }

            state.createTab(workingDir = workingDirectory, initialCommand = normalizedCommand, tabId = configId)
            // Record the mapping (configId is the tabId)
            if (configId != null) {
                val configKey = sidebarConfigKey(windowId, configId)
                sidebarConfigToTabId[configKey] = configId
                logger.debug(LogCategory.TERMINAL, "Recorded tabId for config", mapOf("tabId" to configId, "windowId" to windowId))
            }
        }
        return true
    }

    /**
     * Register the tabId for a config after the first tab is created.
     * Called from TabbedTerminalContent after the initial tab renders.
     *
     * @param windowId The window ID
     * @param configId The configuration ID
     * @param tabId The terminal tab ID
     */
    fun registerSidebarTabId(windowId: String, configId: String, tabId: String) {
        val configKey = sidebarConfigKey(windowId, configId)
        sidebarConfigToTabId[configKey] = tabId
        logger.debug(LogCategory.TERMINAL, "Registered tabId for config", mapOf("tabId" to tabId, "configId" to configId, "windowId" to windowId))
    }

    /**
     * Remove tab tracking for a config when it's stopped/removed.
     *
     * @param windowId The window ID
     * @param configId The configuration ID
     */
    fun removeSidebarConfigTracking(windowId: String, configId: String) {
        val configKey = sidebarConfigKey(windowId, configId)
        sidebarConfigToTabId.remove(configKey)
    }

    /**
     * Get the config ID for a sidebar tab ID (reverse lookup).
     * Returns the configId if found, null otherwise.
     *
     * @param windowId The window ID
     * @param tabId The terminal tab ID
     * @return The config ID, or null if not found
     */
    fun getConfigIdForSidebarTab(windowId: String, tabId: String): String? {
        val prefix = "$windowId:"
        return sidebarConfigToTabId.entries
            .filter { it.key.startsWith(prefix) && it.value == tabId }
            .map { it.key.removePrefix(prefix) }
            .firstOrNull()
    }

    /**
     * Clear all sidebar config tracking for a specific window.
     *
     * @param windowId The window ID
     */
    fun clearSidebarConfigTrackingForWindow(windowId: String) {
        val prefix = "$windowId:"
        val keysToRemove = sidebarConfigToTabId.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { sidebarConfigToTabId.remove(it) }
    }

    /**
     * Clear all sidebar config tracking (e.g., when sidebar terminal is closed).
     * @deprecated Use clearSidebarConfigTrackingForWindow(windowId) instead
     */
    @Deprecated("Use clearSidebarConfigTrackingForWindow(windowId) instead", ReplaceWith("clearSidebarConfigTrackingForWindow(windowId)"))
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
                logger.warn(LogCategory.TERMINAL, "Error disposing terminal state", error = e)
            }
        }
        states.clear()
        // Clear sidebar config tracking
        sidebarConfigToTabId.clear()
        // Increment generation to trigger UI recomposition
        _resetGeneration.value++
        logger.info(LogCategory.TERMINAL, "Reset complete: disposed terminal states", mapOf("count" to count, "generation" to _resetGeneration.value))
        return count
    }
}

/**
 * Registry to store terminal states by window and terminal ID, allowing them to persist across
 * composition tree changes (e.g., when splitting panels).
 *
 * Uses composite keys of format "$windowId:$terminalId" for per-window isolation.
 */
private object TerminalStateRegistry {
    private val states = mutableMapOf<String, EmbeddableTerminalState>()

    /** Generation counter for this registry (incremented on reset but not externally observed) */
    private val _resetGeneration = MutableStateFlow(0)
    val resetGeneration: StateFlow<Int> = _resetGeneration.asStateFlow()

    /** Create a composite key for window-scoped terminal state */
    private fun key(windowId: String, terminalId: String) = "$windowId:$terminalId"

    fun getOrCreate(windowId: String, terminalId: String): EmbeddableTerminalState {
        return states.getOrPut(key(windowId, terminalId)) { EmbeddableTerminalState() }
    }

    fun remove(windowId: String, terminalId: String) {
        states.remove(key(windowId, terminalId))?.dispose()
    }

    fun contains(windowId: String, terminalId: String): Boolean = key(windowId, terminalId) in states

    fun resetAll(): Int {
        val count = states.size
        states.values.forEach { state ->
            try {
                state.dispose()
            } catch (e: Exception) {
                logger.warn(LogCategory.TERMINAL, "Error disposing embeddable terminal state", error = e)
            }
        }
        states.clear()
        // Increment generation to trigger UI recomposition
        _resetGeneration.value++
        logger.info(LogCategory.TERMINAL, "TerminalStateRegistry reset complete", mapOf("count" to count, "generation" to _resetGeneration.value))
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
    logger.info(LogCategory.TERMINAL, "Total terminal reset complete", mapOf("total" to total, "tabbed" to tabbedCount, "embeddable" to embeddableCount))
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
 * API Change (BossTerm 1.0.67): The callback signature changed from `(url: String) -> Boolean`
 * to `(info: HyperlinkInfo) -> Boolean`. All consumers in this file have been updated.
 * The HyperlinkInfo provides typed hyperlink info including URL and HyperlinkType enum.
 *
 * Note: This launches coroutines without structured concurrency. If the terminal is closed
 * immediately after a link click, the event might emit after cleanup. This is low-risk
 * because the event bus is fire-and-forget, and BossApp handles stale events gracefully
 * by verifying panel existence before operations.
 *
 * @param info HyperlinkInfo containing URL, type, and metadata (from BossTerm)
 * @param scope CoroutineScope to launch async operations
 * @param terminalId Optional terminal tab ID (for detecting source panel when opening in splits)
 * @param windowId Optional window ID (for filtering events to correct window, Issue #498)
 * @return true if handled by BOSS, false to use BossTerm's default behavior
 */
private fun handleTerminalLinkClick(info: HyperlinkInfo, scope: CoroutineScope, terminalId: String? = null, windowId: String? = null): Boolean {
    return when (info.type) {
        HyperlinkType.HTTP -> {
            // Emit HTTP/HTTPS links to event bus for BossApp to handle
            // BossApp will show dialog or auto-open based on user preference
            scope.launch {
                TerminalLinkEventBus.emitLinkClick(info.url, terminalId, windowId)
            }
            true // Handled - BOSS manages HTTP links
        }
        HyperlinkType.FILE -> {
            // Parse file reference (handles URL encoding and line:column suffixes)
            // Then validate file path before routing to event bus
            scope.launch(Dispatchers.IO) {
                val rawPath = stripFilePrefix(info.url)
                val parsed = parseFileReference(rawPath)

                when (val result = validateFilePath(parsed.path)) {
                    is FileValidationResult.Valid -> {
                        // Route valid file links through TerminalLinkEventBus for consistent
                        // dialog/settings behavior (same "where to open" dialog as HTTP links)
                        // Encode line:column in URL for BossApp to extract
                        val urlWithLocation = buildString {
                            append("file:")
                            append(result.canonicalPath)
                            if (parsed.line > 0) {
                                append(":${parsed.line}")
                                if (parsed.column > 0) {
                                    append(":${parsed.column}")
                                }
                            }
                        }
                        TerminalLinkEventBus.emitLinkClick(urlWithLocation, terminalId, windowId)
                    }
                    is FileValidationResult.Invalid -> {
                        logger.warn(LogCategory.TERMINAL, "Cannot open file from terminal link", mapOf("reason" to result.reason))
                    }
                }
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

    val settings by SettingsManager.instance.settings.collectAsState()
    val scope = rememberCoroutineScope()
    // Get current window ID for per-window terminal isolation (Issue #498)
    val windowId = LocalWindowId.current ?: return

    // If terminalId is provided, use the registry for persistent state
    // Otherwise, use compose's remember (ephemeral state)
    val terminalState = if (terminalId != null) {
        val isNew = !TerminalStateRegistry.contains(windowId, terminalId)
        // Key on both terminalId and resetGeneration to force re-creation after reset
        val state = remember(terminalId, resetGeneration) { TerminalStateRegistry.getOrCreate(windowId, terminalId) }

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

    // Use key() to force complete recreation of terminal when reset happens
    key(resetGeneration) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = settings.defaultBackgroundColor
        ) {
            KeyboardShortcutInterceptor(
                windowId = windowId,
                source = KeyEventSource.COMPONENT_TERMINAL,
                context = ShortcutContext.TERMINAL
            ) {
                EmbeddableTerminal(
                    state = state,
                    // Only send initial command and working directory for newly created terminals
                    initialCommand = if (isNew) initialCommand else null,
                    workingDirectory = if (isNew) workingDirectory else null,
                    onExit = { _ ->
                        // Clean up registry when terminal exits (window-scoped)
                        terminalId?.let { TerminalStateRegistry.remove(windowId, it) }
                        onExit()
                    },
                    onLinkClick = { info -> handleTerminalLinkClick(info, scope, terminalId, windowId) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
