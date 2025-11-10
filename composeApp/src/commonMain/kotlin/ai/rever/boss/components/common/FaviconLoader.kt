package ai.rever.boss.components.common

import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.registery.TabIcon
import ai.rever.boss.components.registery.TabInfo
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hook that loads favicon from cache for a given tab
 * Returns loaded favicon or null if unavailable/error
 *
 * Handles:
 * - Async loading on IO thread (non-blocking)
 * - Error handling with logging
 * - Efficient caching with remember
 */
@Composable
fun rememberFaviconLoader(tabInfo: TabInfo): TabIcon.Image? {
    // Extract faviconCacheKey if this is a Fluck browser tab
    val faviconCacheKey = (tabInfo as? FluckTabInfo)?.faviconCacheKey

    // State to hold the loaded favicon
    var loadedFavicon by remember(faviconCacheKey) {
        mutableStateOf<TabIcon.Image?>(null)
    }

    // Load favicon asynchronously on IO thread
    LaunchedEffect(faviconCacheKey) {
        if (faviconCacheKey != null) {
            loadedFavicon = withContext(Dispatchers.IO) {
                try {
                    ai.rever.boss.cache.loadFaviconFromCache(faviconCacheKey)
                } catch (e: Exception) {
                    println("Error loading favicon for key '$faviconCacheKey': ${e.message}")
                    null
                }
            }
        } else {
            loadedFavicon = null
        }
    }

    return loadedFavicon
}
