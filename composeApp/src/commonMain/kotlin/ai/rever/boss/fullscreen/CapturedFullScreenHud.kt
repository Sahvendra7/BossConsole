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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** How long the reminder stays up after entering. */
internal const val HUD_DWELL_MS = 6000L

/** Height of the strip at the top of the window that brings the reminder back. */
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
    lines +=
        if (exit != null) "$exit to leave captured full screen" else "Hold Esc to leave captured full screen"
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
 * The reminder shown on entering captured full screen, and again when the pointer goes to the top
 * edge.
 *
 * **Not decoration.** The mode hides the menu bar, so on macOS the View menu is not reachable while
 * it runs, and the chrome that holds the blue button is gone by design - which leaves the two
 * shortcuts and the hardwired hold as the only ways out. Something has to say what they are.
 *
 * The top-edge strip is the second chance for anyone who missed the first. It is a plain Compose
 * hover, so it shares the known gap the focus-mode strips document: on Windows the pointer never
 * crosses it over a HARDWARE_ACCELERATED browser surface. That is the reason the reminder dwells
 * for [HUD_DWELL_MS] on entry rather than relying on the strip, and the reason the hold-Escape line
 * is always present rather than shown only when something has gone wrong.
 */
@Composable
fun BoxScope.CapturedFullScreenHud(session: CapturedFullScreen) {
    if (!session.active) return

    val keymap by KeymapSettingsManager.currentSettings.collectAsState()
    val stripInteraction = remember { MutableInteractionSource() }
    val nearTop by stripInteraction.collectIsHoveredAsState()
    var dwelling by remember { mutableStateOf(true) }

    // Restarted per session, so re-entering shows the reminder again.
    LaunchedEffect(session.windowId) {
        dwelling = true
        delay(HUD_DWELL_MS)
        dwelling = false
    }

    // The strip is always present while captured, even while the card is up, so moving to the top
    // edge holds the card open instead of letting it time out under the pointer.
    Box(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(HUD_REVEAL_STRIP_HEIGHT)
                .hoverable(stripInteraction),
    )

    AnimatedVisibility(
        visible = dwelling || nearTop,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BossTheme.colors.raised,
            elevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                capturedHudLines(keymap, session.limitations).forEachIndexed { index, line ->
                    Text(
                        text = line,
                        color = if (index == 0) BossTheme.colors.textPrimary else BossTheme.colors.textSecondary,
                        fontSize = if (index == 0) 14.sp else 12.sp,
                        fontWeight = if (index == 0) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
