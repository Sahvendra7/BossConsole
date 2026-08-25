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
 * The lights occupy a BOX - about 78dp wide and 28dp tall in the window's top-left corner - not a
 * band across the top. The old answer was a 26dp full-width row (`BossTitleBar`, whose only
 * content is a centred title string), which reserves the whole width to protect one corner.
 *
 * So the clearance goes on whatever is actually under that box, and which chrome that is depends
 * on what is switched on. Two cases were missed on the first attempt and both were visible:
 *
 * - The **top bar** spans the full width at y=0, so when it is on it is what the lights land on -
 *   no column is. Its leftmost control sat under the green button.
 * - The lights are **wider than one column**. An icon strip is 40dp, so a 78dp box covers the
 *   whole strip and another 38dp of whatever is beside it - which is the vertical tab bar. Insetting
 *   only the strip left the second half of the box over the bar's Favorites header.
 */
enum class TrafficLightInset {
    /** No inset: not macOS, or the title row is on and already holds them. */
    NONE,

    /**
     * The top bar is under them. It needs a horizontal indent of [TRAFFIC_LIGHT_WIDTH], not a
     * vertical one - the bar is a row, and the lights sit at its start.
     */
    TOP_BAR,

    /**
     * The columns down the left edge are under them: the icon strip, the vertical tab bar, or
     * both. Every one of them that falls inside [TRAFFIC_LIGHT_WIDTH] takes a top inset, which in
     * practice is both when both are on, since a strip alone is narrower than the box.
     */
    LEFT_COLUMNS,

    /**
     * Nothing is up there but content, so there is no chrome to inset.
     *
     * The caller keeps the full-width title row for this case. Padding the content instead would
     * cost the same height across the whole width - no saving - and the content is where a
     * browser's native surface lives, which is not something to leave a hole in.
     */
    CONTENT,
}

/**
 * Where the traffic-light clearance belongs for these settings.
 *
 * Pure, so the cases are a table rather than a conditional buried in the scaffold. Each wrong
 * answer is a visible defect - the buttons over a tab bar's Favorites header, over the top bar's
 * first control, or a 28dp gap above a column that needed none - and all of them are only visible
 * on macOS, which is not where most of this is developed.
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

        // Asked BEFORE the columns: the top bar is above them, so when it is on, it is what is
        // under the lights and the columns start below the box entirely.
        appearance.showTopBar -> {
            TrafficLightInset.TOP_BAR
        }

        appearance.showLeftStrip || appearance.tabBarPosition == TabBarPosition.LEFT -> {
            TrafficLightInset.LEFT_COLUMNS
        }

        else -> {
            TrafficLightInset.CONTENT
        }
    }

/** The top inset for a left-hand column, which is the height of the box or nothing. */
fun TrafficLightInset.columnInset(): Dp = if (this == TrafficLightInset.LEFT_COLUMNS) TRAFFIC_LIGHT_HEIGHT else 0.dp

/** The start indent for the top bar, which is the width of the box or nothing. */
fun TrafficLightInset.barStartInset(): Dp = if (this == TrafficLightInset.TOP_BAR) TRAFFIC_LIGHT_WIDTH else 0.dp

/**
 * Whether the full-width title row still has to be drawn.
 *
 * True when the user asked for it, and true for [TrafficLightInset.CONTENT] - there is no chrome
 * to inset, so the row is what keeps the lights off the content.
 */
fun TrafficLightInset.needsTitleRow(showTitleBar: Boolean): Boolean = showTitleBar || this == TrafficLightInset.CONTENT
