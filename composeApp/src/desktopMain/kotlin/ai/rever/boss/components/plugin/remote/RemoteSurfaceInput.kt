package ai.rever.boss.components.plugin.remote

import ai.rever.boss.keymap.KeymapSettingsManager
import ai.rever.boss.keymap.handler.KeymapMatcher
import ai.rever.boss.keymap.model.KeymapSettings
import ai.rever.boss.keymap.model.ShortcutContext
import ai.rever.boss.ui.sdk.ScrollCoalescer
import ai.rever.boss.ui.sdk.ScrollOffset
import ai.rever.boss.ui.sdk.WidgetEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import java.awt.event.KeyEvent as AwtKeyEvent

/**
 * The two input families a remote surface raises for itself rather than for one of its widgets:
 * unclaimed key presses, and coalesced scrolling.
 *
 * Both were mapped onto the wire by #48 and emitted by nobody, each deferred for a policy decision.
 * The decisions are documented on [toForwardedKey] and in
 * [ScrollCoalescer][ai.rever.boss.ui.sdk.ScrollCoalescer]; this file is where they meet Compose.
 */

/**
 * Forward keys the host did not want to the plugin behind this surface, **without ever consuming one**.
 *
 * ## The policy: the host keymap wins, the plugin gets what is left
 *
 * An out-of-process plugin must not be able to swallow `Cmd+W` and trap the user in a panel. Three
 * things make that impossible here, and only the second is code in this function:
 *
 * 1. **The host keymap runs strictly upstream, at the AWT layer.**
 *    [AWTKeyboardInterceptor][ai.rever.boss.window.AWTKeyboardInterceptor] installs a
 *    `KeyEventDispatcher` on the `KeyboardFocusManager`, which sees every key *before* AWT routes it
 *    to the focused component — i.e. before Compose exists as far as the event is concerned. When a
 *    binding matches and its action dispatches, the interceptor calls `event.consume()` and returns
 *    `true`, and the event is never delivered onward. So a key that reaches a Compose modifier is, by
 *    construction, one the host keymap already declined; `false // Let event propagate normally` at
 *    the end of that dispatcher is the exact point this tap sits after. Nothing needed to change there
 *    — and deliberately so, because the interceptor cannot answer "which remote surface has focus"
 *    (it routes by AWT window, and remote surfaces are not placed in a window yet), while Compose's
 *    own focus system answers it for free.
 *
 * 2. **The tap re-checks the live keymap anyway.** The interceptor has early exits that skip matching
 *    entirely — an unregistered focused window returns before it consults the keymap — and this
 *    modifier would happily forward a `Cmd+T` that arrived down one of those paths. Asking
 *    [KeymapMatcher] the same question the interceptor asks makes "the host keymap wins" a property of
 *    the forwarding rule itself rather than an emergent property of dispatch order, and it is what
 *    makes the rule testable without driving AWT.
 *
 * 3. **This handler always returns `false`.** It is a tap, not a handler: the key continues to
 *    whatever the host has above the surface exactly as if the plugin were not there. A plugin can
 *    *observe* what is left over; it can never claim it. Even a plugin process that hangs cannot
 *    delay a host shortcut, because the shortcut never entered this path.
 *
 * ## What gets forwarded
 *
 * Keys the *focused widget* did not take, because `onKeyEvent` fires on the way up from the focus
 * target: typing into a remote text field produces `TextChange`, not a `Key` per character, and only
 * what the field ignores bubbles this far. That is the same "host first, then the specific thing, then
 * the surface" ordering one level down.
 *
 * @param onEvent the surface's event sink. Key events are tagged with an **empty node id** — they
 *   reach the surface precisely *because* no node claimed them, so attributing one would be a guess.
 *   Same convention as lifecycle, per `EmittedEvent`.
 * ## Cost
 *
 * The matcher is built once per keymap, not once per keystroke. A held key auto-repeats at ~25-30/s and
 * `KeymapMatcher.match` is not free — it allocates a filtered candidate list twice (context, then
 * `WORKSPACE`) and does string normalization per candidate — so constructing one inside the handler put
 * a few hundred short-lived allocations per keystroke on the Compose UI thread. Reading the settings
 * during composition also means a rebind takes effect without the surface having to be re-created.
 *
 * @param hostKeymap the keymap to check against; the live user settings in production, injected in
 *   tests so an assertion about a shortcut does not depend on whoever's `~/.boss/keymap-settings.json`
 *   the suite happens to run beside.
 */
