package ai.rever.boss.layout

import ai.rever.boss.components.window_panel.components.main_window_panels.NEW_TAB_BUTTON_SIZE
import ai.rever.boss.focusmode.FocusModeSettings
import ai.rever.boss.window.WindowAppearanceSettings
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins what the window chrome costs a browser tab.
 *
 * The point of these numbers being in a test is that issue #239 is about a budget nobody was
 * tracking: bar heights were literals in seven different files, so a bar could be added or grown
 * without anyone noticing the page had lost another 30dp. Growing the chrome now means changing an
 * assertion here and saying so.
 *
 * The reference window is a 13" MacBook Air at its default scaled resolution: 1470 x 956 pt, of
 * which ~931 pt is window height once the macOS menu bar is subtracted.
 */
class ChromeMetricsTest {
    private val airHeight = 931.dp
    private val airWidth = 1470.dp

    private val macOs = "Mac OS X"
    private val windowsOs = "Windows 11"

    private val comfortable = ChromeDimens.Comfortable

    /** Border ring plus content inset, off both axes, in every configuration. */
    private val ring = comfortable.panelBorderThickness * 2

    /** Every bar on, focus mode off - what a fresh install draws. */
    private val defaults = WindowAppearanceSettings()

    private val focusOff = FocusModeSettings(enabled = false)

    private val topBarOnlyFocusMode =
        FocusModeSettings(
            enabled = true,
            hideTopBar = true,
            hideLeftSidebar = false,
            hideRightSidebar = false,
            hideBottomBar = false,
        )

    /** Everything a preference can switch off, switched off. */
    private val leanest =
        WindowAppearanceSettings(
            showTopBar = false,
            showBottomBar = false,
            showLeftStrip = false,
            showRightStrip = false,
        )

    private fun budget(
        appearance: WindowAppearanceSettings = defaults,
        focusMode: FocusModeSettings = focusOff,
        dimens: ChromeDimens = comfortable,
        osName: String = macOs,
        isFullscreen: Boolean = false,
    ) = ChromeMetrics.mainPanelBudget(appearance, focusMode, dimens, osName, isFullscreen)

    @Test
    fun `shipped defaults cost 119dp and leave the page 87 percent`() {
        val shipped = budget()

        // 41 top (40+1) + 43 tab (42+1) + 31 bottom (30+1) + 4 ring. No title row: the top bar is
        // the window's topmost row and carries the traffic-light inset itself.
        assertEquals(119.dp, shipped.vertical)
        // 41 per strip (40 plus its VDivider) + 4 ring.
        assertEquals(86.dp, shipped.horizontal)
        assertEquals(0.872f, shipped.verticalFractionOf(airHeight), absoluteTolerance = 0.001f)
        assertEquals(0.941f, shipped.horizontalFractionOf(airWidth), absoluteTolerance = 0.001f)
    }

    @Test
    fun `the defaults now cost the same on every platform`() {
        // Before the rows were merged, macOS paid 27dp more than Windows and Linux for a row whose
        // only content was a label. Platform parity at the defaults is the whole point of PR B.
        assertEquals(budget(osName = windowsOs), budget(osName = macOs))
    }

    @Test
    fun `showing the app name costs nothing`() {
        // It is a label inside the top bar now, not a row of its own.
        assertEquals(
            budget(appearance = WindowAppearanceSettings(showTitleBar = false)),
            budget(appearance = WindowAppearanceSettings(showTitleBar = true)),
        )
    }

    @Test
    fun `the panel border ring is charged in every configuration`() {
        // BossMainPanel draws it whether or not the panel is active, and no preference switches it
        // off, so it is the one part of the budget that is always present. Omitting it understated
        // every figure here by 4dp on each axis until the review of #240 caught it.
        val lean = budget(appearance = leanest, osName = windowsOs)

        assertTrue(lean.vertical >= ring)
        assertEquals(ring, lean.horizontal)
    }

    @Test
    fun `compact density is worth 20dp of height over comfortable`() {
        val roomy = budget(dimens = ChromeDimens.Comfortable)
        val tight = budget(dimens = ChromeDimens.Compact)

        assertEquals(20.dp, roomy.vertical - tight.vertical)
        // 4 off each strip, 8 across both. The ring does not scale with density.
        assertEquals(8.dp, roomy.horizontal - tight.horizontal)
    }

    @Test
    fun `clearing the top bar on macOS swaps it for the traffic-light strip`() {
        val cleared = budget(focusMode = topBarOnlyFocusMode)

        // 27 strip (26+1) instead of 41 top bar, so clearing the top bar on macOS is worth 14dp,
        // not the 41 it is worth elsewhere. The buttons still need their room.
        assertEquals(27.dp + 43.dp + 31.dp + ring, cleared.vertical)
    }

    @Test
    fun `clearing the top bar off macOS costs no reservation at all`() {
        val cleared = budget(focusMode = topBarOnlyFocusMode, osName = windowsOs)

        assertEquals(43.dp + 31.dp + ring, cleared.vertical)
    }

