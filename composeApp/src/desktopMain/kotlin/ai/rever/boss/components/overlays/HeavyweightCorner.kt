package ai.rever.boss.components.overlays

import ai.rever.boss.plugin.browser.LocalAwtWindow
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import javax.swing.RootPaneContainer
import java.awt.Window as AwtWindow

/**
 * Heavyweight host for a corner overlay that outlives a keypress - toast notifications.
 *
 * **Content-sized, and that is the whole reason this exists instead of reusing a sibling.** Both
 * existing renderers are wrong here, for opposite reasons:
 *
 *  - [HeavyweightHud] is parent-sized. A non-focusable AWT window still receives mouse events and
 *    the JVM has no portable click-through, so it swallows every click underneath it. That is
 *    tolerable for the Ctrl+Tab switcher, which is up only while a key is held, and not tolerable
 *    for a toast that lingers for seconds while the user keeps working - it would make the whole
 *    window unclickable for the duration.
 *  - `HeavyweightPopup`'s scrim calls `onDismissRequest` on any click, so clicking anywhere in the
 *    app would dismiss the toast instead of reaching what was clicked.
 *
 * A window sized to its content covers only the toast itself: its own buttons still work, and every
 * click outside it reaches the app. Same trade as [HeavyweightGhost], which is content-sized for
 * the same reason.
 *
 * **Callers must compose this only while there is something to show.** Because the window eats
 * clicks wherever it sits, one composed unconditionally is a permanently dead region of the app -
 * and, being always-on-top, of whatever other application is in front.
 *
 * Two things here are deliberately not the obvious implementation, both because the obvious one
 * fails silently:
 *
 *  - Content is measured against [initialSize], **never against the window's current size** (see
 *    [measuredAgainst]). Measuring against the window makes the size a one-way ratchet: the window
 *    shrinks to fit what is showing, the next toast is then measured inside that smaller window,
 *    measures clipped, and the overlay can never grow back.
 *  - Parent bounds come from the CONTENT PANE, not the window (see [contentPaneBounds]), and are
 *    re-read on the frame clock rather than remembered once.
 */
@Composable
fun HeavyweightCorner(
    alignment: Alignment,
    initialSize: DpSize,
    content: @Composable () -> Unit,
) {
    val parent = LocalAwtWindow.current
    val density = LocalDensity.current.density
    var measured by remember { mutableStateOf<DpSize?>(null) }
    val size = measured ?: initialSize
    var bounds by remember(parent) { mutableStateOf(contentPaneBounds(parent)) }

    val state =
        rememberWindowState(
            size = size,
            position = cornerPosition(bounds, size, alignment).let { WindowPosition(it.first.dp, it.second.dp) },
        )

    // Track the parent on the frame clock. `rememberOverlayParentBounds` is keyed on the window
    // INSTANCE, which never changes, so it captures the bounds once - fine for the sub-second
    // overlays that came before, wrong for one that is up while the user can drag or resize the
    // window out from under it. Only assign on an actual change: each one is a native setLocation.
    LaunchedEffect(parent) {
        while (true) {
            withFrameNanos { }
            val next = contentPaneBounds(parent) ?: continue
            val current = bounds
            if (current == null || !next.contentEquals(current)) bounds = next
        }
    }

    // Assign window state from an effect, never during composition - writing it inline during
    // composition is what made the cursor overlay jitter.
    LaunchedEffect(size, bounds, alignment) {
        state.size = size
        val at = cornerPosition(bounds, size, alignment)
        state.position = WindowPosition(at.first.dp, at.second.dp)
    }

    Window(
        onCloseRequest = {},
        state = state,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        focusable = false,
        resizable = false,
    ) {
        EnsureOverlayWindowTransparent(window)
        Box(
            modifier =
                Modifier
                    // Order matters: the constraint override is OUTSIDE, so the observer inside it
                    // reports a size measured against the ceiling rather than against the window.
                    .measuredAgainst(initialSize)
                    .onGloballyPositioned { coordinates ->
                        val next =
                            DpSize(
                                (coordinates.size.width / density).dp,
                                (coordinates.size.height / density).dp,
                            )
                        // Ignore a zero measurement: it happens while the overlay is torn down, and
                        // acting on it would collapse the window and hide content still showing.
                        if (next.width.value > 0f && next.height.value > 0f && next != measured) {
                            measured = next
                        }
                    },
        ) {
            content()
        }
    }
}

