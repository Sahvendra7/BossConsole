package ai.rever.boss.components.plugin.panels.right_top

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Interface for executing RPA actions on a browser
 */
interface RpaActionExecutor {
    suspend fun navigate(url: String)
    suspend fun click(selector: SelectorInfo)
    suspend fun input(selector: SelectorInfo, value: String)
    suspend fun select(selector: SelectorInfo, value: String)
    suspend fun wait(milliseconds: Long)
    suspend fun scroll(x: Int, y: Int)
    suspend fun switchToFrame(selector: SelectorInfo)
    suspend fun switchToDefaultContent()
    suspend fun refresh()
    suspend fun executeJavaScript(script: String): Any?
    suspend fun waitForElement(selector: SelectorInfo, timeout: Long = 5000): Boolean
    suspend fun isElementVisible(selector: SelectorInfo): Boolean
    suspend fun getElementText(selector: SelectorInfo): String?
    suspend fun scrollIntoView(selector: SelectorInfo)
    
    // Human-like behavior
    suspend fun simulateHumanDelay(minMs: Int = 500, maxMs: Int = 1500)
    suspend fun simulateMouseMovement(toX: Int, toY: Int)
    suspend fun simulateTyping(selector: SelectorInfo, text: String, minDelay: Int = 50, maxDelay: Int = 150)
}

/**
 * Base action executor with common functionality
 */
abstract class BaseActionExecutor : RpaActionExecutor {
    
    /**
     * Find element using different selector strategies
     */
    fun buildSelectorScript(selector: SelectorInfo): String {
        return when (selector.type) {
            "id" -> "document.getElementById('${selector.value}')"
            "css" -> "document.querySelector('${selector.value}')"
            "xpath" -> {
                """
                (function() {
                    var result = document.evaluate('${selector.value}', document, null, 
                        XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                    return result.singleNodeValue;
                })()
                """.trimIndent()
            }
            "text" -> {
                """
                (function() {
                    var elements = document.querySelectorAll('*');
                    for (var i = 0; i < elements.length; i++) {
                        if (elements[i].textContent.trim() === '${selector.value}') {
                            return elements[i];
                        }
                    }
                    return null;
                })()
                """.trimIndent()
            }
            else -> "null"
        }
    }
    
    /**
     * Wait for element with timeout
     */
    override suspend fun waitForElement(selector: SelectorInfo, timeout: Long): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeout) {
            val exists = isElementPresent(selector)
            if (exists) return true
            delay(100)
        }
        
        return false
    }
    
    /**
     * Check if element is present in DOM
     */
    protected suspend fun isElementPresent(selector: SelectorInfo): Boolean {
        val script = "${buildSelectorScript(selector)} != null"
        return executeJavaScript(script) as? Boolean ?: false
    }
    
    /**
     * Check if element is visible
     */
    override suspend fun isElementVisible(selector: SelectorInfo): Boolean {
        val script = """
            (function() {
                var element = ${buildSelectorScript(selector)};
                if (!element) return false;
                var rect = element.getBoundingClientRect();
                return rect.width > 0 && rect.height > 0 && 
                       window.getComputedStyle(element).display !== 'none' &&
                       window.getComputedStyle(element).visibility !== 'hidden';
            })()
        """.trimIndent()
        
        return executeJavaScript(script) as? Boolean ?: false
    }
    
    /**
     * Get element text content
     */
    override suspend fun getElementText(selector: SelectorInfo): String? {
        val script = """
            (function() {
                var element = ${buildSelectorScript(selector)};
                return element ? element.textContent : null;
            })()
        """.trimIndent()
        
        return executeJavaScript(script) as? String
    }
    
    /**
     * Scroll element into view
     */
    override suspend fun scrollIntoView(selector: SelectorInfo) {
        val script = """
            (function() {
                var element = ${buildSelectorScript(selector)};
                if (element) {
                    element.scrollIntoView({
                        behavior: 'smooth',
                        block: 'center',
                        inline: 'center'
                    });
                }
            })()
        """.trimIndent()
        
        executeJavaScript(script)
        delay(500) // Wait for scroll animation
    }
    
    /**
     * Simulate human-like delay
     */
    override suspend fun simulateHumanDelay(minMs: Int, maxMs: Int) {
        val delay = Random.nextInt(minMs, maxMs)
        delay(delay.toLong())
    }
    
    /**
     * Simulate mouse movement to coordinates
     */
    override suspend fun simulateMouseMovement(toX: Int, toY: Int) {
        // Simulate smooth mouse movement
        val steps = Random.nextInt(3, 8)
        val currentX = Random.nextInt(100, 800)
        val currentY = Random.nextInt(100, 600)
        
        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            val x = currentX + ((toX - currentX) * progress).toInt()
            val y = currentY + ((toY - currentY) * progress).toInt()
            
            val script = """
                (function() {
                    var event = new MouseEvent('mousemove', {
                        clientX: $x,
                        clientY: $y,
                        bubbles: true
                    });
                    document.dispatchEvent(event);
                })()
            """.trimIndent()
            
            executeJavaScript(script)
            delay(Random.nextLong(10, 30))
        }
    }
    
    /**
     * Simulate human-like typing
     */
    override suspend fun simulateTyping(selector: SelectorInfo, text: String, minDelay: Int, maxDelay: Int) {
        // First clear the field
        val clearScript = """
            (function() {
                var element = ${buildSelectorScript(selector)};
                if (element) {
                    element.value = '';
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                }
            })()
        """.trimIndent()
        executeJavaScript(clearScript)
        
        // Type character by character
        text.forEach { char ->
            val typeScript = """
                (function() {
                    var element = ${buildSelectorScript(selector)};
                    if (element) {
                        element.value += '$char';
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new KeyboardEvent('keypress', { 
                            key: '$char',
                            bubbles: true 
                        }));
                    }
                })()
            """.trimIndent()
            
            executeJavaScript(typeScript)
            delay(Random.nextLong(minDelay.toLong(), maxDelay.toLong()))
        }
        
        // Trigger change event at the end
        val changeScript = """
            (function() {
                var element = ${buildSelectorScript(selector)};
                if (element) {
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                }
            })()
        """.trimIndent()
        executeJavaScript(changeScript)
    }
}

