package ai.rever.boss.tabfullscreen

import ai.rever.boss.utils.WindowFocusManager
import ai.rever.boss.utils.hasFullscreenSignal
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.ComponentLogger
import ai.rever.boss.utils.logging.LogCategory
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.view.swing.BrowserView
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.WeakHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer

internal fun fillsScreen(
    windowBounds: Rectangle,
    screenBounds: Rectangle,
): Boolean =
    windowBounds.x <= screenBounds.x &&
        windowBounds.y <= screenBounds.y &&
        windowBounds.maxX >= screenBounds.maxX &&
        windowBounds.maxY >= screenBounds.maxY

internal fun shouldUseComposeFullscreenOverlay(
    composeSignalActive: Boolean,
    isShowing: Boolean,
    isMaximized: Boolean,
    windowBounds: Rectangle,
    screenBounds: Rectangle,
): Boolean =
    composeSignalActive &&
        isShowing &&
        !isMaximized &&
        fillsScreen(windowBounds, screenBounds)

internal fun isCurrentFullscreenLifecycle(
    expectedEpoch: Long,
    currentEpoch: Long,
    isInFullscreenMode: Boolean,
): Boolean = expectedEpoch == currentEpoch && isInFullscreenMode

internal fun shouldRestoreFullscreenTabState(
    cleanupEpoch: Long,
    currentEpoch: Long,
    isInFullscreenMode: Boolean,
): Boolean = cleanupEpoch == currentEpoch && !isInFullscreenMode

internal enum class FullscreenRequestDecision {
    IGNORE_DUPLICATE,
    REJECT_CLOSED,
    REJECT_COMPETING,
    BEGIN,
}

internal fun fullscreenRequestDecision(
    frameActive: Boolean,
    isInFullscreenMode: Boolean,
    isSameBrowser: Boolean,
    isBrowserClosed: Boolean,
): FullscreenRequestDecision {
    val fullscreenActive = frameActive || isInFullscreenMode
    return when {
        fullscreenActive && isSameBrowser -> FullscreenRequestDecision.IGNORE_DUPLICATE
        isBrowserClosed -> FullscreenRequestDecision.REJECT_CLOSED
        fullscreenActive -> FullscreenRequestDecision.REJECT_COMPETING
        else -> FullscreenRequestDecision.BEGIN
    }
}

/** EDT-confined once installed in [FullscreenBrowserWindow]. */
internal class FullscreenExitCallbackGate<T : Any> {
    private val notifiedOwners = WeakHashMap<T, Unit>()

    fun begin(owner: T) {
        notifiedOwners.remove(owner)
    }

    fun notifyOnce(
        owner: T,
        callback: () -> Unit,
    ): Boolean {
        if (notifiedOwners.put(owner, Unit) != null) return false
        callback()
        return true
    }
}

private fun runOnEventDispatchThreadAndWait(
    logger: ComponentLogger,
    timeoutMs: Long,
    action: () -> Unit,
) {
    if (SwingUtilities.isEventDispatchThread()) {
        action()
        return
    }

    val task =
        FutureTask<Unit> {
            action()
        }
    SwingUtilities.invokeLater(task)
    try {
        task.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        logger.warn(LogCategory.BROWSER, "Interrupted while closing fullscreen browser window", error = e)
    } catch (e: TimeoutException) {
        task.cancel(false)
        logger.warn(
            LogCategory.BROWSER,
            "Timed out waiting for fullscreen browser cleanup on the EDT",
            error = e,
        )
    } catch (e: ExecutionException) {
        logger.warn(LogCategory.BROWSER, "Could not close fullscreen browser window", error = e.cause ?: e)
    }
}

/**
 * Creates and manages a fullscreen Swing JFrame for browser content.
 * Uses the existing browser instance with a new BrowserView.
 * Uses native macOS fullscreen mode (creates new Space) for proper fullscreen experience.
 */
object FullscreenBrowserWindow {
    private val logger = BossLogger.forComponent("FullscreenBrowserWindow")

