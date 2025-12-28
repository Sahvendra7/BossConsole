package ai.rever.boss.components.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event emitted when a link is clicked in a terminal.
 * Contains the URL to open.
 */
data class TerminalLinkClickEvent(
    val url: String
)

/**
 * Event bus for terminal link clicks.
 *
 * BossApp subscribes to show the link open dialog when the user's
 * preference is ALWAYS_ASK, or to auto-open with their saved preference.
 *
 * Issue #346: Terminal link click prompt with remember preference
 */
object TerminalLinkEventBus {
    private val _linkClickEvents = MutableSharedFlow<TerminalLinkClickEvent>(
        replay = 0,  // Don't replay past events to new subscribers
        extraBufferCapacity = 10  // Buffer events if collector not ready
    )
    val linkClickEvents: SharedFlow<TerminalLinkClickEvent> = _linkClickEvents.asSharedFlow()

    /**
     * Emit a terminal link click event.
     *
     * @param url The URL that was clicked in the terminal
     */
    suspend fun emitLinkClick(url: String) {
        _linkClickEvents.emit(TerminalLinkClickEvent(url))
    }
}
