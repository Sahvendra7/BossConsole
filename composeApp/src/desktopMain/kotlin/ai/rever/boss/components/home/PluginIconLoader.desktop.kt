package ai.rever.boss.components.home

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

private val logger = BossLogger.forComponent("PluginIconLoader")

/** Fetches and decodes plugin store icons. Shape follows `HighQualityFaviconService`. */
private object PluginIcons {
    private const val REQUEST_TIMEOUT_MS = 2500L
    private const val MAX_CONCURRENT_FETCHES = 3

    /**
     * Largest icon accepted, in bytes.
     *
     * `icon_url` is an arbitrary URL from a database row, so the response size is not something
     * this process controls. Without a bound, one oversized row would be read fully into memory
     * before `ImageIO` ever rejected it.
     */
    private const val MAX_BYTES = 512 * 1024

    /**
     * In memory only, for the session.
     *
     * No disk cache, unlike the favicon service: there are tens of these rather than hundreds,
     * they are only read while the home screen is open, and a disk cache would need its own
     * eviction and invalidation for an icon that can change server-side at any time. A miss costs
     * one small request.
     *
     * The value is nullable-wrapped so a *failure* is cached too - otherwise every recomposition
     * would retry an unreachable host.
     */
    private val cache = ConcurrentHashMap<String, Result<Painter?>>()

    private val fetchSemaphore = Semaphore(MAX_CONCURRENT_FETCHES)

    private val httpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = REQUEST_TIMEOUT_MS
            }
            expectSuccess = false
            followRedirects = true
        }
    }

    // Two guard clauses (blank url, cache hit) before the work, each a distinct reason to answer
    // without touching the network.
    @Suppress("ReturnCount")
    suspend fun load(iconUrl: String): Painter? {
        // The common case: `icon_url` is blank for every row in the store today, so this returns
        // before touching the network and the tile renders the plugin's initials.
        if (iconUrl.isBlank()) return null
        cache[iconUrl]?.let { return it.getOrNull() }

        val painter =
            runCatching { fetch(iconUrl) }
                .onFailure { error ->
                    logger.debug(
                        LogCategory.NETWORK,
                        "Plugin icon fetch failed; the tile falls back to initials",
                        mapOf("error" to error.toString()),
                    )
                }.getOrNull()

        // Cached either way, including null: a plugin whose icon 404s must not be re-requested on
        // every recomposition of the grid.
        cache[iconUrl] = Result.success(painter)
        return painter
    }

    private suspend fun fetch(iconUrl: String): Painter? =
        withContext(Dispatchers.IO) {
            // http(s) only. `icon_url` is operator-supplied data, and ImageIO would happily read
            // a `file:` URL, which would turn a store row into a local-file read.
            val scheme = iconUrl.substringBefore("://", missingDelimiterValue = "").lowercase()
            if (scheme != "http" && scheme != "https") {
                logger.warn(
                    LogCategory.NETWORK,
                    "Ignoring a plugin icon URL that is not http(s)",
                    mapOf("scheme" to scheme.ifEmpty { "none" }),
                )
                return@withContext null
            }

            val response = fetchSemaphore.withPermit { httpClient.get(iconUrl) }
            if (response.status != HttpStatusCode.OK) return@withContext null

            val bytes = response.readRawBytes()
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@withContext null

            // Null for anything ImageIO cannot decode, which covers a body that is not an image.
            val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@withContext null
            BitmapPainter(image.toComposeImageBitmap())
        }
}

actual suspend fun loadPluginIcon(iconUrl: String): Painter? = PluginIcons.load(iconUrl)
