package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.LocalSplitViewState
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Enhanced LLM RPA Component with browser integration
 */
@Composable
fun LLMRpaContent(
    component: LLMRpaComponent
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
                // Get all active Fluck tabs
                val activeFluckTabs = splitViewState.collectAllActiveFluckTabs()
                
                val tabs = activeFluckTabs.mapNotNull { activeTab ->
                    // Create FluckTabInfo from ActiveTab
                    createFluckTabInfo(activeTab)
                }
                
                availableTabs.value = tabs
                component.updateAvailableTabs(tabs)
                
                delay(1000) // Update every second
            }
        }
    }
    
    // Get browser connection for selected tab
    val selectedTab by component.selectedTab.collectAsState()
    val browserConnection by rememberBrowserConnectionForTab(selectedTab, splitViewState)
    
    // Update browser connection and executor
    LaunchedEffect(browserConnection, selectedTab) {
        if (browserConnection != null && selectedTab != null) {
            component.browserConnection = browserConnection
            // Create RPA executor for this browser
            component.rpaExecutor = createRpaExecutor(browserConnection!!)
        } else {
            component.browserConnection = null
            component.rpaExecutor = null
        }
    }
    
    // Render the main content
    component.ContentInternal()
}

/**
 * Create RPA executor for browser integration
 */
private fun createRpaExecutor(browser: BrowserIntegration): RpaActionExecutor {
    return object : BaseActionExecutor() {
        override suspend fun navigate(url: String) {
            browser.executeJavaScript("window.location.href = '$url'")
            delay(2000) // Wait for navigation
        }
        
        override suspend fun click(selector: SelectorInfo) {
            val script = """
                (function() {
                    var element = ${buildSelectorScript(selector)};
                    if (element) {
                        element.click();
                        return true;
                    }
                    return false;
                })()
            """.trimIndent()
            
            val result = browser.executeJavaScript(script)
            if (result != true) {
                throw Exception("Element not found: ${selector.value}")
            }
        }
        
        override suspend fun input(selector: SelectorInfo, value: String) {
            val script = """
                (function() {
                    var element = ${buildSelectorScript(selector)};
                    if (element) {
                        element.value = '${value.replace("'", "\\'")}';
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        return true;
                    }
                    return false;
                })()
            """.trimIndent()
            
            val result = browser.executeJavaScript(script)
            if (result != true) {
                throw Exception("Element not found: ${selector.value}")
            }
        }
        
        override suspend fun select(selector: SelectorInfo, value: String) {
            val script = """
                (function() {
                    var element = ${buildSelectorScript(selector)};
                    if (element && element.tagName === 'SELECT') {
                        // Try by visible text first
                        for (var i = 0; i < element.options.length; i++) {
                            if (element.options[i].text === '$value') {
                                element.selectedIndex = i;
                                element.dispatchEvent(new Event('change', { bubbles: true }));
                                return true;
                            }
                        }
                        // Try by value
                        element.value = '$value';
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        return true;
                    }
                    return false;
                })()
            """.trimIndent()
            
            val result = browser.executeJavaScript(script)
            if (result != true) {
                throw Exception("Select element not found: ${selector.value}")
            }
        }
        
        override suspend fun wait(milliseconds: Long) {
            delay(milliseconds)
        }
        
        override suspend fun scroll(x: Int, y: Int) {
            browser.executeJavaScript("window.scrollTo($x, $y)")
            delay(500)
        }
        
        override suspend fun switchToFrame(selector: SelectorInfo) {
            // Frame switching would need platform-specific implementation
            throw UnsupportedOperationException("Frame switching not implemented")
        }
        
        override suspend fun switchToDefaultContent() {
            // Frame switching would need platform-specific implementation
            throw UnsupportedOperationException("Frame switching not implemented")
        }
        
        override suspend fun refresh() {
            browser.executeJavaScript("window.location.reload()")
            delay(2000)
        }
        
        override suspend fun executeJavaScript(script: String): Any? {
            return browser.executeJavaScript(script)
        }
    }
}

/**
 * Platform-specific function to create LLM RPA executor
 */
expect fun createPlatformLLMRpaExecutor(browser: Any): RpaActionExecutor?