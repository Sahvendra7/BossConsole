package ai.rever.boss.components.plugin.tab_types.fluck

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
     * Get the currently selected text in the browser.
     *
     * Uses window.getSelection() API to retrieve selected text.
     * Returns trimmed text or null if no selection exists.
     *
     * **Usage**: `frame.executeJavaScript<String?>(BrowserJavaScripts.getSelectedText)`
     *
     * @return Selected text (trimmed) or null
     */
    val getSelectedText = """
        (function() {
            const sel = window.getSelection();
            return sel ? sel.toString().trim() : null;
        })();
    """.trimIndent()

    /**
     * Get the URL of a right-clicked link.
     *
     * Uses window._rightClickedLinkUrl which is set by the context menu
     * event listener in JxBrowserCompose.
     *
     * **Usage**: `frame.executeJavaScript<String?>(BrowserJavaScripts.getRightClickedLinkUrl)`
     *
     * @return Link URL or null
     */
    val getRightClickedLinkUrl = """
        (function() {
            return window._rightClickedLinkUrl || null;
        })();
    """.trimIndent()

    /**
     * Check if there are any video elements on the current page.
     *
     * Checks for:
     * - Standard HTML5 <video> elements
     * - YouTube-specific video selectors (html5-main-video, video-stream)
     *
     * **Usage**: `frame.executeJavaScript<Boolean>(BrowserJavaScripts.hasVideoElements)`
     *
     * @return true if video elements exist, false otherwise
     */
    val hasVideoElements = """
        (function() {
            const videos = document.querySelectorAll('video');
            const ytVideo = document.querySelector('video.html5-main-video, video.video-stream');
            return videos.length > 0 || ytVideo !== null;
        })();
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
    val enablePictureInPicture = """
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
