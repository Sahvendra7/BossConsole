package ai.rever.boss.plugin.browser

import com.teamdev.jxbrowser.engine.RenderingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the two decisions in [BrowserFrameStall] that decide whether a user's browser view gets
 * yanked out of composition and put back.
 *
 * Both are cheap to get backwards, and both fail in the direction of doing the re-attach too
 * often - which is a visible flicker on pages that were never broken.
 */
class BrowserFrameStallTest {
    @Test
    fun `only http and https pages are watched`() {
        val mode = RenderingMode.HARDWARE_ACCELERATED
        assertTrue(BrowserFrameStall.shouldWatch("https://www.google.com/search?q=x&udm=50&aep=1", mode))
        assertTrue(BrowserFrameStall.shouldWatch("http://example.com", mode))
        assertTrue(BrowserFrameStall.shouldWatch("  https://example.com  ", mode), "a padded URL is still a web page")
        assertTrue(BrowserFrameStall.shouldWatch("HTTPS://EXAMPLE.COM", mode), "scheme is case-insensitive")
    }

    @Test
    fun `internal and empty pages are never watched`() {
        val mode = RenderingMode.HARDWARE_ACCELERATED
        // about:blank and the dashboard's empty URL are what a tab shows when it is *meant* to be
        // empty. Probing them would arm a beacon on a page with no rAF work and then re-attach the
        // view for it, so the blank the user asked for would flicker.
        for (url in listOf(null, "", "   ", "about:blank", "chrome://gpu", "file:///tmp/x.html", "boss://terminal")) {
            assertFalse(BrowserFrameStall.shouldWatch(url, mode), "should not watch $url")
        }
    }

    @Test
    fun `OFF_SCREEN is never watched`() {
        // The stall is an attachment fault in the native-view path. OFF_SCREEN exports frames as a
        // bitmap and has never shown it, so watching there would be pure risk with no upside.
        assertFalse(
            BrowserFrameStall.shouldWatch("https://www.google.com/search?q=x&udm=50&aep=1", RenderingMode.OFF_SCREEN),
        )
    }

    @Test
    fun `a failed beacon read is not a stall`() {
        // The distinction this test exists for: null means the JS round-trip did not happen (a
        // navigation raced the probe, the frame went away), NOT that the page failed to draw.
        // Treating null as a stall re-attaches the view on ordinary pages.
        assertFalse(BrowserFrameStall.isStalled(null))
    }

    @Test
    fun `a painted beacon is not a stall and an unpainted one is`() {
        assertFalse(BrowserFrameStall.isStalled(BrowserFrameStall.BEACON_PAINTED))
        assertTrue(BrowserFrameStall.isStalled("0"))
    }

    @Test
    fun `the beacon script reports the value it also arms`() {
        // The same snippet arms and polls, so a rename on one side cannot silently stop the other
        // from ever matching - which would re-attach the view on every single navigation.
        assertTrue(BrowserFrameStall.BEACON_SCRIPT.contains("__bossFrameBeacon"))
        assertTrue(BrowserFrameStall.BEACON_SCRIPT.contains("requestAnimationFrame"))
        assertTrue(
            BrowserFrameStall.BEACON_SCRIPT.contains("'${BrowserFrameStall.BEACON_PAINTED}'"),
            "the script must assign the exact value isStalled compares against",
        )
    }

    @Test
    fun `both waits are non-trivial`() {
        // A first check that fires immediately would call every page stalled, since no page has
        // painted at commit time.
        assertTrue(BrowserFrameStall.FIRST_CHECK_MS >= 500)
        assertTrue(BrowserFrameStall.CONFIRM_MS >= 500)
        assertEquals(1600L, BrowserFrameStall.FIRST_CHECK_MS + BrowserFrameStall.CONFIRM_MS)
    }
}
