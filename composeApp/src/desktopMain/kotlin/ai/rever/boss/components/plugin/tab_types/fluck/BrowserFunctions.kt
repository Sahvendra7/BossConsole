package ai.rever.boss.components.plugin.tab_types.fluck

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.browser.event.BrowserClosed
import com.teamdev.jxbrowser.event.Subscription
import com.teamdev.jxbrowser.navigation.event.LoadStarted
import com.teamdev.jxbrowser.navigation.event.NavigationFinished
import com.teamdev.jxbrowser.ui.Rect
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import com.teamdev.jxbrowser.view.swing.BrowserView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Window
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFrame
import javax.swing.SwingUtilities
import com.teamdev.jxbrowser.browser.callback.AlertCallback
import com.teamdev.jxbrowser.browser.callback.ConfirmCallback
import com.teamdev.jxbrowser.browser.callback.PromptCallback

// User agent settings
object BrowserSettings {
    var userAgent: String? = null
    var customUserAgent: String? = null
    var currentProfile: String = "browser-profile"
    val availableProfiles = mutableListOf("browser-profile")

    // Browser initialization retry settings (configurable via Settings)
    var maxInitRetries: Int = 3
    var maxRecoveryAttempts: Int = 3

    // JavaScript dialog settings (configurable via Settings > Browser)
    // Due to JxBrowser threading limitations, dialogs must be auto-handled
    enum class JsConfirmBehavior { AUTO_CONFIRM, AUTO_CANCEL }
    var jsConfirmBehavior: JsConfirmBehavior = JsConfirmBehavior.AUTO_CONFIRM
    var jsPromptDefaultValue: String = ""  // Empty string or user-configured default
    var jsPromptUsePageDefault: Boolean = true  // Use page's default value if true, else use jsPromptDefaultValue
}

/**
 * CompositionLocal providing the current AWT Window for this Compose window.
 * Used by JxBrowser to get the correct window handle for BrowserViewState.
 *
 * This fixes the multi-window crash where browsers in window 2 would reference
 * window 1's handle because getValidComposeWindow() returned the first window.
 */
val LocalAwtWindow = compositionLocalOf<Window?> { null }

/**
 * Gets a valid AWT Window that is safe to use with JxBrowser.
 * Returns null if no valid window is available.
 *
 * A valid window must be:
 * - Displayable (has native peer created)
 * - Showing (visible on screen)
 */
private fun getValidComposeWindow(): Window? {
    return Window.getWindows()
        .firstOrNull { window ->
            try {
                // Check if window is properly initialized
                window.isDisplayable && window.isShowing
            } catch (e: Exception) {
                // Window might be in invalid state during disposal
                false
            }
        }
}

/**
 * Configures JavaScript dialog handlers to prevent UI freeze (Issue #369).
 *
 * JxBrowser's JS dialogs (alert, confirm, prompt) use callbacks that block the Chromium
 * process until a response is provided. In Compose Desktop, showing a modal Swing dialog
 * from within these callbacks causes a deadlock between Swing's EDT and JxBrowser's IPC.
 *
 * Solution: Call tell.ok() immediately to unblock JxBrowser, then show a non-blocking
 * informational dialog on the Swing EDT.
 *
 * @param browser The browser instance to configure
 */
private fun setupBrowserDialogHandlers(browser: Browser) {
    val browserId = System.identityHashCode(browser)  // Unique ID for this browser instance

    // Alert callback - unblock immediately, then notify for BOSS-styled dialog
    browser.set(AlertCallback::class.java, AlertCallback { params, tell ->
        val message = params.message()
        val title = params.title()

        // CRITICAL: Call tell.ok() FIRST to unblock JxBrowser
        tell.ok()

        // Emit event for Compose UI to show BOSS-styled dialog
        JsDialogNotifier.notifyAlert(
            browserId = browserId,
            title = title.ifEmpty { "Alert" },
            message = message
        )
    })

    // Confirm callback - behavior based on settings
    browser.set(ConfirmCallback::class.java, ConfirmCallback { params, tell ->
        val message = params.message()
        val title = params.title()
        val confirmed = BrowserSettings.jsConfirmBehavior == BrowserSettings.JsConfirmBehavior.AUTO_CONFIRM

        // CRITICAL: Call tell FIRST to unblock JxBrowser
        if (confirmed) {
            tell.ok()
        } else {
            tell.cancel()
        }

        // Emit event for Compose UI to show BOSS-styled dialog
        JsDialogNotifier.notifyConfirm(
            browserId = browserId,
            title = title.ifEmpty { "Confirm" },
            message = message,
            confirmed = confirmed
        )
    })

    // Prompt callback - value based on settings
    browser.set(PromptCallback::class.java, PromptCallback { params, tell ->
        val message = params.message()
        val pageDefault = params.text()  // text() returns the page's default prompt value
        val title = params.title()

        // Determine value to use based on settings
        val valueToUse = if (BrowserSettings.jsPromptUsePageDefault) {
            pageDefault
        } else {
            BrowserSettings.jsPromptDefaultValue
        }

        // CRITICAL: Call tell.ok() FIRST to unblock JxBrowser
        tell.ok(valueToUse)

        // Emit event for Compose UI to show BOSS-styled dialog
        JsDialogNotifier.notifyPrompt(
            browserId = browserId,
            title = title.ifEmpty { "Prompt" },
            message = message,
            value = valueToUse
        )
    })
}

