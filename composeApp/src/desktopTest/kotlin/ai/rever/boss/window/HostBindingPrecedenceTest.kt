package ai.rever.boss.window

import ai.rever.boss.keymap.model.KeymapActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    @Test
    fun `an undispatched host action is not claimed by the interceptor`() {
        // EDITOR_GO_TO_LINE is bound in every preset but has no dispatch case, by design.
        assertFalse(
            AWTKeyboardInterceptor.dispatchAction(KeymapActions.EDITOR_GO_TO_LINE, "window-1"),
            "the interceptor must report EDITOR_GO_TO_LINE unhandled so the event reaches the editor",
        )
    }

    @Test
    fun `host actions the interceptor does own are still claimed`() {
        // The counterpart: a real host action must keep returning true, or the same change would
        // stop the interceptor consuming chords it genuinely serves.
        assertTrue(AWTKeyboardInterceptor.dispatchAction(KeymapActions.TAB_NEW, "window-1"))
        assertTrue(AWTKeyboardInterceptor.dispatchAction(KeymapActions.BROWSER_DEVTOOLS, "window-1"))
        assertTrue(AWTKeyboardInterceptor.dispatchAction(KeymapActions.TAB_CLOSE, "window-1"))
    }

    @Test
    fun `an unknown action is unhandled`() {
        assertFalse(AWTKeyboardInterceptor.dispatchAction("nonsense.action", "window-1"))
    }
}
