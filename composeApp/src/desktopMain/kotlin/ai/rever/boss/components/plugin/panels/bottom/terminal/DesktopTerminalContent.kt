package ai.rever.boss.components.plugin.panels.bottom.terminal

import ai.rever.boss.components.events.URLEventBus
import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.EmbeddableTerminalState
import ai.rever.bossterm.compose.TabbedTerminal
import ai.rever.bossterm.compose.TabbedTerminalState
import ai.rever.bossterm.compose.rememberEmbeddableTerminalState
import ai.rever.bossterm.compose.settings.SettingsManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Desktop implementation of TabbedTerminalContent using BossTerm's TabbedTerminal.
 *
 * Provides full-featured terminal with:
 * - Multiple tabs within the panel
 * - Split panes (horizontal/vertical)
 * - Tab management keyboard shortcuts
 * - Settings integration (opens BOSS Settings)
 */
@Composable
actual fun TabbedTerminalContent(
    onExit: () -> Unit,
    onShowSettings: () -> Unit
) {
    val settings by SettingsManager.instance.settings.collectAsState()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = settings.defaultBackgroundColor
    ) {
        TabbedTerminal(
            onExit = onExit,
            onShowSettings = onShowSettings,
            onLinkClick = { url ->
                // Open HTTP/HTTPS links in BOSS browser instead of system browser
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    scope.launch {
                        URLEventBus.openURL(url)
                    }
                } else {
                    // For other protocols (file://, mailto:, etc.), open in system
                    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * TabbedTerminal with persistent state across composition changes.
 * Uses TabbedTerminalStateRegistry to preserve terminal sessions when switching tabs.
 *
 * @param terminalId Unique ID for this terminal instance, used as key in state registry
 * @param onExit Called when the last terminal tab is closed
 * @param onShowSettings Called when user requests settings
 * @param onTitleChange Called when terminal window title changes via escape sequences (OSC 0/1/2)
 */
@Composable
actual fun PersistentTabbedTerminalContent(
    terminalId: String,
    onExit: () -> Unit,
    onShowSettings: () -> Unit,
    onTitleChange: ((String) -> Unit)?
) {
    val state = remember(terminalId) { TabbedTerminalStateRegistry.getOrCreate(terminalId) }
    val settings by SettingsManager.instance.settings.collectAsState()
    val scope = rememberCoroutineScope()

    DisposableEffect(terminalId) {
        onDispose {
            // Don't remove from registry here - cleanup happens when tab is closed
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = settings.defaultBackgroundColor
    ) {
        TabbedTerminal(
            state = state,
            onExit = {
                TabbedTerminalStateRegistry.remove(terminalId)
                onExit()
            },
            onShowSettings = onShowSettings,
            onWindowTitleChange = { title -> onTitleChange?.invoke(title) },
            onLinkClick = { url ->
                // Open HTTP/HTTPS links in BOSS browser instead of system browser
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    scope.launch {
                        URLEventBus.openURL(url)
                    }
                } else {
                    // For other protocols (file://, mailto:, etc.), open in system
                    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Registry to store TabbedTerminal states by ID, allowing them to persist across
 * composition tree changes (e.g., when switching tabs).
 */
object TabbedTerminalStateRegistry {
    private val states = mutableMapOf<String, TabbedTerminalState>()

    fun getOrCreate(terminalId: String): TabbedTerminalState {
        return states.getOrPut(terminalId) { TabbedTerminalState() }
    }

    fun remove(terminalId: String) {
        states.remove(terminalId)?.dispose()
    }

    fun contains(terminalId: String): Boolean = terminalId in states
}

/**
 * Registry to store terminal states by ID, allowing them to persist across
 * composition tree changes (e.g., when splitting panels).
 */
private object TerminalStateRegistry {
    private val states = mutableMapOf<String, EmbeddableTerminalState>()

    fun getOrCreate(terminalId: String): EmbeddableTerminalState {
        return states.getOrPut(terminalId) { EmbeddableTerminalState() }
    }

    fun remove(terminalId: String) {
        states.remove(terminalId)?.dispose()
    }

    fun contains(terminalId: String): Boolean = terminalId in states
}

/**
 * Desktop implementation of TerminalContent using BossTerm's EmbeddableTerminal.
 */
@Composable
actual fun TerminalContent(
    terminalId: String?,
    initialCommand: String?,
    onExit: () -> Unit
) {
    // If terminalId is provided, use the registry for persistent state
    // Otherwise, use compose's remember (ephemeral state)
    val terminalState = if (terminalId != null) {
        val isNew = !TerminalStateRegistry.contains(terminalId)
        val state = remember(terminalId) { TerminalStateRegistry.getOrCreate(terminalId) }

        // Clean up when this composable is permanently removed
        DisposableEffect(terminalId) {
            onDispose {
                // Note: We don't remove from registry here because the composable
                // might just be moving in the tree (e.g., during split).
                // Cleanup happens when the tab is actually closed.
            }
        }

        // Only send initial command for newly created terminals
        if (isNew && initialCommand != null) {
            state to true
        } else {
            state to false
        }
    } else {
        // Fallback to ephemeral state for sidebar terminals without ID
        rememberEmbeddableTerminalState() to (initialCommand != null)
    }

    val (state, shouldSendInitialCommand) = terminalState
    val settings by SettingsManager.instance.settings.collectAsState()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = settings.defaultBackgroundColor
    ) {
        EmbeddableTerminal(
            state = state,
            // Only send initial command for newly created terminals
            initialCommand = if (shouldSendInitialCommand) initialCommand else null,
            onExit = { _ ->
                // Clean up registry when terminal exits
                terminalId?.let { TerminalStateRegistry.remove(it) }
                onExit()
            },
            onLinkClick = { url ->
                // Open HTTP/HTTPS links in BOSS browser instead of system browser
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    scope.launch {
                        URLEventBus.openURL(url)
                    }
                } else {
                    // For other protocols (file://, mailto:, etc.), open in system
                    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
