package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.proto.services.KeyModifier
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [KeyEventEncoder].
 *
 * The Compose-typed `encode(sessionId, event)` overload is exercised in the
 * dev preview by real key events; here we drive [KeyEventEncoder.encodeRaw]
 * directly so the test doesn't depend on a Compose Multiplatform internal
 * `KeyEvent` constructor that shifts between Compose releases.
 *
 * AWT `KeyEvent` constants (`VK_A`, `VK_LEFT`, `VK_F1`, …) are reused as
 * key-code values to confirm that the canonical desktop encoding round-trips
 * through `Int` truncation without losing identity.
 */
class KeyEventEncoderTest {

    @Test
    fun `KeyDown carries the typed character as text`() {
        val req = KeyEventEncoder.encodeRaw(
            sessionId = "s",
            keyCode = KeyEvent.VK_A,
            codePoint = 'a'.code,
            isPress = true,
        )
        assertNotNull(req)
        assertEquals("a", req.text)
        assertTrue(req.isPress)
        assertEquals(KeyEvent.VK_A, req.keyCode)
        assertEquals(0, req.modifiers)
    }

    @Test
    fun `KeyUp suppresses text so the character is not echoed twice`() {
        val req = KeyEventEncoder.encodeRaw(
            sessionId = "s",
            keyCode = KeyEvent.VK_A,
            codePoint = 'a'.code,
            isPress = false,
        )
        assertNotNull(req)
        assertEquals("", req.text)
        assertEquals(false, req.isPress)
        // keyCode + modifiers still flow so chord-release detection is possible.
        assertEquals(KeyEvent.VK_A, req.keyCode)
    }

    @Test
    fun `shift and ctrl combine into the KeyModifier bitmask`() {
        val req = KeyEventEncoder.encodeRaw(
            sessionId = "s",
            keyCode = KeyEvent.VK_A,
            codePoint = 'A'.code,
            isPress = true,
            shift = true,
            ctrl = true,
        )
        assertNotNull(req)
        val expected =
            KeyModifier.KEY_MODIFIER_SHIFT_VALUE or KeyModifier.KEY_MODIFIER_CTRL_VALUE
        assertEquals(expected, req.modifiers)
    }

    @Test
    fun `alt and meta combine into the KeyModifier bitmask`() {
        val req = KeyEventEncoder.encodeRaw(
            sessionId = "s",
            keyCode = KeyEvent.VK_X,
            codePoint = 'x'.code,
            isPress = true,
            alt = true,
            meta = true,
        )
        assertNotNull(req)
        val expected =
            KeyModifier.KEY_MODIFIER_ALT_VALUE or KeyModifier.KEY_MODIFIER_META_VALUE
        assertEquals(expected, req.modifiers)
    }

    @Test
    fun `arrow key carries keyCode with empty text on KeyDown`() {
        // Special keys have no Unicode codepoint; encoder still sends the
        // request because keyCode != 0 so the child can interpret VK_LEFT etc.
        val req = KeyEventEncoder.encodeRaw(
            sessionId = "s",
            keyCode = KeyEvent.VK_LEFT,
            codePoint = 0,
            isPress = true,
        )
        assertNotNull(req)
        assertEquals(KeyEvent.VK_LEFT, req.keyCode)
        assertEquals("", req.text)
        assertTrue(req.isPress)
    }

    @Test
    fun `F1 keyCode survives Int truncation from Compose's Long keyCode`() {
        val req = KeyEventEncoder.encodeRaw(
            sessionId = "s",
            keyCode = KeyEvent.VK_F1,
            codePoint = 0,
            isPress = true,
        )
        assertNotNull(req)
        assertEquals(KeyEvent.VK_F1, req.keyCode)
    }

    @Test
    fun `event with neither keyCode nor codepoint is dropped`() {
        val req = KeyEventEncoder.encodeRaw(
            sessionId = "s",
            keyCode = 0,
            codePoint = 0,
            isPress = true,
        )
        assertNull(req)
    }
}
