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
     * The update banner is up, and it is drawn above every bar and column, so it is what the
     * lights land on.
     *
     * This replaces [TOP_BAR] and [LEFT_COLUMNS] for as long as the banner exists, and replacing
     * is the whole point: the clearance belongs to exactly ONE piece of chrome. Leaving the
     * columns their inset while the banner took its own opened an empty 28dp band under the
     * banner, above the tab bar's Favorites shelf - clearance for lights that were no longer
     * there.
     *
     * It does NOT replace [NONE], which is what a title row being on produces: that row is ABOVE
     * the banner, so it goes on holding the lights and the banner needs nothing.
     */
    BANNER,
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
     * Whether the update banner is currently drawing. See [TrafficLightInset.BANNER].
     *
     * Take it from [ai.rever.boss.updater.drawsBanner] rather than from "an update exists": most
     * of `UpdateState` draws no banner at all, and insetting for a banner that is not there is the
     * same empty band by another route.
     */
    bannerVisible: Boolean = false,
): TrafficLightInset {
    val base =
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

            // Everything else: the lights are over the window's left edge, so every column that
            // falls inside the box keeps clear of it - see [columnInset], which each caller asks
            // with its own offset.
            //
            // No width test any more, and no title-row fallback. Requiring the columns to add up
            // to TRAFFIC_LIGHT_WIDTH first meant a collapsed rail - 40dp, and what the shipped
            // default becomes as soon as the bar is collapsed - fell through to a full-width 26dp
            // title row, so "Show Title Bar = off" was not true on macOS. It also made that row
            // appear and disappear on a window RESIZE, since the bar rails itself as the window
            // narrows: the content jumped 26dp mid-drag.
            //
            // What the columns do not cover is the honest remainder - the lights sit over
            // whatever is beside them, which macOS draws and handles either way.
            else -> {
                TrafficLightInset.LEFT_COLUMNS
            }
        }

    // The banner takes over from whatever chrome it is drawn above, and only from that chrome.
    val coverable = base == TrafficLightInset.TOP_BAR || base == TrafficLightInset.LEFT_COLUMNS
    return if (bannerVisible && coverable) TrafficLightInset.BANNER else base
}

/**
 * The top inset for a left-hand column that starts [offsetFromLeft] in from the window's edge.
 *
 * The offset is what makes this per-COLUMN rather than one answer for all of them. They run strip,
 * then an open plugin panel, then the vertical tab bar, and the box is only 78dp wide - so which
 * of them it covers depends on what is switched on. Insetting "the columns" as a group was right
 * only while the tab bar was second: open a plugin panel and the panel is second, and the lights
 * landed on its header while the bar behind it kept a 28dp gap it did not need.
 */
fun TrafficLightInset.columnInset(offsetFromLeft: Dp = 0.dp): Dp =
    if (this == TrafficLightInset.LEFT_COLUMNS && offsetFromLeft < TRAFFIC_LIGHT_WIDTH) {
        TRAFFIC_LIGHT_HEIGHT
    } else {
        0.dp
    }

/**
 * The start indent for the update banner.
 *
 * Non-zero for exactly [TrafficLightInset.BANNER], which is the answer only when the banner is
 * both up and topmost. The banner is a row, so it takes a horizontal indent; the height it has to
 * keep is enforced where it is drawn.
 */
fun TrafficLightInset.bannerStartInset(): Dp = if (this == TrafficLightInset.BANNER) TRAFFIC_LIGHT_WIDTH else 0.dp

/** The start indent for the top bar, which is the width of the box or nothing. */
fun TrafficLightInset.barStartInset(): Dp = if (this == TrafficLightInset.TOP_BAR) TRAFFIC_LIGHT_WIDTH else 0.dp

/**
 * Whether the full-width title row has to be drawn: only when the user asked for it.
 *
 * It used to be drawn as a FALLBACK as well, whenever the left columns came to less than the light
 * box. Two things were wrong with that. "Show Title Bar = off" stopped being true on macOS the
 * moment the tab bar was collapsed, which is one click and the state a lot of windows sit in. And
 * because the bar also rails itself when a window narrows, the row appeared and vanished on a
 * RESIZE - a 26dp jump of the whole window's content, mid-drag, in both directions.
 *
 * The clearance now goes on whichever columns are actually under the box, however narrow they are,
 * and whatever they do not cover is left to macOS.
 */
fun TrafficLightInset.needsTitleRow(showTitleBar: Boolean): Boolean = showTitleBar

/** Where each left-hand column starts, for [columnInset]. */
data class LeftColumnOffsets(
    /** An open plugin panel, which follows the icon strip. */
    val panel: Dp,
    /** The vertical tab bar, which follows the panel when there is one. */
    val bar: Dp,
)

/**
 * Where the left columns start, given what is on screen.
 *
 * Pure, and here rather than inline in the scaffold, because the ORDER is the whole point and it
 * is not obvious from the composition: the strip is outermost, an open plugin panel comes next,
 * and the vertical tab bar is behind the panel - so the bar is only second when no panel is open.
 * Getting that backwards is what put the lights on a panel header while the bar, out of reach
 * behind it, kept a 28dp gap for them.
 */
fun leftColumnOffsets(
    showLeftStrip: Boolean,
    leftPanelOpen: Boolean,
    stripWidth: Dp,
): LeftColumnOffsets {
    val panel = if (showLeftStrip) stripWidth else 0.dp
    // TRAFFIC_LIGHT_WIDTH rather than the panel's measured width: a panel narrower than the box
    // would leave the remainder on the bar, but a sidebar panel is hundreds of dp and the floor a
    // user can drag it to is 20dp, so treating "a panel is open" as "the bar is clear" is right
    // everywhere except a deliberately collapsed sliver.
    val bar = if (leftPanelOpen) TRAFFIC_LIGHT_WIDTH else panel
    return LeftColumnOffsets(panel = panel, bar = bar)
}
