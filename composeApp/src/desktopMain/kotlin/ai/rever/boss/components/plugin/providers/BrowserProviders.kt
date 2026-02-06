package ai.rever.boss.components.plugin.providers

import ai.rever.boss.components.plugin.tab_types.fluck.BrowserZoomSettingsManager
import ai.rever.boss.components.plugin.tab_types.fluck.UrlHistoryManager
import ai.rever.boss.plugin.api.UrlHistoryEntry
import ai.rever.boss.plugin.api.UrlHistoryProvider
import ai.rever.boss.plugin.api.ZoomSettingsProvider
import kotlin.math.abs

/**
 * Desktop implementation of ZoomSettingsProvider that delegates to BrowserZoomSettingsManager.
 */
private class DesktopZoomSettingsProvider : ZoomSettingsProvider {
    override fun getZoomForDomain(domain: String): Double? {
        val zoom = BrowserZoomSettingsManager.getZoomForDomain(domain)
        // Return null if it's the default zoom level (1.0)
        return if (abs(zoom - 1.0) < 0.001) null else zoom
    }

    override fun setZoomForDomain(domain: String, zoomLevel: Double) {
        BrowserZoomSettingsManager.setZoomForDomain(domain, zoomLevel)
    }

    override fun extractDomain(url: String): String? {
        return BrowserZoomSettingsManager.extractDomain(url)
    }

    override fun clearZoomForDomain(domain: String) {
        BrowserZoomSettingsManager.clearDomainZoom(domain)
    }

    override suspend fun saveSettings() {
        BrowserZoomSettingsManager.saveSettings()
    }
}

/**
 * Desktop implementation of UrlHistoryProvider that delegates to UrlHistoryManager.
 */
private class DesktopUrlHistoryProvider : UrlHistoryProvider {
    override fun addUrl(url: String, title: String) {
        UrlHistoryManager.addUrl(url, title)
    }

    override fun getSuggestions(query: String, limit: Int): List<UrlHistoryEntry> {
        return UrlHistoryManager.getSuggestions(query, limit).map { internal ->
            UrlHistoryEntry(
                url = internal.url,
                title = internal.title,
                domain = internal.domain,
                visitCount = internal.visitCount,
                lastVisited = internal.lastVisited
            )
        }
    }

    override fun deleteUrl(url: String) {
        UrlHistoryManager.deleteUrl(url)
    }

    override suspend fun saveHistory() {
        UrlHistoryManager.saveHistory()
    }
}

// Lazy singletons to avoid creating multiple instances
private val zoomSettingsProviderInstance by lazy { DesktopZoomSettingsProvider() }
private val urlHistoryProviderInstance by lazy { DesktopUrlHistoryProvider() }

/**
 * Actual implementation for creating ZoomSettingsProvider on desktop.
 */
actual fun createZoomSettingsProvider(): ZoomSettingsProvider = zoomSettingsProviderInstance

/**
 * Actual implementation for creating UrlHistoryProvider on desktop.
 */
actual fun createUrlHistoryProvider(): UrlHistoryProvider = urlHistoryProviderInstance
