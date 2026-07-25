package ai.rever.boss.utils

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities

internal fun nativeMacOSFullscreenStateForEvent(methodName: String): Boolean? =
    when (methodName) {
        "windowEnteringFullScreen", "windowEnteredFullScreen" -> true
        "windowExitedFullScreen" -> false
        else -> null
    }

/**
 * Captures AWT focus lifecycle events on the EDT and exposes a volatile
 * snapshot that JxBrowser callback threads can safely read.
 */
internal class AwtWindowFocusTracker {
    @Volatile
    private var focusedWindowId: String? = null

    fun snapshotRegistration(
        windowId: String,
        isFocused: Boolean,
    ) {
        if (isFocused) {
            focusedWindowId = windowId
        }
    }

    fun createListener(
        windowId: String,
        onFocusGained: () -> Unit = {},
    ): WindowAdapter =
        object : WindowAdapter() {
            override fun windowGainedFocus(e: WindowEvent?) {
                focusedWindowId = windowId
                onFocusGained()
            }

            override fun windowLostFocus(e: WindowEvent?) {
                if (focusedWindowId == windowId) {
                    focusedWindowId = null
                }
            }
        }

    fun onUnregistered(windowId: String) {
        if (focusedWindowId == windowId) {
            focusedWindowId = null
        }
    }

    fun isFocused(windowId: String): Boolean = focusedWindowId == windowId
}

/**
 * Handles multi-window focus tracking with two intentionally different views:
 * [isWindowFocused] is the live AWT focus used to gate browser input, while
 * [focusedWindowFlow] retains last-focused semantics for external actions such
 * as deep links and file opens.
 */
