package ai.rever.boss.layout

import ai.rever.boss.window.TabBarPosition
import ai.rever.boss.window.WindowAppearanceSettings
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height a macOS window must keep clear at its top-LEFT for the traffic lights.
 *
 * The window sets `apple.awt.fullWindowContent`, so the close / minimise / zoom buttons are drawn
 * over the content rather than in a strip above it. 28dp covers the buttons and the few points of
 * air macOS leaves around them.
 */
val TRAFFIC_LIGHT_HEIGHT: Dp = 28.dp

/**
 * Width of the same box. Not read by the layout - the column being inset is narrower than this
 * anyway - but stated because it is why a full-width row was the wrong shape: the lights occupy
 * one corner, not a band.
 */
val TRAFFIC_LIGHT_WIDTH: Dp = 78.dp

/**
 * Which piece of chrome has to keep clear of the traffic lights, if any.
 *
 * The old answer was "a 26dp row across the whole window", which is `BossTitleBar`'s only job -
 * its content is a centred title string. That reserves the full width of the window to protect one
 * corner, and on a 1400dp window that is about 37,000 square dp spent on a 78dp box.
 *
 * So the row is gone and the inset goes on whatever column is leftmost, which is the only thing
 * the lights can actually land on. The rest of the window starts at the top.
 */
enum class TrafficLightInset {
    /** No inset: not macOS, or the title row is on and already holds them. */
    NONE,

    /** The left icon strip is leftmost. */
    LEFT_STRIP,

    /** No left strip, but the tab bar runs down the left edge. */
    VERTICAL_TAB_BAR,

    /**
     * Nothing is down the left side, so there is no column to inset and the content itself is
     * under the lights.
     *
     * The caller keeps the full-width reserve for this case. Padding the content area instead
     * would cost the same height across the whole width - no saving - and the content is where a
     * browser's native surface lives, which is not something to leave a hole in.
     */
    CONTENT,
}

/**
 * Where the traffic-light clearance belongs for these settings.
 *
 * Pure, so the four cases are a table rather than a conditional buried in the scaffold. Getting it
 * wrong is not subtle - the buttons land on a tab bar's Favorites shelf, or a 28dp gap opens above
 * a column that did not need one - but it is only visible on macOS, which is not where most of
 * this is developed.
 */
fun macTrafficLightInset(
    appearance: WindowAppearanceSettings,
    isMacOs: Boolean,
): TrafficLightInset =
    when {
        // Not macOS: the lights are somebody else's problem, and on Windows and Linux the title
        // row is a normal bar above the content rather than an overlay on top of it.
        !isMacOs -> {
            TrafficLightInset.NONE
        }

        // The row is on and is exactly what it is for.
        appearance.showTitleBar -> {
            TrafficLightInset.NONE
        }

        appearance.showLeftStrip -> {
            TrafficLightInset.LEFT_STRIP
        }

        appearance.tabBarPosition == TabBarPosition.LEFT -> {
            TrafficLightInset.VERTICAL_TAB_BAR
        }

        else -> {
            TrafficLightInset.CONTENT
        }
    }

/**
 * The clearance to apply to [column], which is [TRAFFIC_LIGHT_HEIGHT] when the lights belong
 * to it and nothing otherwise.
 *
 * A function rather than an `if` at each call site: the scaffold reads this twice and is already
 * at detekt's complexity ceiling, and two inline conditionals that must stay opposites are two
 * places for them to stop being opposites.
 *
 * The names are short for a duller reason. ktlint allows 140 columns and requires an expression
 * body where one fits; detekt allows 120. At the original names the one-line form was 128, which
 * neither linter would accept in the other's shape - so the names came down until it fit.
 */
fun TrafficLightInset.clearance(column: TrafficLightInset): Dp = if (this == column) TRAFFIC_LIGHT_HEIGHT else 0.dp

/**
 * Whether the full-width title row still has to be drawn.
 *
 * True when the user asked for it, and true for [TrafficLightInset.CONTENT] - there is no
 * column to inset, so the row is what keeps the lights off the content.
 */
fun TrafficLightInset.needsTitleRow(showTitleBar: Boolean): Boolean = showTitleBar || this == TrafficLightInset.CONTENT