    // Fullscreen lifecycle state is EDT-confined. Public entry/exit methods
    // marshal to the EDT before reading or mutating these fields.
    private var fullscreenFrame: JFrame? = null
    private var currentBrowserView: BrowserView? = null

    @Volatile // Read off-EDT only for the disposal fast path; mutated on the EDT.
    private var currentBrowser: Browser? = null
    private var currentOwnerWindowId: String? = null
    private var ownerFullscreenExitListener: ((String) -> Unit)? = null
    private var overlayOwnerWindow: Window? = null
    private var overlayOwnerFocusListener: WindowAdapter? = null
    private var onExitCallback: (() -> Unit)? = null
    private var isInFullscreenMode = false
    private var hasReachedFullscreen = false // True only after fullscreen animation completes
    private var usesNativeMacOSFullscreen = false
    private var isExiting = false // Prevent multiple exit calls
    private var lifecycleEpoch = 0L
    private val exitCallbackGate = FullscreenExitCallbackGate<Browser>()

    private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

    // macOS fullscreen animation takes ~500ms, wait before enabling exit detection
    private const val FULLSCREEN_ANIMATION_DELAY_MS = 600

    // Delay to allow Compose BrowserView to detach before creating Swing BrowserView
    // This prevents both views from competing for rendering (which causes video freeze)
    private const val COMPOSE_DETACH_DELAY_MS = 100

    // Delay to allow Swing BrowserView to release rendering before Compose BrowserView activates
    // Exit needs more time because we need to ensure the Swing view fully releases the surface
    private const val SWING_RELEASE_DELAY_MS = 200
    private const val EDT_CLEANUP_TIMEOUT_MS = 2_000L
    private const val EXIT_FULLSCREEN_ACTION = "exit-fullscreen"

    fun showFullscreen(
        browser: Browser,
        tabId: String,
        ownerWindowId: String,
        onEnter: () -> Unit,
        onExit: () -> Unit,
    ) {
        if (SwingUtilities.isEventDispatchThread()) {
            handleFullscreenRequest(browser, tabId, ownerWindowId, onEnter, onExit)
        } else {
            SwingUtilities.invokeLater {
                handleFullscreenRequest(browser, tabId, ownerWindowId, onEnter, onExit)
            }
        }
    }

    private fun handleFullscreenRequest(
        browser: Browser,
        tabId: String,
        ownerWindowId: String,
        onEnter: () -> Unit,
        onExit: () -> Unit,
    ) {
        val decision =
            fullscreenRequestDecision(
                frameActive = fullscreenFrame != null,
                isInFullscreenMode = isInFullscreenMode,
                isSameBrowser = currentBrowser === browser,
                isBrowserClosed = browser.isClosed,
            )
        if (decision != FullscreenRequestDecision.IGNORE_DUPLICATE) {
            // Deduplicate within one request/exit attempt, not forever for a
            // browser that may lose several competing requests over its life.
            exitCallbackGate.begin(browser)
        }
        when (decision) {
            FullscreenRequestDecision.IGNORE_DUPLICATE -> {
                logger.debug(LogCategory.BROWSER, "Ignoring duplicate fullscreen request from active browser")
            }

            FullscreenRequestDecision.REJECT_CLOSED -> {
                logger.warn(LogCategory.BROWSER, "Ignoring fullscreen request from closed browser")
                exitCallbackGate.notifyOnce(browser, onExit)
            }

            FullscreenRequestDecision.REJECT_COMPETING -> {
                rejectFullscreenRequest(browser, onExit)
            }

            FullscreenRequestDecision.BEGIN -> {
                beginFullscreenSession(browser, tabId, ownerWindowId, onEnter, onExit)
            }
        }
    }

