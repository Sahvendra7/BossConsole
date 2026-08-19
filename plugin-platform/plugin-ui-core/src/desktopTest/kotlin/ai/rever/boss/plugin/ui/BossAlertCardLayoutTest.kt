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
import androidx.compose.ui.unit.Dp
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
                Box(Modifier.size(width, height).testTag(FRAME)) {
                    BossAlertCard(
                        buttons = { TextButton(onClick = {}) { Text(actionLabel) } },
                        title = { Text("Allow this?") },
                        text = body,
                    )
                }
            }
        }
    }

    private fun heightOf(label: String): Dp =
        rule.onNodeWithText(label).getUnclippedBoundsInRoot().let { it.bottom - it.top }

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
            "\"$actionLabel\" measured ${heightOf(actionLabel)} tall — the body was measured " +
                "first and left the actions nothing, so the card asks for consent with nothing " +
                "to click",
        )
    }

    @Test
    fun `a card that fits is measured exactly as it was before the body could flex`() {
        // weight(1f) without fill = false would stretch this to the full 600dp. The guard is that
        // a short dialog is still its content's height, which is what every existing call site
        // looks like.
        var tall = Dp.Unspecified
        rule.setContent {
            BossTheme {
                Box(Modifier.size(400.dp, 600.dp)) {
                    Box(Modifier.testTag(CARD)) {
                        BossAlertCard(
                            buttons = { TextButton(onClick = {}) { Text(actionLabel) } },
                            title = { Text("Allow this?") },
                            text = { Text("one short line") },
                        )
                    }
                }
            }
        }
        val card = rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
        tall = card.bottom - card.top
        assertTrue(
            tall < 300.dp,
            "a one-line alert measured $tall tall in a 600dp frame — the body is filling its " +
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

        val lastLine = rule.onNodeWithText(LAST_LINE).getUnclippedBoundsInRoot()
        val action = rule.onNodeWithText(actionLabel).getUnclippedBoundsInRoot()
        assertTrue(
            action.top > lastLine.top,
            "the actions sit at ${action.top} and the body's last line at ${lastLine.top} — the " +
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

        val frame = rule.onNodeWithTag(FRAME).getUnclippedBoundsInRoot()
        listOf("Allow this?", actionLabel).forEach { label ->
            val b = rule.onNodeWithText(label).getUnclippedBoundsInRoot()
            assertTrue(b.bottom - b.top > 0.dp, "\"$label\" was squeezed to ${b.bottom - b.top}")
            assertTrue(b.top >= frame.top, "\"$label\" starts ${frame.top - b.top} above the card")
            assertTrue(b.bottom <= frame.bottom, "\"$label\" ends ${b.bottom - frame.bottom} below the card")
        }
    }

    @Test
    fun `a narrow card shrinks and a roomy one does not`() {
        // #214's property, still unpinned by a test in this repo: exactly AlertWidth where there is
        // room for it, and the window's width where there is not.
        listOf(240.dp to true, 900.dp to false).forEach { (frameWidth, shouldShrink) ->
            rule.setContent {
                BossTheme {
                    Box(Modifier.size(frameWidth, 600.dp)) {
                        Box(Modifier.testTag(CARD)) {
                            BossAlertCard(
                                buttons = { TextButton(onClick = {}) { Text(actionLabel) } },
                                text = { Text("x") },
                            )
                        }
                    }
                }
            }
            val card = rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
            val cardWidth = card.right - card.left
            if (shouldShrink) {
                assertTrue(
                    cardWidth < frameWidth && cardWidth > 0.dp,
                    "a card in a $frameWidth frame measured $cardWidth — it did not shrink to fit",
                )
            } else {
                assertEquals(
                    400.dp, cardWidth,
                    "a card in a $frameWidth frame measured $cardWidth rather than AlertWidth",
                )
            }
        }
    }

    private companion object {
        const val FRAME = "alert-frame"
        const val CARD = "alert-card"
        const val BODY_LINES = 40

        fun bodyLine(i: Int) = "line $i of a long body"

        val LAST_LINE = bodyLine(BODY_LINES - 1)
    }
}
