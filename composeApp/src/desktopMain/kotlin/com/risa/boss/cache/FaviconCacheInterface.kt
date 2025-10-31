package com.risa.boss.cache

import ai.rever.boss.components.registery.TabIcon

/**
 * Desktop implementation of favicon cache loading.
 */
actual fun loadFaviconFromCache(cacheKey: String?): TabIcon.Image? {
    if (cacheKey == null) return null
    return FaviconCache.loadFavicon(cacheKey)
}
