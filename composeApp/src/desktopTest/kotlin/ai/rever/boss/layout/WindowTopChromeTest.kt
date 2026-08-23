package ai.rever.boss.layout

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The traffic-light reservation, asserted without a live window.
 *
 * None of this is observable from a running app in a test: the buttons are drawn by the macOS window
 * server outside the Java hierarchy, so the only way to keep the rules honest is to keep them pure
 * and pin them here. The one that matters most is `the inset never disappears while the buttons
 * are there` - the failure it rules out is window controls painted on top of a tab.
 */
class WindowTopChromeTest {
    private val macNames = listOf("Mac OS X", "mac os x", "Darwin/Mac OS X")
    private val otherNames = listOf("Windows 11", "Linux", "FreeBSD")

    @Test
    fun `the lights overlay content on macOS in a windowed window`() {
        macNames.forEach { os ->
            assertTrue(WindowTopChrome.lightsOverlayContent(os, isFullscreen = false), os)
        }
    }

    @Test
    fun `no other platform draws window buttons over app content`() {
        // Windows and Linux keep a real OS title bar; nothing needs reserving inside the window.
        otherNames.forEach { os ->
            assertFalse(WindowTopChrome.lightsOverlayContent(os, isFullscreen = false), os)
            assertEquals(0.dp, WindowTopChrome.leadingInset(os, isFullscreen = false), os)
        }
    }

    @Test
    fun `macOS fullscreen takes the buttons away, so nothing is reserved`() {
        assertFalse(WindowTopChrome.lightsOverlayContent("Mac OS X", isFullscreen = true))
        assertEquals(0.dp, WindowTopChrome.leadingInset("Mac OS X", isFullscreen = true))
    }

    @Test
    fun `a windowed macOS window insets its topmost row`() {
        assertEquals(
            WindowTopChrome.LeadingInset,
            WindowTopChrome.leadingInset("Mac OS X", isFullscreen = false),
        )
    }

    @Test
    fun `the inset never disappears while the buttons are there`() {
        // The fullscreen flag comes from Compose's window placement, which a click on the green
        // button does not update. This pins the direction that staleness may take: a stale `false`
        // keeps the inset and wastes width in a fullscreen window, which is survivable. A stale
        // `true` would drop the inset with the buttons still on screen, which is not.
        val everyCombination =
            (macNames + otherNames).flatMap { os ->
                listOf(os to true, os to false)
            }

        everyCombination.forEach { (os, fullscreen) ->
            val overlaid = WindowTopChrome.lightsOverlayContent(os, fullscreen)
            val inset = WindowTopChrome.leadingInset(os, fullscreen)
            assertTrue(
                !overlaid || inset > 0.dp,
                "the buttons overlay content for os=$os fullscreen=$fullscreen but nothing is reserved",
            )
        }
    }

    @Test
    fun `the strip stands in only when no window-wide bar is on top`() {
        assertFalse(
            WindowTopChrome.needsReservationStrip("Mac OS X", isFullscreen = false, topBarOnScreen = true),
            "the top bar carries the inset itself, so a strip on top of it would be 27dp wasted",
        )
        assertTrue(
            WindowTopChrome.needsReservationStrip("Mac OS X", isFullscreen = false, topBarOnScreen = false),
        )
    }

    @Test
    fun `no strip is ever needed where the lights do not overlay content`() {
        listOf(true, false).forEach { topBarOnScreen ->
            assertFalse(
                WindowTopChrome.needsReservationStrip("Windows 11", false, topBarOnScreen),
                "topBarOnScreen=$topBarOnScreen",
            )
            assertFalse(
                WindowTopChrome.needsReservationStrip("Mac OS X", true, topBarOnScreen),
                "topBarOnScreen=$topBarOnScreen",
            )
        }
    }

    @Test
    fun `the strip is tall enough for the buttons`() {
        assertTrue(ChromeDimens.Comfortable.trafficLightStripHeight >= ChromeDimens.MIN_TRAFFIC_LIGHT_STRIP)
        assertTrue(ChromeDimens.Compact.trafficLightStripHeight >= ChromeDimens.MIN_TRAFFIC_LIGHT_STRIP)
        assertTrue(ChromeDimens.Spacious.trafficLightStripHeight >= ChromeDimens.MIN_TRAFFIC_LIGHT_STRIP)
    }

    @Test
    fun `the top bar is always tall enough to host the buttons itself`() {
        // The top bar replaces the strip as the buttons' home at every density, so if any density
        // made it shorter than the buttons need, they would clip on a normal launch.
        ChromeDensity.entries.forEach { density ->
            val dimens = ChromeDimens.of(density)
            assertTrue(
                dimens.topBarHeight >= ChromeDimens.MIN_TRAFFIC_LIGHT_STRIP,
                "density $density leaves the top bar shorter than the traffic lights",
            )
        }
    }
}
