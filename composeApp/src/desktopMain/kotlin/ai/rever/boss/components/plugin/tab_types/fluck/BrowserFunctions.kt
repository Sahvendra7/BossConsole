package ai.rever.boss.components.plugin.tab_types.fluck

import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import kotlinx.coroutines.MainScope
import java.awt.Window

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

actual fun createBrowser(): Any {
    return FluckEngine.engine.newBrowser()
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

actual fun getBrowserState(url: String): Pair<Any, Any>? {
    return try {
        // Create a new browser - each tab has its own independent browser
        val browser = createBrowser() as Browser
        val browserViewState = createBrowserViewState(browser)

        // If browserViewState creation failed (no valid window), clean up and return null
        if (browserViewState == null) {
            println("Warning: Could not create BrowserViewState - no valid window available")
            browser.close()
            return null
        }

        // Load the URL
        if (url != "about:blank" && url.isNotEmpty()) {
            browser.navigation().loadUrl(url)
        }

        Pair(browser, browserViewState)
    } catch (e: Exception) {
        println("Error getting browser state: ${e.message}")
        e.printStackTrace()
        null
    }
}

