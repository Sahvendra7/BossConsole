package ai.rever.boss.components.plugin.tab_types.fluck

import com.teamdev.jxbrowser.browser.callback.ShowContextMenuCallback
import com.teamdev.jxbrowser.ui.Point

/**
 * Data class containing context menu information.
 *
 * Uses a hybrid approach:
 * - JxBrowser's ShowContextMenuCallback intercepts right-click events
 * - Location is provided by the native callback
 * - Link URL, selected text, video status are populated via JavaScript inspection
 *   after the callback fires (done in the calling code)
 *
 * @param location Screen location where context menu was requested
 * @param linkUrl URL of the link under cursor, or null if not on a link
 * @param selectedText Currently selected text, or null if no selection
 * @param hasVideo Whether there's a video element on the page
 * @param isEditable Whether the element under cursor is an editable form field
 * @param pageUrl Current page URL
 */
data class ContextMenuInfo(
    val location: Point,
    val linkUrl: String? = null,
    val selectedText: String? = null,
    val hasVideo: Boolean = false,
    val isEditable: Boolean = false,
    val pageUrl: String = ""
)

/**
 * JxBrowser callback that intercepts native context menu requests.
 *
 * Instead of showing JxBrowser's default context menu, this callback:
 * 1. Suppresses the native menu via tell.close()
 * 2. Extracts the click location and content type from params
 * 3. Invokes the callback with initial ContextMenuInfo
 *
 * The calling code should then use JavaScript to populate additional info
 * (link URL, selected text, video status) since the JxBrowser 8.x API
 * uses frame.inspect() which requires async handling.
 *
 * Thread Safety:
 * - This callback runs on JxBrowser's thread, NOT the UI thread
 * - Use Dispatchers.Main when updating Compose state from the callback
 *
 * Usage:
 * ```
 * browser.set(ShowContextMenuCallback::class.java,
 *     BrowserContextMenuCallback(browser) { info ->
 *         // Update Compose state on main thread
 *         coroutineScope.launch(Dispatchers.Main) {
 *             contextMenuInfo = info
 *             showContextMenu = true
 *         }
 *     }
 * )
 * ```
 *
 * @param browser The LockedBrowser instance for JavaScript execution
 * @param onContextMenuRequested Callback invoked with context menu information
 */
class BrowserContextMenuCallback(
    private val browser: LockedBrowser,
    private val onContextMenuRequested: (ContextMenuInfo) -> Unit
) : ShowContextMenuCallback {

    override fun on(params: ShowContextMenuCallback.Params, tell: ShowContextMenuCallback.Action) {
        // Extract location from native callback
        val location = params.location()
        val pageUrl = try {
            params.browser().url()
        } catch (e: Exception) {
            ""
        }

        // Use JavaScript to get link URL, selected text, video status, and editable state
        // This is more reliable than the native PointInspection API for our use case
        var linkUrl: String? = null
        var selectedText: String? = null
        var hasVideo = false
        var isEditable = false

        try {
            browser.mainFrame().ifPresent { frame ->
                // Get link URL at click position
                linkUrl = frame.executeJavaScript<String?>(BrowserJavaScripts.getRightClickedLinkUrl)

                // Get selected text
                val selection = frame.executeJavaScript<String?>(BrowserJavaScripts.getSelectedText)
                selectedText = if (!selection.isNullOrBlank()) selection else null

                // Check if right-clicked on a video element (not just if page has videos)
                hasVideo = frame.executeJavaScript<Boolean>(BrowserJavaScripts.isClickedOnVideo) ?: false

                // Check if focused element is editable
                isEditable = frame.executeJavaScript<Boolean>("""
                    (function() {
                        var el = document.activeElement;
                        if (!el) return false;
                        var tag = el.tagName.toLowerCase();
                        if (tag === 'input' || tag === 'textarea') return true;
                        if (el.isContentEditable) return true;
                        return false;
                    })()
                """.trimIndent()) ?: false
            }
        } catch (e: Exception) {
            // JavaScript execution failed - proceed with defaults
        }

        // Build context menu info
        val info = ContextMenuInfo(
            location = location,
            linkUrl = linkUrl,
            selectedText = selectedText,
            hasVideo = hasVideo,
            isEditable = isEditable,
            pageUrl = pageUrl
        )

        // Suppress JxBrowser's native context menu
        tell.close()

        // Invoke callback with extracted info (runs on JxBrowser thread)
        onContextMenuRequested(info)
    }
}
