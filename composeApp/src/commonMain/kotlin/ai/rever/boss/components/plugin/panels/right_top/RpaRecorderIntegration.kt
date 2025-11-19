@file:OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)

package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.LocalSplitViewState
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Integration utilities for connecting RPA Recorder to browser instances
 */
interface BrowserIntegration {
    /**
     * Execute JavaScript in the browser
     */
    suspend fun executeJavaScript(script: String): Any?
    
    /**
     * Check if browser is available
     */
    fun isBrowserAvailable(): Boolean
    
    /**
     * Get current URL
     */
    suspend fun getCurrentUrl(): String?

}

/**
 * Interface for accessing the active browser tab
 */
expect class BrowserAccessor() {
    /**
     * Get browser integration for the active tab
     */
    fun getActiveBrowserIntegration(): BrowserIntegration?
    
    companion object {
        var selectedTabId: String?
    }
}

/**
 * Platform-specific function to create FluckTabInfo from ActiveTab
 */
expect fun createFluckTabInfo(activeTab: Any): FluckTabInfo?

/**
 * Platform-specific function to store split view state
 */
expect fun storeSplitViewState(splitViewState: Any)

/**
 * Composable state for browser connection to a specific tab
 */
@Composable
fun rememberBrowserConnectionForTab(
    tab: FluckTabInfo?
): State<BrowserIntegration?> {
    val browserState = remember { mutableStateOf<BrowserIntegration?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    DisposableEffect(tab) {
        val job = if (tab != null) {
            coroutineScope.launch {
                // Poll for browser connection
                while (true) {
                    try {
                        val accessor = BrowserAccessor()
                        // Set the selected tab for the accessor to use
                        BrowserAccessor.selectedTabId = tab.id
                        val integration = accessor.getActiveBrowserIntegration()
                        
                        // Verify the browser is actually available before setting
                        if (integration?.isBrowserAvailable() == true) {
                            browserState.value = integration
                        } else {
                            browserState.value = null
                        }
                    } catch (e: Exception) {
                        // Browser might be disposed or inaccessible
                        browserState.value = null
                    }
                    delay(500)
                }
            }
        } else {
            browserState.value = null
            null
        }
        
        onDispose {
            job?.cancel()
            BrowserAccessor.selectedTabId = null
            browserState.value = null
        }
    }
    
    return browserState
}

/**
 * Enhanced RPA Recorder Component with browser integration
 */
@Composable
fun RpaRecorderContent(
    component: RpaRecorderComponent
) {
    // Import LocalSplitViewState
    val splitViewState = LocalSplitViewState.current
    
    // Collect available Fluck tabs directly here
    val availableTabs = remember { mutableStateOf<List<FluckTabInfo>>(emptyList()) }
    
    LaunchedEffect(splitViewState) {
        if (splitViewState != null) {
            // Store split view state for browser accessor
            storeSplitViewState(splitViewState)
            while (true) {
                try {
                    // Get all active Fluck tabs
                    val activeFluckTabs = splitViewState.collectAllActiveFluckTabs()
                    
                    val tabs = activeFluckTabs.mapNotNull { activeTab ->
                        try {
                            // Create FluckTabInfo from ActiveTab
                            createFluckTabInfo(activeTab)
                        } catch (e: Exception) {
                            // Tab might be disposing
                            null
                        }
                    }
                    
                    availableTabs.value = tabs
                    component.updateAvailableTabs(tabs)
                } catch (e: Exception) {
                    // Handle any errors during tab collection
                    println("Error collecting tabs: ${e.message}")
                }
                
                delay(1000) // Update every second
            }
        }
    }
    
    // Get browser connection for selected tab
    val selectedTab by component.selectedTab.collectAsState()
    val browserConnection by rememberBrowserConnectionForTab(selectedTab)
    val isConnected = try {
        browserConnection?.isBrowserAvailable() == true
    } catch (e: Exception) {
        false
    }
    
    // Update recording state based on browser connection
    val recordingState by component.isRecording.collectAsState()
    LaunchedEffect(browserConnection, recordingState, selectedTab) {
        val browser = browserConnection
        if (recordingState && browser != null && selectedTab != null) {
            component.connectToBrowser(browser)
        } else if (!recordingState || selectedTab == null) {
            component.disconnectFromBrowser()
        }
    }
    
    // Display connection status
    component.updateConnectionStatus(isConnected && selectedTab != null)
    
    // Render the main content
    component.ContentInternal()
}

/**
 * Extension functions for RpaRecorderComponent
 */
private val _isConnected = MutableStateFlow(false)
val RpaRecorderComponent.isConnected: StateFlow<Boolean> get() = _isConnected

fun RpaRecorderComponent.updateConnectionStatus(connected: Boolean) {
    _isConnected.value = connected
}

suspend fun RpaRecorderComponent.connectToBrowser(browser: BrowserIntegration) {
    try {
        println("RPA Recorder Integration: Connecting to browser")
        
        // Store browser connection reference
        browserConnection = browser
        
        // Inject the event capture script
        println("RPA Recorder Integration: Injecting event capture script")
        val scriptResult = browser.executeJavaScript(RpaEventCapture.eventCaptureScript)
        println("RPA Recorder Integration: Event capture script injected, result: $scriptResult")
        
        // Set up callback handler
        browser.executeJavaScript("""
            window.__rpaRecordAction = function(actionJson) {
                // Store actions in window for retrieval
                window.__rpaRecordedActions = window.__rpaRecordedActions || [];
                window.__rpaRecordedActions.push(actionJson);
                console.log('RPA Action Recorded:', actionJson);
            };
            console.log('RPA Recorder: Callback handler installed');
        """.trimIndent())
        
        println("RPA Recorder Integration: Starting action polling")
        // Start polling for recorded actions
        startActionPolling(browser)
        
        // Add initial navigation action now that we have browser connection
        addInitialNavigation()
        
    } catch (e: Exception) {
        println("RPA Recorder Integration: Error connecting to browser: ${e.message}")
    }
}

fun RpaRecorderComponent.disconnectFromBrowser() {
    // Clear browser connection reference
    browserConnection = null
    // Stop polling
    stopActionPolling()
}

private var pollingJob: kotlinx.coroutines.Job? = null

private fun RpaRecorderComponent.startActionPolling(browser: BrowserIntegration) {
    pollingJob = kotlinx.coroutines.GlobalScope.launch {
        var pollCount = 0
        var lastUrl = ""

        while (isRecording.value) {
            try {
                // Check if browser is still available before accessing
                if (!browser.isBrowserAvailable()) {
                    println("RPA Recorder: Browser no longer available, stopping polling")
                    break
                }

                // Check if URL has changed (navigation occurred)
                val currentUrl = browser.getCurrentUrl() ?: ""
                if (currentUrl != lastUrl && currentUrl.isNotEmpty()) {
                    lastUrl = currentUrl
                    _currentUrl.value = currentUrl

                    // Re-inject event capture script after navigation
                    if (pollCount > 0) { // Skip first iteration
                        println("RPA Recorder: Page navigated to $currentUrl, re-injecting event capture")
                        delay(500) // Wait for page to stabilize

                        // Check browser availability before re-injecting scripts
                        if (!browser.isBrowserAvailable()) {
                            println("RPA Recorder: Browser disposed during navigation, stopping polling")
                            break
                        }

                        // Re-inject scripts
                        browser.executeJavaScript(RpaEventCapture.eventCaptureScript)
                        browser.executeJavaScript("""
                            window.__rpaRecordAction = function(actionJson) {
                                window.__rpaRecordedActions = window.__rpaRecordedActions || [];
                                window.__rpaRecordedActions.push(actionJson);
                                console.log('RPA Action Recorded:', actionJson);
                            };
                            console.log('RPA Recorder: Re-injected after navigation');
                        """.trimIndent())
                    }
                }

                // Check browser availability before retrieving actions
                if (!browser.isBrowserAvailable()) {
                    println("RPA Recorder: Browser disposed, stopping polling")
                    break
                }

                // Retrieve recorded actions from browser
                val actions = browser.executeJavaScript("""
                    (function() {
                        const actions = window.__rpaRecordedActions || [];
                        window.__rpaRecordedActions = [];
                        return JSON.stringify(actions);
                    })();
                """.trimIndent()) as? String

                if (!actions.isNullOrEmpty() && actions != "[]") {
                    println("RPA Recorder Polling: Found actions: $actions")
                    // Parse and process actions
                    val actionsList = kotlinx.serialization.json.Json.decodeFromString<List<String>>(actions)
                    actionsList.forEach { actionJson ->
                        try {
                            val action = kotlinx.serialization.json.Json.decodeFromString<RecordedAction>(actionJson)
                            onActionRecorded(action)
                            println("RPA Recorder Polling: Recorded action - Type: ${action.type}, Value: ${action.value}")
                        } catch (e: Exception) {
                            println("RPA Recorder Polling: Error parsing action: ${e.message}")
                        }
                    }
                }

                pollCount++

            } catch (e: Exception) {
                // Log error and break the loop to prevent freeze
                println("RPA Recorder Polling: Error - ${e.message}")
                println("RPA Recorder: Stopping polling due to error")
                break
            }

            delay(100) // Poll every 100ms
        }
    }
}

private fun RpaRecorderComponent.stopActionPolling() {
    pollingJob?.cancel()
    pollingJob = null
}
