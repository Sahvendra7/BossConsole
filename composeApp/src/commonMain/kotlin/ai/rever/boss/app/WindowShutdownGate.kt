package ai.rever.boss.app

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which windows are live so teardown work that belongs to the **app** can
 * be gated on the last window closing instead of running once per window.
 *
 * Motivating case (Issue #19): every window's dispose saved its own layout into
 * the single "Last Session" workspace, so closing a secondary window overwrote
 * the primary window's session. With this gate the write has one writer — the
 * last window to be disposed.
 */
object WindowShutdownGate {
    private val liveWindows = ConcurrentHashMap.newKeySet<String>()

    /** Number of windows currently registered. */
    val liveWindowCount: Int
        get() = liveWindows.size

    /** Register [windowId] as live. Idempotent. */
    fun register(windowId: String) {
        liveWindows.add(windowId)
    }

    /**
     * Unregister [windowId].
     *
     * @return true when [windowId] was the last live window, i.e. the caller is
     * the one that should perform app-level teardown. False for a window closing
     * while others stay open, and false for an id that was never registered (or
     * is being unregistered twice) so the work can't run more than once.
     */
    fun releaseAndWasLast(windowId: String): Boolean {
        if (!liveWindows.remove(windowId)) return false
        return liveWindows.isEmpty()
    }

    /** True while [windowId] is the only live window. */
    fun isOnlyLiveWindow(windowId: String): Boolean = liveWindows.size == 1 && liveWindows.contains(windowId)

    /** Test-only: drop all tracked windows. */
    internal fun resetForTesting() {
        liveWindows.clear()
    }
}
