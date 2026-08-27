package ai.rever.boss.fullscreen

import ai.rever.boss.window.MenuActionsHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.flow.filter
import java.awt.Window

/**
 * Everything captured full screen needs from inside a window's composition: the toggle, the pointer
 * release, and putting the window's geometry back.
 *
 * Its own composable rather than another block in `BossWindow`, which is already long, and because
 * the geometry half only makes sense next to the grab half that `CapturedFullScreenController`
 * owns.
 *
 * ## Why the window is resized rather than sent to `WindowPlacement.Fullscreen`
 *
 * On macOS the mode hides the Dock and the menu bar itself, through the presentation options in
 * `MacInputCapture`, so there is nothing left for AppKit full screen to add - and a probe of
 * `com.apple.eawt.Application.requestToggleFullScreen` from a JVM blocked and never returned, twice.
 * Covering the display by setting position and size is the path that was measured to work, it
 * enters and leaves with no Space transition, and it keeps one state machine instead of two.
 *
 * `GraphicsConfiguration.bounds` is in logical points, the same unit as [WindowState.size], so the
 * conversion is a straight `.dp` - the same assumption `BossWindow`'s own fit-to-content sizing
 * already makes.
 */
@Composable
fun CapturedFullScreenEffects(
    windowId: String,
    window: Window,
    windowState: WindowState,
) {
    // What to put back on the way out. Held across the whole session, so an exit by any of the
    // four routes restores the same geometry.
    val restore = remember { PreCaptureGeometry() }

    LaunchedEffect(windowId, window) {
        MenuActionsHandler.capturedFullScreenEvents
            .filter { it == windowId }
            .collect {
                if (CapturedFullScreenState.current.value.capturing(windowId)) {
                    CapturedFullScreenController.exit()
                    restore.applyTo(windowState)
                } else {
                    restore.captureFrom(windowState)
                    val bounds = CapturedFullScreenController.displayBoundsOf(window)
                    // Placement first: a Maximized or Fullscreen window ignores an explicit size,
                    // the same ordering BossWindow's fit-to-content sizing documents.
                    windowState.placement = WindowPlacement.Floating
                    windowState.position = WindowPosition(bounds.x.dp, bounds.y.dp)
                    windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
                    CapturedFullScreenController.enter(windowId, window)
                }
            }
    }

    LaunchedEffect(windowId) {
        MenuActionsHandler.pointerReleaseEvents
            .filter { it == windowId }
            .collect {
                if (!CapturedFullScreenState.current.value.capturing(windowId)) return@collect
                if (CapturedFullScreenState.current.value.pointerConfined) {
                    CapturedFullScreenController.releasePointer()
                } else {
                    // The same shortcut takes it back, so the action is a toggle rather than a
                    // one-way door that leaves the user without the confinement they asked for.
                    CapturedFullScreenController.reconfinePointer(window)
                }
            }
    }

    // The window going away is one of the four release paths. The controller also watches the AWT
    // window directly, because a composition can be torn down in ways this effect does not see.
    DisposableEffect(windowId) {
        onDispose {
            if (CapturedFullScreenState.current.value.capturing(windowId)) {
                CapturedFullScreenController.exit()
            }
        }
    }
}

/**
 * The window's size, position and placement from before a session started.
 *
 * A holder rather than three `remember`s so that "we have nothing saved" is one question. Restoring
 * a geometry that was never captured would move a window the user never asked to move.
 */
class PreCaptureGeometry {
    private var size: DpSize? = null
    private var position: WindowPosition? = null
    private var placement: WindowPlacement? = null

    fun captureFrom(state: WindowState) {
        size = state.size
        position = state.position
        placement = state.placement
    }

    fun applyTo(state: WindowState) {
        // All three are written together by captureFrom, so one guard is the whole question:
        // "is there a geometry to go back to". Restoring a partial one would move a window the
        // user never asked to move.
        val savedSize = size
        val savedPosition = position
        val savedPlacement = placement
        if (savedSize == null || savedPosition == null || savedPlacement == null) return
        // Size and position before placement: a window going back to Maximized still needs the
        // right un-maximised geometry underneath it, which is the ordering BossWindow's Restore
        // request uses for the same reason.
        state.size = savedSize
        state.position = savedPosition
        state.placement = savedPlacement
        size = null
        position = null
        placement = null
    }
}
