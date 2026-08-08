package ai.rever.boss.components.overlays

import ai.rever.boss.plugin.browser.LocalAwtWindow
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState

/**
 * Heavyweight host for a long-lived corner overlay - toast notifications.
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
 * click outside it reaches the app untouched. Same trade as [HeavyweightGhost], which is
 * content-sized for the same reason.
 *
 * Sizing is necessarily two-pass, because a window's size must be chosen before its content can be
 * measured: it opens at [initialSize], then follows the measured content. Make [initialSize] a
 * generous upper bound rather than a guess at the real size - measuring inside a window that is too
 * small measures CLIPPED content, and the overlay would then settle at the clipped size and stay
 * there.
 */
@Composable
fun HeavyweightCorner(
    alignment: Alignment,
    initialSize: DpSize,
    content: @Composable () -> Unit,
) {
    val parent = LocalAwtWindow.current
    val bounds = rememberOverlayParentBounds(parent)
    val density = LocalDensity.current.density
    var measured by remember { mutableStateOf<DpSize?>(null) }
    val size = measured ?: initialSize

    val state =
        rememberWindowState(
            size = size,
            position =
                cornerPosition(bounds, size, alignment).let { WindowPosition(it.first.dp, it.second.dp) },
        )

    // Assign from an effect, never during composition. Both values change while the overlay is up
    // (the content grows as toasts stack, and the parent moves), and writing window state inline
    // during composition is what made the cursor overlay jitter.
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
                Modifier.onGloballyPositioned { coordinates ->
                    val next =
                        DpSize(
                            (coordinates.size.width / density).dp,
                            (coordinates.size.height / density).dp,
                        )
                    // Ignore a zero measurement. It happens while the window is being torn down, and
                    // acting on it would collapse the overlay and hide a toast that is still showing.
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
