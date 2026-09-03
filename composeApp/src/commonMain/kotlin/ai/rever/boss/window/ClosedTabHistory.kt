package ai.rever.boss.window

import ai.rever.boss.plugin.api.TabInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * The stack Cmd+Shift+T pops: recently closed tabs, newest first, per WINDOW.
 *
 * Window-scoped rather than panel-scoped on purpose. A panel can be closed by the same gesture
 * that closes its last tab (see the close-tab handler in BossAppMenuActionEffects), so a stack
 * owned by the panel would be collected exactly when the user wants to undo — and browsers scope
 * this to the window anyway. Reopening therefore lands in whichever panel is active now, not
 * necessarily the one the tab was closed from; that is the same compromise Chrome makes when the
 * originating window is gone.
 *
 * What is recorded is the panel's CURRENT [TabInfo] — the live navigation state, so reopening a
 * browser tab returns to the page it was showing, not the URL it was opened with. The tab's
 * component is NOT retained: `removeTab` destroys it (and with it any Chromium process) before
 * this ever sees the entry, so a deep history costs a handful of config objects, not browsers.
 *
 * `java.util.concurrent` in commonMain follows [ai.rever.boss.plugin.browser.ActiveBrowserRegistry]:
 * composeApp has a single `jvm("desktop")` target. Entries are written from the Compose UI thread
 * and read from the menu-flow collectors.
 */
object ClosedTabHistory {
    /**
     * How many closures a window remembers. Chrome keeps 25; the cost here is one [TabInfo] each,
     * and the bound matters mainly so "Close Other Tabs" on a huge window cannot grow without end.
     */
    const val MAX_ENTRIES = 25

    private val byWindow = ConcurrentHashMap<String, ArrayDeque<TabInfo>>()

    private val _depths = MutableStateFlow<Map<String, Int>>(emptyMap())

    /**
     * How many reopenable closures each window holds.
     *
     * Exposed as state, not just queried, because the File menu's "Reopen Closed Tab" item has
     * to grey itself out the moment the stack empties — the same reason MenuActionsHandler
     * publishes splitEnabledState rather than letting the menu ask.
     */
    val depths: StateFlow<Map<String, Int>> = _depths.asStateFlow()

    /**
     * Record [tab] as the most recently closed tab in [windowId].
     *
     * Callers pass only USER-visible closures. Closing a tab because its plugin was disabled, or
     * tearing a window's tabs down to swap workspaces, must not land here: the first cannot be
     * recreated (its factory is gone) and the second would bury the user's real closures under a
     * layer of bookkeeping.
     */
    fun record(
        windowId: String,
        tab: TabInfo,
    ) {
        // computeIfAbsent, not getOrPut: the latter is a get-then-put extension on MutableMap
        // and two first-closures racing for one window would each build a deque, one silently
        // discarded along with its entry.
        val stack = byWindow.computeIfAbsent(windowId) { ArrayDeque() }
        val depth =
            synchronized(stack) {
                // Re-closing a reopened tab should move it to the top, not add a second copy.
                stack.removeAll { it.id == tab.id }
                stack.addFirst(tab)
                while (stack.size > MAX_ENTRIES) stack.removeLast()
                stack.size
            }
        publishDepth(windowId, depth)
    }

    /** Remove and return the most recently closed tab in [windowId], or null if there is none. */
    fun pop(windowId: String): TabInfo? {
        val stack = byWindow[windowId] ?: return null
        val popped = synchronized(stack) { stack.removeFirstOrNull() }
        if (popped != null) {
            publishDepth(windowId, synchronized(stack) { stack.size })
        }
        return popped
    }

    /** Whether [windowId] has anything to reopen (drives the menu item's enabled state). */
    fun hasEntries(windowId: String): Boolean {
        val stack = byWindow[windowId] ?: return false
        return synchronized(stack) { stack.isNotEmpty() }
    }

    /** Drop a closed window's history. */
    fun clear(windowId: String) {
        byWindow.remove(windowId)
        _depths.update { it - windowId }
    }

    private fun publishDepth(
        windowId: String,
        depth: Int,
    ) {
        _depths.update { if (depth == 0) it - windowId else it + (windowId to depth) }
    }
}
