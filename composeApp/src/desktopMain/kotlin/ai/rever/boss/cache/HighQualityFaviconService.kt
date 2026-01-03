package ai.rever.boss.cache

import ai.rever.boss.components.registery.TabIcon
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.imageio.ImageIO

/**
 * Service for fetching high-quality favicons for the dashboard.
 * Uses Google's favicon service to get larger icons (up to 128px).
 * Falls back to standard favicon cache if high-quality version unavailable.
 *
 * Performance optimizations:
 * - Reduced timeouts (2.5s) for faster failure detection
 * - Concurrency limit (3 simultaneous fetches) to prevent network flooding
 * - Cache-first approach to minimize network requests
 */
object HighQualityFaviconService {
    private const val HQ_CACHE_DIR_NAME = "favicon-hq-cache"
    private const val ICON_SIZE = 128 // Request 128px icons from Google
    private const val CONNECTION_TIMEOUT_MS = 2500 // Reduced from 5000ms
    private const val READ_TIMEOUT_MS = 2500 // Reduced from 5000ms
    private const val MAX_CONCURRENT_FETCHES = 3

    // Semaphore to limit concurrent network requests
    private val fetchSemaphore = Semaphore(MAX_CONCURRENT_FETCHES)

    private val cacheDir: File by lazy {
        val dir = File(System.getProperty("user.home"), ".boss/cache/$HQ_CACHE_DIR_NAME")
        dir.mkdirs()
        dir
    }

    /**
     * Get a high-quality favicon for a URL.
     * First checks HQ cache, then fetches from Google if needed.
     * Falls back to standard favicon cache if Google service fails.
     *
     * Uses semaphore to limit concurrent network requests to MAX_CONCURRENT_FETCHES.
     *
     * @param url The page URL to get favicon for
     * @param standardCacheKey The cache key from the standard favicon cache (fallback)
     * @return TabIcon.Image if found, null otherwise
     */
    suspend fun getHighQualityFavicon(url: String, standardCacheKey: String?): TabIcon.Image? {
        return withContext(Dispatchers.IO) {
            try {
                val domain = extractDomain(url) ?: return@withContext loadStandardFavicon(standardCacheKey)
                val cacheKey = generateCacheKey(domain)

                // Check HQ cache first (no semaphore needed for local cache)
                val cached = loadFromCache(cacheKey)
                if (cached != null) {
                    return@withContext cached
                }

                // Try to fetch from Google's favicon service (with concurrency limit)
                val fetched = fetchSemaphore.withPermit {
                    fetchFromGoogle(domain, cacheKey)
                }
                if (fetched != null) {
                    return@withContext fetched
                }

                // Fall back to standard favicon
                loadStandardFavicon(standardCacheKey)
            } catch (e: Exception) {
                println("[HQFavicon] Error: ${e.message}")
                loadStandardFavicon(standardCacheKey)
            }
        }
    }

    /**
     * Extract domain from URL.
     */
    private fun extractDomain(url: String): String? {
        return try {
            val uri = URL(url)
            uri.host?.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate cache key for domain.
     */
    private fun generateCacheKey(domain: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(domain.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Load favicon from HQ cache.
     */
    private fun loadFromCache(cacheKey: String): TabIcon.Image? {
        val cacheFile = File(cacheDir, "$cacheKey.png")
        if (!cacheFile.exists()) return null

        return try {
            val bufferedImage = ImageIO.read(cacheFile) ?: return null
            val imageBitmap = bufferedImage.toComposeImageBitmap()
            TabIcon.Image(BitmapPainter(imageBitmap))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch high-quality favicon from Google's service.
     * URL format: https://www.google.com/s2/favicons?domain=example.com&sz=128
     */
    private fun fetchFromGoogle(domain: String, cacheKey: String): TabIcon.Image? {
        val googleUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=$ICON_SIZE"

        return try {
            val connection = URL(googleUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val bufferedImage = ImageIO.read(connection.inputStream)
                if (bufferedImage != null && bufferedImage.width >= 32) {
                    // Save to cache
                    val cacheFile = File(cacheDir, "$cacheKey.png")
                    ImageIO.write(bufferedImage, "PNG", cacheFile)

                    val imageBitmap = bufferedImage.toComposeImageBitmap()
                    TabIcon.Image(BitmapPainter(imageBitmap))
                } else {
                    // Image too small, skip caching
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("[HQFavicon] Failed to fetch from Google for $domain: ${e.message}")
            null
        }
    }

    /**
     * Load from standard favicon cache as fallback.
     */
    private fun loadStandardFavicon(cacheKey: String?): TabIcon.Image? {
        if (cacheKey == null) return null
        return FaviconCache.loadFavicon(cacheKey)
    }

    /**
     * Clear the HQ favicon cache.
     */
    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            println("[HQFavicon] Error clearing cache: ${e.message}")
        }
    }

    /**
     * Get cache stats.
     */
    fun getCacheStats(): Pair<Int, Long> {
        val files = cacheDir.listFiles() ?: return Pair(0, 0L)
        return Pair(files.size, files.sumOf { it.length() })
    }
}
