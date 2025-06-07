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
        var refCount: Int = 0
    )
    
    /**
     * Get or create a browser for a given URL
     */
    fun getOrCreateBrowser(url: String): BrowserState {
        synchronized(browserPool) {
            // Try to find an existing browser for this URL
            val existingState = browserPool[url]
            if (existingState != null && !existingState.browser.isClosed) {
                existingState.refCount++
                return existingState
            } else if (existingState?.browser?.isClosed == true) {
                // Browser was closed, remove it from pool
                browserPool.remove(url)
            }
            
            // Also check if we have a browser that navigated to this URL
            browserPool.values.find { it.lastUrl == url && !it.browser.isClosed }?.let { state ->
                state.refCount++
                return state
            }
            
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
            
            browserPool[url] = state
            return state
        }
    }
    
    /**
     * Release a browser reference
     */
    fun releaseBrowser(url: String) {
        synchronized(browserPool) {
            val state = browserPool[url] ?: return
            state.refCount--
            
            if (state.refCount <= 0) {
                // Don't immediately dispose - keep it around for a bit in case it's needed again
                // This helps with configuration switching
                GlobalScope.launch {
                    delay(30000) // Wait 30 seconds
                    synchronized(browserPool) {
                        // Check again if it's still not in use and browser is not closed
                        if (state.refCount <= 0) {
                            try {
                                if (!state.browser.isClosed) {
                                    disposeBrowserViewState(state.browserViewState)
                                    disposeBrowser(state.browser)
                                }
                            } catch (e: Exception) {
                                println("Error disposing browser: ${e.message}")
                            }
                            browserPool.remove(url)
                        }
                    }
                }
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