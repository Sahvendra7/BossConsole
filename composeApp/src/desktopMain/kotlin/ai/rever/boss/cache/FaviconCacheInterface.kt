package ai.rever.boss.cache

import ai.rever.boss.components.registery.TabIcon

/**
 * Desktop implementation of favicon cache loading.
 */
actual fun loadFaviconFromCache(cacheKey: String?): TabIcon.Image? {
    if (cacheKey == null) return null
    return FaviconCache.loadFavicon(cacheKey)
}

/**
 * Desktop implementation of high-quality favicon loading.
 * Uses Google's favicon service for larger icons (128px).
 */
actual suspend fun loadHighQualityFavicon(url: String, standardCacheKey: String?): TabIcon.Image? {
    return HighQualityFaviconService.getHighQualityFavicon(url, standardCacheKey)
}
