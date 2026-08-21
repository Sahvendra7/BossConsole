package ai.rever.boss.plugin.browser

import com.teamdev.jxbrowser.js.JsAccessible

/**
 * Page→plugin bridge for [BrowserHandleImpl.setPageEventScript].
 *
 * An instance is injected onto every main-frame `window` under `PAGE_EVENT_BRIDGE`
 * (`window.__bossPageEvent`) immediately before the plugin's own script is evaluated, so a
 * listener that script installs can hand an event straight back.
 *
 * Unlike [CoBrowseBridge], which serves one known feature, this carries a payload the host does
 * not interpret at all: the plugin supplies the script, so the plugin decides what an event is and
 * what its JSON means. The host's only jobs are to install this object and to forward the string.
 *
 * IMPORTANT, and the same constraint [CoBrowseBridge] documents: [emit] runs on a JxBrowser thread
 * and inside the page's own event dispatch. [onEvent] MUST be non-blocking - the caller enqueues
 * and returns. Exceptions are swallowed so a misbehaving sink can never crash the page's JS thread
 * or leave a submit half-dispatched.
 */
internal class PageEventBridge(
    @Volatile var onEvent: ((String) -> Unit)? = null,
) {
    @JsAccessible
    fun emit(json: String) {
        try {
            onEvent?.invoke(json)
        } catch (_: Throwable) {
            // Never propagate into the page's JS thread.
        }
    }
}
