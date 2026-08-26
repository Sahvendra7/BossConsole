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
    /**
     * Whether the vertical bar is down to its slim rail, which changes what it can cover.
     *
     * A rail is one [ChromeDimens.stripWidth], so on its own it is narrower than the light box and
     * cannot protect the corner - the lights spill onto whatever is beside it, which is a browser
     * pane and cannot be inset at all. That case falls back to the title row.
     *
     * Read from `tabBarCollapsed`, the standing preference. **Known gap**: the bar also rails
     * itself when a panel is too narrow (`TabBarLayout.railShown`), and that is decided during
     * layout where this is not, so an auto-collapsed bar on a narrow window still overlaps.
     * Closing it means this decision moving to where the layout is known.
     */
    barCollapsed: Boolean = false,
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

        // Only when the columns are actually wide enough to hold the box. Insetting chrome that
        // is narrower than the lights protects part of the corner and leaves the rest over the
        // content, which is worse than not trying: it looks deliberate.
        leftChromeWidth(appearance, barCollapsed) >= TRAFFIC_LIGHT_WIDTH -> {
            TrafficLightInset.LEFT_COLUMNS
        }

        else -> {
            TrafficLightInset.CONTENT
        }
    }

/** The top inset for a left-hand column, which is the height of the box or nothing. */
fun TrafficLightInset.columnInset(): Dp = if (this == TrafficLightInset.LEFT_COLUMNS) TRAFFIC_LIGHT_HEIGHT else 0.dp

/**
 * The start indent for the update banner, which sits above everything else when it is up.
 *
 * Any answer but [TrafficLightInset.NONE] and [TrafficLightInset.CONTENT] means there is no title
 * row, so the banner is the topmost chrome and the lights are drawn over it. CONTENT keeps the
 * row, which holds them, and NONE means there is nothing to clear.
 */
fun TrafficLightInset.bannerStartInset(): Dp =
    when (this) {
        TrafficLightInset.TOP_BAR, TrafficLightInset.LEFT_COLUMNS -> TRAFFIC_LIGHT_WIDTH
        TrafficLightInset.NONE, TrafficLightInset.CONTENT -> 0.dp
    }

/** The start indent for the top bar, which is the width of the box or nothing. */
fun TrafficLightInset.barStartInset(): Dp = if (this == TrafficLightInset.TOP_BAR) TRAFFIC_LIGHT_WIDTH else 0.dp

/**
 * Whether the full-width title row still has to be drawn.
 *
 * True when the user asked for it, and true for [TrafficLightInset.CONTENT] - there is no chrome
 * to inset, so the row is what keeps the lights off the content.
 */
fun TrafficLightInset.needsTitleRow(showTitleBar: Boolean): Boolean = showTitleBar || this == TrafficLightInset.CONTENT

/**
 * How much chrome runs down the window's left edge, which is what decides whether the corner can
 * be protected by insetting columns at all.
 *
 * The icon strip is one [ChromeDimens.stripWidth]; the vertical tab bar is its configured width,
 * or the same rail width when collapsed. A bar in TOP position contributes nothing.
 */
internal fun leftChromeWidth(
    appearance: WindowAppearanceSettings,
    barCollapsed: Boolean,
): Dp {
    val strip = if (appearance.showLeftStrip) ChromeDimens.MIN_STRIP_WIDTH else 0.dp
    val bar =
        when {
            appearance.tabBarPosition != TabBarPosition.LEFT -> 0.dp
            barCollapsed -> ChromeDimens.MIN_STRIP_WIDTH
            else -> appearance.tabBarVerticalWidth.dp
        }
    return strip + bar
}
