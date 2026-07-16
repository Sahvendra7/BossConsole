package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.plugin.browser.LockedBrowser
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import com.teamdev.jxbrowser.navigation.event.LoadFinished
import kotlinx.coroutines.*

private val logger = BossLogger.forComponent("DesktopRpaEngine")

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
