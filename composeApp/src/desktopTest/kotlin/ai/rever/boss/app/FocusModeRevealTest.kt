package ai.rever.boss.app

import ai.rever.boss.focusmode.FocusModeEdge
import ai.rever.boss.focusmode.FocusModeSettings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The per-edge switches, exercised through the composable that actually drives the
 * chrome rather than through the settings object alone.
 *
 * `FocusModeSettingsTest` proves what the Windows defaults *say*; this proves the
 * bars follow them - the sidebars stay up on Windows, and no hover strip is laid
 * over an edge that was never hidden. Neither could be seen from the data class,
 * because `rememberFocusModeReveal` writes `show*` from four `LaunchedEffect`s
 * whose keys are the ones this change rewrote.
 */
class FocusModeRevealTest {
    @get:Rule
    val rule = createComposeRule()

    /**
     * Mount the reveal for [settings] and settle it. The grace period before a bar
     * hides is 2s, so the clock has to pass it or every edge would still read
     * "shown" and the test would pass for the wrong reason.
     */
    private fun revealFor(settings: FocusModeSettings): FocusModeRevealState {
        lateinit var state: FocusModeRevealState
        rule.setContent {
            state = rememberFocusModeReveal(settings)
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(GRACE_PERIOD_MS)
        rule.waitForIdle()
        return state
    }

    @Test
    fun `windows defaults keep both sidebars while clearing top and bottom`() {
        val state = revealFor(FocusModeSettings.defaultsFor("Windows 11").copy(enabled = true))

        assertTrue(state.showLeftSidebar, "left sidebar must stay visible on Windows")
        assertTrue(state.showRightSidebar, "right sidebar must stay visible on Windows")
        assertFalse(state.showTopBar, "focus mode must still clear the top bar on Windows")
        assertFalse(state.showBottomBar, "focus mode must still clear the bottom bar on Windows")
    }

    @Test
    fun `other platforms clear all four`() {
        val state = revealFor(FocusModeSettings.defaultsFor("Mac OS X").copy(enabled = true))

        assertFalse(state.showTopBar)
        assertFalse(state.showLeftSidebar)
        assertFalse(state.showRightSidebar)
        assertFalse(state.showBottomBar)
    }

    @Test
    fun `focus mode off shows everything`() {
        val state = revealFor(FocusModeSettings.defaultsFor("Mac OS X").copy(enabled = false))

        assertTrue(state.showTopBar)
        assertTrue(state.showLeftSidebar)
        assertTrue(state.showRightSidebar)
        assertTrue(state.showBottomBar)
    }

    /** Turning an edge's switch off mid-session brings that bar back, and only it. */
    @Test
    fun `switching an edge off returns its bar`() {
        var settings by mutableStateOf(FocusModeSettings.defaultsFor("Linux").copy(enabled = true))
        lateinit var state: FocusModeRevealState
        rule.setContent { state = rememberFocusModeReveal(settings) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(GRACE_PERIOD_MS)
        rule.waitForIdle()
        assertFalse(state.showLeftSidebar, "precondition: Linux starts by clearing the left sidebar")

        settings = settings.copy(hideLeftSidebar = false)
        rule.waitForIdle()

        assertTrue(state.showLeftSidebar)
        assertFalse(state.showRightSidebar, "the other edges are untouched")
        assertFalse(state.showTopBar)
    }

    /**
     * A strip is an invisible band the width of the reveal offset. Laying one over an
     * edge that is never hidden would put dead space over live content - on Windows,
     * over both sidebars, which is where the plugin panels are.
     *
     * Auto-reveal is turned on explicitly here: a Windows install has it off by
     * default, which is a separate switch and the subject of the next test.
     */
    @Test
    fun `strips exist only for edges that are actually hidden`() {
        val settings =
            FocusModeSettings
                .defaultsFor("Windows 11")
                .copy(enabled = true, autoRevealEnabled = true)
        rule.setContent {
            val state = rememberFocusModeReveal(settings)
            Box(modifier = Modifier.fillMaxSize()) {
                FocusModeHoverStrips(state = state, settings = settings, revealOffsetDp = 30.dp)
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(GRACE_PERIOD_MS)
        rule.waitForIdle()

        rule.onNodeWithTag(focusStripTag(FocusModeEdge.TOP)).assertExists()
        rule.onNodeWithTag(focusStripTag(FocusModeEdge.BOTTOM)).assertExists()
        rule.onNodeWithTag(focusStripTag(FocusModeEdge.LEFT)).assertDoesNotExist()
        rule.onNodeWithTag(focusStripTag(FocusModeEdge.RIGHT)).assertDoesNotExist()
    }

    /**
     * With auto-reveal off there is nothing to hover, so no strip is laid out at all.
     *
     * This is the state a stock Windows install is in - `defaultAutoReveal` is false
     * there - so on Windows focus mode clears the top and bottom bars and lays down
     * no strips at all. Toggling focus mode back off is what returns those two bars,
     * and the sidebars never left.
     */
    @Test
    fun `no strips at all when auto-reveal is off`() {
        val settings = FocusModeSettings.defaultsFor("Windows 11").copy(enabled = true)
        assertFalse(settings.autoRevealEnabled, "precondition: Windows starts with hover-reveal off")
        rule.setContent {
            val state = rememberFocusModeReveal(settings)
            Box(modifier = Modifier.fillMaxSize()) {
                FocusModeHoverStrips(state = state, settings = settings, revealOffsetDp = 30.dp)
            }
        }
        rule.waitForIdle()

        for (edge in FocusModeEdge.entries) {
            rule.onNodeWithTag(focusStripTag(edge)).assertDoesNotExist()
        }
    }

    /** Tags are derived, so a renamed edge cannot silently detach a test from its strip. */
    @Test
    fun `every edge has its own strip tag`() {
        val tags = FocusModeEdge.entries.map { focusStripTag(it) }
        assertEquals(tags.size, tags.toSet().size, "duplicate strip tags: $tags")
        assertEquals("focus-strip-left", focusStripTag(FocusModeEdge.LEFT))
    }

    private companion object {
        /** Matches the hide delay in `rememberFocusModeReveal`, plus a frame. */
        const val GRACE_PERIOD_MS = 2_500L
    }
}
