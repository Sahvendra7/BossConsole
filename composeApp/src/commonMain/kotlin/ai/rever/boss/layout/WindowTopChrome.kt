package ai.rever.boss.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where the window's topmost row has to give way to the macOS traffic lights.
 *
 * On macOS the window sets `apple.awt.fullWindowContent` and `apple.awt.transparentTitleBar` (see
 * `BossWindow`), so app content extends underneath the close/minimise/zoom buttons rather than
 * starting below them. Something has to keep clear of them. That used to be a whole 26dp row whose
 * only other content was a centered "Boss Console" label; now the topmost row keeps a leading
 * inset instead, and the label moved into the top bar.
 *
 * Every decision here is a pure function of the platform and the window's fullscreen state so it
 * can be asserted without a live window - none of it is observable from a test otherwise.
 */
object WindowTopChrome {
    /**
     * Width kept clear at the leading edge for the three buttons.
     *
     * Not derivable: AWT exposes no metric for the traffic lights, and the buttons are drawn by the
     * window server outside the Java content hierarchy. 78dp covers the three 12pt buttons on their
     * 20pt pitch from a 20pt left margin, plus a little air before the first control - the same
     * reservation Electron apps make for the same buttons.
     */
    val LeadingInset = 78.dp

    /**
     * Whether the traffic lights are drawn over this window's content right now.
     *
     * `contains("mac")` rather than a tighter match on purpose: this must agree exactly with the
     * check in `BossWindow` that decides whether to set the `fullWindowContent` client properties,
     * because the reservation is only needed when those are on. If the two ever disagree, the
     * buttons land on top of a tab.
     *
     * False in fullscreen: macOS takes the lights away with the title bar there, and a reservation
     * that stayed would be a permanent gap in a layout that has no room to spare.
     */
    fun lightsOverlayContent(
        osName: String,
        isFullscreen: Boolean,
    ): Boolean = osName.lowercase().contains("mac") && !isFullscreen

    /**
     * Leading inset for the window's topmost row.
     *
     * Note which way this fails. The fullscreen flag comes from Compose's window placement, which
     * tracks the app's own fullscreen requests but not necessarily a click on the green button; when
     * it is stale it is stale *false*, so the inset stays and costs width in a fullscreen window
     * that has plenty. The opposite error - dropping the inset while the buttons are still there -
     * would put them on top of a control, and no path here produces it.
     */
    fun leadingInset(
        osName: String,
        isFullscreen: Boolean,
    ): Dp = if (lightsOverlayContent(osName, isFullscreen)) LeadingInset else 0.dp

    /**
     * Whether a bare reservation strip has to stand in for the topmost row.
     *
     * The top bar carries the inset while it is on screen. It can be switched off outright or
     * cleared by focus mode, and then the tab bar would be the topmost row - one tab bar per panel,
     * so insetting "the" tab bar means picking out the one panel that happens to sit in the window's
     * top-left corner, which in a split is a layout-phase question. A strip the height of the
     * buttons costs the same 27dp the old title row did and needs none of that.
     */
    fun needsReservationStrip(
        osName: String,
        isFullscreen: Boolean,
        topBarOnScreen: Boolean,
    ): Boolean = lightsOverlayContent(osName, isFullscreen) && !topBarOnScreen
}
