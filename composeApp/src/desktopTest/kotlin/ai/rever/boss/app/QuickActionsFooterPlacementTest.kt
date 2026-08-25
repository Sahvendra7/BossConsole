package ai.rever.boss.app

import ai.rever.boss.focusmode.FocusModeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the precedence between the three renderings of Settings / Search / Sign Out.
 *
 * The order is rail, then the tab bar's footer, then the floating cluster, and each step is a
 * choice rather than an accident. The floating cluster is a native always-on-top window with no
 * click-through, so it is the most intrusive of the three and goes last; the rail was already
 * preferred over it and stays preferred; the tab bar's foot is chrome the app draws anyway.
 *
 * Getting this backwards does not crash - it puts a dead click region over the content area in a
 * window that had a perfectly good place to put four icons.
 */
class QuickActionsFooterPlacementTest {
    private val focusOff = FocusModeSettings()

    private fun placement(
        rightStripHidden: Boolean,
        verticalTabBar: Boolean,
    ) = focusQuickActionsPlacement(
        settings = focusOff,
        topBarHidden = true,
        rightStripHidden = rightStripHidden,
        showTopBar = false,
        verticalTabBar = verticalTabBar,
    )

    @Test
    fun `the rail still wins when there is a rail`() {
        assertEquals(
            FocusQuickActionsPlacement.RIGHT_RAIL,
            placement(rightStripHidden = false, verticalTabBar = true),
            "the vertical tab bar displaces the floating cluster, not the rail",
        )
    }

    @Test
    fun `the tab bar's foot takes the floating cluster's place`() {
        assertEquals(
            FocusQuickActionsPlacement.TAB_BAR_FOOTER,
            placement(rightStripHidden = true, verticalTabBar = true),
        )
    }

    @Test
    fun `without either it floats, as before`() {
        assertEquals(
            FocusQuickActionsPlacement.FLOATING,
            placement(rightStripHidden = true, verticalTabBar = false),
        )
    }

    @Test
    fun `the top bar being up still beats all three`() {
        assertEquals(
            FocusQuickActionsPlacement.NONE,
            focusQuickActionsPlacement(
                settings = focusOff,
                topBarHidden = false,
                rightStripHidden = true,
                showTopBar = true,
                verticalTabBar = true,
            ),
            "the top bar owns these three whenever it is on screen",
        )
    }

    @Test
    fun `the footer list is empty for every other placement`() {
        // What lets the bar call it unconditionally and render nothing.
        FocusQuickActionsPlacement.entries
            .filter { it != FocusQuickActionsPlacement.TAB_BAR_FOOTER }
            .forEach { placement ->
                assertTrue(
                    focusQuickActionsFooter(placement, {}, {}, {}).isEmpty(),
                    "$placement should contribute no footer actions",
                )
            }
        assertEquals(
            FOCUS_QUICK_ACTION_COUNT,
            focusQuickActionsFooter(FocusQuickActionsPlacement.TAB_BAR_FOOTER, {}, {}, {}).size,
        )
    }

    @Test
    fun `the launcher adds a fourth action without disturbing the reserve`() {
        // The rail's reserve is FOCUS_QUICK_ACTION_COUNT rows, and it stays at three because the
        // launcher can never join the rail flavour - see PluginLauncherPlacementTest.
        val withLauncher =
            focusQuickActionsFooter(
                FocusQuickActionsPlacement.TAB_BAR_FOOTER,
                {},
                {},
                {},
                pluginLauncher = {},
            )

        assertEquals(FOCUS_QUICK_ACTION_COUNT + 1, withLauncher.size)
        val rail = focusQuickActionsRail(FocusQuickActionsPlacement.RIGHT_RAIL, {}, {}, {})
        assertEquals(FOCUS_QUICK_ACTION_COUNT, rail.size)
    }

    @Test
    fun `focus mode clearing the right edge also reaches the footer`() {
        // hides(RIGHT) is the other way there is no rail. It should land on the tab bar's foot
        // rather than the floating cluster, exactly as a switched-off strip does.
        val hidesRight = FocusModeSettings(enabled = true, hideRightSidebar = true)

        assertEquals(
            FocusQuickActionsPlacement.TAB_BAR_FOOTER,
            focusQuickActionsPlacement(
                settings = hidesRight,
                topBarHidden = true,
                rightStripHidden = false,
                showTopBar = false,
                verticalTabBar = true,
            ),
        )
    }
}
