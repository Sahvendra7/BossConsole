package ai.rever.boss.fullscreen

import ai.rever.boss.keymap.KeymapSettingsManager
import ai.rever.boss.keymap.model.KeymapActions
import ai.rever.boss.keymap.model.KeymapSettings
import ai.rever.boss.plugin.ui.BossTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** How long the bar stays up after entering, before it takes the pointer to bring it back. */
internal const val HUD_DWELL_MS = 6000L

/** Height of the strip at the top of the window that reveals the bar. */
internal val HUD_REVEAL_STRIP_HEIGHT = 24.dp

/**
 * What the reminder says, given the live keymap.
 *
 * Pure and separate from the composable so the wording can be tested, and because the one thing it
 * must never do is go stale: both escapes are rebindable, so a hardcoded string would confidently
 * tell a user to press a combination that no longer does anything. The hold-Escape line is a
 * literal precisely because that one cannot be rebound.
 */
internal fun capturedHudLines(
    settings: KeymapSettings,
    limitations: Set<CaptureLimitation>,
): List<String> {
    val exit = settings.getBinding(KeymapActions.CAPTURED_FULLSCREEN_TOGGLE)?.displayString()
    val release = settings.getBinding(KeymapActions.POINTER_RELEASE)?.displayString()

    val lines = mutableListOf<String>()
    // Falls back to the hold when the action has been unbound entirely, rather than printing an
    // empty chord: an unbound exit is exactly when someone needs to be told the other way out.
    lines += if (exit != null) "$exit to leave captured full screen" else "Hold Esc to leave captured full screen"
    if (release != null) lines += "$release to release the pointer"
    lines += "Hold Esc for 2 seconds if you get stuck"

    limitations.forEach { limitation ->
        lines +=
            when (limitation) {
                CaptureLimitation.POINTER_NOT_CONFINED -> "Note: the pointer could not be confined on this system"
                CaptureLimitation.KEYBOARD_NOT_GRABBED -> "Note: your system shortcuts are still active"
                CaptureLimitation.WAYLAND_NO_GRAB -> "Note: Wayland does not allow confining the pointer"
            }
    }
    return lines
}

/**
 * The control bar for a captured session: shown on entry, and again whenever the pointer reaches the
 * top edge.
 *
 * ## Why it carries Settings, Toolbox, Search and Sign Out
 *
 * The first version of this mode hid them, on the reasoning that the display should hold nothing but
 * content. That reasoning was wrong, and in a way this repository has already paid for once. The
 * mode hides the menu bar through the macOS presentation options **and** every bar the window
 * draws, so with the actions gone as well:
 *
 * - **Toolbox had no route at all** on macOS. It is a menu-bar menu, and the menu bar is hidden.
 * - **Sign Out had no route at all**, on any platform. It is raised only from the top bar and from
 *   the quick-actions cluster, and it has no keyboard shortcut - the native View menu has no item
 *   for it either.
 *
 * That is exactly the regression `docs/release-notes/v9.4.13.md:47` records, where hiding the top
 * bar left Sign Out rendered nowhere, arrived at a second time by a different route.
 *
 * They live **here** rather than in the floating quick-actions cluster because that cluster is a
 * heavyweight always-on-top window with no click-through: permanently over full-screen content is
 * the one place it must not be. A reveal bar costs nothing until the pointer asks for it, which is
 * also how Parallels surfaces its own controls in the same mode.
 *
 * ## The Windows caveat
 *
 * The strip is ordinary Compose hover, so it shares the gap the focus-mode strips document: under a
 * HARDWARE_ACCELERATED browser surface the pointer never crosses it, because the page composites
 * above the Compose scene. That is why the bar dwells for [HUD_DWELL_MS] on entry rather than
 * relying on the reveal, and why the hold-Escape line is always present rather than shown only when
 * something has gone wrong.
 *
 * @param exitButton the blue button, which leaves the mode.
 * @param actions Settings / Toolbox / Search / Sign Out, built by `focusQuickActionButtons` so this
 *   bar and the ordinary chrome cannot show a different set.
 */
@Composable
fun BoxScope.CapturedFullScreenHud(
    session: CapturedFullScreen,
    exitButton: (@Composable () -> Unit)? = null,
    actions: List<@Composable () -> Unit> = emptyList(),
) {
    if (!session.active) return

    val keymap by KeymapSettingsManager.currentSettings.collectAsState()
    val stripInteraction = remember { MutableInteractionSource() }
    val barInteraction = remember { MutableInteractionSource() }
    val nearTop by stripInteraction.collectIsHoveredAsState()
    val onBar by barInteraction.collectIsHoveredAsState()
    var dwelling by remember { mutableStateOf(true) }

    // Restarted per session, so re-entering shows the bar again.
    LaunchedEffect(session.windowId) {
        dwelling = true
        delay(HUD_DWELL_MS)
        dwelling = false
    }

    // Always present while captured, even while the bar is up, so moving to the top edge holds it
    // open instead of letting it time out from under the pointer.
    Box(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(HUD_REVEAL_STRIP_HEIGHT)
                .hoverable(stripInteraction),
    )

    AnimatedVisibility(
        visible = dwelling || nearTop || onBar,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
    ) {
        CapturedControlBar(
            lines = capturedHudLines(keymap, session.limitations),
            exitButton = exitButton,
            actions = actions,
            // Hovering the bar itself keeps it up, so a pointer travelling from the strip down to a
            // button does not dismiss the thing it is reaching for.
            modifier = Modifier.hoverable(barInteraction),
        )
    }
}

/** The revealed card: the way out, the actions the hidden chrome owned, and the shortcut reminder. */
@Composable
private fun CapturedControlBar(
    lines: List<String>,
    exitButton: (@Composable () -> Unit)?,
    actions: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BossTheme.colors.raised,
        elevation = 8.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (exitButton != null || actions.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    exitButton?.invoke()
                    if (exitButton != null && actions.isNotEmpty()) {
                        Divider(color = BossTheme.colors.line, modifier = Modifier.height(20.dp).width(1.dp))
                    }
                    actions.forEach { it() }
                }
            }

            lines.forEachIndexed { index, line ->
                Text(
                    text = line,
                    color = if (index == 0) BossTheme.colors.textPrimary else BossTheme.colors.textSecondary,
                    fontSize = if (index == 0) 13.sp else 11.sp,
                    fontWeight = if (index == 0) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