/**
 * Execute a single RPA action
 */
suspend fun RpaActionExecutor.executeAction(
    action: RpaActionConfig,
    humanLikeMode: Boolean = true,
    speedMultiplier: Float = 1.0f
): ActionExecutionResult {
    val startTime = System.currentTimeMillis()
    
    return try {
        // Pre-action human behavior
        if (humanLikeMode) {
            simulateHumanDelay(
                minMs = (300 / speedMultiplier).toInt(),
                maxMs = (800 / speedMultiplier).toInt()
            )
        }
        
        when (action.type) {
            "navigate" -> {
                if (action.value != null) {
                    navigate(action.value)
                    wait((2000 / speedMultiplier).toLong())
                }
            }
            
            "click" -> {
                waitForElement(action.selector)
                scrollIntoView(action.selector)
                
                if (humanLikeMode) {
                    // Simulate mouse movement to element
                    val rect = getElementBounds(action.selector)
                    if (rect != null) {
                        simulateMouseMovement(rect.centerX, rect.centerY)
                    }
                }
                
                click(action.selector)
            }
            
            "input" -> {
                waitForElement(action.selector)
                scrollIntoView(action.selector)
                
                if (humanLikeMode && action.value != null) {
                    simulateTyping(action.selector, action.value)
                } else if (action.value != null) {
                    input(action.selector, action.value)
                }
            }
            
            "select" -> {
                waitForElement(action.selector)
                scrollIntoView(action.selector)
                if (action.value != null) {
                    select(action.selector, action.value)
                }
            }
            
            "wait" -> {
                val waitTime = action.value?.toLongOrNull() ?: 1000
                wait((waitTime / speedMultiplier).toLong())
            }
            
            "scroll" -> {
                val coords = action.value?.split(",")?.map { it.trim().toIntOrNull() ?: 0 } ?: listOf(0, 0)
                scroll(coords.getOrElse(0) { 0 }, coords.getOrElse(1) { 0 })
            }
            
            "switch_frame" -> {
                switchToFrame(action.selector)
            }
            
            "switch_to_default" -> {
                switchToDefaultContent()
            }
            
            "refresh" -> {
                refresh()
                wait((2000 / speedMultiplier).toLong())
            }
            
            else -> {
                throw UnsupportedOperationException("Unknown action type: ${action.type}")
            }
        }
        
        // Post-action delay
        if (humanLikeMode) {
            simulateHumanDelay(
                minMs = (200 / speedMultiplier).toInt(),
                maxMs = (500 / speedMultiplier).toInt()
            )
        }
        
        ActionExecutionResult(
            actionIndex = -1, // Will be set by caller
            actionName = action.name,
            success = true,
            timestamp = startTime
        )
        
    } catch (e: Exception) {
        ActionExecutionResult(
            actionIndex = -1, // Will be set by caller
            actionName = action.name,
            success = false,
            error = e.message,
            timestamp = startTime
        )
    }
}

/**
 * Get element bounds for mouse movement
 */
private suspend fun RpaActionExecutor.getElementBounds(selector: SelectorInfo): ElementBounds? {
    val script = """
        (function() {
            var element = ${(this as? BaseActionExecutor)?.buildSelectorScript(selector) ?: "null"};
            if (!element) return null;
            var rect = element.getBoundingClientRect();
            return {
                x: rect.left,
                y: rect.top,
                width: rect.width,
                height: rect.height,
                centerX: rect.left + rect.width / 2,
                centerY: rect.top + rect.height / 2
            };
        })()
    """.trimIndent()
    
    val result = executeJavaScript(script) as? Map<*, *> ?: return null
    
    return ElementBounds(
        x = (result["x"] as? Number)?.toInt() ?: 0,
        y = (result["y"] as? Number)?.toInt() ?: 0,
        width = (result["width"] as? Number)?.toInt() ?: 0,
        height = (result["height"] as? Number)?.toInt() ?: 0,
        centerX = (result["centerX"] as? Number)?.toInt() ?: 0,
        centerY = (result["centerY"] as? Number)?.toInt() ?: 0
    )
}

/**
 * Element bounds data class
 */
private data class ElementBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val centerX: Int,
    val centerY: Int
)