@Composable
internal fun Modifier.forwardUnclaimedKeys(
    onEvent: (nodeId: String, event: WidgetEvent) -> Unit,
    hostKeymap: KeymapSettings = KeymapSettingsManager.currentSettings.collectAsState().value,
): Modifier {
    val matcher = remember(hostKeymap) { KeymapMatcher(hostKeymap) }
    return onKeyEvent { keyEvent ->
        keyEvent.toForwardedKey(matcher)?.let { forwarded -> onEvent("", forwarded) }
        // Never true. See point 3 above — this is the anti-trap guarantee, and it is one word.
        false
    }.focusable()
}

/**
 * The `Key` event a plugin should see for this key press, or `null` if it must not be forwarded.
 *
 * Three refusals:
 *
 * - **Anything that is not a key *down*.** `KeyEvent` on the wire has a key code and modifiers and no
 *   up/down discriminator, so forwarding both edges would deliver every press twice with no way for a
 *   plugin to tell them apart. Down is the edge that means "the user pressed this".
 * - **A modifier pressed on its own.** Holding Shift to type a capital letter would otherwise deliver a
 *   bare `VK_SHIFT` ahead of the letter, and a plugin listening for keys would see two events for one
 *   keystroke. The modifier is not lost — it arrives as a flag on the key it modifies, which is the
 *   only form the wire type can express. Same set the AWT interceptor skips, for the same reason.
 * - **Anything [hostKeymap] binds.** Checked in [ShortcutContext.GLOBAL], which is what [KeymapMatcher]
 *   resolves for a surface that is neither a browser, a terminal nor an editor — the same context the
 *   AWT interceptor derives for one — and which also covers `WORKSPACE` bindings.
 *
 * `Key.nativeKeyCode` rather than the packed `Key.keyCode`: the proto field is an `int32` and the
 * documented meaning is a platform key code, which on this host is the AWT `VK_` constant. The packed
 * Compose value would neither fit nor mean anything to a plugin.
 */
internal fun ComposeKeyEvent.toForwardedKey(hostKeymap: KeymapMatcher): WidgetEvent.Key? {
    // One conjunction rather than three guard clauses: `&&` short-circuits, so the keymap lookup — the
    // only expensive test of the three — still runs at most once per real key press.
    val forwardable =
        type == KeyEventType.KeyDown &&
            key.nativeKeyCode !in MODIFIER_ONLY_KEYS &&
            hostKeymap.match(this, ShortcutContext.GLOBAL) == null
    return if (!forwardable) {
        null
    } else {
        WidgetEvent.Key(
            keyCode = key.nativeKeyCode,
            ctrl = isCtrlPressed,
            alt = isAltPressed,
            shift = isShiftPressed,
            meta = isMetaPressed,
        )
    }
}

/**
 * Keys that only ever qualify another key.
 *
 * A superset of `AWTKeyboardInterceptor.isModifierOnlyKey`, which lacks `VK_ALT_GRAPH`. Kept as its own
 * list rather than shared with the interceptor because the two answer different questions — the
 * interceptor asks "can this be a shortcut", this asks "is this an event a plugin wants" — and the
 * answers only mostly coincide.
 */
private val MODIFIER_ONLY_KEYS =
    setOf(
        AwtKeyEvent.VK_SHIFT,
        AwtKeyEvent.VK_CONTROL,
        AwtKeyEvent.VK_ALT,
        AwtKeyEvent.VK_ALT_GRAPH,
        AwtKeyEvent.VK_META,
        AwtKeyEvent.VK_CAPS_LOCK,
        AwtKeyEvent.VK_NUM_LOCK,
        AwtKeyEvent.VK_SCROLL_LOCK,
    )

/**
 * Report [scrollState]'s movement to the plugin as coalesced deltas.
 *
 * The whole policy is in [ScrollCoalescer] — one event per window, and the resting position always
 * delivered — so that it is testable without a UI toolkit and so the Rust renderer has one spec to
 * mirror. This is only the wiring: a `snapshotFlow` over the offset, which already emits at most once
 * per frame, feeding the coalescer.
 *
 * Keyed on the node id so a tree update that replaces the node restarts the reporter against its new
 * state instead of publishing another node's offsets, and scoped to the composition so a closed
 * surface leaves nothing running.
 */
@Composable
internal fun ReportScrollPosition(
    nodeId: String,
    scrollState: ScrollState,
    onEvent: (nodeId: String, event: WidgetEvent) -> Unit,
) {
    LaunchedEffect(nodeId, scrollState) {
        ScrollCoalescer
            .coalesce(snapshotFlow { ScrollOffset(x = 0f, y = scrollState.value.toFloat()) })
            .collect { scroll -> onEvent(nodeId, scroll) }
    }
}
