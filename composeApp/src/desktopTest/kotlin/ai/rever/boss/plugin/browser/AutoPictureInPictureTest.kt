package ai.rever.boss.plugin.browser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a backgrounded tab pops its video out, and what counts as "still in a call".
 *
 * The counting half is the part that looks like bookkeeping and is not: a call opens audio and
 * video separately, so the obvious boolean reports the call over the moment anyone mutes.
 */
class AutoPictureInPictureTest {
    // ---- the decision ----

    @Test
    fun `a tab holding the camera or mic pops out when it is backgrounded`() {
        assertTrue(
            shouldAutoPictureInPicture(
                url = "https://meet.google.com/abc-defg-hij",
                isCapturing = true,
                alreadyPoppedOut = false,
            ),
        )
    }

    @Test
    fun `a tab capturing nothing is left alone`() {
        // The whole point of gating on capture: a YouTube tab must not follow you around.
        assertFalse(
            shouldAutoPictureInPicture(
                url = "https://www.youtube.com/watch?v=abc",
                isCapturing = false,
                alreadyPoppedOut = false,
            ),
        )
    }

    @Test
    fun `a pop-out already on screen is never replaced`() {
        // Chromium allows one at a time, and one the user opened by hand is theirs.
        assertFalse(
            shouldAutoPictureInPicture(
                url = "https://meet.google.com/abc-defg-hij",
                isCapturing = true,
                alreadyPoppedOut = true,
            ),
        )
    }

    @Test
    fun `only https pages qualify`() {
        for (url in listOf("http://meet.example.com/x", "file:///tmp/call.html", "about:blank", "")) {
            assertFalse(
                shouldAutoPictureInPicture(url, isCapturing = true, alreadyPoppedOut = false),
                "$url should not qualify",
            )
        }
        assertTrue(
            shouldAutoPictureInPicture("HTTPS://meet.example.com/x", isCapturing = true, alreadyPoppedOut = false),
            "the scheme comparison must be case-insensitive",
        )
    }

    // ---- the counting ----

    @Test
    fun `muting the mic mid-call does not end the call`() {
        // The regression this exists for. Audio and video are separate streams; a boolean would
        // report "no longer capturing" here while the camera is still on.
        val tracker = CaptureTracker()
        tracker.started(CapturedMedia.AUDIO)
        tracker.started(CapturedMedia.VIDEO)

        tracker.stopped(CapturedMedia.AUDIO)

        assertTrue(tracker.isCapturing(), "video is still live, so the call is still running")
    }

    @Test
    fun `the call ends only when every stream has stopped`() {
        val tracker = CaptureTracker()
        tracker.started(CapturedMedia.AUDIO)
        tracker.started(CapturedMedia.VIDEO)
        tracker.stopped(CapturedMedia.AUDIO)
        tracker.stopped(CapturedMedia.VIDEO)

        assertFalse(tracker.isCapturing())
    }

    @Test
    fun `two streams of one kind need two stops`() {
        val tracker = CaptureTracker()
        tracker.started(CapturedMedia.VIDEO)
        tracker.started(CapturedMedia.VIDEO)

        tracker.stopped(CapturedMedia.VIDEO)
        assertTrue(tracker.isCapturing(), "one camera stream is still open")

        tracker.stopped(CapturedMedia.VIDEO)
        assertFalse(tracker.isCapturing())
    }

    @Test
    fun `a stop with no matching start cannot drive the count negative`() {
        // A handle created while a call is already running never saw the start - JxBrowser does
        // not replay events - so the first thing it sees can be a stop. If that went to -1, the
        // next start would leave the count at 0 and the tab would look idle mid-call.
        val tracker = CaptureTracker()
        tracker.stopped(CapturedMedia.AUDIO)
        tracker.stopped(CapturedMedia.AUDIO)

        tracker.started(CapturedMedia.AUDIO)

        assertTrue(tracker.isCapturing())
    }

    @Test
    fun `a fresh tracker is not capturing`() {
        assertFalse(CaptureTracker().isCapturing())
    }

    @Test
    fun `clear drops everything`() {
        val tracker = CaptureTracker()
        tracker.started(CapturedMedia.AUDIO)
        tracker.started(CapturedMedia.VIDEO)

        tracker.clear()

        assertFalse(tracker.isCapturing())
    }

    @Test
    fun `the pop-out poll outlasts the site route's own deadline`() {
        // The poll has to still be running when the site route gives up, or a site-route pop-out
        // is read while pending, recorded as a failure, and never closed again on the way back -
        // stranding the window on screen after the user returns. Two files, two constants,
        // nothing else connecting them: raising the deadline alone silently reintroduces it.
        val pollBudgetMs = BrowserHandleImpl.AUTO_PIP_POLL_MS * BrowserHandleImpl.AUTO_PIP_POLL_ATTEMPTS

        assertTrue(
            pollBudgetMs > PopOutScripts.SITE_PIP_DEADLINE_MS,
            "poll budget ${pollBudgetMs}ms must outlast the site deadline " +
                "${PopOutScripts.SITE_PIP_DEADLINE_MS}ms",
        )
    }
}
