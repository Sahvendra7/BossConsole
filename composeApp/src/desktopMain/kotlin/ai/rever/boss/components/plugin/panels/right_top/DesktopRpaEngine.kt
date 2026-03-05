package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.plugin.browser.FluckEngine
import ai.rever.boss.plugin.browser.LockedBrowser
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import com.arkivanov.decompose.ComponentContext
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.navigation.event.LoadFinished
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock

private val logger = BossLogger.forComponent("DesktopRpaEngine")

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

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }
    }

    private var currentExecutor: JxBrowserActionExecutor? = null

    override fun loadConfiguration(file: ConfigFileInfo) {
        try {
            val configFile = File(file.path)
            if (configFile.exists()) {
                val content = configFile.readText()
                val config = json.decodeFromString<RpaConfiguration>(content)
                _selectedConfig.value = config
                _executionStatus.value = ExecutionStatus.IDLE
            }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Error loading configuration", error = e)
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
            logger.error(LogCategory.SYSTEM, "Error loading configurations", error = e)
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
                    logger.error(LogCategory.SYSTEM, "RPA Engine: No browser found for RPA execution")
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
                    logger.debug(LogCategory.SYSTEM, "Executing action", mapOf(
                        "step" to "${index + 1}/${config.actions.size}",
                        "name" to action.name,
                        "type" to action.type.toString(),
                        "selectorType" to action.selector.type.toString(),
                        "selectorValue" to (action.selector.value ?: "none"),
                        "value" to (action.value ?: "none")
                    ))
                    
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
                        logger.debug(LogCategory.SYSTEM, "Action succeeded", mapOf("actionName" to action.name))
                    } else {
                        logger.error(LogCategory.SYSTEM, "Action failed", mapOf("actionName" to action.name, "error" to (result.error ?: "unknown")))
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
                logger.error(LogCategory.SYSTEM, "Execution error", error = e)
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
     * Get the browser instance from the most recently created tab
     */
    private fun getRpaBrowser(): LockedBrowser? {
        return try {
            // Get the most recently created browser from FluckEngine
            // The tab creation in RpaEngine.startExecution creates a new browser
            val browsers = FluckEngine.engine.browsers()
            val browser = browsers.lastOrNull { !it.isClosed }
            
            if (browser != null) {
                // Create a new lock for RPA-created browsers
                val lock = ReentrantReadWriteLock()
                
                // Add navigation listeners for logging
                browser.navigation().on(com.teamdev.jxbrowser.navigation.event.NavigationStarted::class.java) { event ->
                    logger.debug(LogCategory.BROWSER, "RPA Engine: Navigating", mapOf("url" to event.url()))
                }

                browser.navigation().on(LoadFinished::class.java) { event ->
                    logger.debug(LogCategory.BROWSER, "RPA Engine: Page loaded successfully")
                    
                    // Disabled anti-detection as it may be causing detection
                    // injectAntiDetectionScript(browser)
                }
                
                // Disabled anti-detection as manual interaction works fine
                // if (browser.url() != "about:blank" && browser.url().isNotEmpty()) {
                //     injectAntiDetectionScript(browser)
                // }

                logger.debug(LogCategory.BROWSER, "RPA Engine: Using browser instance from created tab")
                
                // Wrap with LockedBrowser for thread-safety
                LockedBrowser(browser, lock)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(LogCategory.BROWSER, "Error getting browser for RPA", error = e)
            null
        }
    }
    
}

/**
 * JxBrowser implementation of RPA Action Executor with thread-safe LockedBrowser
 * Made internal so it can be used by both RPA Engine and LLM RPA
 */
internal class JxBrowserActionExecutor(
    private val browser: LockedBrowser
) : BaseActionExecutor() {
    
    init {
        // Disable anti-detection script as it may be causing detection
        // Manual interaction works fine, so the issue is with JavaScript execution
        
        // browser.navigation().on(LoadFinished::class.java) { event ->
        //     injectAntiDetectionScript()
        // }
    }

    /**
     * Wait for page to finish loading
     */
    private suspend fun waitForPageLoad(timeoutMs: Long = 30000) {
        val loaded = CompletableDeferred<Boolean>()
        val subscription = browser.unsafe().navigation().on(LoadFinished::class.java) {
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
                logger.warn(LogCategory.BROWSER, "JavaScript execution error", mapOf("error" to (e.message ?: "unknown")))
                null
            }
        }
    }
}

