package ai.rever.boss.utils

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.lang.reflect.InaccessibleObjectException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import javax.swing.SwingUtilities

internal fun nativeMacOSFullscreenStateForEvent(methodName: String): Boolean? =
    when (methodName) {
        // Treat "entering" as fullscreen immediately so a page request during
        // the Space animation does not issue a competing native toggle.
        "windowEnteringFullScreen", "windowEnteredFullScreen" -> true

        // Keep the state true through the exit animation. The separate
        // exit-start signal closes overlays immediately without allowing a
        // competing native toggle before the Space has actually gone away.
        "windowExitedFullScreen" -> false

        else -> null
    }

internal fun isNativeMacOSFullscreenExitStarting(methodName: String): Boolean = methodName == "windowExitingFullScreen"

internal fun hasFullscreenSignal(
    windowId: String,
    composeFullscreenIds: Set<String>,
    nativeFullscreenIds: Set<String>,
    nativeTrackedIds: Set<String>,
): Boolean =
    if (windowId in nativeTrackedIds) {
        windowId in nativeFullscreenIds
    } else {
        windowId in composeFullscreenIds
    }

internal fun shouldNotifyComposeFullscreenExit(
    wasComposeFullscreen: Boolean,
    isNativeFullscreen: Boolean,
): Boolean = wasComposeFullscreen && !isNativeFullscreen

internal class FullscreenExitNotifier {
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()

    fun add(listener: (String) -> Unit) {
        listeners += listener
    }

    fun remove(listener: (String) -> Unit) {
        listeners -= listener
    }

    fun notify(windowId: String) {
        listeners.forEach { listener -> listener(windowId) }
    }
}

private class MacOSFullscreenTracker(
    private val onFullscreenChanged: (windowId: String, isFullscreen: Boolean) -> Unit,
    private val onFullscreenExitStarted: (windowId: String) -> Unit,
) {
    private data class Registration(
        val window: Window,
        val listener: Any,
        val listenerClass: Class<*>,
        val removeMethod: Method,
    )

    private val logger = BossLogger.forComponent("MacOSFullscreenTracker")
    private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

    // EDT-confined through WindowFocusManager.registerWindow/unregisterWindow.
    private val registrations = mutableMapOf<String, Registration>()

    fun register(
        windowId: String,
        window: Window,
    ): Boolean {
        if (!isMacOS) return false

        unregister(windowId)
        return runReflection("register", windowId) {
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
                            if (isNativeMacOSFullscreenExitStarting(method.name)) {
                                onFullscreenExitStarted(windowId)
                            }
                            nativeMacOSFullscreenStateForEvent(method.name)?.let { isFullscreen ->
                                onFullscreenChanged(windowId, isFullscreen)
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
            registrations[windowId] = Registration(window, listener, listenerClass, removeMethod)
        }
    }

    fun unregister(windowId: String) {
        val registration = registrations.remove(windowId) ?: return
        runReflection("unregister", windowId) {
            registration.removeMethod.invoke(
                null,
                registration.window,
                registration.listenerClass.cast(registration.listener),
            )
        }
    }

    private fun runReflection(
        operation: String,
        windowId: String,
        block: () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (e: ReflectiveOperationException) {
            logFailure(operation, windowId, e)
            false
        } catch (e: InaccessibleObjectException) {
            logFailure(operation, windowId, e)
            false
        } catch (e: IllegalArgumentException) {
            logFailure(operation, windowId, e)
            false
        } catch (e: ClassCastException) {
            logFailure(operation, windowId, e)
            false
        } catch (e: SecurityException) {
            logFailure(operation, windowId, e)
            false
        }

    private fun logFailure(
        operation: String,
        windowId: String,
        error: Throwable,
    ) {
        logger.warn(
            LogCategory.UI,
            "Could not $operation native macOS fullscreen listener",
            mapOf("windowId" to windowId),
            error,
        )
    }
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

internal data class RegisteredWindowFullscreenState(
    val window: Window,
    val nativeTrackingAvailable: Boolean,
    val nativeFullscreen: Boolean,
    val composeFullscreen: Boolean,
)

/**
 * Handles multi-window focus tracking with two intentionally different views:
 * [isWindowFocused] is the live AWT focus used to gate browser input, while
 * [focusedWindowFlow] retains last-focused semantics for external actions such
 * as deep links and file opens.
 */
actual object WindowFocusManager {
    private val windows = ConcurrentHashMap<String, Window>()
    private val composeFullscreenWindowIds = ConcurrentHashMap.newKeySet<String>()
    private val nativeFullscreenWindowIds = ConcurrentHashMap.newKeySet<String>()
    private val nativeTrackedWindowIds = ConcurrentHashMap.newKeySet<String>()
    internal val fullscreenExitNotifier = FullscreenExitNotifier()
    private val macOSFullscreenTracker =
        MacOSFullscreenTracker(
            onFullscreenChanged = { windowId, isFullscreen ->
                if (isFullscreen) {
                    nativeFullscreenWindowIds += windowId
                } else if (nativeFullscreenWindowIds.remove(windowId)) {
                    fullscreenExitNotifier.notify(windowId)
                }
            },
            onFullscreenExitStarted = fullscreenExitNotifier::notify,
        )

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

        // Native fullscreen tracking is optional and must not prevent the
        // focus listener above from being installed if EAWT is unavailable.
        if (macOSFullscreenTracker.register(windowId, window)) {
            nativeTrackedWindowIds += windowId
        } else {
            nativeTrackedWindowIds -= windowId
        }
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

    /** Records Compose placement without overwriting the native macOS signal. */
    fun updateWindowFullscreen(
        windowId: String,
        isFullscreen: Boolean,
    ) {
        if (isFullscreen) {
            composeFullscreenWindowIds += windowId
        } else {
            val wasComposeFullscreen = composeFullscreenWindowIds.remove(windowId)
            if (shouldNotifyComposeFullscreenExit(
                    wasComposeFullscreen,
                    windowId in nativeFullscreenWindowIds,
                )
            ) {
                fullscreenExitNotifier.notify(windowId)
            }
        }
    }

    /** Returns the requested window and its independently tracked fullscreen signals. */
    internal fun getWindowFullscreenState(windowId: String): RegisteredWindowFullscreenState? {
        val window = windows[windowId] ?: return null
        return RegisteredWindowFullscreenState(
            window = window,
            nativeTrackingAvailable = windowId in nativeTrackedWindowIds,
            nativeFullscreen = windowId in nativeFullscreenWindowIds,
            composeFullscreen = windowId in composeFullscreenWindowIds,
        )
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
        macOSFullscreenTracker.unregister(windowId)

        windows.remove(windowId)
        val wasComposeFullscreen = composeFullscreenWindowIds.remove(windowId)
        val wasNativeFullscreen = nativeFullscreenWindowIds.remove(windowId)
        nativeTrackedWindowIds -= windowId
        if (wasComposeFullscreen || wasNativeFullscreen) {
            fullscreenExitNotifier.notify(windowId)
        }
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
