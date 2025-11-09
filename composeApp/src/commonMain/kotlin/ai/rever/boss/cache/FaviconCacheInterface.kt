package ai.rever.boss.cache

import ai.rever.boss.components.registery.TabIcon

/**
 * Platform-specific favicon cache interface.
 * Desktop implementation uses file-based cache, other platforms return null.
 */
expect fun loadFaviconFromCache(cacheKey: String?): TabIcon.Image?
