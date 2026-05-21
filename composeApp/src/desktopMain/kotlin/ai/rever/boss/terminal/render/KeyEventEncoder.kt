package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.proto.services.KeyModifier
import ai.rever.boss.ipc.proto.services.SendKeyEventRequest
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Encodes a Compose [KeyEvent] into a [SendKeyEventRequest] for the
 * out-of-process terminal plugin. Modifiers are packed into the
 * [KeyModifier] bitmask defined in `terminal.proto`. [utf16CodePoint]
 * is rendered as a String so IME-emitted characters survive the
 * round trip.
 *
 * Returns null if [event] should be ignored entirely (e.g. the
 * undefined "unknown" key with no codepoint). Repeat events on
 * desktop arrive as separate KeyDown events; this encoder reports
 * repeatCount = 1 and lets the child collapse repeats if it cares.
 *
 * `text` is only attached to KeyDown events. KeyUp events still
 * traverse the wire so consumers can implement chord/release
 * detection, but they never duplicate the character that was already
 * delivered on press.
 */
object KeyEventEncoder {

    fun encode(sessionId: String, event: KeyEvent): SendKeyEventRequest? =
        encodeRaw(
            sessionId = sessionId,
            keyCode = event.key.keyCode.toInt(),
            codePoint = event.utf16CodePoint,
            isPress = event.type == KeyEventType.KeyDown,
            shift = event.isShiftPressed,
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
            meta = event.isMetaPressed,
        )

    /**
     * Pure-data encode entry point. Exposed for unit tests so the logic can be
     * exercised without constructing a Compose `KeyEvent` (whose internals
     * shift across Compose Multiplatform versions). The Compose-typed
     * [encode] overload is the only caller in production.
     */
    fun encodeRaw(
        sessionId: String,
        keyCode: Int,
        codePoint: Int,
        isPress: Boolean,
        shift: Boolean = false,
        ctrl: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ): SendKeyEventRequest? {
        if (keyCode == 0 && codePoint == 0) return null

        val text = if (isPress && codePoint != 0) String(Character.toChars(codePoint)) else ""
        return SendKeyEventRequest.newBuilder()
            .setSessionId(sessionId)
            .setKeyCode(keyCode)
            .setModifiers(encodeModifiers(shift, ctrl, alt, meta))
            .setText(text)
            .setIsPress(isPress)
            .setRepeatCount(1)
            .build()
    }

    fun encodeModifiers(
        shift: Boolean,
        ctrl: Boolean,
        alt: Boolean,
        meta: Boolean,
    ): Int {
        var mask = 0
        if (shift) mask = mask or KeyModifier.KEY_MODIFIER_SHIFT_VALUE
        if (ctrl) mask = mask or KeyModifier.KEY_MODIFIER_CTRL_VALUE
        if (alt) mask = mask or KeyModifier.KEY_MODIFIER_ALT_VALUE
        if (meta) mask = mask or KeyModifier.KEY_MODIFIER_META_VALUE
        return mask
    }
}
