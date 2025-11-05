package ai.rever.boss.components.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event emitted when a terminal tab should be opened
 *
 * @property command Optional initial command to run in the terminal
 */
data class TerminalOpenEvent(
    val command: String?
)

/**
 * Event bus for handling terminal open requests across all windows
 *
 * When a CLI command requests a terminal (e.g., `boss terminal` or `boss terminal -c "ls"`),
 * this event bus coordinates the request. Each window's BossApp listens for events and the
 * active window handles the request by creating a new terminal tab.
 *
 * Similar to URLEventBus and FileEventBus but for terminal tabs.
 */
object TerminalEventBus {
    private val _terminalOpenEvents = MutableSharedFlow<TerminalOpenEvent>(
        replay = 0,  // Don't replay past events to new subscribers (new windows)
        extraBufferCapacity = 10  // Buffer up to 10 events if collector not ready yet
    )
    val terminalOpenEvents: SharedFlow<TerminalOpenEvent> = _terminalOpenEvents.asSharedFlow()

    /**
     * Emit a terminal open event
     *
     * All windows will receive this event, and the active window will create
     * a new terminal tab (or focus existing terminal if one is available).
     *
     * @param command Optional command to run in the terminal
     */
    suspend fun openTerminal(command: String? = null) {
        _terminalOpenEvents.emit(TerminalOpenEvent(command))
    }
}
