package ai.rever.boss.plugin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What [BossAlertCard] does when it has less room than it wants.
 *
 * Measured against the card rather than the scene throughout, and against a constrained parent
 * rather than through [BossAlertDialog] — the dialog routes to a platform window that does not
 * inherit its composer's size, so "less room than it wants" is not a state reachable through it.
 * That is why the card is `internal`.
 */
class BossAlertCardLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private val actionLabel = "Don't allow"

    /**
     * A body that wants more height than the frame has.
     *
     * Lines of text rather than `Modifier.height(2_000.dp)`: a size modifier is coerced into the
     * incoming constraints, so a "2000dp" box in a 300dp frame is simply 300dp and nothing
     * overflows — the first version of this test measured that and proved nothing. Text reports the
     * height its lines actually need.
     */
    @Composable
    private fun TallBody() = Column { repeat(BODY_LINES) { Text(bodyLine(it)) } }

    private fun setCard(
        width: Dp,
        height: Dp,
        body: @Composable () -> Unit,
    ) {
        rule.setContent {
            BossTheme {
                // A short viewport is the whole point, so the frame is sized rather than filled.
                // CARD is tagged separately and is what assertions compare against: the card is
                // inset from the frame and centred in it, so an action row hanging below the card's
                // own edge can still sit inside the frame.
                Box(Modifier.size(width, height).testTag(FRAME)) {
                    Box(Modifier.testTag(CARD)) {
                        BossAlertCard(
                            buttons = { TextButton(onClick = {}) { Text(actionLabel) } },
                            title = { Text("Allow this?") },
                            text = body,
                        )
                    }
                }
            }
        }
    }

    private fun boundsOf(label: String): DpRect = rule.onNodeWithText(label).getUnclippedBoundsInRoot()

    private fun cardBounds(): DpRect = rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()

    private fun heightOf(label: String): Dp = boundsOf(label).run { bottom - top }

    @Test
    fun `the actions keep their height when the body is taller than the window`() {
        // The defect, measured: a Column offers each non-weighted child what the previous ones
        // left, so the body took what it wanted and the actions — measured last — were offered
        // nothing. In this frame "Don't allow" came out 0dp tall at y=276.
        //
        // Height, not bounds. The actions are never pushed outside the card: they are squeezed to
        // nothing inside it, and a zero-height node still reports a position within the frame, so
        // a containment assertion passes against the defect. The first version of this test made
        // exactly that mistake — the same mistake, on the other axis, that a `deny.left >= 0`
        // assertion made in the plugin that reported this.
        setCard(width = 400.dp, height = 300.dp, body = { TallBody() })

        assertTrue(
            heightOf(actionLabel) > 0.dp,
            "\"$actionLabel\" measured ${heightOf(actionLabel)} tall - the body was measured " +
                "first and left the actions nothing, so the card asks for consent with nothing " +
                "to click",
        )
    }

    @Test
    fun `a card that fits is measured exactly as it was before the body could flex`() {
        // weight(1f) without fill = false would stretch this to the full 600dp. The guard is that
        // a short dialog is still its content's height, which is what every existing call site
        // looks like.
        setCard(width = 400.dp, height = 600.dp) { Text("one short line") }

        val card = cardBounds()
        val tall = card.bottom - card.top
        assertTrue(
            tall < 300.dp,
            "a one-line alert measured $tall tall in a 600dp frame - the body is filling its " +
                "weighted share instead of wrapping, so every short dialog in the app just grew",
        )
    }

    @Test
    fun `an unbounded height shows the whole body rather than scrolling it`() {
        // Guards this fix's own hazard rather than the original defect, and the measurements are
        // why the `hasBoundedHeight` branch exists. With an unbounded height the card is 1116dp
        // and the body renders inline; with the weight applied unconditionally it is 156dp and the
        // body is scrolled down to about one line. A window that sizes to its content cannot clip
        // it, so there is nothing to trade away there — scrolling is pure loss.
        //
        // Two traps this test had to get past, both of which made it pass against the defect:
        //
        //  - A sized Box is not unbounded. Inside `setContent` even an unsized Box inherits the
        //    test window's bounded height, so `hasBoundedHeight` stays true. A verticalScroll
        //    parent is what hands its child an infinite main axis, the way a window that sizes to
        //    content does.
        //  - A one-line body fits in the collapsed space, so `height > 0` holds either way. The
        //    body has to be taller than the collapse.
        //
        // Scale-free assertion: if nothing is scrolled away, the actions sit BELOW the last line
        // of the body. Collapsed, they sit above most of it — 90dp against the last line's 996dp.
        rule.setContent {
            BossTheme {
                Column(Modifier.width(400.dp).verticalScroll(rememberScrollState())) {
                    BossAlertCard(
                        buttons = { TextButton(onClick = {}) { Text(actionLabel) } },
                        title = { Text("Allow this?") },
                        text = { TallBody() },
                    )
                }
            }
        }

        val lastLine = boundsOf(LAST_LINE)
        val action = boundsOf(actionLabel)
        assertTrue(
            action.top > lastLine.top,
            "the actions sit at ${action.top} and the body's last line at ${lastLine.top} - the " +
                "body was given a weighted share of an unbounded axis and scrolled away under a " +
                "parent that would have shown all of it",
        )
    }

    @Test
    fun `the title and the actions both keep their height and stay on the card`() {
        // The body giving way is only correct if the two things framing it do not. A fix that
        // scrolled the whole Column would keep both nodes full-height and move them off the card
        // instead, so this checks height AND containment — the two failure modes are different
        // and neither assertion sees the other.
        setCard(width = 400.dp, height = 260.dp, body = { TallBody() })

        val card = cardBounds()
        listOf("Allow this?", actionLabel).forEach { label ->
            val b = boundsOf(label)
            assertTrue(b.bottom - b.top > 0.dp, "\"$label\" was squeezed to ${b.bottom - b.top}")
            assertTrue(b.top >= card.top, "\"$label\" starts ${card.top - b.top} above the card")
            assertTrue(b.bottom <= card.bottom, "\"$label\" ends ${b.bottom - card.bottom} below the card")
        }
    }

    @Test
    fun `a card narrower than it wants shrinks to fit`() {
        // #214's property, previously unpinned by any test in this repo. Split from the roomy case
        // rather than looped: two setContent calls in one test lean on desktop re-entrancy, and a
        // failure would not say which iteration produced it.
        setCard(width = 240.dp, height = 600.dp) { Text("x") }

        val cardWidth = cardBounds().let { it.right - it.left }
        assertTrue(
            cardWidth < 240.dp && cardWidth > 0.dp,
            "a card in a 240.dp frame measured $cardWidth - it did not shrink to fit",
        )
    }

    @Test
    fun `a card with room to spare is exactly AlertWidth`() {
        setCard(width = 900.dp, height = 600.dp) { Text("x") }

        val cardWidth = cardBounds().let { it.right - it.left }
        assertEquals(
            AlertWidth,
            cardWidth,
            "a card in a 900.dp frame measured $cardWidth rather than AlertWidth",
        )
    }

    @Test
    fun `the actions survive the shortest window that can hold them`() {
        // The floor, pinned. Only the body flexes, so the padding, title and spacers are still
        // measured before the actions: below roughly 176dp there is nothing left to give them.
        // Measured - 36dp at 180dp, 33dp at 173dp, 20dp at 160dp, 0dp at 140dp - so this asserts
        // the last height that fully works rather than pretending the floor is not there.
        setCard(width = 400.dp, height = 180.dp) { TallBody() }

        assertEquals(
            36.dp,
            heightOf(actionLabel),
            "the actions measured ${heightOf(actionLabel)} at a 180.dp parent - the floor moved, " +
                "and the fixed chrome above the body is what pushed it",
        )
    }

    @Test
    fun `the body scrolls rather than overflowing its box`() {
        // The scrolling half of the trade, which nothing else here pins: dropping verticalScroll
        // while keeping weight(1f, fill = false) leaves all the other assertions green, because the
        // body's own Column would place its remaining lines outside the Box and overdraw the action
        // row - actions still non-zero, still inside the card. That is arguably worse than the bug
        // being fixed, and it is the exact behaviour the "scroll rather than clip" argument rests on.
        setCard(width = 400.dp, height = 300.dp) { TallBody() }

        val before = boundsOf(LAST_LINE).top
        rule.onNodeWithText(LAST_LINE).performScrollTo()
        val after = boundsOf(LAST_LINE).top
        assertTrue(
            after < before,
            "scrolling to the body's last line moved it from $before to $after - the body is not " +
                "scrollable, so its overflow is drawn over the actions instead of inside a viewport",
        )
    }

    private companion object {
        const val FRAME = "alert-frame"
        const val CARD = "alert-card"
        const val BODY_LINES = 40

        fun bodyLine(i: Int) = "line $i of a long body"

        val LAST_LINE = bodyLine(BODY_LINES - 1)
    }
}