/**
 * Configures browser to handle popup requests intelligently based on window features.
 *
 * Complete solution using JxBrowser's two-phase popup handling:
 * 1. CreatePopupCallback - Allows all popup creation (returns create())
 * 2. OpenPopupCallback - Checks initialBounds() to decide:
 *    - Empty bounds (regular links) → Open as tab in BOSS
 *    - Non-empty bounds (OAuth popups) → Create Swing window to display popup
 *
 * This properly fixes both issues:
 * - Issue #137: Mail app links open as tabs, not OS windows
 * - Issue #173: OAuth popups work correctly with window.opener communication
 *
 * Implementation details:
 * - Empty bounds popups: Close popup browser, open URL as tab via callback
 * - Non-empty bounds popups: Create JFrame, embed BrowserView, handle window lifecycle
 *
 * @param browser The browser instance to configure
 * @param onOpenInNewTab Callback invoked with the target URL when popup should open in new tab
 */
private fun configureBrowserPopupHandler(
    browser: Browser,
    onOpenInNewTab: (String) -> Unit
) {
    // Phase 1: Allow all popup creation
    browser.set(
        com.teamdev.jxbrowser.browser.callback.CreatePopupCallback::class.java,
        com.teamdev.jxbrowser.browser.callback.CreatePopupCallback {
            // Allow popup creation - we'll decide what to do in OpenPopupCallback
            com.teamdev.jxbrowser.browser.callback.CreatePopupCallback.Response.create()
        }
    )

    // Phase 2: Handle popup display based on bounds
    browser.set(
        com.teamdev.jxbrowser.browser.callback.OpenPopupCallback::class.java,
        com.teamdev.jxbrowser.browser.callback.OpenPopupCallback { params ->
            val popupBrowser = params.popupBrowser()
            val initialBounds = params.initialBounds()
            val targetUrl = popupBrowser.url()

            // Check if popup has specific window dimensions
            val isEmptyBounds = initialBounds == Rect.empty()

            if (isEmptyBounds) {
                // No dimensions = regular link (mail app, target="_blank")
                // Open as tab in BOSS instead of OS window
                if (targetUrl.isEmpty() || targetUrl == "about:blank") {
                    // Use LoadStarted instead of NavigationFinished for immediate response
                    // This fires as soon as navigation begins, not when page fully loads
                    val cleanedUp = AtomicBoolean(false)
                    var subscription: Subscription? = null
                    val scope = CoroutineScope(Dispatchers.Default + Job())

                    subscription = popupBrowser.navigation().on(LoadStarted::class.java) {
                        try {
                            // Issue #255: Protect popup browser URL access from "closed object" exception
                            val loadedUrl = popupBrowser.url()
                            if (loadedUrl.isNotEmpty() && loadedUrl != "about:blank") {
                                // Only cleanup if we're the first handler to run
                                if (cleanedUp.compareAndSet(false, true)) {
                                    // Check if this URL is a download - if so, skip opening in new tab
                                    val isDownload = FluckEngine.isActiveDownload(loadedUrl)
                                    println("BrowserFunctions: Popup LoadStarted - URL: $loadedUrl, isDownload: $isDownload")
                                    if (!isDownload) {
                                        // Notify that a tab is being opened (might be download redirect)
                                        FluckEngine.notifyTabOpened()
                                        onOpenInNewTab(loadedUrl)
                                    } else {
                                        println("BrowserFunctions: Skipping new tab for download URL")
                                    }
                                    subscription?.unsubscribe()
                                    scope.cancel()
                                    if (!popupBrowser.isClosed) {
                                        popupBrowser.close()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Popup browser was closed - cleanup and exit
                            // Issue #255: Gracefully handle "closed object" exceptions
                            if (cleanedUp.compareAndSet(false, true)) {
                                subscription?.unsubscribe()
                                scope.cancel()
                            }
                        }
                    }

                    // Timeout fallback: cleanup after 3 seconds (reduced from 10s)
                    scope.launch {
                        delay(3_000)
                        // Only cleanup if we're the first handler to run
                        if (cleanedUp.compareAndSet(false, true)) {
                            subscription?.unsubscribe()
                            if (!popupBrowser.isClosed) {
                                popupBrowser.close()
                            }
                            println("Warning: Popup navigation timed out after 3s, closing browser")
                        }
                    }
                } else {
                    // Check if this URL is a download - if so, skip opening in new tab
                    val isDownload = FluckEngine.isActiveDownload(targetUrl)
                    println("BrowserFunctions: Popup with immediate URL - URL: $targetUrl, isDownload: $isDownload")
                    if (!isDownload) {
                        // Notify that a tab is being opened (might be download redirect)
                        FluckEngine.notifyTabOpened()
                        onOpenInNewTab(targetUrl)
                    } else {
                        println("BrowserFunctions: Skipping new tab for download URL")
                    }
                    popupBrowser.close()
                }
            } else {
                // Has dimensions = OAuth/payment popup (window.open with features)
                // Create Swing window to display the popup browser
                SwingUtilities.invokeLater {
                    try {
                        // Create JFrame for the popup
                        val frame = JFrame()
                        val subscriptions = mutableListOf<Subscription>()

                        frame.title = "Popup" // Will be updated by page title
                        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

                        // Set position and size from bounds
                        frame.setLocation(initialBounds.origin().x(), initialBounds.origin().y())
                        frame.setSize(initialBounds.size().width(), initialBounds.size().height())

                        // Create BrowserView and add to frame
                        val browserView = BrowserView.newInstance(popupBrowser)
                        frame.contentPane.add(browserView)

                        // Update frame title when page title changes - store subscription
                        subscriptions += popupBrowser.on(com.teamdev.jxbrowser.browser.event.TitleChanged::class.java) { event ->
                            SwingUtilities.invokeLater {
                                frame.title = event.title()
                            }
                        }

                        // Close frame when browser closes - store subscription
                        subscriptions += popupBrowser.on(BrowserClosed::class.java) {
                            SwingUtilities.invokeLater {
                                // Cleanup subscriptions before disposing
                                subscriptions.forEach { it.unsubscribe() }
                                frame.dispose()
                            }
                        }

                        // Close browser when frame closes
                        frame.addWindowListener(object : java.awt.event.WindowAdapter() {
                            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                                // Cleanup subscriptions before closing browser
                                subscriptions.forEach {
                                    try {
                                        it.unsubscribe()
                                    } catch (_: Exception) {
                                        // Ignore errors during cleanup
                                    }
                                }
                                if (!popupBrowser.isClosed) {
                                    popupBrowser.close()
                                }
                            }
                        })

                        // Show the popup window
                        frame.isVisible = true
                    } catch (e: Exception) {
                        println("Error creating popup window: ${e.message}")
                        // Close browser on error
                        if (!popupBrowser.isClosed) {
                            popupBrowser.close()
                        }
                    }
                }
            }

            // Return proceed() to notify the engine we've handled the popup
            com.teamdev.jxbrowser.browser.callback.OpenPopupCallback.Response.proceed()
        }
    )
}

actual fun createBrowser(): Any {
    val browser = FluckEngine.engine.newBrowser()
    // Enable swipe navigation for touchscreen devices
    browser.settings().enableOverscrollHistoryNavigation()
    // Register download callback on this browser
    FluckEngine.setupBrowserDownloadHandler(browser as com.teamdev.jxbrowser.browser.Browser)
    // Register screen capture handler to prevent repeated permission dialogs on macOS
    FluckEngine.setupCaptureSessionHandler(browser)
    return browser
}

actual suspend fun resetBrowserProfile(): Boolean {
    return FluckEngine.resetBrowserProfile().success
}

actual fun disposeBrowser(browser: Any) {
    try {
        val jxBrowser = browser as? Browser
        if (jxBrowser != null && !jxBrowser.isClosed) {
            jxBrowser.close()
        }
    } catch (e: Exception) {
        // Suppress exceptions during disposal to prevent crashes in cleanup code
        println("Warning: Exception during browser disposal: ${e.message}")
    }
}

actual fun createBrowserViewState(browser: Any, window: Any?): Any? {
    val jxBrowser = browser as Browser

    // Use provided window first (from LocalAwtWindow), fall back to finding a valid window
    val awtWindow = (window as? Window)?.takeIf {
        try { it.isDisplayable && it.isShowing } catch (e: Exception) { false }
    } ?: getValidComposeWindow()

    if (awtWindow == null) {
        println("⚠️  No valid window available for BrowserViewState - window may not be ready yet")
        return null
    }

    // Use MainScope to ensure UI operations happen on the main thread
    return BrowserViewState(jxBrowser, MainScope(), awtWindow)
}

actual fun disposeBrowserViewState(browserViewState: Any) {
    // BrowserViewState doesn't have explicit disposal on JVM
}

actual fun getBrowserState(
    url: String,
    onOpenInNewTab: ((String) -> Unit)?,
    onBrowserClosed: (() -> Unit)?,
    window: Any?
): Pair<Any, Any>? {
    return try {
        // Verify engine is available before creating browser
        val engine = FluckEngine.engine
        if (engine.isClosed) {
            println("⚠️ getBrowserState: Engine is closed, cannot create browser")
            return null
        }

        // Create a new browser - each tab has its own independent browser
        val browser = createBrowser() as Browser

        // Verify browser was created successfully
        if (browser.isClosed) {
            println("⚠️ getBrowserState: Browser was closed immediately after creation")
            return null
        }

        // Subscribe to BrowserClosed event for event-driven recovery (Issue #351)
        // This replaces polling - we get notified immediately when browser closes
        if (onBrowserClosed != null) {
            browser.on(BrowserClosed::class.java) {
                println("🔔 [BrowserFunctions] BrowserClosed event fired")
                onBrowserClosed()
            }
        }

        // Configure JS dialog handlers to prevent UI freeze (Issue #369)
        // Must call tell.ok() immediately to unblock JxBrowser, then show informational dialog
        setupBrowserDialogHandlers(browser)

        // Configure popup handler BEFORE creating view state
        // This intercepts target="_blank" and window.open() intelligently:
        // - OAuth popups with specific dimensions → Real popup windows
        // - Regular links without dimensions → New tabs
        if (onOpenInNewTab != null) {
            configureBrowserPopupHandler(browser, onOpenInNewTab)
        }

        // Pass the window from LocalAwtWindow to ensure browsers get the correct window handle
        val browserViewState = createBrowserViewState(browser, window)

        // If browserViewState creation failed (no valid window), clean up and return null
        if (browserViewState == null) {
            println("⚠️ getBrowserState: Could not create BrowserViewState - no valid window available")
            if (!browser.isClosed) {
                browser.close()
            }
            return null
        }

        // Load the URL (verify browser is still valid first)
        if (!browser.isClosed && url != "about:blank" && url.isNotEmpty()) {
            browser.navigation().loadUrl(url)
        }

        Pair(browser, browserViewState)
    } catch (e: Exception) {
        // Provide detailed error info for debugging
        val errorType = when {
            e.message?.contains("closed object", ignoreCase = true) == true ->
                "JxBrowser closed object error (engine or browser was disposed)"
            e.message?.contains("SharedMemory", ignoreCase = true) == true ->
                "JxBrowser IPC error (Chromium process may have crashed)"
            else -> "Unknown error"
        }
        println("❌ getBrowserState failed: $errorType")
        println("   Details: ${e.message}")
        null
    }
}

/**
 * Returns the current engine generation counter.
 * Increments every time the FluckEngine is reinitialized.
 */
actual fun getEngineGeneration(): Long = FluckEngine.currentEngineGeneration

/**
 * Checks if a browser instance is still valid and usable.
 * Returns false if browser is null, closed, or its engine is closed.
 */
actual fun isBrowserValid(browser: Any?): Boolean {
    if (browser == null) return false
    val jxBrowser = browser as? Browser ?: return false
    return try {
        // Check if browser is closed
        !jxBrowser.isClosed
    } catch (e: Exception) {
        // Any exception means browser is in bad state
        println("⚠️ isBrowserValid: Exception checking browser state: ${e.message}")
        false
    }
}

/**
 * Returns user-friendly error message if engine initialization failed.
 * Useful for showing specific feedback about license validation or network errors.
 */
actual fun getEngineInitError(): String? {
    return FluckEngine.initError?.let { error ->
        when (error) {
            is EngineInitError.LicenseValidation -> error.message
            is EngineInitError.NetworkError -> error.message
            is EngineInitError.Other -> error.message
        }
    }
}

/**
 * Resets engine initialization state to allow retry after fixing network issues.
 */
actual fun resetEngineInitialization() {
    FluckEngine.resetInitializationState()
}

/**
 * Get max initialization retries from settings.
 * Configurable via Settings > Browser > Advanced.
 */
actual fun getMaxInitRetries(): Int = BrowserSettings.maxInitRetries

/**
 * Get max recovery attempts from settings.
 * Configurable via Settings > Browser > Advanced.
 */
actual fun getMaxRecoveryAttempts(): Int = BrowserSettings.maxRecoveryAttempts

/**
 * Composable function to observe engine generation changes.
 * Returns the current generation and triggers recomposition when it changes.
 */
@Composable
actual fun collectEngineGeneration(): Long {
    val generation by FluckEngine.engineGenerationFlow.collectAsState()
    return generation
}

/**
 * Get the current AWT Window from the LocalAwtWindow CompositionLocal.
 * This ensures browsers get the correct window handle for their containing window.
 */
@Composable
actual fun getCurrentAwtWindow(): Any? = LocalAwtWindow.current

