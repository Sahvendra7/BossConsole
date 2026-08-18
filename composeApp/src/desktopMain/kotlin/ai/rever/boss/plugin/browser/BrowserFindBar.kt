package ai.rever.boss.plugin.browser

import ai.rever.boss.plugin.ui.BossTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdev.jxbrowser.browser.Browser
import kotlin.math.roundToInt

/** Height of the bar, and the size the corner overlay opens at before it measures. */
internal val FIND_BAR_HEIGHT = 34.dp

/**
 * Upper bound the corner overlay is measured against.
 *
 * A ceiling, not a first guess: `HeavyweightCorner` measures content against this and never
 * against its own window, so anything wider is CLIPPED rather than merely mis-sized on the first
 * frame. Comfortably above the bar's natural width, which is the field plus a counter and four
 * icon buttons.
 */
internal val FIND_BAR_CEILING = DpSize(360.dp, 60.dp)

/**
 * Gap between the bar and the pane's top and end edges, in dp.
 *
 * Applied by shrinking the anchor REGION rather than by padding the bar. Padding would work on the
 * lightweight path and be actively wrong on the heavyweight one: that overlay window is sized to
 * its content, so transparent padding grows the window, and a non-focusable AWT window still eats
 * every click under it (the JVM has no portable click-through). The margin would become a dead
 * strip across the top-right of the page.
 */
private const val FIND_BAR_MARGIN_DP = 8

/**
 * The rectangle a pane's find bar anchors inside: [boundsInWindow] converted to dp and pulled in
 * by [FIND_BAR_MARGIN_DP], or null when there is nowhere sensible to put it.
 *
 * Null is a real answer, not a failure. `boundsInWindow` reports CLIPPED bounds, so a pane scrolled
 * or collapsed out of view measures empty - and `HeavyweightCorner` resolves an unmeasurable parent
 * to the screen origin, which would put an always-on-top bar in the corner of the primary display
 * instead of over the page.
 */
@Suppress("ReturnCount")
internal fun findBarRegion(
    boundsInWindow: Rect,
    density: Float,
): IntRect? {
    if (density <= 0f || !density.isFinite()) return null
    if (!boundsInWindow.hasArea()) return null
    val region =
        IntRect(
            left = (boundsInWindow.left / density).roundToInt(),
            top = (boundsInWindow.top / density).roundToInt(),
            right = (boundsInWindow.right / density).roundToInt(),
            bottom = (boundsInWindow.bottom / density).roundToInt(),
        ).deflate(FIND_BAR_MARGIN_DP)
    // A pane narrower than twice the margin deflates to nothing. Better no bar than one placed at
    // an inverted rectangle's corner.
    return region.takeIf { it.width > 0 && it.height > 0 }
}

/**
 * Whether this rectangle describes a real, finite area.
 *
 * `boundsInWindow` can be empty (a clipped-away pane) and, for a node in a degenerate layout, can
 * carry infinities - which survive the division by density and turn into a garbage `IntRect`.
 */
private fun Rect.hasArea(): Boolean {
    val w = width
    val h = height
    return w.isFinite() && h.isFinite() && w > 0f && h > 0f
}

/**
 * Find-in-page bar for one browser pane.
 *
 * Replaces the Swing `JDialog` this used to be. Three things that were wrong there are properties
 * of being Compose rather than fixes applied to it: colours come from [BossTheme] on every
 * recomposition (the dialog snapshotted them at construction, so a theme switch never reached an
 * open bar), the bar sizes itself, and it is placed by its caller against the browser pane rather
 * than against the whole window.
 *
 * Owns no state. Everything is read from [state] and every change is a call into
 * [BrowserFindController], so the two paths that can open this bar cannot end up with two
 * different notions of what is being searched.
 */
