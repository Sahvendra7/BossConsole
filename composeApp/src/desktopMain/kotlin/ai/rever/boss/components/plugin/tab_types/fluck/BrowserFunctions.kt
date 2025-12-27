package ai.rever.boss.components.plugin.tab_types.fluck

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

// User agent settings
object BrowserSettings {
    var userAgent: String? = null
    var customUserAgent: String? = null
    var currentProfile: String = "browser-profile"
    val availableProfiles = mutableListOf("browser-profile")
}

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
    // Register download callback on this browser
    FluckEngine.setupBrowserDownloadHandler(browser as com.teamdev.jxbrowser.browser.Browser)
    return browser
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

actual fun createBrowserViewState(browser: Any): Any? {
    val jxBrowser = browser as Browser

    // Get a valid window - no blocking, just check if one is ready now
    // Browser initialization is now async (LaunchedEffect), so this is called after window is displayed
    val window = getValidComposeWindow()

    if (window == null) {
        println("⚠️  No valid window available for BrowserViewState - window may not be ready yet")
        return null
    }

    // Use MainScope to ensure UI operations happen on the main thread
    return BrowserViewState(jxBrowser, MainScope(), window)
}

actual fun disposeBrowserViewState(browserViewState: Any) {
    // BrowserViewState doesn't have explicit disposal on JVM
}

actual fun getBrowserState(
    url: String,
    onOpenInNewTab: ((String) -> Unit)?
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

        // Configure popup handler BEFORE creating view state
        // This intercepts target="_blank" and window.open() intelligently:
        // - OAuth popups with specific dimensions → Real popup windows
        // - Regular links without dimensions → New tabs
        if (onOpenInNewTab != null) {
            configureBrowserPopupHandler(browser, onOpenInNewTab)
        }

        val browserViewState = createBrowserViewState(browser)

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

