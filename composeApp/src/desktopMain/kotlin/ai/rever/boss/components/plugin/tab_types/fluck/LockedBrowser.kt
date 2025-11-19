package ai.rever.boss.components.plugin.tab_types.fluck

import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.devtools.DevTools
import com.teamdev.jxbrowser.event.Subscription
import com.teamdev.jxbrowser.frame.Frame
import com.teamdev.jxbrowser.navigation.Navigation
import com.teamdev.jxbrowser.zoom.Zoom
import com.teamdev.jxbrowser.zoom.ZoomLevel
import java.util.Optional
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

/**
 * Thread-safe wrapper for JxBrowser operations.
 *
 * All browser access is protected by a read lock to prevent
 * "closed object" exceptions during disposal (Issue #255).
 *
 * The lock is acquired automatically on every method call,
 * ensuring no race conditions between browser operations
 * and disposal on the IO thread.
 *
 * Usage:
 * ```
 * val lockedBrowser = LockedBrowser(browser, browserLock)
 * val url = lockedBrowser.url()  // Automatically acquires read lock
 * lockedBrowser.navigation().goBack()  // Lock handled internally
 * ```
 */
class LockedBrowser(
    private val browser: Browser,
    private val lock: ReentrantReadWriteLock
) {
    fun url(): String = lock.read { browser.url() }

    fun title(): String = lock.read { browser.title() }

    val isClosed: Boolean
        get() = lock.read { browser.isClosed }

    fun navigation(): LockedNavigation = LockedNavigation(browser.navigation(), lock)

    fun mainFrame(): Optional<Frame> = lock.read { browser.mainFrame() }

    fun devTools(): LockedDevTools = LockedDevTools(browser.devTools(), lock)

    fun zoom(): LockedZoom = LockedZoom(browser.zoom(), lock)

    /**
     * Access raw browser for operations that can't be wrapped.
     * Use this for event registration (browser.on()) since events are typically
     * registered once during setup and don't need per-call locking.
     * WARNING: For regular browser operations, use wrapper methods instead.
     */
    fun unsafe(): Browser = browser
}

/**
 * Thread-safe wrapper for Navigation operations.
 */
class LockedNavigation(
    private val navigation: Navigation,
    private val lock: ReentrantReadWriteLock
) {
    fun loadUrl(url: String) = lock.read { navigation.loadUrl(url) }

    fun canGoBack(): Boolean = lock.read { navigation.canGoBack() }

    fun canGoForward(): Boolean = lock.read { navigation.canGoForward() }

    fun goBack() = lock.read { navigation.goBack() }

    fun goForward() = lock.read { navigation.goForward() }

    fun reload() = lock.read { navigation.reload() }

    fun stop() = lock.read { navigation.stop() }
}

/**
 * Thread-safe wrapper for DevTools operations.
 */
class LockedDevTools(
    private val devTools: DevTools,
    private val lock: ReentrantReadWriteLock
) {
    fun show() = lock.read { devTools.show() }
}

/**
 * Thread-safe wrapper for Zoom operations.
 */
class LockedZoom(
    private val zoom: Zoom,
    private val lock: ReentrantReadWriteLock
) {
    fun level(): ZoomLevel = lock.read { zoom.level() }

    fun level(newLevel: ZoomLevel) = lock.read { zoom.level(newLevel) }
}
