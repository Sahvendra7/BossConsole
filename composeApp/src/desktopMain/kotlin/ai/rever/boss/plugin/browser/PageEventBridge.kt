package ai.rever.boss.plugin.browser

import com.teamdev.jxbrowser.js.JsAccessible

/**
 * Page to plugin bridge for [BrowserHandleImpl.setPageEventScript].
 *
 * An instance is injected onto every main-frame `window` under `PAGE_EVENT_BRIDGE`
 * (`window.__bossPageEvent`) immediately before the plugin's own script is evaluated, so a listener
 * that script installs can hand an event straight back.
 *
 * Unlike [CoBrowseBridge], which serves one known feature, this carries a payload the host does not
 * interpret at all: the plugin supplies the script, so the plugin decides what an event is and what
 * its JSON means.
 *
 * **The URL is the host's contribution, and the reason [urlProvider] exists.** The bridge is a
 * public property on `window`, so any script on the page can post - what reaches the plugin is
 * untrusted input, and a URL written into the JSON is whatever the poster chose. So the host reads
 * the posting document's URL itself, here, at the moment of the call. That is also strictly better
 * than the plugin reading the handle's URL after the fact: [emit] runs inside the page's own event
 * dispatch, before the navigation a submit triggers can commit, so the answer is the document that
 * actually posted rather than whatever the tab has moved on to. The first consumer uses it to decide
 * which site a password gets stored against.
 *
 * IMPORTANT, and the same constraint [CoBrowseBridge] documents: [emit] runs on a JxBrowser thread
 * and inside the page's own event dispatch. [onEvent] MUST be non-blocking - the caller enqueues and
 * returns. Exceptions are swallowed so a misbehaving sink can never crash the page's JS thread or
 * leave a submit half-dispatched.
 */
internal class PageEventBridge(
    @Volatile var onEvent: ((url: String, json: String) -> Unit)? = null,
    /**
     * Answers the posting document's URL. A lambda rather than a field because one bridge instance
     * serves every document this browser loads, so the value has to be read per call.
     */
    @Volatile var urlProvider: () -> String = { "" },
) {
    @JsAccessible
    fun emit(json: String) {
        try {
            val sink = onEvent ?: return
            // Read before the sink runs, and defensively: a throwing url read must not lose the
            // event, and an empty URL is something the consumer can recognise and refuse.
            val url = runCatching { urlProvider() }.getOrDefault("")
            sink(url, json)
        } catch (_: Throwable) {
            // Never propagate into the page's JS thread.
        }
    }
}