    /** Rejects a losing browser without publishing a false enter state. */
    private fun rejectFullscreenRequest(
        browser: Browser,
        onExit: () -> Unit,
    ) {
        logger.warn(LogCategory.BROWSER, "Rejecting fullscreen request while another browser is active")
        val exitRequested =
            runCatching { browser.fullScreen().exit() }
                .onFailure { error ->
                    logger.warn(LogCategory.BROWSER, "Could not reject competing browser fullscreen", error = error)
                }.isSuccess
        // A successful request emits FullScreenExited, whose normal observer
        // publishes the callback. Fall back locally only when no event can follow.
        if (!exitRequested) {
            exitCallbackGate.notifyOnce(browser, onExit)
        }
    }

    private fun beginFullscreenSession(
        browser: Browser,
        tabId: String,
        ownerWindowId: String,
        onEnter: () -> Unit,
        onExit: () -> Unit,
    ) {
        // Mark fullscreen state FIRST so Compose BrowserView hides immediately
        // This triggers recomposition in JxBrowserCompose.kt, replacing BrowserView with placeholder
        isInFullscreenMode = true
        currentBrowser = browser
        currentOwnerWindowId = ownerWindowId
        onExitCallback = onExit
        isExiting = false
        hasReachedFullscreen = false
        lifecycleEpoch++
        val expectedEpoch = lifecycleEpoch
        TabFullscreenStateManager.enterFullscreen(tabId)
        onEnter()

        logger.info(LogCategory.BROWSER, "Fullscreen state set, waiting for Compose detach", mapOf("tabId" to tabId))

        // Delay window creation to allow Compose BrowserView to detach from rendering
        // This gives JxBrowser time to release the Compose rendering surface
        SwingUtilities.invokeLater {
            Timer(COMPOSE_DETACH_DELAY_MS) {
                SwingUtilities.invokeLater {
                    if (isCurrentSession(expectedEpoch, browser)) {
                        createFullscreenWindow(browser, tabId, expectedEpoch)
                    }
                }
            }.apply {
                isRepeats = false
                start()
            }
        }
    }

    private fun isCurrentSession(
        expectedEpoch: Long,
        browser: Browser,
    ): Boolean =
        isCurrentFullscreenLifecycle(expectedEpoch, lifecycleEpoch, isInFullscreenMode) &&
            currentBrowser === browser

    private fun isCurrentFrameSession(
        expectedEpoch: Long,
        browser: Browser,
        frame: JFrame,
    ): Boolean = isCurrentSession(expectedEpoch, browser) && fullscreenFrame === frame

    /**
     * Creates and displays the fullscreen window with a Swing BrowserView.
     * Called after Compose BrowserView has had time to detach from rendering.
     */
    private fun createFullscreenWindow(
        browser: Browser,
        tabId: String,
        expectedEpoch: Long,
    ) {
        var createdFrame: JFrame? = null
        try {
            // Check if we've been cancelled during the delay
            if (!isCurrentSession(expectedEpoch, browser)) {
                logger.warn(LogCategory.BROWSER, "Fullscreen cancelled during delay")
                return
            }

            // Double-check for race conditions
            if (fullscreenFrame != null) {
                logger.warn(LogCategory.BROWSER, "Fullscreen already active (race condition prevented)")
                return
            }

            val ownerWindow = currentOwnerWindowId?.let(WindowFocusManager::getWindow)
            val frame = ownerWindow?.graphicsConfiguration?.let(::JFrame) ?: JFrame()
            createdFrame = frame
            frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            frame.background = Color.BLACK
            frame.contentPane.background = Color.BLACK
            frame.contentPane.layout = BorderLayout()
            installExitShortcut(frame)

            // Create BrowserView for existing browser instance
            // At this point, the Compose BrowserView should be detached from rendering
            val browserView = BrowserView.newInstance(browser)
            browserView.background = Color.BLACK
            frame.contentPane.add(browserView, BorderLayout.CENTER)

            logger.info(LogCategory.BROWSER, "Swing BrowserView created after Compose detach delay")

            installFullscreenFrameListeners(frame, browser, expectedEpoch)

            fullscreenFrame = frame
            currentBrowserView = browserView

            if (isMacOS) {
                enterMacOSFullscreen(frame, browser, ownerWindow, expectedEpoch)
            } else {
                // Windows/Linux: use maximized undecorated window
                frame.isUndecorated = true
                val screenBounds = displayBounds(frame)
                frame.setBounds(screenBounds.x, screenBounds.y, screenBounds.width, screenBounds.height)
                frame.isVisible = true
                hasReachedFullscreen = true
            }

            frame.toFront()
            frame.requestFocus()
            browserView.requestFocusInWindow()

            logger.info(LogCategory.BROWSER, "Fullscreen window opened", mapOf("tabId" to tabId, "isMacOS" to isMacOS))
        } catch (e: Exception) {
            logger.error(LogCategory.BROWSER, "Failed to create fullscreen window", error = e)
            runCatching { createdFrame?.dispose() }
                .onFailure { error ->
                    logger.warn(LogCategory.BROWSER, "Could not dispose failed fullscreen window", error = error)
                }
            if (isCurrentSession(expectedEpoch, browser)) {
                val callback = onExitCallback
                resetState()
                TabFullscreenStateManager.exitFullscreen()
                callback?.let { exitCallbackGate.notifyOnce(browser, it) }
            }
        }
    }

