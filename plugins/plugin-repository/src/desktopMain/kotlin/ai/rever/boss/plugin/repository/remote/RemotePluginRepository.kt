package ai.rever.boss.plugin.repository.remote

import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import ai.rever.boss.plugin.repository.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository that connects to the remote plugin store (Supabase Edge Function).
 *
 * Features:
 * - List and search plugins from the remote store
 * - Download plugins with progress tracking
 * - SHA-256 verification of downloaded JARs
 * - Local caching of downloaded JARs
 *
 * @param downloadCache Cache for downloaded plugin JARs
 */
class RemotePluginRepository(
    private val downloadCache: PluginDownloadCache = PluginDownloadCache()
) : PluginRepository {

    private val logger = BossLogger.forComponent("RemotePluginRepository")

    private val downloadHttpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 300_000 // 5 minutes for large downloads
        }
    }

    /**
     * Cached plugin list from last refresh.
     */
    private var cachedPlugins: List<PluginInfo> = emptyList()

    /**
     * Active download progress flows by plugin ID.
     */
    private val downloadProgress = ConcurrentHashMap<String, MutableStateFlow<Float>>()

    override val id: String = "supabase-store"
    override val name: String = "BOSS Plugin Store"
    override val isLocal: Boolean = false

    override val isAvailable: Boolean
        get() = PluginStoreConfig.isInitialized && checkAvailability()

    private fun checkAvailability(): Boolean {
        // Check if config is initialized - actual health check is async
        return PluginStoreConfig.isInitialized
    }

    override suspend fun listPlugins(): Result<List<PluginInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!PluginStoreConfig.isInitialized) {
                logger.warn(LogCategory.NETWORK, "Plugin store not initialized")
                return@runCatching emptyList()
            }

            val response = PluginStoreClient.listPlugins(
                page = 1,
                pageSize = 100, // Get first 100 plugins
                sortBy = "downloads"
            )

            val plugins = response.plugins.map { it.toPluginInfo() }
            cachedPlugins = plugins

            logger.info(LogCategory.NETWORK, "Listed remote plugins", mapOf(
                "count" to plugins.size,
                "totalCount" to response.totalCount
            ))

            plugins
        }.onFailure { e ->
            logger.error(LogCategory.NETWORK, "Failed to list remote plugins", error = e)
        }
    }

    override suspend fun searchPlugins(filter: PluginSearchFilter): Result<PluginSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (!PluginStoreConfig.isInitialized) {
                return@runCatching PluginSearchResult(
                    plugins = emptyList(),
                    totalCount = 0,
                    page = filter.page,
                    pageSize = filter.pageSize
                )
            }

            val response = PluginStoreClient.searchPlugins(filter)
            val plugins = response.plugins.map { it.toPluginInfo() }

            logger.debug(LogCategory.NETWORK, "Searched remote plugins", mapOf(
                "query" to filter.query,
                "resultCount" to plugins.size,
                "totalCount" to response.totalCount
            ))

            PluginSearchResult(
                plugins = plugins,
                totalCount = response.totalCount,
                page = response.page,
                pageSize = response.pageSize
            )
        }.onFailure { e ->
            logger.error(LogCategory.NETWORK, "Failed to search remote plugins", error = e)
        }
    }

    override suspend fun getPlugin(pluginId: String): Result<PluginInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            if (!PluginStoreConfig.isInitialized) {
                return@runCatching null
            }

            val response = PluginStoreClient.getPlugin(pluginId)
            response?.toPluginInfo()
        }.onFailure { e ->
            logger.error(LogCategory.NETWORK, "Failed to get remote plugin", mapOf("pluginId" to pluginId), e)
        }
    }

    override suspend fun getPluginVersions(pluginId: String): Result<List<PluginInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!PluginStoreConfig.isInitialized) {
                return@runCatching emptyList()
            }

            val response = PluginStoreClient.getPlugin(pluginId)
                ?: return@runCatching emptyList()

            // Convert each version to PluginInfo
            response.versions.map { version ->
                PluginInfo(
                    pluginId = response.pluginId,
                    displayName = response.displayName,
                    version = version.version,
                    description = response.description,
                    author = response.authorName,
                    url = response.homepageUrl,
                    type = parsePluginType(response.type),
                    apiVersion = response.apiVersion,
                    size = version.jarSize,
                    sha256 = version.sha256,
                    dependencies = version.dependencies.map { it.pluginId },
                    changelog = version.changelog,
                    verified = response.verified
                )
            }
        }.onFailure { e ->
            logger.error(LogCategory.NETWORK, "Failed to get plugin versions", mapOf("pluginId" to pluginId), e)
        }
    }

    override suspend fun downloadPlugin(
        pluginId: String,
        version: String?,
        targetPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!PluginStoreConfig.isInitialized) {
                throw DownloadException("Plugin store not initialized", pluginId, id)
            }

            // Get download info
            val downloadInfo = if (version != null) {
                PluginStoreClient.getDownloadUrl(pluginId, version)
            } else {
                PluginStoreClient.getDownloadUrl(pluginId)
            }

            // Check cache first
            val cachedFile = downloadCache.getCachedJar(pluginId, downloadInfo.version, downloadInfo.sha256)
            if (cachedFile != null) {
                logger.info(LogCategory.NETWORK, "Using cached JAR", mapOf(
                    "pluginId" to pluginId,
                    "version" to downloadInfo.version
                ))
                cachedFile.copyTo(File(targetPath), overwrite = true)
                return@runCatching targetPath
            }

            // Initialize progress tracking
            val progressFlow = MutableStateFlow(0f)
            downloadProgress[pluginId] = progressFlow

            try {
                logger.info(LogCategory.NETWORK, "Downloading plugin", mapOf(
                    "pluginId" to pluginId,
                    "version" to downloadInfo.version,
                    "size" to downloadInfo.size
                ))

                // Download with progress tracking
                downloadHttpClient.prepareGet(downloadInfo.downloadUrl).execute { response ->
                    val channel = response.bodyAsChannel()
                    val totalBytes = response.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLongOrNull() ?: downloadInfo.size
                    var downloadedBytes = 0L

                    File(targetPath).outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (!channel.isClosedForRead) {
                            val bytes = channel.readAvailable(buffer)
                            if (bytes > 0) {
                                output.write(buffer, 0, bytes)
                                downloadedBytes += bytes
                                if (totalBytes > 0) {
                                    progressFlow.value = downloadedBytes.toFloat() / totalBytes
                                }
                            }
                        }
                    }
                }

                // Verify SHA-256
                val actualSha256 = File(targetPath).sha256()
                if (!actualSha256.equals(downloadInfo.sha256, ignoreCase = true)) {
                    File(targetPath).delete()
                    throw DownloadException(
                        "SHA-256 mismatch. Expected: ${downloadInfo.sha256}, Got: $actualSha256",
                        pluginId, id
                    )
                }

                // Cache the downloaded JAR
                downloadCache.cacheJar(pluginId, downloadInfo.version, File(targetPath))

                progressFlow.value = 1f

                logger.info(LogCategory.NETWORK, "Plugin downloaded successfully", mapOf(
                    "pluginId" to pluginId,
                    "version" to downloadInfo.version,
                    "path" to targetPath
                ))

                targetPath
            } finally {
                downloadProgress.remove(pluginId)
            }
        }.onFailure { e ->
            logger.error(LogCategory.NETWORK, "Failed to download plugin", mapOf("pluginId" to pluginId), e)
        }
    }

    override fun getDownloadProgress(pluginId: String): Flow<Float>? {
        return downloadProgress[pluginId]?.asStateFlow()
    }

    override suspend fun refresh(): Result<Unit> {
        return listPlugins().map { }
    }

    /**
     * Rate a plugin in the remote store.
     *
     * Requires authentication (access token set in PluginStoreConfig).
     *
     * @param pluginId The plugin ID to rate
     * @param rating Rating from 1-5
     * @param review Optional review text
     * @return Result indicating success or failure
     */
    suspend fun ratePlugin(pluginId: String, rating: Int, review: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = PluginStoreClient.ratePlugin(pluginId, rating, review)
            if (!response.success) {
                throw PluginStoreException(response.error ?: "Failed to rate plugin")
            }
            logger.info(LogCategory.NETWORK, "Plugin rated", mapOf(
                "pluginId" to pluginId,
                "rating" to rating
            ))
        }.onFailure { e ->
            logger.error(LogCategory.NETWORK, "Failed to rate plugin", mapOf("pluginId" to pluginId), e)
        }
    }

    /**
     * Check if the remote store is healthy.
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        if (!PluginStoreConfig.isInitialized) return@withContext false
        PluginStoreClient.checkHealth()
    }

    // ============================================================================
    // Helper Functions
    // ============================================================================

    private fun parsePluginType(type: String): ai.rever.boss.plugin.api.PluginType = when (type.lowercase()) {
        "tab" -> ai.rever.boss.plugin.api.PluginType.TAB
        "hybrid", "mixed" -> ai.rever.boss.plugin.api.PluginType.MIXED
        else -> ai.rever.boss.plugin.api.PluginType.PANEL
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
