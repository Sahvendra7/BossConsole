package ai.rever.boss.layout

import ai.rever.boss.window.TabBarPosition
import ai.rever.boss.window.WindowAppearanceSettings
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `the left strip takes it when there is one`() {
        assertEquals(
            TrafficLightInset.LEFT_STRIP,
            macTrafficLightInset(bare.copy(showLeftStrip = true), isMacOs = true),
        )
    }

    @Test
    fun `the strip wins over the tab bar, because it is further left`() {
        // Both present: the strip is the outer column, so it is what the lights land on. Insetting
        // the tab bar instead would leave the buttons over the strip's top icon.
        val both = bare.copy(showLeftStrip = true, tabBarPosition = TabBarPosition.LEFT)

        assertEquals(TrafficLightInset.LEFT_STRIP, macTrafficLightInset(both, isMacOs = true))
    }

    @Test
    fun `the vertical tab bar takes it when no strip is on`() {
        assertEquals(
            TrafficLightInset.VERTICAL_TAB_BAR,
            macTrafficLightInset(bare, isMacOs = true),
        )
    }

    @Test
    fun `with nothing down the left the content is under them`() {
        // No strip and tabs across the top: there is no column to inset, so the caller keeps the
        // full-width row. Padding the content would cost the same height across the same width.
        val topTabs = bare.copy(tabBarPosition = TabBarPosition.TOP)

        assertEquals(TrafficLightInset.CONTENT, macTrafficLightInset(topTabs, isMacOs = true))
    }

    @Test
    fun `the shipped defaults inset the tab bar`() {
        // The default configuration on every platform: no title row, no strips, tabs down the
        // left. This is the case the whole change is for.
        assertEquals(
            TrafficLightInset.VERTICAL_TAB_BAR,
            macTrafficLightInset(WindowAppearanceSettings(), isMacOs = true),
        )
    }
}