    @Test
    fun `fullscreen drops the reservation because macOS takes the buttons away`() {
        val windowed = budget(focusMode = topBarOnlyFocusMode, isFullscreen = false)
        val fullscreen = budget(focusMode = topBarOnlyFocusMode, isFullscreen = true)

        assertEquals(27.dp, windowed.vertical - fullscreen.vertical)
    }

    @Test
    fun `focus mode enabled but hiding nothing changes nothing`() {
        val idle =
            FocusModeSettings(
                enabled = true,
                hideTopBar = false,
                hideLeftSidebar = false,
                hideRightSidebar = false,
                hideBottomBar = false,
            )

        assertEquals(budget(), budget(focusMode = idle))
    }

    @Test
    fun `the leanest reachable state is the tab bar plus the buttons' room`() {
        val lean = budget(appearance = leanest)

        // The tab bar has no switch, and on macOS something must hold the traffic lights.
        assertEquals(27.dp + 43.dp + ring, lean.vertical)
        assertEquals(ring, lean.horizontal)
        assertTrue(lean.verticalFractionOf(airHeight) > 0.92f)
    }

    @Test
    fun `off macOS the leanest state is the tab bar alone`() {
        val lean = budget(appearance = leanest, osName = windowsOs)

        assertEquals(43.dp + ring, lean.vertical)
        assertTrue(lean.verticalFractionOf(airHeight) > 0.94f)
    }

    @Test
    fun `the tab bar is never free`() {
        // Whatever else is switched off, a tabbed browser keeps its tab row.
        ChromeDensity.entries.forEach { density ->
            val dimens = ChromeDimens.of(density)
            val lean = budget(appearance = leanest, dimens = dimens, osName = windowsOs)
            assertEquals(
                dimens.tabBarHeight + dimens.dividerThickness + dimens.panelBorderThickness * 2,
                lean.vertical,
                "density $density",
            )
        }
    }

    @Test
    fun `a degenerate window size reports zero rather than dividing by zero`() {
        val shipped = budget()

        assertEquals(0f, shipped.verticalFractionOf(0.dp))
        assertEquals(0f, shipped.horizontalFractionOf((-10).dp))
    }

    @Test
    fun `chrome taller than the window clamps to zero rather than going negative`() {
        assertEquals(0f, budget().verticalFractionOf(100.dp))
    }

    @Test
    fun `comfortable reproduces the literals it replaced`() {
        // The aggregate above catches a regression but reports a total; this names the field that
        // moved, and is the most direct statement of PR A's no-op claim.
        assertEquals(
            ChromeDimens(
                trafficLightStripHeight = 26.dp,
                topBarHeight = 40.dp,
                tabBarHeight = 42.dp,
                bottomBarHeight = 30.dp,
                stripWidth = 40.dp,
                panelTopBarHeight = 28.dp,
            ),
            ChromeDimens.Comfortable,
        )
    }

    @Test
    fun `the tab bar floor really clears the new-tab button`() {
        // MIN_TAB_BAR's KDoc derives itself from NEW_TAB_BUTTON_SIZE in prose. Bump that constant
        // and the prose is quietly wrong with a green suite, so pin the relationship instead, the
        // same discipline SidebarBottomActionsLayoutTest applies to the rail metrics.
        assertTrue(
            ChromeDimens.MIN_TAB_BAR >= NEW_TAB_BUTTON_SIZE + 4.dp,
            "MIN_TAB_BAR ${ChromeDimens.MIN_TAB_BAR} leaves the $NEW_TAB_BUTTON_SIZE new-tab " +
                "button less than 2dp a side",
        )
    }

    @Test
    fun `every compact metric respects the floor its content imposes`() {
        val compact = ChromeDimens.Compact

        assertTrue(compact.trafficLightStripHeight >= ChromeDimens.MIN_TRAFFIC_LIGHT_STRIP)
        assertTrue(compact.tabBarHeight >= ChromeDimens.MIN_TAB_BAR)
        assertTrue(compact.stripWidth >= ChromeDimens.MIN_STRIP_WIDTH)
    }

    @Test
    fun `density ordering is monotonic so compact is never the roomiest`() {
        val compact = ChromeDimens.Compact
        val roomy = ChromeDimens.Comfortable
        val spacious = ChromeDimens.Spacious

        assertTrue(compact.topBarHeight < roomy.topBarHeight)
        assertTrue(roomy.topBarHeight < spacious.topBarHeight)
        assertTrue(compact.tabBarHeight < roomy.tabBarHeight)
        assertTrue(roomy.tabBarHeight < spacious.tabBarHeight)
        assertTrue(compact.bottomBarHeight < roomy.bottomBarHeight)
        assertTrue(roomy.bottomBarHeight < spacious.bottomBarHeight)
        assertTrue(compact.stripWidth < roomy.stripWidth)
        assertTrue(roomy.stripWidth < spacious.stripWidth)
    }

    @Test
    fun `the ring does not scale with density`() {
        // It answers to a rendering artifact rather than to taste: 1dp is invisible, 3dp is a frame.
        ChromeDensity.entries.forEach { density ->
            assertEquals(
                comfortable.panelBorderThickness,
                ChromeDimens.of(density).panelBorderThickness,
                "density $density",
            )
        }
    }
}
