package ai.rever.boss.layout

import ai.rever.boss.window.TabBarPosition
import ai.rever.boss.window.WindowAppearanceSettings
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins which column keeps clear of the macOS traffic lights.
 *
 * The window sets `apple.awt.fullWindowContent`, so the buttons are drawn over the content. The
 * title row used to hold them, at the cost of reserving the window's whole width to protect one
 * 78dp corner; now the clearance goes on whichever column is leftmost.
 *
 * Every case here is a visible defect if it answers wrongly - the buttons landing on a tab bar's
 * Favorites shelf, or a 28dp gap opening above a column that needed none - and all of them are
 * only visible on macOS, which is not where most of this is developed.
 */
class MacTrafficLightsTest {
    private val bare =
        WindowAppearanceSettings(
            showTitleBar = false,
            showTopBar = false,
            showLeftStrip = false,
            tabBarPosition = TabBarPosition.LEFT,
        )

    @Test
    fun `no inset off macOS`() {
        // Elsewhere the title row is an ordinary bar above the content, not an overlay on it.
        assertEquals(TrafficLightInset.NONE, macTrafficLightInset(bare, isMacOs = false))
    }

    @Test
    fun `no inset while the title row is on`() {
        // The row is exactly what holds them.
        assertEquals(
            TrafficLightInset.NONE,
            macTrafficLightInset(bare.copy(showTitleBar = true), isMacOs = true),
        )
    }

    @Test
    fun `the top bar takes it whenever it is on`() {
        // It spans the full width at y=0, so it is above every column and it is what the lights
        // are drawn over. Missing this put the green button on top of the bar's first control.
        val withTopBar = bare.copy(showTopBar = true)

        assertEquals(TrafficLightInset.TOP_BAR, macTrafficLightInset(withTopBar, isMacOs = true))
    }

    @Test
    fun `the top bar takes it even with both columns on`() {
        // The columns start below the bar, so the box cannot reach them.
        val everything = bare.copy(showTopBar = true, showLeftStrip = true)

        assertEquals(TrafficLightInset.TOP_BAR, macTrafficLightInset(everything, isMacOs = true))
    }

    @Test
    fun `the columns take it when the top bar is off`() {
        assertEquals(
            TrafficLightInset.LEFT_COLUMNS,
            macTrafficLightInset(bare.copy(showLeftStrip = true), isMacOs = true),
            "a strip alone",
        )
        assertEquals(
            TrafficLightInset.LEFT_COLUMNS,
            macTrafficLightInset(bare, isMacOs = true),
            "the vertical tab bar alone",
        )
        assertEquals(
            TrafficLightInset.LEFT_COLUMNS,
            macTrafficLightInset(bare.copy(showLeftStrip = true), isMacOs = true),
            "both - and both are inset, because a 40dp strip is narrower than the 78dp box",
        )
    }

    @Test
    fun `the box is wider than one column, which is why both are inset`() {
        // The reason LEFT_COLUMNS is one answer rather than "whichever column is leftmost". A
        // strip is 40dp and the lights are 78dp, so insetting only the strip leaves the second
        // half of the box over the tab bar beside it.
        assertTrue(
            TRAFFIC_LIGHT_WIDTH > 40.dp,
            "if a strip ever gets wider than the light box, this rule can be narrowed again",
        )
    }

    @Test
    fun `with no bar and nothing down the left the content is under them`() {
        // No top bar, no strip, tabs across the top: nothing to inset, so the caller keeps the
        // full-width row. Padding the content would cost the same height across the same width.
        val topTabs = bare.copy(tabBarPosition = TabBarPosition.TOP)

        assertEquals(TrafficLightInset.CONTENT, macTrafficLightInset(topTabs, isMacOs = true))
    }

    @Test
    fun `each answer produces exactly one kind of inset`() {
        // The two insets are different axes - a column takes height, the bar takes width - so a
        // case that produced both, or neither where one is needed, is a layout bug either way.
        assertEquals(TRAFFIC_LIGHT_HEIGHT, TrafficLightInset.LEFT_COLUMNS.columnInset())
        assertEquals(0.dp, TrafficLightInset.LEFT_COLUMNS.barStartInset())
        assertEquals(TRAFFIC_LIGHT_WIDTH, TrafficLightInset.TOP_BAR.barStartInset())
        assertEquals(0.dp, TrafficLightInset.TOP_BAR.columnInset())
        assertEquals(0.dp, TrafficLightInset.NONE.columnInset())
        assertEquals(0.dp, TrafficLightInset.NONE.barStartInset())
    }

    @Test
    fun `the shipped defaults inset the left columns`() {
        // The default configuration on every platform: no title row, no top bar, no strips, tabs
        // down the left. This is the case the whole change is for.
        assertEquals(
            TrafficLightInset.LEFT_COLUMNS,
            macTrafficLightInset(WindowAppearanceSettings(), isMacOs = true),
        )
    }
}
