package ai.rever.boss.plugin.browser

/**
 * The kind of device capture a page has open. Deliberately not JxBrowser's `MediaStreamType`:
 * keeping this file free of engine types is what lets the decision below be unit-tested, since
 * nothing here can construct a `Browser`.
 */
internal enum class CapturedMedia {
    AUDIO,
    VIDEO,
}

/**
 * What a page is capturing right now, counted per kind rather than flagged.
 *
 * Counting is the whole point. A video call opens audio and video as separate streams and closes
 * them separately - muting the mic mid-call ends the audio stream on its own - so a boolean flips
 * to "not in a call" on the first stop while the call is plainly still running. It also has to
 * survive the same kind twice: a page that adds a second camera, or re-negotiates its stream,
 * raises two starts and the first stop must not cancel both.
 *
 * Synchronised because the capture events arrive on a JxBrowser thread while the tab-switch path
 * reads this from the UI thread.
 */
internal class CaptureTracker {
    private val counts = mutableMapOf<CapturedMedia, Int>()

    @Synchronized
    fun started(media: CapturedMedia) {
        counts[media] = (counts[media] ?: 0) + 1
    }

    @Synchronized
    fun stopped(media: CapturedMedia) {
        val remaining = (counts[media] ?: 0) - 1
        // Never let a count go negative: a stop with no matching start would otherwise need two
        // starts to climb back to capturing. JxBrowser replays no events to a late subscriber, so
        // a handle created while a call is already running sees exactly that.
        if (remaining <= 0) counts.remove(media) else counts[media] = remaining
    }

    @Synchronized
    fun isCapturing(): Boolean = counts.isNotEmpty()

    @Synchronized
    fun clear() = counts.clear()
}

/**
 * Whether backgrounding this tab should pop its video out.
 *
 * Mirrors the video-conferencing branch of Chrome's `AutoPictureInPictureTabHelper`: a page that
 * holds the camera or microphone is in a call, and a call is worth keeping on screen. Chrome's
 * other branch - ordinary media playback, gated on the Media Engagement Index - is deliberately
 * not copied, so a YouTube tab does not follow you around.
 *
 * `https` only, matching Chrome (which has a browsertest named `CannotAutopipViaHttp`). Capture
 * needs a secure context anyway, so in practice this only excludes `file:` and oddities.
 *
 * @param alreadyPoppedOut a pop-out the user opened by hand must never be replaced, and Chromium
 *   allows only one at a time regardless.
 */
internal fun shouldAutoPictureInPicture(
    url: String,
    isCapturing: Boolean,
    alreadyPoppedOut: Boolean,
): Boolean {
    if (!isCapturing || alreadyPoppedOut) return false
    return url.substringBefore(':', missingDelimiterValue = "").lowercase() == "https"
}
