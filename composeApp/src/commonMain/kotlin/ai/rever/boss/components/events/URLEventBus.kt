package ai.rever.boss.components.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event emitted when a URL should be opened in a new or existing tab
 *
 * @property url The URL to open (http:// or https://)
 * @property title Initial title for the tab (domain name or "Loading...")
 */
data class URLOpenEvent(
    val url: String,
    val title: String
)

/**
 * Event bus for handling URL open requests across all windows
 *
 * When BOSS is set as the default browser, external applications pass URLs
 * through this event bus. Each window's BossApp listens for events and the
 * active window handles the URL by creating a new Fluck browser tab.
 *
 * Similar to FileEventBus but for HTTP(S) URLs instead of file paths.
 */
object URLEventBus {
    private val _urlOpenEvents = MutableSharedFlow<URLOpenEvent>(
        replay = 0,  // Don't replay past events to new subscribers (new windows)
        extraBufferCapacity = 10  // Buffer up to 10 events if collector not ready yet
    )
    val urlOpenEvents: SharedFlow<URLOpenEvent> = _urlOpenEvents.asSharedFlow()

    /**
     * Emit a URL open event
     *
     * All windows will receive this event, and the active window will create
     * a new tab (or focus existing tab if URL is already open).
     *
     * @param url The URL to open
     * @param title Initial tab title (defaults to "Loading...")
     */
    suspend fun openURL(url: String, title: String = "Loading...") {
        _urlOpenEvents.emit(URLOpenEvent(url, title))
    }
}
