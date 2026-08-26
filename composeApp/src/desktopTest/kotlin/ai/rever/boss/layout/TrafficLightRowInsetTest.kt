package ai.rever.boss.layout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the clearance for a ROW that has to work out for itself whether it is in the corner.
 *
 * The columns down the left edge are handed their offset, because the scaffold composes them. The
 * row of tab chips at the top of a pane is not: it is inside the split tree, and whether the lights
 * reach it depends on which pane it is in and how the window happens to be split.
 *
 * The case that made this necessary: a collapsed tab bar is a 40dp rail, the light box is 78dp, so
 * the first 38dp of the leftmost pane's strip is underneath the buttons - which is where its first
 * chip was. The old rule never showed it because it answered that configuration with a full-width
 * title row instead, pushing everything down.
 */
class TrafficLightRowInsetTest {
    @get:Rule
    val rule = createComposeRule()

    /** [trafficLightStartInset] under a given answer, for a row measured at [origin]. */
    private fun insetFor(
        answer: TrafficLightInset,
        origin: Offset?,
    ): Dp? {
        var result: Dp? = null
        var read = false
        rule.setContent {
            CompositionLocalProvider(LocalTrafficLightInset provides answer) {
                result = trafficLightStartInset(origin)
                read = true
            }
        }
        rule.waitForIdle()
        assert(read) { "the composable never ran" }
        return result
    }

    /** Window px for a dp value, which is what a measured origin is in. */
    private fun px(value: Dp): Float {
        var result = 0f
        rule.setContent { result = with(LocalDensity.current) { value.toPx() } }
        rule.waitForIdle()
        return result
    }

    @Test
    fun `a row behind a collapsed rail clears the rest of the box`() {
        // The reported regression, in numbers: a 40dp rail leaves 38dp of the row underneath.
        val inset = insetFor(TrafficLightInset.LEFT_COLUMNS, Offset(px(40.dp), 0f))
        assertEquals(TRAFFIC_LIGHT_WIDTH - 40.dp, inset)
    }

    @Test
    fun `a row starting past the box needs nothing`() {
        // A pane one split to the right: same row, same window, no lights over it.
        assertEquals(0.dp, insetFor(TrafficLightInset.LEFT_COLUMNS, Offset(px(400.dp), 0f)))
        assertEquals(0.dp, insetFor(TrafficLightInset.LEFT_COLUMNS, Offset(px(TRAFFIC_LIGHT_WIDTH), 0f)))
    }

    @Test
    fun `a row below the box needs nothing, whatever its x`() {
        // This is what keeps a browser toolbar, or any second row, from indenting itself - and
        // what makes a top bar or a title row above the strip take care of itself.
        assertEquals(0.dp, insetFor(TrafficLightInset.LEFT_COLUMNS, Offset(0f, px(TRAFFIC_LIGHT_HEIGHT))))
        assertEquals(0.dp, insetFor(TrafficLightInset.LEFT_COLUMNS, Offset(0f, px(200.dp))))
    }

    @Test
    fun `a row at the very corner clears the whole box`() {
        assertEquals(TRAFFIC_LIGHT_WIDTH, insetFor(TrafficLightInset.LEFT_COLUMNS, Offset.Zero))
    }

    @Test
    fun `no answer but LEFT_COLUMNS asks a row for anything`() {
        // TOP_BAR and BANNER mean something above this row already holds the lights, and NONE
        // means there is nothing to hold.
        listOf(TrafficLightInset.NONE, TrafficLightInset.TOP_BAR, TrafficLightInset.BANNER).forEach {
            assertEquals(0.dp, insetFor(it, Offset.Zero), "$it must not indent a row")
        }
    }

    @Test
    fun `an unmeasured row reserves nothing rather than guessing`() {
        // Null, not zero: the caller can tell "not yet known" from "known to be zero". Reserving
        // the full width before measuring would make every strip in the window snap left on its
        // second frame.
        assertNull(insetFor(TrafficLightInset.LEFT_COLUMNS, null))
    }
}
