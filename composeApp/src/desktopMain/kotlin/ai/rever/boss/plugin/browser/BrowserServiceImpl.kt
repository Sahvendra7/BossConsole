package ai.rever.boss.plugin.browser

import ai.rever.boss.components.plugin.tab_types.fluck.FluckEngine
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import com.teamdev.jxbrowser.browser.Browser
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop implementation of [BrowserService] that wraps [FluckEngine].
 *
 * This service provides plugins with access to JxBrowser functionality
 * without exposing JxBrowser types directly.
 *
 * This is a singleton service shared across all plugins.
 */
object BrowserServiceImpl : BrowserService {

    private val logger = BossLogger.forComponent("BrowserServiceImpl")

    // Track active browser handles for resource management
    private val activeBrowsers = ConcurrentHashMap<String, BrowserHandleImpl>()

    override fun isAvailable(): Boolean {
        return try {
            // Check if engine can be accessed without errors
            // Accessing FluckEngine.engine triggers initialization if needed
            FluckEngine.initError == null && !FluckEngine.engine.isClosed
        } catch (e: Exception) {
            logger.debug(LogCategory.BROWSER, "BrowserService not available", mapOf(
                "error" to (e.message ?: "unknown")
            ))
            false
        }
    }

    override suspend fun createBrowser(config: BrowserConfig): BrowserHandle? {
        return try {
            // Get engine (may initialize on first access)
            val engine = FluckEngine.engine

            // Create new browser instance
            val browser: Browser = engine.newBrowser()

            // Enable swipe navigation for touchscreen devices
            browser.settings().enableOverscrollHistoryNavigation()

            // Get current engine generation for staleness detection
            val generation = FluckEngine.currentEngineGeneration

            // Create handle wrapper
            val handle = BrowserHandleImpl(browser, config, generation)

            // Track active browser
            activeBrowsers[handle.id] = handle

            logger.info(LogCategory.BROWSER, "Browser created via BrowserService", mapOf(
                "handleId" to handle.id,
                "url" to config.url,
                "activeBrowsers" to activeBrowsers.size
            ))

            handle
        } catch (e: Exception) {
            logger.error(LogCategory.BROWSER, "Failed to create browser", error = e)
            null
        }
    }

    override suspend fun disposeBrowser(handle: BrowserHandle) {
        // Remove from tracking
        activeBrowsers.remove(handle.id)

        // Dispose the handle
        handle.dispose()

        logger.debug(LogCategory.BROWSER, "Browser disposed via BrowserService", mapOf(
            "handleId" to handle.id,
            "remainingBrowsers" to activeBrowsers.size
        ))
    }

    override fun getActiveBrowserCount(): Int {
        return activeBrowsers.size
    }

    /**
     * Dispose all active browsers.
     *
     * Called during application shutdown to ensure clean cleanup.
     */
    fun disposeAll() {
        val count = activeBrowsers.size
        activeBrowsers.values.toList().forEach { handle ->
            try {
                handle.dispose()
            } catch (e: Exception) {
                logger.warn(LogCategory.BROWSER, "Error disposing browser", error = e)
            }
        }
        activeBrowsers.clear()

        logger.info(LogCategory.BROWSER, "All browsers disposed", mapOf("count" to count))
    }
}
