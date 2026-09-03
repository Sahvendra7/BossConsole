package ai.rever.boss.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [SettingsWindowState.reveal] and the highlight's lifetime.
 *
 * All of it is plain logic that a composable then reads, and every failure here looks like a UI
 * glitch rather than a state bug: a row lighting up on a page nobody asked about, or a pick that
 * visibly does nothing because the value it wrote equalled the one already there.
 */
class SettingsWindowRevealTest {
    @Test
    fun `revealing a row navigates and arms the highlight`() {
        val state = SettingsWindowState()

        state.reveal(section = "APPEARANCE", group = "Tab Bar", label = "Show Title Bar", highlightable = true)

        assertEquals("APPEARANCE", state.section)
        assertTrue(state.visible)
        assertEquals("Show Title Bar", state.highlight?.label)
        assertEquals("Tab Bar", state.highlight?.group)
    }

    @Test
    fun `the same row twice bumps the nonce`() {
        // The whole reason the nonce exists: the value is otherwise unchanged, so the window's
        // keyed effect would not re-run and picking a row a second time would do nothing at all.
        val state = SettingsWindowState()

        state.reveal(section = "APPEARANCE", group = null, label = "Show Title Bar", highlightable = true)
        val first = state.highlight
        state.reveal(section = "APPEARANCE", group = null, label = "Show Title Bar", highlightable = true)

        assertNotEquals(first, state.highlight, "a repeat must be a new value or the effect will not re-run")
    }

    @Test
    fun `an entry that cannot be highlighted clears the last one rather than leaving it armed`() {
        // A plugin page or a control with no search target can only reach its section. Pointing at
        // nothing is the honest outcome; keeping the previous pick would light a row on a page it
        // does not belong to.
        val state = SettingsWindowState()
        state.reveal(section = "APPEARANCE", group = null, label = "Show Title Bar", highlightable = true)

        state.reveal(section = "ai-gateway", group = null, label = "AI Gateway", highlightable = false)

        assertEquals("ai-gateway", state.section)
        assertNull(state.highlight)
    }

    @Test
    fun `closing the window disarms the highlight`() {
        // SettingsContent composes fresh each time `visible` flips, so a highlight left here fires
        // on that first composition - long after the pick it belonged to. The nonce cannot help:
        // it only distinguishes repeats within one composition's lifetime.
        val state = SettingsWindowState()
        state.reveal(section = "APPEARANCE", group = null, label = "Show Title Bar", highlightable = true)

        state.close()

        assertNull(state.highlight, "reopening Settings must not re-light the last row picked")
        assertNull(state.section)
    }

    @Test
    fun `a plain open leaves an unrelated section alone and only raises the window`() {
        // The bug the whole holder exists for: assigning `true` to an already-true flag changed
        // nothing, and Settings read as a dead button. open() with no section must bump the
        // counter, not clear where a deep link went.
        val state = SettingsWindowState()
        state.open(section = "APPEARANCE")
        val raisedOnce = state.focusRequest

        state.open()

        assertEquals("APPEARANCE", state.section)
        assertEquals(raisedOnce + 1, state.focusRequest)
    }
}
