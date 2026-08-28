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
 * release, and putting the window's placement back.
 *
 * Its own composable rather than another block in `BossWindow`, which is already long, and because
 * the placement half only makes sense next to the grab half that `CapturedFullScreenController`
 * owns.
 *
 * The mode uses **real** full screen - `WindowPlacement.Fullscreen`, which Compose implements
 * through skiko's `NSWindow toggleFullScreen:` and which `View > Enter Full Screen` has always
 * used here. An earlier version sized the window to the display instead, on the strength of a probe
 * where `com.apple.eawt.Application.requestToggleFullScreen` blocked; that probe tested a path
 * Compose does not take, and a display-sized window is not the same thing anyway - it keeps its
 * title bar and stays in the current Space.
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
                    // Real full screen, not a window sized to the display.
                    //
                    // The first version of this did the latter, on the strength of a probe where
                    // `com.apple.eawt.Application.requestToggleFullScreen` blocked and never
                    // returned. That probe tested the wrong thing: Compose does not go through
                    // eawt. `WindowPlacement.Fullscreen` reaches skiko, which calls NSWindow's
                    // `toggleFullScreen:` - the same path `View > Enter Full Screen` in this app
                    // has always used. A resized window is also not equivalent: it keeps its title
                    // bar and its Space, so it reads as a big window rather than as full screen.
                    windowState.placement = WindowPlacement.Fullscreen
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
