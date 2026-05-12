package ai.rever.boss.plugin.browser

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import com.teamdev.jxbrowser.browser.Browser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

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

    // POST bodies captured from popup handoffs, awaiting consumption by the next
    // createBrowser call for the same URL. A FIFO queue per URL so that two
    // popups to the same destination (e.g. clicking Print twice in OncoEMR
    // before the first new tab finishes loading) don't clobber each other.
    // See [stashPopupPost].
    private val pendingPopupPosts = ConcurrentHashMap<String, ConcurrentLinkedDeque<PendingPopupPost>>()
    private const val PENDING_POPUP_TTL_MS = 10_000L

    private data class PendingPopupPost(
        val postData: ByteArray,
        val contentType: String,
        val createdAtMs: Long
    )

    override fun stashPopupPost(url: String, postData: ByteArray, contentType: String) {
        val now = System.currentTimeMillis()
        // compute (not computeIfAbsent + addLast) so the append is atomic with
        // the queue's membership in the map. Otherwise a concurrent consume or
        // GC that just observed an empty queue could remove the entry between
        // computeIfAbsent returning and addLast appending — orphaning the body
        // and reintroducing the regression this fix exists for.
        pendingPopupPosts.compute(url) { _, existing ->
            (existing ?: ConcurrentLinkedDeque()).also {
                it.addLast(PendingPopupPost(postData, contentType, now))
            }
        }
        // Opportunistic GC. Stash is infrequent (one call per popup), so the
        // map walk here is cheap relative to the cost of a background sweeper.
        gcStalePopupPosts(now)
    }

    private fun consumePopupPost(url: String): PendingPopupPost? {
        val now = System.currentTimeMillis()
        var entry: PendingPopupPost? = null
        // The whole drain-and-poll runs inside compute so a racing stash for
        // the same URL serializes on this key — preventing TOCTOU where we'd
        // remove a queue from the map after another thread had just appended.
        pendingPopupPosts.compute(url) { _, q ->
            if (q == null) return@compute null
            dropStaleHeads(q, now)
            entry = q.pollFirst()
            if (q.isEmpty()) null else q
        }
        return entry
    }

    private fun gcStalePopupPosts(now: Long) {
        for (key in pendingPopupPosts.keys) {
            pendingPopupPosts.compute(key) { _, q ->
                if (q == null) return@compute null
                dropStaleHeads(q, now)
                if (q.isEmpty()) null else q
            }
        }
    }

    private fun dropStaleHeads(queue: ConcurrentLinkedDeque<PendingPopupPost>, now: Long) {
        while (true) {
            val head = queue.peekFirst() ?: return
            if (now - head.createdAtMs <= PENDING_POPUP_TTL_MS) return
            queue.pollFirst()
        }
    }

    override fun isAvailable(): Boolean {
        return try {
            val initErr = FluckEngine.initError
            val engineClosed = FluckEngine.engine.isClosed
            val available = initErr == null && !engineClosed
            if (!available) {
                logger.warn(LogCategory.BROWSER, "BrowserService not available", mapOf(
                    "initError" to (initErr?.toString() ?: "none"),
                    "engineClosed" to engineClosed.toString()
                ))
            }
            available
        } catch (e: Exception) {
            logger.warn(LogCategory.BROWSER, "BrowserService not available - exception", mapOf(
                "error" to (e.message ?: "unknown"),
                "errorType" to e.javaClass.simpleName
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

            // If a popup handed off a POST body for this URL (form-submit target="_blank"),
            // consume it and replay on first navigation. Explicit config wins if set.
            val effectiveConfig = if (config.initialPostData == null && config.url.isNotBlank()) {
                consumePopupPost(config.url)?.let { entry ->
                    config.copy(
                        initialPostData = entry.postData,
                        initialPostContentType = entry.contentType
                    )
                } ?: config
            } else {
                config
            }

            // Create handle wrapper
            val handle = BrowserHandleImpl(browser, effectiveConfig, generation)

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

    /** Return all active browser handles for internal lookup (e.g. RPA recorder). */
    internal fun getActiveHandles(): List<BrowserHandleImpl> = activeBrowsers.values.toList()

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
