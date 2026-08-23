package ai.rever.boss.components.bars.horizontal

import ai.rever.boss.layout.BossChrome
import ai.rever.boss.plugin.ui.BossTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A bare strip holding the room the macOS traffic lights need, and nothing else.
 *
 * This is what is left of `BossTitleBar`, which spent 26dp plus a divider drawing a centered
 * "Boss Console" label. The label moved into the top bar (`BossTopBar`), which is the topmost row
 * now and carries the buttons' leading inset itself - so on a normal launch this strip never
 * renders and the window is 27dp shorter on chrome. That claim depends on
 * `FocusModeEdgeRevealState.shown` being seeded rather than starting `false`: before it was, the
 * top bar was absent on frame one, this strip stood in for it, and it was removed again immediately.
 *
 * It exists for the configuration where no window-wide row is on screen at all: the top bar
 * switched off, or cleared by focus mode. The tab bar would be the topmost row then, and there is
 * one of those per panel - insetting "the" tab bar would mean identifying the single panel in the
 * window's top-left corner, which in a split is a layout-phase question. Standing a strip up
 * instead costs exactly what the old title row cost, in the one case that used to be the only case.
 *
 * Whether it is needed at all is `WindowTopChrome.needsReservationStrip`, decided by the caller and
 * unit-tested there; the caller also owns the animation that crosses it over with the top bar.
 *
 * Double-tap to maximise is kept here as well as on the top bar: whichever row sits at the top of
 * the window, dragging or double-tapping it should behave like a title bar, because to the user it
 * is one.
 */
@Composable
fun TrafficLightStrip(onToggleMaximize: (() -> Unit)? = null) {
    // Keyed on Unit with the callback read through rememberUpdatedState, not keyed on the callback.
    // onToggleMaximize is a lambda capturing the AWT window; if it is not memoised, keying on it
    // restarts the gesture coroutine on every recomposition and resets a double-tap mid-gesture.
    val toggleMaximize by rememberUpdatedState(onToggleMaximize)

    HorizontalBar(
        modifier =
            Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { toggleMaximize?.invoke() },
                )
            },
        height = BossChrome.dimens.trafficLightStripHeight,
    ) {}
    Divider(color = BossTheme.colors.line)
}
