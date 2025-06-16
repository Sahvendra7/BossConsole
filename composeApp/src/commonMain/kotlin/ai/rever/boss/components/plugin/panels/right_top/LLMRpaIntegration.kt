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
        val browser = browserConnection
        val tab = selectedTab
        if (browser != null && tab != null) {
            println("LLM RPA Integration: Browser connection established for tab: ${tab.id}")
            component.browserConnection = browser
            // Create RPA executor for this browser
            component.rpaExecutor = createRpaExecutor(browser)
            println("LLM RPA Integration: RPA executor created")
        } else {
            println("LLM RPA Integration: No browser connection - browserConnection: $browser, selectedTab: $tab")
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
    // Try to use platform-specific executor first
    val platformExecutor = createPlatformLLMRpaExecutor(browser)
    if (platformExecutor != null) {
        return platformExecutor
    }
    
    // Fallback to generic executor
    return object : BaseActionExecutor() {
        override suspend fun navigate(url: String) {
            browser.executeJavaScript("window.location.href = '$url'")
            delay(2000) // Wait for navigation
        }
        
        override suspend fun click(selector: SelectorInfo) {
            // First wait for element to be present
            val waited = waitForElement(selector, 5000)
            if (!waited) {
                throw Exception("Element not found after waiting: ${selector.type}=${selector.value}")
            }
            
            val script = """
                (function() {
                    console.log('LLM RPA: Attempting to click element with selector:', '${selector.type}', '${selector.value}');
                    var element = ${buildSelectorScript(selector)};
                    if (element) {
                        console.log('LLM RPA: Element found:', element);
                        // Ensure element is visible and clickable
                        element.scrollIntoView({behavior: 'smooth', block: 'center'});
                        // Click immediately without setTimeout
                        element.click();
                        console.log('LLM RPA: Clicked element');
                        return true;
                    }
                    console.log('LLM RPA: Element not found');
                    return false;
                })()
            """.trimIndent()
            
            val result = browser.executeJavaScript(script)
            delay(300) // Wait for click action
            if (result != true) {
                throw Exception("Element not found: ${selector.type}=${selector.value}")
            }
        }
        
        override suspend fun input(selector: SelectorInfo, value: String) {
            // First wait for element to be present
            val waited = waitForElement(selector, 5000)
            if (!waited) {
                throw Exception("Input element not found after waiting: ${selector.type}=${selector.value}")
            }
            
            val script = """
                (function() {
                    console.log('LLM RPA: Attempting to input text with selector:', '${selector.type}', '${selector.value}');
                    var element = ${buildSelectorScript(selector)};
                    if (element) {
                        console.log('LLM RPA: Input element found:', element);
                        // Focus the element first
                        element.focus();
                        // Clear existing value
                        element.value = '';
                        // Set new value
                        element.value = '${value.replace("'", "\\'")}';
                        // Trigger various events that websites might listen to
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        element.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                        console.log('LLM RPA: Input value set to:', element.value);
                        return true;
                    }
                    console.log('LLM RPA: Input element not found');
                    return false;
                })()
            """.trimIndent()
            
            val result = browser.executeJavaScript(script)
            if (result != true) {
                throw Exception("Input element not found: ${selector.type}=${selector.value}")
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