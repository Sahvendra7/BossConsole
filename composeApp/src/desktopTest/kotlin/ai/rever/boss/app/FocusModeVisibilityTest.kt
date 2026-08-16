package ai.rever.boss.app

import ai.rever.boss.focusmode.FocusModeSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [focusQuickActionsVisible], which was the highest-consequence line in this feature with no
 * test behind it.
 *
 * The `hides(TOP)` half looks redundant next to `!showTopBar` and is not. `showTopBar` is written
 * from a `LaunchedEffect`, so it reads false on the first composition of every window regardless of
 * whether focus mode is enabled - and dropping the conjunct is a native always-on-top window
 * created and disposed on every window open, a corner flash for users who never enable focus mode,
 * and a content-pane read before the pane is showing. None of that is visible to any other test.
 */
class FocusModeVisibilityTest {
    private val focusOnHidingTop = FocusModeSettings(enabled = true, hideTopBar = true)

    /** Named so the appearance flag reads as what it is at each call: nothing is hidden for good. */
    private fun visible(
        settings: FocusModeSettings,
        showTopBar: Boolean,
        topBarHidden: Boolean = false,
    ) = focusQuickActionsVisible(settings, topBarHidden = topBarHidden, showTopBar = showTopBar)

    @Test
    fun `hidden once focus mode has cleared the top bar`() {
        assertTrue(visible(focusOnHidingTop, showTopBar = false))
    }

    @Test
    fun `stands down while the top bar is revealed`() {
        assertFalse(visible(focusOnHidingTop, showTopBar = true))
    }

    @Test
    fun `never composed when focus mode is off, whatever the reveal state says`() {
        // The launch path: showTopBar is false on the first composition of every window because the
        // effect that turns it back on has not run yet. This is the case the settings half exists
        // for, and the only one where the two conjuncts disagree.
        val off = focusOnHidingTop.copy(enabled = false)

        assertFalse(visible(off, showTopBar = false))
        assertFalse(visible(off, showTopBar = true))
    }

    @Test
    fun `never composed when focus mode is on but keeps the top bar`() {
        // Per-edge settings: focus mode enabled with hideTopBar off leaves the bar in place, so the
        // three actions were never taken away and the cluster has nothing to restore.
        val keepsTop = focusOnHidingTop.copy(hideTopBar = false)

        assertFalse(visible(keepsTop, showTopBar = false))
        assertFalse(visible(keepsTop, showTopBar = true))
    }

    @Test
    fun `composed when the top bar is switched off for good, with focus mode never enabled`() {
        // The whole reason the appearance flag reaches this predicate. Settings, Search and Sign
        // Out live only in the top bar, and the native View menu carries Settings but neither of
        // the other two - so keying this on focus mode alone leaves Sign Out unreachable for any
        // user who hides the top bar from its own right-click menu.
        val noFocusMode = FocusModeSettings(enabled = false)

        assertTrue(visible(noFocusMode, showTopBar = false, topBarHidden = true))
    }

    @Test
    fun `a hidden top bar still stands down while something is showing the bar`() {
        // `!showTopBar` keeps its meaning against the new flag too: if the bar is on screen it owns
        // its own three actions, whatever the preference underneath says.
        assertFalse(visible(FocusModeSettings(enabled = false), showTopBar = true, topBarHidden = true))
    }
}
