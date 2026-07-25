package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.browser.BrowserConfig
import ai.rever.boss.plugin.browser.BrowserHandle
import ai.rever.boss.plugin.browser.BrowserService
import ai.rever.boss.plugin.browser.BrowserServiceImpl
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

private val windowBrowserServices = ConcurrentHashMap<String, WindowScopedBrowserService>()
private const val BROWSER_DISPOSAL_TIMEOUT_MS = 2_000L

/**
 * Desktop implementation of BrowserService provider.
 *
 * Returns a window-scoped view of the BrowserServiceImpl singleton that wraps
 * FluckEngine. The wrapper keeps plugin browser ownership isolated per window.
 * A null or blank window ID has no valid cleanup owner, so no service is exposed.
 */
actual fun getBrowserServiceInstance(windowId: String?): BrowserService? =
    windowId
        ?.takeIf(String::isNotBlank)
        ?.let { windowBrowserServices.computeIfAbsent(it, ::WindowScopedBrowserService) }

private class WindowScopedBrowserService(
    private val windowId: String,
) : BrowserService by BrowserServiceImpl {
    private val logger = BossLogger.forComponent("WindowScopedBrowserService")
    private val lifecycleLock = Any()
    private val disposalStarted = AtomicBoolean(false)
    private var closed = false

    override suspend fun createBrowser(config: BrowserConfig): BrowserHandle? {
        val creationStarted =
            synchronized(lifecycleLock) {
                if (closed) {
                    false
                } else {
                    BrowserServiceImpl.tryBeginBrowserCreation(windowId)
                }
            }
        if (!creationStarted) return null

        return try {
            BrowserServiceImpl.createBrowserForWindow(windowId, config)
        } finally {
            BrowserServiceImpl.finishBrowserCreation(windowId)
        }
    }

    /**
     * Reports this window's owned handles, not the process-wide active count.
     */
    override fun getActiveBrowserCount(): Int = BrowserServiceImpl.getActiveBrowserCountForWindow(windowId)

    fun close() {
        val shouldDispose =
            synchronized(lifecycleLock) {
                if (closed) {
                    false
                } else {
                    closed = true
                    true
                }
            }
        if (!shouldDispose) return

        if (SwingUtilities.isEventDispatchThread()) {
            disposeOwnedBrowsersOnce()
            return
        }

        logger.debug(
            LogCategory.BROWSER,
            "Browser service close called off-EDT; marshalling before disposal",
            mapOf("windowId" to windowId),
        )
        val task =
            FutureTask<Unit> {
                disposeOwnedBrowsersOnce()
            }
        SwingUtilities.invokeLater(task)
        try {
            task.get(BROWSER_DISPOSAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            disposeAfterMarshalFailure(task, "Interrupted while closing browser service", e)
        } catch (e: TimeoutException) {
            disposeAfterMarshalFailure(task, "Timed out closing browser service on the EDT", e)
        } catch (e: ExecutionException) {
            disposeAfterMarshalFailure(task, "Could not close browser service on the EDT", e.cause ?: e)
        }
    }

    private fun disposeAfterMarshalFailure(
        task: FutureTask<Unit>,
        message: String,
        error: Throwable,
    ) {
        task.cancel(false)
        logger.warn(LogCategory.BROWSER, message, mapOf("windowId" to windowId), error)
        runCatching(::disposeOwnedBrowsersOnce)
            .onFailure { fallbackError ->
                logger.error(
                    LogCategory.BROWSER,
                    "Browser service fallback disposal failed",
                    mapOf("windowId" to windowId),
                    fallbackError,
                )
            }
    }

    private fun disposeOwnedBrowsersOnce() {
        if (!disposalStarted.compareAndSet(false, true)) return
        var completed = false
        try {
            BrowserServiceImpl.disposeAllForWindow(windowId)
            completed = true
        } finally {
            if (!completed) {
                disposalStarted.set(false)
            }
        }
    }
}

internal actual fun disposePluginBrowsers(windowId: String) {
    windowBrowserServices.remove(windowId)?.close()
        ?: BrowserServiceImpl.disposeAllForWindow(windowId)
}