@Composable
internal fun BrowserFindBar(
    browser: Browser,
    state: BrowserFindState,
) {
    val colors = BossTheme.colors

    // The field's own value, so the caret and selection live where the user put them. `state.query`
    // is the authority on WHAT is searched and is written back on every edit; mirroring the caret
    // into it as well would move the caret to the end on every recomposition driven by a search
    // result arriving.
    var field by remember { mutableStateOf(TextFieldValue(state.query)) }

    Row(
        modifier =
            Modifier
                .height(FIND_BAR_HEIGHT)
                .background(colors.panel, RoundedCornerShape(6.dp))
                .border(1.dp, colors.line, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FindQueryField(
            browser = browser,
            state = state,
            field = field,
            onFieldChange = { field = it },
        )

        Spacer(Modifier.width(8.dp))

        FindMatchCounter(state = state, hasQuery = field.text.isNotEmpty())

        FindBarActions(browser = browser, state = state)
    }
}

/** The query field, and every key the bar answers while it has focus. */
@Composable
private fun FindQueryField(
    browser: Browser,
    state: BrowserFindState,
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
) {
    val colors = BossTheme.colors
    val focusRequester = remember { FocusRequester() }

    // Cmd+F with the bar already open re-focuses and selects, which is what Chrome, Safari and
    // Firefox all do. Keyed on the tick so a second request is a second event.
    LaunchedEffect(state.focusTick) {
        onFieldChange(field.copy(selection = TextRange(0, field.text.length)))
        runCatching { focusRequester.requestFocus() }
    }

    BasicTextField(
        value = field,
        onValueChange = {
            onFieldChange(it)
            BrowserFindController.setQuery(browser, it.text)
        },
        singleLine = true,
        textStyle =
            MaterialTheme.typography.body2.copy(
                color = colors.textPrimary,
                fontSize = 13.sp,
            ),
        cursorBrush = SolidColor(colors.signal),
        modifier =
            Modifier
                .width(150.dp)
                .focusRequester(focusRequester)
                // onPreviewKeyEvent, not onKeyEvent: Enter and Escape are handled BEFORE the text
                // field sees them, or Enter inserts a newline into a single-line field (which
                // silently does nothing) instead of advancing to the next match.
                .onPreviewKeyEvent { event -> handleFindBarKey(browser, event) },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (field.text.isEmpty()) {
                    Text(
                        text = "Find in page",
                        color = colors.textMuted,
                        fontSize = 13.sp,
                    )
                }
                inner()
            }
        },
    )
}

/**
 * The keys the field claims. Extracted so the decision is one readable table rather than a modifier
 * lambda inside a modifier chain.
 */
private fun handleFindBarKey(
    browser: Browser,
    event: KeyEvent,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        event.key == Key.Escape -> {
            BrowserFindController.close(browser)
            true
        }

        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
            submitFind(browser, backward = event.isShiftPressed)
            true
        }

        // Cmd+G / Shift+Cmd+G, the macOS "find again" pair. Handled here as well as in the key
        // callback because while this field holds focus the app's AWT interceptor resolves no
        // window (the bar is its own always-on-top window) and so routes nothing.
        event.key == Key.G && event.isMetaPressed -> {
            submitFind(browser, backward = event.isShiftPressed)
            true
        }

        else -> {
            false
        }
    }
}

private fun submitFind(
    browser: Browser,
    backward: Boolean,
) {
    if (backward) BrowserFindController.previous(browser) else BrowserFindController.next(browser)
}

/**
 * `n/total`, or nothing at all.
 *
 * Blank until a search for the CURRENT query has settled. Chromium reports progress updates while
 * a search runs, and rendering those is what made the old Swing bar flash a red `0/0` on every
 * keystroke.
 */
@Composable
private fun FindMatchCounter(
    state: BrowserFindState,
    hasQuery: Boolean,
) {
    val colors = BossTheme.colors
    val show = state.settled && hasQuery
    Text(
        text = if (show) "${state.currentMatch}/${state.totalMatches}" else "",
        color = if (show && state.totalMatches == 0) colors.alert else colors.textSecondary,
        fontSize = 11.sp,
        modifier = Modifier.width(46.dp),
    )
}

/** Previous, next, match-case and close. */
@Composable
private fun FindBarActions(
    browser: Browser,
    state: BrowserFindState,
) {
    val colors = BossTheme.colors
    FindBarIcon(
        icon = Icons.Filled.KeyboardArrowUp,
        description = "Previous match",
        enabled = state.totalMatches > 0,
        tint = colors.textPrimary,
        disabledTint = colors.textMuted,
        onClick = { submitFind(browser, backward = true) },
    )
    FindBarIcon(
        icon = Icons.Filled.KeyboardArrowDown,
        description = "Next match",
        enabled = state.totalMatches > 0,
        tint = colors.textPrimary,
        disabledTint = colors.textMuted,
        onClick = { submitFind(browser, backward = false) },
    )

    // Text rather than an icon: there is no "match case" glyph in the bundled Material set, and
    // "Aa" is what every browser's find bar uses for it.
    IconButton(
        onClick = { BrowserFindController.setMatchCase(browser, !state.matchCase) },
        modifier = Modifier.size(26.dp),
    ) {
        Text(
            text = "Aa",
            color = if (state.matchCase) colors.signalText else colors.textMuted,
            fontSize = 12.sp,
            fontWeight = if (state.matchCase) FontWeight.Bold else FontWeight.Normal,
        )
    }

    FindBarIcon(
        icon = Icons.Filled.Close,
        description = "Close find bar",
        enabled = true,
        tint = colors.textPrimary,
        disabledTint = colors.textMuted,
        onClick = { BrowserFindController.close(browser) },
    )
}

@Composable
private fun FindBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    disabledTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(26.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = if (enabled) tint else disabledTint,
        )
    }
}
