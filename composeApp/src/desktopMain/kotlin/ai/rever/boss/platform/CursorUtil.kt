package ai.rever.boss.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.awt.awtEventOrNull
import java.awt.Cursor

/**
 * Desktop (JVM) implementation of CursorUtil using AWT cursor functionality.
 */
@OptIn(ExperimentalComposeUiApi::class)
actual object CursorUtil {
    /**
     * Changes the cursor to a horizontal resize cursor when hovering.
     */
    actual fun Modifier.cursorForHorizontalResize(): Modifier {
        return this
            .onPointerEvent(PointerEventType.Enter) { pointerEvent ->
                pointerEvent.awtEventOrNull?.component?.cursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
            }
            .onPointerEvent(PointerEventType.Exit) { pointerEvent ->
                pointerEvent.awtEventOrNull?.component?.cursor = Cursor.getDefaultCursor()
            }
    }
    
    /**
     * Changes the cursor to a vertical resize cursor when hovering.
     */
    actual fun Modifier.cursorForVerticalResize(): Modifier {
        return this
            .onPointerEvent(PointerEventType.Enter) { pointerEvent ->
                pointerEvent.awtEventOrNull?.component?.cursor = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
            }
            .onPointerEvent(PointerEventType.Exit) { pointerEvent ->
                pointerEvent.awtEventOrNull?.component?.cursor = Cursor.getDefaultCursor()
            }
    }
}