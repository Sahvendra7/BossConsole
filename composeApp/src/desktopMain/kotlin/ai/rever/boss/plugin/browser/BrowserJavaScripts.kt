package ai.rever.boss.plugin.browser

/**
 * Centralized repository of JavaScript code snippets used in JxBrowser.
 *
 * This object contains all JavaScript code executed in the browser context,
 * making it easier to maintain, test, and reuse across the codebase.
 *
 * Benefits:
 * - Keeps JxBrowserCompose.kt cleaner and more focused on UI logic
 * - Provides a single source of truth for browser JavaScript
 * - Makes JavaScript code easier to find, update, and document
 * - Enables future testing of JavaScript snippets if needed
 */
object BrowserJavaScripts {
    /**
     * JavaScript to inject for Cmd+Click (Mac) / Ctrl+Click (Windows/Linux) to open links in new tabs.
     * Should be injected once after page load.
     *
     * When the user holds Cmd/Ctrl and clicks on a link, this intercepts the click,
     * prevents the default navigation, and calls window.open() with _blank target.
     * JxBrowser's OpenPopupCallback then routes this to open as a new tab.
     *
     * Uses capture phase (true) to intercept before normal click handlers.
     *
     * The guards all exist because this hijacks the click before the page sees it, so anything
     * it claims wrongly is a link the page can no longer handle itself:
     * - never a `download` anchor, whose whole point is not to navigate;
     * - only http(s), so `javascript:`, `mailto:`, `blob:` and `data:` stay with the page. This
     *   also covers an SVG `<a>`, whose `href` is an `SVGAnimatedString` rather than a string -
     *   it has no `protocol`, so it falls through to Chromium instead of being opened as
     *   `[object SVGAnimatedString]`. Do not "simplify" this to `new URL(link.href)`.
     * - only the primary button. Near-dead as written, since Chromium fires `auxclick` rather
     *   than `click` for the others, but the cost is one comparison.
     *
     * `defaultPrevented` is checked for the case where another capture-phase listener above us
     * has already cancelled the click. It says nothing about the page's own handlers: this runs
     * on the capture phase, so those have not run yet.
     *
     * Anything that falls through reaches Chromium's native cmd+click, which arrives at
     * `CreatePopupCallback` with a correct target URL - so falling through is always safe.
     */
    val injectCmdClickHandler =
        """
        (function() {
            if (!window._cmdClickHandlerAdded) {
                document.addEventListener('click', function(event) {
                    if (!(event.metaKey || event.ctrlKey)) return;
                    if (event.button !== 0 || event.defaultPrevented) return;
                    const link = event.target.closest('a');
                    if (!link || !link.href) return;
                    if (link.hasAttribute('download')) return;
                    const protocol = link.protocol;
                    if (protocol !== 'http:' && protocol !== 'https:') return;
                    event.preventDefault();
                    event.stopPropagation();
                    window.open(link.href, '_blank');
                }, true);
                window._cmdClickHandlerAdded = true;
            }
        })();
        """.trimIndent()

    /**
     * Generate JavaScript to find a link element at given screen coordinates.
     *
     * Uses document.elementFromPoint() to find the element, then traverses up
     * the DOM tree to find the nearest anchor tag with an href.
     *
     * **Usage**: `frame.executeJavaScript<String?>(BrowserJavaScripts.getLinkAtPoint(x, y))`
     *
     * @param x The x coordinate in the viewport
     * @param y The y coordinate in the viewport
     * @return JavaScript code that returns the link URL or null
     */
    fun getLinkAtPoint(
        x: Int,
        y: Int,
    ): String =
        """
        (function() {
            var el = document.elementFromPoint($x, $y);
            while (el) {
                if (el.tagName === 'A' && el.href) return el.href;
                el = el.parentElement;
            }
            return null;
        })()
        """.trimIndent()

    /**
     * Put the video a call is actually showing into Picture-in-Picture.
     *
     * Returns `"entered"` on success and a short reason otherwise, so the caller can tell whether
     * a pop-out is ours to close later.
     *
     * Picking the element is the whole difficulty, and [enablePictureInPicture] gets it wrong on a
     * call: it takes the first `<video>` over 100x100, and a meeting page has many - the
     * self-view, one per participant, and a screen share. So this scores them instead:
     *
     * - **A live `srcObject` is required.** Every participant tile is a `MediaStream`; a `<video>`
     *   with a `src` is an advert or a background loop, never the call.
     * - **Muted loses heavily.** The self-view is always muted (you do not echo yourself), and it
     *   is the one tile nobody wants popped out.
     * - **Bigger wins**, by painted area, because the speaker's tile is the large one.
     * - Zero-dimension and `disablePictureInPicture` elements are skipped: a video that has not
     *   produced a frame rejects with `InvalidStateError`, which is the same failure as picking
     *   nothing but harder to read in a log.
     */
    val enterCallPictureInPicture =
        """
        (function () {
            if (document.pictureInPictureElement) return 'already';
            var best = null;
            var bestScore = -1;
            var videos = document.querySelectorAll('video');
            for (var i = 0; i < videos.length; i++) {
                var v = videos[i];
                if (!v.srcObject) continue;
                if (v.disablePictureInPicture) continue;
                if (!v.videoWidth || !v.videoHeight) continue;
                var score = v.videoWidth * v.videoHeight;
                if (v.muted) score = score / 1000;
                if (score > bestScore) { bestScore = score; best = v; }
            }
            if (!best) return 'no call video';
            try {
                best.requestPictureInPicture();
                return 'entered';
            } catch (e) {
                return 'failed: ' + e.name;
            }
        })()
        """.trimIndent()

    /**
     * Enable Picture-in-Picture mode for videos on the page.
     *
     * Attempts to find and activate PiP on:
     * 1. YouTube's main video player
     * 2. The only video on the page
     * 3. The largest visible video (if multiple)
     *
     * Toggles PiP off if already active.
     *
     * **Usage**: `frame.executeJavaScript<Unit>(BrowserJavaScripts.enablePictureInPicture)`
     */
    val enablePictureInPicture =
        """
        (function() {
            // Find all video elements on the page
            const videos = document.querySelectorAll('video');

            // For YouTube and similar sites, find the main video player
            let targetVideo = null;

            // Check for YouTube specific video
            const ytVideo = document.querySelector('video.html5-main-video, video.video-stream');
            if (ytVideo) {
                targetVideo = ytVideo;
            } else if (videos.length === 1) {
                // If there's only one video, use it
                targetVideo = videos[0];
            } else if (videos.length > 1) {
                // If multiple videos, try to find the visible one
                for (let video of videos) {
                    const rect = video.getBoundingClientRect();
                    if (rect.width > 100 && rect.height > 100 &&
                        video.readyState >= 2) { // HAVE_CURRENT_DATA
                        targetVideo = video;
                        break;
                    }
                }
            }

            if (targetVideo) {
                if (document.pictureInPictureElement) {
                    document.exitPictureInPicture();
                } else if (targetVideo.requestPictureInPicture) {
                    targetVideo.requestPictureInPicture().catch(err => {
                        console.error('PiP failed:', err);
                    });
                }
            }
        })();
        """.trimIndent()
}
