package ai.rever.boss.components.plugin.panels.bottom.terminal

import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.EmbeddableTerminalState
import ai.rever.bossterm.compose.TabbedTerminal
import ai.rever.bossterm.compose.rememberEmbeddableTerminalState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

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
    TabbedTerminal(
        onExit = onExit,
        onShowSettings = onShowSettings,
        modifier = Modifier.fillMaxSize()
    )
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

    EmbeddableTerminal(
        state = state,
        onExit = { _ ->
            // Clean up registry when terminal exits
            terminalId?.let { TerminalStateRegistry.remove(it) }
            onExit()
        },
        onReady = {
            // Send initial command only for new terminals
            if (shouldSendInitialCommand) {
                initialCommand?.let { cmd ->
                    state.write(cmd + "\n")
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
