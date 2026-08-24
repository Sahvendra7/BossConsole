package ai.rever.boss.layout

import ai.rever.boss.components.window_panel.components.main_window_panels.NEW_TAB_BUTTON_SIZE
import ai.rever.boss.focusmode.FocusModeSettings
import ai.rever.boss.window.TabBarPosition
import ai.rever.boss.window.TabBarVerticalWidthRange
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

    /**
     * The classic chrome: app name on, all four bars on, tabs across the top, focus mode off.
     *
     * Spelled out rather than taken from WindowAppearanceSettings(). It used to BE the default,
     * and when the default moved to a hidden top bar and a left tab bar these fixtures silently
     * changed meaning underneath ten tests - including one comparing "top" against "left" that
     * was suddenly comparing left against left and still passing its first assertion.
     *
     * What these tests measure is the arithmetic of a configuration, not which configuration
     * ships. The shipped one is asserted once, separately, below.
     */
    private val classic =
        WindowAppearanceSettings(
            showTitleBar = true,
            showTopBar = true,
            tabBarPosition = TabBarPosition.TOP,
        )

    private val focusOff = FocusModeSettings(enabled = false)

    private val topBarOnlyFocusMode =
        FocusModeSettings(
            enabled = true,
            hideTopBar = true,
            hideLeftSidebar = false,
            hideRightSidebar = false,
            hideBottomBar = false,
        )

    /** Everything a preference can switch off, switched off, with the tab row still on top. */
    private val leanest =
        WindowAppearanceSettings(
            showTitleBar = false,
            showTopBar = false,
            showBottomBar = false,
            showLeftStrip = false,
            showRightStrip = false,
            // Explicit, because this is the TOP-position baseline the left-position tests below
            // compare against. Taking it from the default made that comparison vacuous.
            tabBarPosition = TabBarPosition.TOP,
        )

    /**
     * The budget under test, with the platform threaded in.
     *
     * `osName` and `isFullscreen` are parameters rather than ambient because the traffic-light
     * reservation is a macOS-only, windowed-only row: the same appearance settings cost different
     * height on different platforms, and a test that could not say which one it meant would be
     * asserting the host's OS rather than the arithmetic.
     */
    private fun budget(
        appearance: WindowAppearanceSettings = classic,
        focusMode: FocusModeSettings = focusOff,
        dimens: ChromeDimens = comfortable,
        osName: String = macOs,
        isFullscreen: Boolean = false,
    ) = ChromeMetrics.mainPanelBudget(appearance, focusMode, dimens, osName, isFullscreen)

    @Test
    fun `the classic chrome costs 119dp and leaves the page 87 percent`() {
        val classicChrome = budget()

        // 41 top (40+1) + 43 tab (42+1) + 31 bottom (30+1) + 4 ring. No title row: the top bar is
        // the window's topmost row and carries the traffic-light inset itself.
        assertEquals(119.dp, classicChrome.vertical)
        // 41 per strip (40 plus its VDivider) + 4 ring.
        assertEquals(86.dp, classicChrome.horizontal)
        assertEquals(0.872f, classicChrome.verticalFractionOf(airHeight), absoluteTolerance = 0.001f)
        assertEquals(0.941f, classicChrome.horizontalFractionOf(airWidth), absoluteTolerance = 0.001f)
    }

    @Test
    fun `the classic chrome now costs the same on every platform`() {
        // Before the rows were merged, macOS paid 27dp more than Windows and Linux for a row whose
        // only content was a label. Platform parity is the whole point of PR B.
        assertEquals(budget(osName = windowsOs), budget(osName = macOs))
    }

    @Test
    fun `showing the app name costs nothing`() {
        // It is a label inside the top bar now, not a row of its own.
        assertEquals(
            budget(appearance = classic.copy(showTitleBar = false)),
            budget(appearance = classic.copy(showTitleBar = true)),
        )
    }

    @Test
    fun `the shipped defaults spend less height and more width than the classic chrome`() {
        // What a fresh install actually gets: no top bar, tabs down the left. The trade is the
        // point - the row the tab bar occupied leaves the vertical axis and a 200dp column arrives
        // on the horizontal one. The top bar leaving does not buy its whole 41dp back on macOS,
        // because with no window-wide bar on top something still has to hold the traffic lights.
        val shipped = budget(appearance = WindowAppearanceSettings())

        // 27 reservation strip (26+1) + 31 bottom (30+1) + 4 ring. No top bar, and no tab row.
        assertEquals(62.dp, shipped.vertical)
        // 41 per strip (40+1) + 4 ring + 200 bar + 1 divider.
        assertEquals(287.dp, shipped.horizontal)

        assertTrue(shipped.vertical < budget().vertical, "the shipped chrome must cost less height")
        assertTrue(shipped.horizontal > budget().horizontal, "and it pays for that in width")
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
        // Whatever else is switched off, a tabbed browser keeps its tab row. Off macOS, so the
        // reservation strip does not add a second density-dependent term to the sum.
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

    // --- vertical tab bar ---

    /** Everything switchable off, and the tab bar moved to the leading edge. */
    private val leanestVertical = leanest.copy(tabBarPosition = TabBarPosition.LEFT)

    @Test
    fun `a left tab bar is charged to width instead of height`() {
        // Off macOS, so "nothing left on the vertical axis" is exactly true rather than true
        // except for the traffic-light strip. The macOS case is asserted separately below.
        val moved = budget(appearance = leanestVertical, osName = windowsOs)

        // Nothing but the ring left vertically: the tab row moved off that axis entirely.
        assertEquals(ring, moved.vertical)
        assertEquals(
            ring + leanestVertical.tabBarVerticalWidth.dp + comfortable.dividerThickness,
            moved.horizontal,
        )
    }

    @Test
    fun `a left tab bar does not excuse macOS from holding the traffic lights`() {
        // The two features meet here: moving the tab row off the vertical axis empties the top of
        // the window, and on macOS an empty top is exactly when the reservation strip is needed.
        // Charging nothing here would have drawn the window buttons over the first tab.
        val moved = budget(appearance = leanestVertical)

        assertEquals(27.dp + ring, moved.vertical)
    }

    @Test
    fun `moving the tab bar left trades height for width, it does not add both`() {
        // The regression this guards: charging the bar vertically regardless of position, which
        // would leave the page paying for a row that is not there AND for the column that is.
        val top = budget(appearance = leanest, osName = windowsOs)
        val left = budget(appearance = leanestVertical, osName = windowsOs)

        assertTrue(left.vertical < top.vertical, "a left bar must cost less height, not the same")
        assertTrue(left.horizontal > top.horizontal, "a left bar must cost width")
    }

    @Test
    fun `a collapsed left bar costs only a strip`() {
        // The rail is deliberately the same width as the window's own icon strips, so a collapsed
        // tab bar costs exactly what adding one more strip would.
        val collapsed = leanestVertical.copy(tabBarCollapsed = true)
        val moved = budget(appearance = collapsed, osName = windowsOs)

        assertEquals(ring + comfortable.stripWidth + comfortable.dividerThickness, moved.horizontal)
    }

    @Test
    fun `an out-of-range width cannot be charged`() {
        // The setting is decoded from a file, and a budget is not the place to discover that.
        val absurd = leanestVertical.copy(tabBarVerticalWidth = 5000f)
        val moved = budget(appearance = absurd, osName = windowsOs)

        assertEquals(
            ring + TabBarVerticalWidthRange.endInclusive.dp + comfortable.dividerThickness,
            moved.horizontal,
        )
    }

    @Test
    fun `the top position is unchanged by any of this`() {
        // The classic chrome must cost exactly what it did before the bar could move.
        val classicChrome = budget()
        assertEquals(119.dp, classicChrome.vertical)
        assertEquals(86.dp, classicChrome.horizontal)
    }

    @Test
    fun `a degenerate window size reports zero rather than dividing by zero`() {
        val classicChrome = budget()

        assertEquals(0f, classicChrome.verticalFractionOf(0.dp))
        assertEquals(0f, classicChrome.horizontalFractionOf((-10).dp))
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
