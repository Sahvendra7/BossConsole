package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.browser.BrowserConfig
import ai.rever.boss.plugin.browser.BrowserHandle
import ai.rever.boss.plugin.browser.BrowserService
import ai.rever.boss.plugin.browser.BrowserServiceImpl
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities

private val windowBrowserServices = ConcurrentHashMap<String, WindowScopedBrowserService>()

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
        if (!SwingUtilities.isEventDispatchThread()) {
            logger.warn(
                LogCategory.BROWSER,
                "Browser service close called off-EDT; marshalling before disposal",
                mapOf("windowId" to windowId),
            )
            try {
                SwingUtilities.invokeAndWait { close() }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.warn(LogCategory.BROWSER, "Interrupted while closing browser service", error = e)
            } catch (e: InvocationTargetException) {
                logger.warn(LogCategory.BROWSER, "Could not close browser service on the EDT", error = e.cause ?: e)
            }
            return
        }

        synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            BrowserServiceImpl.disposeAllForWindow(windowId)
        }
    }
}

internal actual fun disposePluginBrowsers(windowId: String) {
    windowBrowserServices.remove(windowId)?.close()
        ?: BrowserServiceImpl.disposeAllForWindow(windowId)
}
