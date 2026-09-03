package ai.rever.boss.window

import ai.rever.boss.keymap.model.KeymapActions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [AWTKeyboardInterceptor.keyNameMatches].
 *
 * The interceptor compares an AWT keycode's name against the `key` string stored in the keymap.
 * Those two vocabularies drifted: the presets use Compose's `Key.DirectionLeft` naming while the
 * interceptor emitted `"Left"`, so no arrow binding ever matched on this path — Cmd+Arrow panel
 * navigation survived only on the native menu accelerator and went dead whenever a terminal or
 * browser held focus. Cmd+Opt+Arrow tab stepping has no menu fallback at all, so it depends on
 * this agreeing.
 */
class AWTKeyNameMatchingTest {
    @Test
    fun `arrow spellings are interchangeable`() {
        // What getKeyName now emits, against what the presets store.
        assertTrue(AWTKeyboardInterceptor.keyNameMatches("DirectionLeft", "DirectionLeft"))
        assertTrue(AWTKeyboardInterceptor.keyNameMatches("DirectionRight", "DirectionRight"))

        // A keymap file written by an older build, or hand-edited, still matches.
        assertTrue(AWTKeyboardInterceptor.keyNameMatches("Left", "DirectionLeft"))
        assertTrue(AWTKeyboardInterceptor.keyNameMatches("ArrowUp", "DirectionUp"))
        assertTrue(AWTKeyboardInterceptor.keyNameMatches("Down", "DirectionDown"))
    }

    @Test
    fun `common aliases are interchangeable`() {
        assertTrue(AWTKeyboardInterceptor.keyNameMatches("Space", "Spacebar"))
        assertTrue(AWTKeyboardInterceptor.keyNameMatches("Esc", "Escape"))
        assertTrue(AWTKeyboardInterceptor.keyNameMatches("Return", "Enter"))
    }

    @Test
    fun `comparison is case insensitive`() {
        assertTrue(AWTKeyboardInterceptor.keyNameMatches("t", "T"))
        assertTrue(AWTKeyboardInterceptor.keyNameMatches("closebracket", "CloseBracket"))
    }

    @Test
    fun `distinct keys stay distinct`() {
        assertFalse(AWTKeyboardInterceptor.keyNameMatches("DirectionLeft", "DirectionRight"))
        assertFalse(AWTKeyboardInterceptor.keyNameMatches("OpenBracket", "CloseBracket"))
        assertFalse(AWTKeyboardInterceptor.keyNameMatches("Equals", "Minus"))
        assertFalse(AWTKeyboardInterceptor.keyNameMatches("Nine", "Eight"))
    }
}

/**
 * Tests for [AWTKeyboardInterceptor.dispatchIfMultiPanel].
 *
 * The default panel-navigation bindings are bare Cmd+Arrow, which macOS also reserves for caret
 * movement. Claiming the chord in a window with nothing to navigate to would take
 * "caret to line start" away from every text field and web page and give back nothing.
 */
class PanelNavigationGateTest {
    @org.junit.jupiter.api.Test
    fun `a single-panel window leaves the chord to the focused component`() {
        val windowId = "gate-single"
        MenuActionsHandler.updatePanelCount(windowId, 1)

        var fired = false
        val handled = AWTKeyboardInterceptor.dispatchIfMultiPanel(windowId) { fired = true }

        assertFalse(handled, "the event must propagate so Cmd+Left still moves the caret")
        assertFalse(fired)
    }

    @org.junit.jupiter.api.Test
    fun `an unknown window is treated as single-panel`() {
        var fired = false
        val handled = AWTKeyboardInterceptor.dispatchIfMultiPanel("gate-never-registered") { fired = true }

        assertFalse(handled)
        assertFalse(fired)
    }

    @org.junit.jupiter.api.Test
    fun `a split window navigates and claims the chord`() {
        val windowId = "gate-split"
        MenuActionsHandler.updatePanelCount(windowId, 2)

        var firedFor: String? = null
        val handled = AWTKeyboardInterceptor.dispatchIfMultiPanel(windowId) { firedFor = it }

        assertTrue(handled)
        assertEquals(windowId, firedFor)
    }
}

/**
 * The host-binding vs plugin-default precedence rule in [AWTKeyboardInterceptor].
 *
 * [ai.rever.boss.components.plugin.registries.PluginShortcutRegistryImpl] documents that plugin
 * defaults apply only where no host binding matched, and that host bindings always win. That held
 * only for host actions the interceptor can dispatch itself. Several host bindings exist purely so
 * a chord is listed and rebindable while a plugin serves it from its own composition —
 * EDITOR_GO_TO_LINE (Cmd+L) is opened by the editor plugin's onPreviewKeyEvent, and the
 * interceptor has no case for it.
 *
 * For those, `dispatchAction` returns false. Falling through to the plugin-default pass then
 * inverts the rule: a plugin registering the same chord as a GLOBAL default shadows the host
 * binding and consumes the event (a plugin's `onAction` returns Unit, so
 * `PluginShortcutRegistryImpl.dispatch` reports success for anything registered), so the real
 * handler never sees the key. Cmd+L is exactly this collision — the fluck browser contributes it
 * for Focus Address Bar.
 */
class HostBindingPrecedenceTest {
    @org.junit.jupiter.api.Test
    fun `an undispatched host action is not claimed by the interceptor`() {
        // EDITOR_GO_TO_LINE is bound in every preset but has no dispatch case, by design.
        assertFalse(
            AWTKeyboardInterceptor.dispatchActionForTest(KeymapActions.EDITOR_GO_TO_LINE, "window-1"),
            "the interceptor must report EDITOR_GO_TO_LINE unhandled so the event reaches the editor",
        )
    }

    @org.junit.jupiter.api.Test
    fun `host actions the interceptor does own are still claimed`() {
        // The counterpart: a real host action must keep returning true, or the same change would
        // stop the interceptor consuming chords it genuinely serves.
        assertTrue(AWTKeyboardInterceptor.dispatchActionForTest(KeymapActions.TAB_NEW, "window-1"))
        assertTrue(AWTKeyboardInterceptor.dispatchActionForTest(KeymapActions.TAB_REOPEN_CLOSED, "window-1"))
        assertTrue(AWTKeyboardInterceptor.dispatchActionForTest(KeymapActions.BROWSER_DEVTOOLS, "window-1"))
    }

    @org.junit.jupiter.api.Test
    fun `an unknown action is unhandled`() {
        assertFalse(AWTKeyboardInterceptor.dispatchActionForTest("nonsense.action", "window-1"))
    }
}
