package ai.rever.boss.components.settings.search

import ai.rever.boss.components.settings.shared.SettingsTheme.SurfaceColor
import ai.rever.boss.plugin.ui.BossThemeController
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/**
 * The `SettingsSection(title = ...)` a control is nested inside, or null at the top of a page.
 *
 * `compositionLocalOf`, not `staticCompositionLocalOf`: the static flavour invalidates its whole
 * subtree when the value changes, which is right for the constants the codebase uses it for
 * ([ai.rever.boss.plugin.ui.LocalBossColors], `LocalHeavyweightOverlays`) and wrong for a value
 * that changes per group and per search.
 */
internal val LocalSettingsGroup = compositionLocalOf<String?> { null }

/** What to highlight right now, or null. See [LocalSettingsGroup] for the choice of flavour. */
internal val LocalSettingsHighlight = compositionLocalOf<SettingsHighlight?> { null }

/**
 * Paints a settings row's surface, and marks it as the control named [label] so search can scroll
 * to it and flash it.
 *
 * It does both jobs rather than sitting next to `.background(SurfaceColor)` because the wash has to
 * paint *over* that surface, which makes the two calls order-dependent - and because a separate
 * line in all 12 shared controls pushed `SettingsTextField` over detekt's function-length limit for
 * no reason a reader would ever guess from the diff.
 *
 * **The control brings itself into view.** The obvious alternative - have the window record every
 * control's y-offset and scroll the container - has to solve a race it cannot see: on the frame the
 * section changes, the target does not exist yet, so the scroll runs against the outgoing section's
 * layout. Here the effect lives inside the target, so it cannot run before that target has been
 * composed and placed, and there are no offsets to keep.
 *
 * Matching is on ([LocalSettingsGroup], [label]) rather than on the label alone, because Performance
 * has two "Warning Threshold" rows and a label-only match would light both and scroll to whichever
 * came last.
 */
@Composable
internal fun Modifier.settingsRowSurface(label: String): Modifier = searchTarget(label, surface = SurfaceColor)

/**
 * Marks a `SettingsSection` group header as the search target named [title], painting no surface.
 *
 * Separate from [settingsRowSurface] precisely because of the background: a group container has
 * always been transparent, and giving it a filled surface would put a panel-coloured slab behind
 * every heading and its whole body - a visible restyle of the entire Settings window smuggled in
 * behind a search feature.
 */
@Composable
internal fun Modifier.settingsGroupTarget(title: String): Modifier = searchTarget(title, surface = null)

@Composable
private fun Modifier.searchTarget(
    label: String,
    surface: Color?,
): Modifier {
    val highlight = LocalSettingsHighlight.current
    val group = LocalSettingsGroup.current
    val matched = highlight != null && highlight.label == label && highlight.group == group

    // Hooks are allocated unconditionally: `remember` calls cannot sit behind a condition that
    // changes between compositions without shifting every later slot in this control's group.
    val requester = remember { BringIntoViewRequester() }
    val wash = remember { Animatable(0f) }

    LaunchedEffect(highlight?.nonce, matched) {
        if (!matched) {
            if (wash.value != 0f) wash.snapTo(0f)
            return@LaunchedEffect
        }
        // Wait out the frame that composed us. A LaunchedEffect runs after composition but can
        // still beat layout, and asking to be brought into view before anything has been placed
        // scrolls against stale (or zero) bounds and reports success either way - the silent
        // half-failure this whole approach exists to avoid.
        withFrameNanos { }
        requester.bringIntoView()
        wash.snapTo(1f)
        wash.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = FADE_MILLIS, delayMillis = HOLD_MILLIS),
        )
    }

    val base =
        (if (surface != null) background(surface) else this)
            .bringIntoViewRequester(requester)
    if (!matched) return base

    // Tagged only on the matched branch, so "exactly one control lights up" is assertable rather
    // than argued: the duplicate "Warning Threshold" rows in Performance are one composition apart,
    // and a highlight keyed on the label alone would tag both.
    val tagged = base.testTag(HIGHLIGHT_TEST_TAG)
    val alpha = wash.value
    return if (alpha <= 0f) {
        tagged
    } else {
        tagged.background(
            BossThemeController.current.colors.signalWash
                .copy(alpha = alpha),
        )
    }
}

/** Carried by the single control the search is pointing at. See [settingsRowSurface]. */
internal const val HIGHLIGHT_TEST_TAG = "settings-search-highlight"

/** Long enough to find the row with your eyes before it starts going. */
private const val HOLD_MILLIS = 900

/** Long enough that the fade reads as an answer rather than as a flicker. */
private const val FADE_MILLIS = 1400
