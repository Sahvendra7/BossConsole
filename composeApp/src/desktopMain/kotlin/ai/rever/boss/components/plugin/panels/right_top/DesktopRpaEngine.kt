package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.plugin.tab_types.fluck.FluckEngine
import com.arkivanov.decompose.ComponentContext
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.navigation.event.LoadFinished
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop implementation of RPA Engine Factory
 */
actual class RpaEngineFactory {
    actual fun createComponent(ctx: ComponentContext, panelInfo: ai.rever.boss.components.registery.PanelInfo): RpaEngineComponent {
        return DesktopRpaEngineComponent(ctx, panelInfo)
    }
}

/**
 * Desktop RPA Engine Component
 */
class DesktopRpaEngineComponent(
    ctx: ComponentContext,
    panelInfo: ai.rever.boss.components.registery.PanelInfo
) : RpaEngineComponent(ctx, panelInfo) {
    
    private var currentExecutor: JxBrowserActionExecutor? = null
    
    override fun loadConfiguration(file: ConfigFileInfo) {
        try {
            val configFile = File(file.path)
            if (configFile.exists()) {
                val content = configFile.readText()
                val config = Json {
                    ignoreUnknownKeys = true
                }.decodeFromString<RpaConfiguration>(content)
                _selectedConfig.value = config
                _executionStatus.value = ExecutionStatus.IDLE
            }
        } catch (e: Exception) {
            println("Error loading configuration: ${e.message}")
            _executionStatus.value = ExecutionStatus.ERROR
        }
    }
    
    override fun loadAvailableConfigurations() {
        try {
            val userHome = System.getProperty("user.home")
            val rpaConfigDir = File(userHome, "Downloads/rpa_config")
            
            if (rpaConfigDir.exists() && rpaConfigDir.isDirectory) {
                val configFiles = rpaConfigDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".json") && file.name.startsWith("rpa_configuration")
                }?.sortedByDescending { it.lastModified() } ?: emptyList()
                
                _availableConfigs.value = configFiles.map { file ->
                    ConfigFileInfo(
                        name = file.name,
                        path = file.absolutePath,
                        lastModified = file.lastModified()
                    )
                }
            } else {
                _availableConfigs.value = emptyList()
            }
        } catch (e: Exception) {
            println("Error loading configurations: ${e.message}")
            _availableConfigs.value = emptyList()
        }
    }
    
    override suspend fun executeActions() {
        val config = _selectedConfig.value ?: return
        
        withContext(Dispatchers.Main) {
            try {
                // Wait a bit for the tab to be created and initialized
                delay(1000)
                
                // Get the most recently created browser (which should be our RPA tab)
                val browser = getRpaBrowser()
                if (browser == null) {
                    println("RPA Engine: No browser found for RPA execution")
                    _executionStatus.value = ExecutionStatus.ERROR
                    return@withContext
                }
                
                // Create action executor
                currentExecutor = JxBrowserActionExecutor(browser)
                
                // Execute each action
                val startIndex = if (_currentActionIndex.value >= 0) _currentActionIndex.value else 0
                
                for (index in startIndex until config.actions.size) {
                    // Check if execution was stopped or paused
                    if (_executionStatus.value != ExecutionStatus.EXECUTING) {
                        break
                    }
                    
                    _currentActionIndex.value = index
                    val action = config.actions[index]
                    
                    // Log current action
                    println("\n[${index + 1}/${config.actions.size}] Executing: ${action.name}")
                    println("  Type: ${action.type}")
                    println("  Selector: ${action.selector.type} - ${action.selector.value ?: "none"}")
                    println("  Value: ${action.value ?: "none"}")
                    
                    // Execute action with current settings
                    val result = currentExecutor!!.executeAction(
                        action = action,
                        humanLikeMode = _humanLikeMode.value,
                        speedMultiplier = _executionSpeed.value
                    ).copy(actionIndex = index)
                    
                    // Add result
                    _executionResults.value = _executionResults.value + result
                    
                    // Log result
                    if (result.success) {
                        println("  ✓ Success")
                    } else {
                        println("  ✗ Failed: ${result.error}")
                        _executionStatus.value = ExecutionStatus.ERROR
                        break
                    }
                    
                    // Small delay between actions
                    delay(100)
                }
                
                // Update final status
                if (_executionStatus.value == ExecutionStatus.EXECUTING) {
                    _executionStatus.value = ExecutionStatus.COMPLETED
                }
                
            } catch (e: Exception) {
                println("Execution error: ${e.message}")
                e.printStackTrace()
                _executionStatus.value = ExecutionStatus.ERROR
                
                // Add error result
                val errorResult = ActionExecutionResult(
                    actionIndex = _currentActionIndex.value,
                    actionName = "Execution Error",
                    success = false,
                    error = e.message
                )
                _executionResults.value = _executionResults.value + errorResult
            }
        }
    }
    
    /**
     * Inject anti-detection JavaScript to make the browser appear more human-like
     */
    private fun injectAntiDetectionScript(browser: Browser) {
        try {
            val mainFrame = browser.mainFrame().orElse(null)
            if (mainFrame != null) {
                mainFrame.executeJavaScript<Any>("""
                    // Override navigator.webdriver
                    Object.defineProperty(navigator, 'webdriver', {
                        get: () => undefined
                    });
                    
                    // Override navigator properties to appear more human
                    Object.defineProperty(navigator, 'maxTouchPoints', {
                        get: () => 0
                    });
                    
                    Object.defineProperty(navigator, 'vendor', {
                        get: () => 'Google Inc.'
                    });
                    
                    Object.defineProperty(navigator, 'appVersion', {
                        get: () => '5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
                    });
                    
                    // Override screen properties
                    Object.defineProperty(screen, 'availWidth', {
                        get: () => 1920
                    });
                    Object.defineProperty(screen, 'availHeight', {
                        get: () => 1080
                    });
                    Object.defineProperty(screen, 'width', {
                        get: () => 1920
                    });
                    Object.defineProperty(screen, 'height', {
                        get: () => 1080
                    });
                    
                    // Add languages
                    Object.defineProperty(navigator, 'languages', {
                        get: () => ['en-US', 'en']
                    });
                    
                    // Override plugins to look more realistic
                    Object.defineProperty(navigator, 'plugins', {
                        get: () => {
                            const pluginData = [
                                { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer' },
                                { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai' },
                                { name: 'Native Client', filename: 'internal-nacl-plugin' }
                            ];
                            const plugins = {};
                            pluginData.forEach((p, i) => {
                                plugins[i] = p;
                            });
                            plugins.length = pluginData.length;
                            return plugins;
                        }
                    });
                    
                    // Override permissions.query to always return 'granted' for notifications
                    const originalQuery = window.navigator.permissions.query;
                    window.navigator.permissions.query = function(parameters) {
                        if (parameters.name === 'notifications') {
                            return Promise.resolve({ state: 'granted' });
                        }
                        return originalQuery.apply(this, arguments);
                    };
                    
                    // Remove automation-related properties
                    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
                    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
                    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;
                    
                    // Make chrome object look more complete
                    if (window.chrome) {
                        window.chrome.runtime = {
                            connect: () => {},
                            sendMessage: () => {},
                            onMessage: { addListener: () => {} },
                            id: 'aapbdbdomjkkjkaonfhkkikfgjllcleb' // Google Translate extension ID
                        };
                        window.chrome.loadTimes = () => ({
                            requestTime: Date.now() / 1000,
                            startLoadTime: Date.now() / 1000,
                            commitLoadTime: Date.now() / 1000,
                            finishDocumentLoadTime: Date.now() / 1000,
                            finishLoadTime: Date.now() / 1000,
                            firstPaintTime: Date.now() / 1000,
                            firstPaintAfterLoadTime: 0,
                            navigationType: 'Other',
                            wasFetchedViaSpdy: false,
                            wasNpnNegotiated: true,
                            npnNegotiatedProtocol: 'h2',
                            wasAlternateProtocolAvailable: false,
                            connectionInfo: 'h2'
                        });
                    }
                    
                    // WebGL fingerprinting protection
                    const getParameter = WebGLRenderingContext.prototype.getParameter;
                    WebGLRenderingContext.prototype.getParameter = function(parameter) {
                        if (parameter === 37445) { // UNMASKED_VENDOR_WEBGL
                            return 'Intel Inc.';
                        }
                        if (parameter === 37446) { // UNMASKED_RENDERER_WEBGL
                            return 'Intel Iris OpenGL Engine';
                        }
                        return getParameter.apply(this, arguments);
                    };
                    
                    // Canvas fingerprinting protection
                    const toDataURL = HTMLCanvasElement.prototype.toDataURL;
                    HTMLCanvasElement.prototype.toDataURL = function() {
                        const context = this.getContext('2d');
                        if (context) {
                            // Add slight noise to make fingerprint unique
                            const imageData = context.getImageData(0, 0, this.width, this.height);
                            for (let i = 0; i < imageData.data.length; i += 4) {
                                imageData.data[i] = imageData.data[i] + (Math.random() * 2 - 1);
                                imageData.data[i] = Math.max(0, Math.min(255, imageData.data[i]));
                            }
                            context.putImageData(imageData, 0, 0);
                        }
                        return toDataURL.apply(this, arguments);
                    };
                    
                    // Override toString methods to hide modifications
                    const originalToString = Function.prototype.toString;
                    Function.prototype.toString = function() {
                        if (this === window.navigator.permissions.query) {
                            return 'function query() { [native code] }';
                        }
                        return originalToString.call(this);
                    };
                    
                    console.log('LLM RPA: Anti-detection measures applied');
                """)
            }
        } catch (e: Exception) {
            println("Failed to inject anti-detection script: ${e.message}")
        }
    }

    /**
     * Get the browser instance from the most recently created tab
     */
    private fun getRpaBrowser(): Browser? {
        return try {
            // Get the most recently created browser from FluckEngine
            // The tab creation in RpaEngine.startExecution creates a new browser
            val browsers = FluckEngine.engine.browsers()
            val browser = browsers.lastOrNull { !it.isClosed }
            
            if (browser != null) {
                // Add navigation listeners for logging
                browser.navigation().on(com.teamdev.jxbrowser.navigation.event.NavigationStarted::class.java) { event ->
                    println("RPA Engine: Navigating to ${event.url()}")
                }
                
                browser.navigation().on(LoadFinished::class.java) { event ->
                    println("RPA Engine: Page loaded successfully")
                    
                    // Disabled anti-detection as it may be causing detection
                    // injectAntiDetectionScript(browser)
                }
                
                // Disabled anti-detection as manual interaction works fine
                // if (browser.url() != "about:blank" && browser.url().isNotEmpty()) {
                //     injectAntiDetectionScript(browser)
                // }
                
                println("RPA Engine: Using browser instance from created tab")
            }
            
            browser
        } catch (e: Exception) {
            println("Error getting browser for RPA: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
}

/**
 * JxBrowser implementation of RPA Action Executor
 * Made internal so it can be used by both RPA Engine and LLM RPA
 */
internal class JxBrowserActionExecutor(
    private val browser: Browser
) : BaseActionExecutor() {
    
    init {
        // Disable anti-detection script as it may be causing detection
        // Manual interaction works fine, so the issue is with JavaScript execution
        
        // browser.navigation().on(LoadFinished::class.java) { event ->
        //     injectAntiDetectionScript()
        // }
    }
    
    private fun injectAntiDetectionScript() {
        try {
            val mainFrame = browser.mainFrame().orElse(null)
            if (mainFrame != null) {
                mainFrame.executeJavaScript<Any>("""
                    // Override navigator.webdriver
                    Object.defineProperty(navigator, 'webdriver', {
                        get: () => undefined
                    });
                    
                    // Override navigator properties to appear more human
                    Object.defineProperty(navigator, 'maxTouchPoints', {
                        get: () => 0
                    });
                    
                    Object.defineProperty(navigator, 'vendor', {
                        get: () => 'Google Inc.'
                    });
                    
                    Object.defineProperty(navigator, 'appVersion', {
                        get: () => '5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
                    });
                    
                    // Override screen properties
                    Object.defineProperty(screen, 'availWidth', {
                        get: () => 1920
                    });
                    Object.defineProperty(screen, 'availHeight', {
                        get: () => 1080
                    });
                    Object.defineProperty(screen, 'width', {
                        get: () => 1920
                    });
                    Object.defineProperty(screen, 'height', {
                        get: () => 1080
                    });
                    
                    // Add languages
                    Object.defineProperty(navigator, 'languages', {
                        get: () => ['en-US', 'en']
                    });
                    
                    // Override plugins to look more realistic
                    Object.defineProperty(navigator, 'plugins', {
                        get: () => {
                            const pluginData = [
                                { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer' },
                                { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai' },
                                { name: 'Native Client', filename: 'internal-nacl-plugin' }
                            ];
                            const plugins = {};
                            pluginData.forEach((p, i) => {
                                plugins[i] = p;
                            });
                            plugins.length = pluginData.length;
                            return plugins;
                        }
                    });
                    
                    // Override permissions.query to always return 'granted' for notifications
                    const originalQuery = window.navigator.permissions.query;
                    window.navigator.permissions.query = function(parameters) {
                        if (parameters.name === 'notifications') {
                            return Promise.resolve({ state: 'granted' });
                        }
                        return originalQuery.apply(this, arguments);
                    };
                    
                    // Remove automation-related properties
                    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
                    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
                    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;
                    
                    // Make chrome object look more complete
                    if (window.chrome) {
                        window.chrome.runtime = {
                            connect: () => {},
                            sendMessage: () => {},
                            onMessage: { addListener: () => {} },
                            id: 'aapbdbdomjkkjkaonfhkkikfgjllcleb' // Google Translate extension ID
                        };
                        window.chrome.loadTimes = () => ({
                            requestTime: Date.now() / 1000,
                            startLoadTime: Date.now() / 1000,
                            commitLoadTime: Date.now() / 1000,
                            finishDocumentLoadTime: Date.now() / 1000,
                            finishLoadTime: Date.now() / 1000,
                            firstPaintTime: Date.now() / 1000,
                            firstPaintAfterLoadTime: 0,
                            navigationType: 'Other',
                            wasFetchedViaSpdy: false,
                            wasNpnNegotiated: true,
                            npnNegotiatedProtocol: 'h2',
                            wasAlternateProtocolAvailable: false,
                            connectionInfo: 'h2'
                        });
                    }
                    
                    // WebGL fingerprinting protection
                    const getParameter = WebGLRenderingContext.prototype.getParameter;
                    WebGLRenderingContext.prototype.getParameter = function(parameter) {
                        if (parameter === 37445) { // UNMASKED_VENDOR_WEBGL
                            return 'Intel Inc.';
                        }
                        if (parameter === 37446) { // UNMASKED_RENDERER_WEBGL
                            return 'Intel Iris OpenGL Engine';
                        }
                        return getParameter.apply(this, arguments);
                    };
                    
                    // Canvas fingerprinting protection
                    const toDataURL = HTMLCanvasElement.prototype.toDataURL;
                    HTMLCanvasElement.prototype.toDataURL = function() {
                        const context = this.getContext('2d');
                        if (context) {
                            // Add slight noise to make fingerprint unique
                            const imageData = context.getImageData(0, 0, this.width, this.height);
                            for (let i = 0; i < imageData.data.length; i += 4) {
                                imageData.data[i] = imageData.data[i] + (Math.random() * 2 - 1);
                                imageData.data[i] = Math.max(0, Math.min(255, imageData.data[i]));
                            }
                            context.putImageData(imageData, 0, 0);
                        }
                        return toDataURL.apply(this, arguments);
                    };
                    
                    // Override toString methods to hide modifications
                    const originalToString = Function.prototype.toString;
                    Function.prototype.toString = function() {
                        if (this === window.navigator.permissions.query) {
                            return 'function query() { [native code] }';
                        }
                        return originalToString.call(this);
                    };
                    
                    console.log('JxBrowserActionExecutor: Anti-detection measures applied');
                """)
            }
        } catch (e: Exception) {
            println("JxBrowserActionExecutor: Failed to inject anti-detection script: ${e.message}")
        }
    }
    
    /**
     * Wait for page to finish loading
     */
    private suspend fun waitForPageLoad(timeoutMs: Long = 30000) {
        val loaded = CompletableDeferred<Boolean>()
        val subscription = browser.navigation().on(LoadFinished::class.java) {
            loaded.complete(true)
        }
        
        try {
            withTimeout(timeoutMs) {
                loaded.await()
            }
        } finally {
            subscription.unsubscribe()
        }
    }
    
    override suspend fun navigate(url: String) {
        withContext(Dispatchers.Main) {
            browser.navigation().loadUrl(url)
            waitForPageLoad()
        }
    }
    
    override suspend fun click(selector: SelectorInfo) {
        val script = """
            (function() {
                var element = ${buildSelectorScript(selector)};
                if (element) {
                    // Dispatch mouse events for better compatibility
                    var rect = element.getBoundingClientRect();
                    var x = rect.left + rect.width / 2;
                    var y = rect.top + rect.height / 2;
                    
                    var mousedown = new MouseEvent('mousedown', {
                        view: window,
                        bubbles: true,
                        cancelable: true,
                        clientX: x,
                        clientY: y
                    });
                    
                    var mouseup = new MouseEvent('mouseup', {
                        view: window,
                        bubbles: true,
                        cancelable: true,
                        clientX: x,
                        clientY: y
                    });
                    
                    var click = new MouseEvent('click', {
                        view: window,
                        bubbles: true,
                        cancelable: true,
                        clientX: x,
                        clientY: y
                    });
                    
                    element.dispatchEvent(mousedown);
                    element.dispatchEvent(mouseup);
                    element.dispatchEvent(click);
                    
                    // Also try direct click as fallback
                    element.click();
                    return true;
                }
                return false;
            })()
        """.trimIndent()
        
        val result = executeJavaScript(script) as? Boolean ?: false
        if (!result) {
            throw Exception("Element not found: ${selector.value}")
        }
    }
    
    override suspend fun input(selector: SelectorInfo, value: String) {
        val script = """
            (function() {
                var element = ${buildSelectorScript(selector)};
                if (element) {
                    element.focus();
                    element.value = '${value.replace("'", "\\'")}';
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                    return true;
                }
                return false;
            })()
        """.trimIndent()
        
        val result = executeJavaScript(script) as? Boolean ?: false
        if (!result) {
            throw Exception("Element not found: ${selector.value}")
        }
    }
    
    override suspend fun select(selector: SelectorInfo, value: String) {
        val script = """
            (function() {
                var element = ${buildSelectorScript(selector)};
                if (element && element.tagName === 'SELECT') {
                    // Try to select by visible text
                    for (var i = 0; i < element.options.length; i++) {
                        if (element.options[i].text === '${value.replace("'", "\\'")}') {
                            element.selectedIndex = i;
                            element.dispatchEvent(new Event('change', { bubbles: true }));
                            return true;
                        }
                    }
                    // Try to select by value
                    element.value = '${value.replace("'", "\\'")}';
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                    return true;
                }
                return false;
            })()
        """.trimIndent()
        
        val result = executeJavaScript(script) as? Boolean ?: false
        if (!result) {
            throw Exception("Select element not found: ${selector.value}")
        }
    }
    
    override suspend fun wait(milliseconds: Long) {
        delay(milliseconds)
    }
    
    override suspend fun scroll(x: Int, y: Int) {
        val script = "window.scrollTo($x, $y);"
        executeJavaScript(script)
        delay(300) // Wait for scroll to complete
    }
    
    override suspend fun switchToFrame(selector: SelectorInfo) {
        // JxBrowser handles frames differently
        // For now, we'll work within the main frame
        // This could be enhanced to support frame switching
        val script = """
            (function() {
                var frame = ${buildSelectorScript(selector)};
                if (frame && frame.tagName === 'IFRAME') {
                    // Store frame reference for future operations
                    window.__currentFrame = frame;
                    return true;
                }
                return false;
            })()
        """.trimIndent()
        
        val result = executeJavaScript(script) as? Boolean ?: false
        if (!result) {
            throw Exception("Frame not found: ${selector.value}")
        }
    }
    
    override suspend fun switchToDefaultContent() {
        val script = "window.__currentFrame = null;"
        executeJavaScript(script)
    }
    
    override suspend fun refresh() {
        withContext(Dispatchers.Main) {
            browser.navigation().reload()
            waitForPageLoad()
        }
    }
    
    override suspend fun executeJavaScript(script: String): Any? {
        return withContext(Dispatchers.Main) {
            try {
                browser.mainFrame().orElse(null)?.executeJavaScript<Any?>(script)
            } catch (e: Exception) {
                println("JavaScript execution error: ${e.message}")
                null
            }
        }
    }
}

