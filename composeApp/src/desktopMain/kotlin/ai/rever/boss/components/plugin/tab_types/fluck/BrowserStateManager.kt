package ai.rever.boss.components.plugin.tab_types.fluck

import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.navigation.event.NavigationFinished
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Manages browser instances globally to preserve state across tab recreations
 */
object BrowserStateManager {
    // Map of URL to browser instance
    private val browserPool = mutableMapOf<String, BrowserState>()
    
    // Track active browsers
    private val activeBrowsers = mutableSetOf<String>()
    
    data class BrowserState(
        val browser: Browser,
        val browserViewState: Any,
        var lastUrl: String,
        var lastTitle: String = "",
        var refCount: Int = 0,
        val instanceId: String = java.util.UUID.randomUUID().toString() // Unique ID for this browser instance
    )
    
    /**
     * Get or create a browser for a given URL
     * Returns the browser state and its unique instance ID
     */
    fun getOrCreateBrowser(url: String): Pair<BrowserState, String> {
        synchronized(browserPool) {
            // Always create a new browser instance to avoid BrowserViewState conflicts
            // JxBrowser doesn't support multiple view states per browser
            
            // Create new browser
            val browser = createBrowser() as Browser
            val browserViewState = createBrowserViewState(browser)
            
            val state = BrowserState(
                browser = browser,
                browserViewState = browserViewState,
                lastUrl = url,
                refCount = 1
            )
            
            // Track URL changes
            try {
                browser.navigation().on(NavigationFinished::class.java) { event ->
                    if (event.isInMainFrame && !browser.isClosed) {
                        state.lastUrl = event.url()
                        state.lastTitle = browser.title() ?: ""
                    }
                }
            } catch (e: Exception) {
                println("Error setting up navigation listener: ${e.message}")
            }
            
            // Load the URL
            if (url != "about:blank" && url.isNotEmpty()) {
                browser.navigation().loadUrl(url)
            }
            
            // Use the instance ID as the key
            browserPool[state.instanceId] = state
            return Pair(state, state.instanceId)
        }
    }
    
    /**
     * Release a browser reference by instance ID
     */
    fun releaseBrowser(instanceId: String) {
        synchronized(browserPool) {
            val state = browserPool[instanceId] ?: return
            
            state.refCount--
            
            if (state.refCount <= 0) {
                // Immediately dispose browser resources
                try {
                    if (!state.browser.isClosed) {
                        disposeBrowserViewState(state.browserViewState)
                        disposeBrowser(state.browser)
                    }
                } catch (e: Exception) {
                    println("Error disposing browser: ${e.message}")
                }
                browserPool.remove(instanceId)
            }
        }
    }
    
    /**
     * Get an existing browser state if available
     */
    fun getBrowserState(url: String): BrowserState? {
        synchronized(browserPool) {
            // First check direct URL match
            browserPool[url]?.let { state ->
                if (!state.browser.isClosed) {
                    return state
                } else {
                    // Browser is closed, remove it
                    browserPool.remove(url)
                }
            }
            
            // Then check if any browser navigated to this URL
            return browserPool.values.find { state -> 
                state.lastUrl == url && !state.browser.isClosed 
            }
        }
    }
    
    /**
     * Clean up all browsers
     */
    fun cleanup() {
        synchronized(browserPool) {
            browserPool.values.forEach { state ->
                try {
                    if (!state.browser.isClosed) {
                        disposeBrowserViewState(state.browserViewState)
                        disposeBrowser(state.browser)
                    }
                } catch (e: Exception) {
                    println("Error cleaning up browser: ${e.message}")
                }
            }
            browserPool.clear()
        }
    }
}