/**
 * Measures content against [ceiling] rather than against the incoming constraints.
 *
 * Callers observe the result with an `onGloballyPositioned` placed INSIDE this modifier, so what it
 * reports is the ceiling-constrained size rather than whatever the window currently is.
 *
 * This is what stops the overlay's size from becoming a one-way ratchet. The natural implementation
 * - measure normally and report `onGloballyPositioned`'s size - feeds the window's own size back
 * into the measurement: once the window has shrunk to fit one toast, the next is measured inside
 * that smaller window, so it measures CLIPPED, the reported size never grows, and every later toast
 * renders squashed. Nothing warns; the window is still transparent, still correctly placed, and
 * still passes every gate.
 *
 * Measuring against a constant ceiling instead makes the answer independent of the current size, so
 * it converges rather than ratcheting. [ceiling] is therefore a hard upper bound on the overlay, not
 * merely a first guess.
 */
internal fun Modifier.measuredAgainst(ceiling: DpSize): Modifier =
    layout { measurable, _ ->
        val placeable =
            measurable.measure(
                Constraints(
                    maxWidth = ceiling.width.roundToPx(),
                    maxHeight = ceiling.height.roundToPx(),
                ),
            )
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * The parent's CONTENT PANE bounds on screen as `[x, y, width, height]`, or null if unreadable.
 *
 * Not the window's own bounds, which is what [rememberOverlayParentBounds] returns. `BossWindow` is
 * decorated, so its frame bounds include the native title bar and borders - and for an overlay
 * anchored to a CORNER that difference is the bug, not a rounding error: anchored to the frame, a
 * top-aligned overlay sits over the title bar, and since it eats clicks, over the window controls
 * with it. The parent-sized renderers cannot see this because they cover a superset either way.
 *
 * [HeavyweightGhost] documents the same trap from the other side and avoids it by reading the cursor
 * instead of converting at all; a corner anchor has no equivalent escape, so it converts correctly.
 */
internal fun contentPaneBounds(parent: AwtWindow?): IntArray? {
    val pane = (parent as? RootPaneContainer)?.contentPane?.takeIf { it.isShowing } ?: return null
    return runCatching {
        val at = pane.locationOnScreen
        intArrayOf(at.x, at.y, pane.width, pane.height)
    }.getOrNull()
}

/**
 * Top-left corner, in AWT logical units, for an overlay of [size] placed at [alignment] inside
 * [bounds] - or the origin when the parent could not be measured.
 *
 * Pure so the arithmetic is pinned by a test; composing a `Window` needs a display, so this is the
 * only reachable part. Offsets are floored at zero so content larger than the parent overhangs the
 * bottom-right rather than being pushed off the top-left, where it would be unreachable.
 */
internal fun cornerPosition(
    bounds: IntArray?,
    size: DpSize,
    alignment: Alignment,
): Pair<Int, Int> {
    if (bounds == null) return 0 to 0
    val width = size.width.value.toInt()
    val height = size.height.value.toInt()
    val slackX = (bounds[2] - width).coerceAtLeast(0)
    val slackY = (bounds[3] - height).coerceAtLeast(0)
    val x =
        bounds[0] +
            when (alignment) {
                Alignment.TopStart, Alignment.CenterStart, Alignment.BottomStart -> 0
                Alignment.TopEnd, Alignment.CenterEnd, Alignment.BottomEnd -> slackX
                else -> slackX / 2
            }
    val y =
        bounds[1] +
            when (alignment) {
                Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd -> 0
                Alignment.BottomStart, Alignment.BottomCenter, Alignment.BottomEnd -> slackY
                else -> slackY / 2
            }
    return x to y
}