    private fun installFullscreenFrameListeners(
        frame: JFrame,
        browser: Browser,
        expectedEpoch: Long,
    ) {
        frame.addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    requestPageExit()
                }
            },
        )

        // Detect when exiting native fullscreen (green button or ESC).
        // Overlay and Windows/Linux frames are fixed-size and exit directly.
        frame.addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    if (!usesNativeMacOSFullscreen || !hasReachedFullscreen) return
                    if (!isCurrentFrameSession(expectedEpoch, browser, frame) || isExiting) return

                    SwingUtilities.invokeLater {
                        if (isCurrentFrameSession(expectedEpoch, browser, frame) &&
                            !isWindowInFullscreen(frame) &&
                            !isExiting
                        ) {
                            logger.info(LogCategory.BROWSER, "Native fullscreen exited via resize detection")
                            requestPageExit()
                        }
                    }
                }
            },
        )
    }

    private fun enterMacOSFullscreen(
        frame: JFrame,
        browser: Browser,
        ownerWindow: Window?,
        expectedEpoch: Long,
    ) {
        val fullscreenHostBounds = currentOwnerWindowId?.let(::existingFullscreenWindowBounds)
        if (fullscreenHostBounds != null) {
            // macOS can ignore a second native fullscreen-Space request while another
            // window from this app already owns a fullscreen Space. Keep the video in
            // that Space and cover it with an undecorated screen-sized window instead.
            showBorderlessOverlay(
                frame = frame,
                bounds = fullscreenHostBounds,
                watchOwnerExit = true,
                expectedEpoch = expectedEpoch,
            )
            logger.info(
                LogCategory.BROWSER,
                "Opened fullscreen video as borderless overlay in existing fullscreen Space",
            )
            return
        }

        usesNativeMacOSFullscreen = true
        frame.rootPane.putClientProperty("apple.awt.fullscreenable", true)
        frame.setSize(800, 600) // Initial size before fullscreen
        if (ownerWindow != null) {
            frame.setLocationRelativeTo(ownerWindow)
        } else {
            val bounds = displayBounds(frame)
            frame.setLocation(
                bounds.x + (bounds.width - frame.width) / 2,
                bounds.y + (bounds.height - frame.height) / 2,
            )
        }
        frame.isVisible = true
        requestNativeMacOSFullscreen(frame, browser, expectedEpoch)
    }

    private fun requestNativeMacOSFullscreen(
        frame: JFrame,
        browser: Browser,
        expectedEpoch: Long,
    ) {
        SwingUtilities.invokeLater {
            if (!isCurrentFrameSession(expectedEpoch, browser, frame)) return@invokeLater
            if (!toggleMacOSFullscreen(frame)) {
                showBorderlessOverlay(
                    frame = frame,
                    bounds = displayBounds(frame),
                    watchOwnerExit = false,
                    expectedEpoch = expectedEpoch,
                )
                return@invokeLater
            }

            // Wait for fullscreen animation to complete before enabling exit detection.
            Timer(FULLSCREEN_ANIMATION_DELAY_MS) {
                if (!isCurrentFrameSession(expectedEpoch, browser, frame)) return@Timer
                if (isWindowInFullscreen(frame)) {
                    hasReachedFullscreen = true
                    logger.info(
                        LogCategory.BROWSER,
                        "Fullscreen animation completed, exit detection enabled",
                    )
                } else {
                    logger.warn(
                        LogCategory.BROWSER,
                        "macOS ignored native fullscreen toggle; using borderless overlay",
                    )
                    showBorderlessOverlay(
                        frame = frame,
                        bounds = displayBounds(frame),
                        watchOwnerExit = false,
                        expectedEpoch = expectedEpoch,
                    )
                }
            }.apply {
                isRepeats = false
                start()
            }
        }
    }

    private fun showBorderlessOverlay(
        frame: JFrame,
        bounds: Rectangle,
        watchOwnerExit: Boolean,
        expectedEpoch: Long,
    ) {
        if (lifecycleEpoch != expectedEpoch || fullscreenFrame !== frame) return
        if (frame.isDisplayable) {
            frame.dispose()
        }
        frame.isUndecorated = true
        frame.isAlwaysOnTop = true
        frame.extendedState = JFrame.NORMAL
        frame.setBounds(bounds)
        frame.isVisible = true
        frame.toFront()
        installOverlayFocusBehavior(frame)
        usesNativeMacOSFullscreen = false
        hasReachedFullscreen = true
        if (watchOwnerExit) {
            watchOwnerFullscreenExit(currentOwnerWindowId, expectedEpoch)
        }
    }

    private fun installOverlayFocusBehavior(frame: JFrame) {
        clearOverlayOwnerFocusListener()
        frame.addWindowFocusListener(
            object : WindowAdapter() {
                override fun windowLostFocus(event: WindowEvent?) {
                    if (fullscreenFrame === frame && !usesNativeMacOSFullscreen) {
                        // Do not cover other applications when the user switches
                        // away from Boss; restore the overlay level on return.
                        frame.isAlwaysOnTop = false
                    }
                }

                override fun windowGainedFocus(event: WindowEvent?) {
                    restoreOverlayFocus(frame)
                }
            },
        )

        val ownerWindow = currentOwnerWindowId?.let(WindowFocusManager::getWindow) ?: return
        val ownerListener =
            object : WindowAdapter() {
                override fun windowGainedFocus(event: WindowEvent?) {
                    restoreOverlayFocus(frame)
                    frame.requestFocus()
                }
            }
        overlayOwnerWindow = ownerWindow
        overlayOwnerFocusListener = ownerListener
        ownerWindow.addWindowFocusListener(ownerListener)
    }

    private fun restoreOverlayFocus(frame: JFrame) {
        if (fullscreenFrame === frame && !usesNativeMacOSFullscreen) {
            frame.isAlwaysOnTop = true
            frame.toFront()
        }
    }

    private fun clearOverlayOwnerFocusListener() {
        val ownerWindow = overlayOwnerWindow
        val listener = overlayOwnerFocusListener
        if (ownerWindow != null && listener != null) {
            ownerWindow.removeWindowFocusListener(listener)
        }
        overlayOwnerWindow = null
        overlayOwnerFocusListener = null
    }

    /**
     * Returns the display bounds of an already-fullscreen window in this process.
     * A new undecorated video window can cover that same fullscreen Space without
     * asking macOS to create a second native fullscreen Space.
     */
    private fun existingFullscreenWindowBounds(ownerWindowId: String): Rectangle? {
        val state = WindowFocusManager.getWindowFullscreenState(ownerWindowId)
        if (state == null ||
            !hasFullscreenSignal(
                nativeStateAvailable = state.nativeStateAvailable,
                nativeFullscreen = state.nativeFullscreen,
                composeFullscreen = state.composeFullscreen,
            )
        ) {
            return null
        }
        return if (state.nativeStateAvailable) {
            state.window.graphicsConfiguration?.bounds
        } else {
            ownerWindowBoundsFallback(
                ownerWindowId = ownerWindowId,
                ownerWindow = state.window,
                composeSignalActive = state.composeFullscreen,
            )
        }
    }

    private fun ownerWindowBoundsFallback(
        ownerWindowId: String,
        ownerWindow: Window,
        composeSignalActive: Boolean,
    ): Rectangle? {
        // Best-effort fallback only for the browser's owning window. Never scan
        // another Boss window or treat an ordinary maximized frame as fullscreen.
        val screenBounds = ownerWindow.graphicsConfiguration?.bounds
        val isMaximized =
            ownerWindow is Frame &&
                ownerWindow.extendedState and Frame.MAXIMIZED_BOTH != 0
        val fallbackBounds =
            screenBounds?.takeIf {
                shouldUseComposeFullscreenOverlay(
                    composeSignalActive = composeSignalActive,
                    isShowing = ownerWindow.isShowing,
                    isMaximized = isMaximized,
                    windowBounds = ownerWindow.bounds,
                    screenBounds = screenBounds,
                )
            }

        if (fallbackBounds != null) {
            logger.info(
                LogCategory.BROWSER,
                "Using owner-window bounds fallback for fullscreen video overlay",
                mapOf("ownerWindowId" to ownerWindowId),
            )
        }
        return fallbackBounds
    }

    /**
     * Request native macOS fullscreen using reflection.
     * Uses com.apple.eawt.Application.requestToggleFullScreen() which creates
     * a proper macOS fullscreen Space (like Chrome/Safari behavior).
     */
    private fun toggleMacOSFullscreen(window: Window): Boolean =
        try {
            val appClass = Class.forName("com.apple.eawt.Application")
            val getAppMethod = appClass.getDeclaredMethod("getApplication")
            getAppMethod.isAccessible = true
            val app = getAppMethod.invoke(null)
            val requestToggleMethod = appClass.getDeclaredMethod("requestToggleFullScreen", Window::class.java)
            requestToggleMethod.isAccessible = true
            requestToggleMethod.invoke(app, window)
            logger.info(LogCategory.BROWSER, "Requested macOS native fullscreen")
            true
        } catch (e: Exception) {
            logger.warn(LogCategory.BROWSER, "Could not toggle macOS fullscreen", error = e)
            false
        }

    /**
     * Check if window is currently in fullscreen state (macOS).
     * On macOS in fullscreen, the window bounds match the screen bounds exactly.
     */
    private fun isWindowInFullscreen(frame: JFrame): Boolean = fillsScreen(frame.bounds, displayBounds(frame))

    private fun displayBounds(frame: JFrame): Rectangle =
        frame.graphicsConfiguration?.bounds
            ?: GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration.bounds

    private fun installExitShortcut(frame: JFrame) {
        frame.rootPane
            .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), EXIT_FULLSCREEN_ACTION)
        frame.rootPane.actionMap.put(
            EXIT_FULLSCREEN_ACTION,
            object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent?) {
                    logger.info(LogCategory.BROWSER, "Fullscreen exit requested from host UI")
                    requestPageExit()
                }
            },
        )
    }

    private fun watchOwnerFullscreenExit(
        ownerWindowId: String?,
        expectedEpoch: Long,
    ) {
        if (ownerWindowId == null) return
        ownerFullscreenExitListener?.let(WindowFocusManager.fullscreenExitNotifier::remove)
        val listener: (String) -> Unit = { exitedWindowId ->
            if (exitedWindowId == ownerWindowId) {
                SwingUtilities.invokeLater {
                    if (isCurrentOwnerOverlay(ownerWindowId, expectedEpoch)) {
                        logger.info(
                            LogCategory.BROWSER,
                            "Closing fullscreen video overlay because its host exited fullscreen",
                            mapOf("ownerWindowId" to ownerWindowId),
                        )
                        requestPageExit()
                    }
                }
            }
        }
        ownerFullscreenExitListener = listener
        WindowFocusManager.fullscreenExitNotifier.add(listener)

        // The owner may have completed its exit between the state read that
        // selected overlay mode and listener registration.
        val state = WindowFocusManager.getWindowFullscreenState(ownerWindowId)
        val stillFullscreen =
            state != null &&
                hasFullscreenSignal(
                    nativeStateAvailable = state.nativeStateAvailable,
                    nativeFullscreen = state.nativeFullscreen,
                    composeFullscreen = state.composeFullscreen,
                )
        if (!stillFullscreen) {
            SwingUtilities.invokeLater {
                requestPageExit()
            }
        }
    }

    private fun isCurrentOwnerOverlay(
        ownerWindowId: String,
        expectedEpoch: Long,
    ): Boolean =
        currentOwnerWindowId == ownerWindowId &&
            lifecycleEpoch == expectedEpoch &&
            isInFullscreenMode &&
            !usesNativeMacOSFullscreen

    /**
     * Reset all state variables.
     */
    private fun resetState(): Long {
        lifecycleEpoch++
        ownerFullscreenExitListener?.let(WindowFocusManager.fullscreenExitNotifier::remove)
        ownerFullscreenExitListener = null
        clearOverlayOwnerFocusListener()
        fullscreenFrame = null
        currentBrowserView = null
        currentBrowser = null
        currentOwnerWindowId = null
        onExitCallback = null
        isInFullscreenMode = false
        hasReachedFullscreen = false
        usesNativeMacOSFullscreen = false
        isExiting = false
        return lifecycleEpoch
    }

    /**
     * Shared cleanup logic for exiting fullscreen mode.
     * Hides and detaches the Swing BrowserView, disposes the frame,
     * and signals TabFullscreenStateManager after a delay.
     *
     * @param frame The JFrame to dispose
     * @param browserView The BrowserView to detach (nullable)
     * @param callback Optional callback to invoke after cleanup completes
     * @param exitingBrowser Browser that owned the cleanup session
     * @param cleanupEpoch Reset epoch that must still be current before Compose is restored
     */
    private fun cleanupAndExit(
        frame: JFrame,
        browserView: BrowserView?,
        callback: (() -> Unit)?,
        exitingBrowser: Browser?,
        cleanupEpoch: Long,
    ) {
        val cleanup =
            Runnable {
                try {
                    // Hide and detach the Swing BrowserView to release rendering surface
                    browserView?.let { view ->
                        view.isVisible = false
                        view.repaint()
                        frame.contentPane.remove(view)
                    }
                    frame.contentPane.revalidate()
                    frame.contentPane.repaint()
                } catch (e: Exception) {
                    logger.error(LogCategory.BROWSER, "Error detaching fullscreen browser view", error = e)
                } finally {
                    try {
                        frame.isAlwaysOnTop = false
                        frame.dispose()
                    } catch (e: Exception) {
                        logger.error(LogCategory.BROWSER, "Error disposing fullscreen window", error = e)
                    }
                }

                logger.info(LogCategory.BROWSER, "Fullscreen window disposed, waiting for rendering release")

                // Delay before telling Compose to show its BrowserView
                // This gives JxBrowser time to fully release the Swing rendering surface
                Timer(SWING_RELEASE_DELAY_MS) {
                    if (shouldRestoreFullscreenTabState(cleanupEpoch, lifecycleEpoch, isInFullscreenMode)) {
                        TabFullscreenStateManager.exitFullscreen()
                        logger.info(LogCategory.BROWSER, "Fullscreen exit complete, Compose BrowserView enabled")
                    }
                    // The old tab still needs its local exit notification when
                    // another browser starts a newer global session. A re-entry
                    // by the same browser supersedes this callback instead.
                    if (currentBrowser !== exitingBrowser && exitingBrowser != null) {
                        callback?.let { exitCallbackGate.notifyOnce(exitingBrowser, it) }
                    }
                }.apply {
                    isRepeats = false
                    start()
                }
            }

        if (SwingUtilities.isEventDispatchThread()) {
            cleanup.run()
        } else {
            SwingUtilities.invokeLater(cleanup)
        }
    }

    /**
     * Keep the browser page and host window in sync: ask Chromium to leave
     * HTML fullscreen first, then tear down directly only if its exit event is
     * lost. JxBrowser's FullScreen.exit() covers all frames in the browser.
     */
    private fun requestPageExit() {
        if (isExiting || !isInFullscreenMode) return
        isExiting = true
        val browser = currentBrowser
        val exitEpoch = lifecycleEpoch
        if (browser == null ||
            runCatching { browser.fullScreen().exit() }
                .onFailure { error ->
                    logger.warn(LogCategory.BROWSER, "Could not request browser fullscreen exit", error = error)
                }.isFailure
        ) {
            performExitDirect()
            return
        }

        Timer(FULLSCREEN_ANIMATION_DELAY_MS) {
            if (lifecycleEpoch == exitEpoch &&
                currentBrowser === browser &&
                isInFullscreenMode
            ) {
                logger.warn(LogCategory.BROWSER, "Browser fullscreen exit event timed out; closing host window")
                performExitDirect()
            }
        }.apply {
            isRepeats = false
            start()
        }
    }

    /** Requests exit only if fullscreen belongs to this browser, without blocking its callback thread. */
    fun requestExit(browser: Browser) {
        SwingUtilities.invokeLater {
            if (currentBrowser === browser) {
                logger.info(LogCategory.BROWSER, "Fullscreen exit requested by owning browser")
                requestPageExit()
            }
        }
    }

    /**
     * Direct exit without relying on fullscreen toggle.
     */
    private fun performExitDirect() {
        val frame = fullscreenFrame
        val browserView = currentBrowserView
        val browser = currentBrowser
        val callback = onExitCallback

        val cleanupEpoch = resetState()
        if (frame == null) {
            TabFullscreenStateManager.exitFullscreen()
            if (browser != null) {
                callback?.let { exitCallbackGate.notifyOnce(browser, it) }
            }
        } else {
            cleanupAndExit(frame, browserView, callback, browser, cleanupEpoch)
        }
    }

    /**
     * Closes the fullscreen window.
     * Safe to call multiple times - will only close once.
     */
    private fun exitFullscreen() {
        val frame = fullscreenFrame
        if (frame == null) {
            if (isInFullscreenMode) {
                resetState()
                TabFullscreenStateManager.exitFullscreen()
            }
            return
        }
        val browserView = currentBrowserView
        val browser = currentBrowser

        val cleanupEpoch = resetState()
        cleanupAndExit(frame, browserView, null, browser, cleanupEpoch)
    }

    /** Closes the matching session and publishes each browser's exit callback at most once. */
    fun exitFullscreenAsync(
        browser: Browser,
        onExit: () -> Unit,
    ) {
        SwingUtilities.invokeLater {
            if (currentBrowser === browser) {
                exitFullscreen()
            }
            exitCallbackGate.notifyOnce(browser, onExit)
        }
    }

    /**
     * Closes fullscreen only when it belongs to the browser being disposed.
     * Blocks a background disposer until the Swing view has been detached, so
     * the browser cannot close underneath the fullscreen window's cleanup.
     * Callers must not hold a lock that the EDT could need; current disposal
     * paths either already run on the EDT or hold only owner-local lifecycle state.
     */
    fun exitFullscreen(browser: Browser) {
        if (currentBrowser !== browser) return
        runOnEventDispatchThreadAndWait(logger, EDT_CLEANUP_TIMEOUT_MS) {
            if (currentBrowser === browser) {
                exitFullscreen()
            }
        }
    }
}
