package ai.rever.boss.layout

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

    /** macOS defaults: title bar on, all four bars on, focus mode off. */
    private val macDefaults = WindowAppearanceSettings(showTitleBar = true)

    /** Windows/Linux defaults, which differ only in the title bar. */
    private val nonMacDefaults = WindowAppearanceSettings(showTitleBar = false)

    private val focusOff = FocusModeSettings(enabled = false)

    @Test
    fun `shipped macOS defaults cost 142dp and leave the page 84 percent`() {
        val budget = ChromeMetrics.mainPanelBudget(macDefaults, focusOff)

        // 27 title (26+1) + 41 top (40+1) + 43 tab (42+1) + 31 bottom (30+1)
        assertEquals(142.dp, budget.vertical)
        assertEquals(80.dp, budget.horizontal)
        assertEquals(0.847f, budget.verticalFractionOf(airHeight), absoluteTolerance = 0.001f)
        assertEquals(0.946f, budget.horizontalFractionOf(airWidth), absoluteTolerance = 0.001f)
    }

    @Test
    fun `Windows and Linux defaults save the title row`() {
        val budget = ChromeMetrics.mainPanelBudget(nonMacDefaults, focusOff)

        assertEquals(115.dp, budget.vertical)
        assertEquals(80.dp, budget.horizontal)
    }

    @Test
    fun `compact density is worth 20dp of height over comfortable`() {
        val comfortable = ChromeMetrics.mainPanelBudget(macDefaults, focusOff, ChromeDimens.Comfortable)
        val compact = ChromeMetrics.mainPanelBudget(macDefaults, focusOff, ChromeDimens.Compact)

        assertEquals(20.dp, comfortable.vertical - compact.vertical)
        // 8 off each strip.
        assertEquals(8.dp, comfortable.horizontal - compact.horizontal)
    }

    @Test
    fun `focus mode clearing every edge leaves only the tab bar`() {
        val focusOn =
            FocusModeSettings(
                enabled = true,
                hideTopBar = true,
                hideLeftSidebar = true,
                hideRightSidebar = true,
                hideBottomBar = true,
            )

        val budget = ChromeMetrics.mainPanelBudget(macDefaults, focusOn)

        // Title row survives: it answers to the appearance preference, not to focus mode.
        assertEquals(27.dp + 43.dp, budget.vertical)
        assertEquals(0.dp, budget.horizontal)
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

        assertEquals(
            ChromeMetrics.mainPanelBudget(macDefaults, focusOff),
            ChromeMetrics.mainPanelBudget(macDefaults, idle),
        )
    }

    @Test
    fun `a hidden bar costs nothing even with focus mode off`() {
        val leanest =
            WindowAppearanceSettings(
                showTitleBar = false,
                showTopBar = false,
                showBottomBar = false,
                showLeftStrip = false,
                showRightStrip = false,
            )

        val budget = ChromeMetrics.mainPanelBudget(leanest, focusOff)

        // The tab bar has no switch, so this is the floor the current architecture can reach.
        assertEquals(43.dp, budget.vertical)
        assertEquals(0.dp, budget.horizontal)
        assertTrue(budget.verticalFractionOf(airHeight) > 0.95f)
    }

    @Test
    fun `the tab bar is never free`() {
        // Whatever else is switched off, a tabbed browser keeps its tab row.
        ChromeDensity.entries.forEach { density ->
            val budget =
                ChromeMetrics.mainPanelBudget(
                    WindowAppearanceSettings(
                        showTitleBar = false,
                        showTopBar = false,
                        showBottomBar = false,
                        showLeftStrip = false,
                        showRightStrip = false,
                    ),
                    focusOff,
                    ChromeDimens.of(density),
                )
            assertEquals(ChromeDimens.of(density).tabBarHeight + 1.dp, budget.vertical, "density $density")
        }
    }

    @Test
    fun `a degenerate window size reports zero rather than dividing by zero`() {
        val budget = ChromeMetrics.mainPanelBudget(macDefaults, focusOff)

        assertEquals(0f, budget.verticalFractionOf(0.dp))
        assertEquals(0f, budget.horizontalFractionOf((-10).dp))
    }

    @Test
    fun `chrome taller than the window clamps to zero rather than going negative`() {
        val budget = ChromeMetrics.mainPanelBudget(macDefaults, focusOff)

        assertEquals(0f, budget.verticalFractionOf(100.dp))
    }

    @Test
    fun `every compact metric respects the floor its content imposes`() {
        val compact = ChromeDimens.Compact

        assertTrue(compact.titleBarHeight >= ChromeDimens.MIN_TITLE_BAR)
        assertTrue(compact.tabBarHeight >= ChromeDimens.MIN_TAB_BAR)
        assertTrue(compact.stripWidth >= ChromeDimens.MIN_STRIP_WIDTH)
    }

    @Test
    fun `density ordering is monotonic so compact is never the roomiest`() {
        val compact = ChromeDimens.Compact
        val comfortable = ChromeDimens.Comfortable
        val spacious = ChromeDimens.Spacious

        assertTrue(compact.topBarHeight < comfortable.topBarHeight)
        assertTrue(comfortable.topBarHeight < spacious.topBarHeight)
        assertTrue(compact.tabBarHeight < comfortable.tabBarHeight)
        assertTrue(comfortable.tabBarHeight < spacious.tabBarHeight)
        assertTrue(compact.bottomBarHeight < comfortable.bottomBarHeight)
        assertTrue(comfortable.bottomBarHeight < spacious.bottomBarHeight)
        assertTrue(compact.stripWidth < comfortable.stripWidth)
        assertTrue(comfortable.stripWidth < spacious.stripWidth)
    }
}
