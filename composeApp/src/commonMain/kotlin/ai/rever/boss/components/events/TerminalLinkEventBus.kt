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
    /**
     * SharedFlow for terminal link click events.
     *
     * Buffer sizing rationale:
     * - replay = 0: New subscribers shouldn't see old events (user already dismissed dialog)
     * - extraBufferCapacity = 10: Provides headroom for rapid link clicks while dialog is shown.
     *   This is a conservative buffer; in practice, users rarely click more than a few links
     *   before the first dialog appears (~16ms compose frame time). If buffer overflows,
     *   emit() suspends until space is available (no events lost, just delayed).
     */
    private val _linkClickEvents = MutableSharedFlow<TerminalLinkClickEvent>(
        replay = 0,
        extraBufferCapacity = 10
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
