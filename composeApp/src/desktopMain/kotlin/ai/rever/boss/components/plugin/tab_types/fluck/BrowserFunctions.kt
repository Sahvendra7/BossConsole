package ai.rever.boss.components.plugin.tab_types.fluck

import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import kotlinx.coroutines.MainScope
import java.awt.Frame
import java.awt.Window

// User agent settings
object BrowserSettings {
    var userAgent: String? = null
    var customUserAgent: String? = null
    var currentProfile: String = "browser-profile"
    val availableProfiles = mutableListOf("browser-profile")
}

actual fun createBrowser(): Any {
    return FluckEngine.engine.newBrowser()
}

actual fun disposeBrowser(browser: Any) {
    val jxBrowser = browser as? Browser
    if (jxBrowser != null && !jxBrowser.isClosed) {
        jxBrowser.close()
    }
}

// Try to update user agent on existing browsers (not all changes are possible without restart)
fun updateExistingBrowserSettings() {
    try {
        // Note: JxBrowser doesn't support changing user agent on existing browsers
        // The user agent is set at engine level and requires restart
        // However, we can update some other settings dynamically
        
        // For now, we'll just log that settings have been updated
        println("Browser settings updated. Some changes may require browser restart.")
    } catch (e: Exception) {
        println("Error updating browser settings: ${e.message}")
    }
}

actual fun createBrowserViewState(browser: Any): Any {
    val jxBrowser = browser as Browser
    val window = Window.getWindows().firstOrNull() ?: Frame()
    // Use MainScope to ensure UI operations happen on the main thread
    return BrowserViewState(jxBrowser, MainScope(), window)
}

actual fun disposeBrowserViewState(browserViewState: Any) {
    // BrowserViewState doesn't have explicit disposal on JVM
}

actual fun getBrowserState(url: String): Pair<Any, Any>? {
    return try {
        // Simply create a new browser - don't use the state manager
        // This ensures each tab has its own independent browser
        val browser = createBrowser() as Browser
        val browserViewState = createBrowserViewState(browser)
        
        // Load the URL
        if (url != "about:blank" && url.isNotEmpty()) {
            browser.navigation().loadUrl(url)
        }
        
        Pair(browser, browserViewState)
    } catch (e: Exception) {
        println("Error getting browser state: ${e.message}")
        null
    }
}

actual fun releaseBrowserState(url: String) {
    // Nothing to do - browser will be disposed when the component is disposed
} 