actual object WindowFocusManager {
    private data class MacOSFullscreenRegistration(
        val window: Window,
        val listener: Any,
        val listenerClass: Class<*>,
        val removeMethod: Method,
    )

    private val logger = BossLogger.forComponent("WindowFocusManager")
    private val windows = ConcurrentHashMap<String, Window>()
    private val fullscreenWindowIds = ConcurrentHashMap.newKeySet<String>()
    private val macOSFullscreenRegistrations = mutableMapOf<String, MacOSFullscreenRegistration>()

    // EDT-confined; registerWindow/unregisterWindow enforce this before mutation.
    private val windowListeners = mutableMapOf<String, WindowAdapter>()
    private val awtFocusTracker = AwtWindowFocusTracker()
    private var focusedWindowId: String? = null
    private var mainWindow: Window? = null // Kept for backward compatibility

    // StateFlow to observe focus changes (for elegant focus restoration)
    private val _focusedWindowFlow = MutableStateFlow<String?>(null)
    actual val focusedWindowFlow: StateFlow<String?> = _focusedWindowFlow.asStateFlow()

    /**
     * Registers an application window with focus tracking. Must run on the EDT.
     * A window registered before it is focused remains absent from the live
     * snapshot until its first focus-gained event, so browser input fails closed.
     *
     * @param windowId Unique identifier for the window
     * @param window The AWT window instance
     */
    fun registerWindow(
        windowId: String,
        window: Window,
    ) {
        check(SwingUtilities.isEventDispatchThread()) {
            "WindowFocusManager.registerWindow must run on the EDT"
        }
        windows[windowId] = window
        registerMacOSFullscreenListener(windowId, window)

        // First window becomes the main window (backward compatibility).
        if (mainWindow == null) {
            mainWindow = window
            focusedWindowId = windowId
        }

        // Registration runs on the EDT. Snapshot an already-focused window in
        // case its focus-gained event happened before the listener was attached.
        awtFocusTracker.snapshotRegistration(windowId, window.isFocused)

        val listener =
            awtFocusTracker.createListener(windowId) {
                focusedWindowId = windowId
                _focusedWindowFlow.value = windowId
            }

        windowListeners[windowId] = listener
        window.addWindowFocusListener(listener)
    }

    /**
     * Register the main application window (backward compatibility)
     *
     * @param window The AWT window instance
     */
    fun registerWindow(window: Window) {
        // Generate a default ID for backward compatibility
        val windowId = "window-${System.identityHashCode(window)}"
        registerWindow(windowId, window)
    }

    /**
     * Get a registered window by ID.
     *
     * @param windowId The window ID
     * @return The AWT Window, or null if not registered
     */
    fun getWindow(windowId: String): Window? = windows[windowId]

    /** Records the authoritative Compose placement for a registered window. */
    fun updateWindowFullscreen(
        windowId: String,
        isFullscreen: Boolean,
    ) {
        if (isFullscreen) {
            fullscreenWindowIds += windowId
        } else {
            fullscreenWindowIds -= windowId
        }
    }

    /**
     * Returns a registered window whose Compose placement is fullscreen,
     * preferring the last-focused window when multiple fullscreen Spaces exist.
     */
    fun getFullscreenWindow(): Window? {
        val preferredId = resolveActionableWindowId()
        if (preferredId != null && preferredId in fullscreenWindowIds) {
            windows[preferredId]?.let { return it }
        }
        return fullscreenWindowIds.firstNotNullOfOrNull(windows::get)
    }

    /**
     * Unregisters a window when it closes. Must run on the EDT.
     *
     * @param windowId The window ID to unregister
     */
    fun unregisterWindow(windowId: String) {
        check(SwingUtilities.isEventDispatchThread()) {
            "WindowFocusManager.unregisterWindow must run on the EDT"
        }
        windowListeners.remove(windowId)?.let { listener ->
            windows[windowId]?.removeWindowFocusListener(listener)
        }
        unregisterMacOSFullscreenListener(windowId)

        windows.remove(windowId)
        fullscreenWindowIds -= windowId
        awtFocusTracker.onUnregistered(windowId)
        if (focusedWindowId == windowId) {
            // Preserve the existing last-focused flow contract for external
            // actions; another window will publish itself when it gains focus.
            focusedWindowId = null
            _focusedWindowFlow.value = null
        }
    }

    /**
     * Returns the current AWT focus snapshot maintained by EDT focus events.
     * The volatile snapshot is safe to read from JxBrowser callback threads.
     * It intentionally returns false before the first focus-gained event and
     * after unregister, keeping orphaned owner-scoped browsers fail-closed.
     */
    actual fun isWindowFocused(windowId: String): Boolean = awtFocusTracker.isFocused(windowId)

    /**
     * Best-effort window id for actions that need "the" active window but may run
     * before a real OS focus-gained event has fired for it — e.g. a deep link
     * dispatched by an MCP tool while the caller's own window (not BOSS) has OS
     * focus. Prefers [focusedWindowId] (set at registration and on every focus
     * gain) over [focusedWindowFlow] (only ever set inside the focus-gained
     * listener, so it can lag or stay null even once a window is plainly
     * available), falling back to any registered window. Returns null only if no
     * window is registered at all.
     */
    fun resolveActionableWindowId(): String? = focusedWindowId ?: focusedWindowFlow.value ?: windows.keys.firstOrNull()

    private fun registerMacOSFullscreenListener(
        windowId: String,
        window: Window,
    ) {
        if (!System.getProperty("os.name").lowercase().contains("mac")) return

        unregisterMacOSFullscreenListener(windowId)
        try {
            val utilitiesClass = Class.forName("com.apple.eawt.FullScreenUtilities")
            val listenerClass = Class.forName("com.apple.eawt.FullScreenListener")
            val listener =
                Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) { proxy, method, arguments ->
                    when (method.name) {
                        "equals" -> {
                            proxy === arguments?.firstOrNull()
                        }

                        "hashCode" -> {
                            System.identityHashCode(proxy)
                        }

                        "toString" -> {
                            "BossMacOSFullscreenListener($windowId)"
                        }

                        else -> {
                            nativeMacOSFullscreenStateForEvent(method.name)?.let { isFullscreen ->
                                updateWindowFullscreen(windowId, isFullscreen)
                                logger.info(
                                    LogCategory.UI,
                                    "Native macOS fullscreen state changed",
                                    mapOf("windowId" to windowId, "isFullscreen" to isFullscreen),
                                )
                            }
                            null
                        }
                    }
                }
            val addMethod =
                utilitiesClass.getMethod(
                    "addFullScreenListenerTo",
                    Window::class.java,
                    listenerClass,
                )
            val removeMethod =
                utilitiesClass.getMethod(
                    "removeFullScreenListenerFrom",
                    Window::class.java,
                    listenerClass,
                )

            addMethod.invoke(null, window, listener)
            macOSFullscreenRegistrations[windowId] =
                MacOSFullscreenRegistration(window, listener, listenerClass, removeMethod)
        } catch (e: ReflectiveOperationException) {
            logger.warn(
                LogCategory.UI,
                "Could not register native macOS fullscreen listener",
                mapOf("windowId" to windowId),
                e,
            )
        }
    }

    private fun unregisterMacOSFullscreenListener(windowId: String) {
        val registration = macOSFullscreenRegistrations.remove(windowId) ?: return
        try {
            registration.removeMethod.invoke(
                null,
                registration.window,
                registration.listenerClass.cast(registration.listener),
            )
        } catch (e: ReflectiveOperationException) {
            logger.warn(
                LogCategory.UI,
                "Could not unregister native macOS fullscreen listener",
                mapOf("windowId" to windowId),
                e,
            )
        }
    }

    /**
     * Bring a specific window to front by its ID
     *
     * @param windowId The ID of the window to focus
     * @return true if the window was found and focused, false otherwise
     */
    actual fun focusWindow(windowId: String): Boolean {
        val window = windows[windowId]
        return if (window != null) {
            SwingUtilities.invokeLater {
                // Make window visible if minimized
                if (!window.isVisible) {
                    window.isVisible = true
                }

                // Bring to front
                window.toFront()

                // Request focus
                window.requestFocus()
            }
            true
        } else {
            false
        }
    }

    /**
     * Bring the first registered window to front (backward compatibility)
     */
    actual fun bringToFront() {
        mainWindow?.let { window ->
            SwingUtilities.invokeLater {
                // Make window visible if minimized
                if (!window.isVisible) {
                    window.isVisible = true
                }

                // Bring to front
                window.toFront()

                // Request focus
                window.requestFocus()
            }
        }
    }
}
