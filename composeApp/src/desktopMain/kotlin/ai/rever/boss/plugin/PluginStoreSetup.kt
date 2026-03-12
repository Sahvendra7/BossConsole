package ai.rever.boss.plugin

import ai.rever.boss.config.SupabaseClientConfig
import ai.rever.boss.plugin.pathutils.BossDirectories
import ai.rever.boss.services.supabase.SupabaseConfig
import ai.rever.boss.plugin.repository.LocalPluginRepository
import ai.rever.boss.plugin.repository.PluginRepositoryManager
import ai.rever.boss.plugin.repository.remote.PluginDownloadCache
import ai.rever.boss.plugin.repository.remote.PluginStoreConfig
import ai.rever.boss.plugin.repository.remote.PluginStoreRealtimeService
import ai.rever.boss.plugin.repository.remote.RemotePluginRepository
import ai.rever.boss.plugin.updater.PluginUpdateManager
import ai.rever.boss.plugin.updater.UpdateCheckerConfig
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Information about a system plugin that should always be installed.
 * System plugins are auto-downloaded from GitHub releases if missing.
 */
data class SystemPluginInfo(
    /** Unique plugin ID (e.g., "ai.rever.boss.plugin.api") */
    val pluginId: String,
    /** GitHub repository in format "owner/repo" */
    val githubRepo: String,
    /** Artifact prefix for JAR files (e.g., "boss-plugin-api") */
    val artifactPrefix: String,
    /** Load priority (lower = loads first) */
    val loadPriority: Int
)

/**
 * Sets up the plugin store infrastructure including:
 * - Local and remote plugin repositories
 * - Plugin update manager
 * - Download cache
 *
 * This is the central point for plugin store initialization.
 */
object PluginStoreSetup {
    private val logger = BossLogger.forComponent("PluginStoreSetup")

    private val manifestJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private var initialized = false

    /**
     * Local plugin directory (installed plugins).
     */
    private val _pluginDir: File by lazy {
        BossDirectories.resolve("plugins").apply { mkdirs() }
    }

    /**
     * Download cache directory.
     */
    private val _cacheDir: File by lazy {
        BossDirectories.resolve("plugin-cache").apply { mkdirs() }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * List of system plugins that must always be installed.
     * These are auto-downloaded from GitHub releases if missing.
     * Ordered by load priority (lower = loads first).
     */
    private val systemPlugins = listOf(
        SystemPluginInfo(
            pluginId = "ai.rever.boss.plugin.api",
            githubRepo = "risa-labs-inc/boss-plugin-api",
            artifactPrefix = "boss-plugin-api",
            loadPriority = 0
        ),
        SystemPluginInfo(
            pluginId = "ai.rever.boss.plugin.dynamic.pluginmanager",
            githubRepo = "risa-labs-inc/boss-plugin-plugin-manager",
            artifactPrefix = "boss-plugin-plugin-manager",
            loadPriority = 5
        ),
        SystemPluginInfo(
            pluginId = "ai.rever.boss.plugin.dynamic.terminaltab",
            githubRepo = "risa-labs-inc/boss-plugin-terminal-tab",
            artifactPrefix = "boss-plugin-terminal-tab",
            loadPriority = 10
        ),
        SystemPluginInfo(
            pluginId = "ai.rever.boss.plugin.dynamic.terminal",
            githubRepo = "risa-labs-inc/boss-plugin-terminal",
            artifactPrefix = "boss-plugin-terminal",
            loadPriority = 10
        ),
        SystemPluginInfo(
            pluginId = "ai.rever.boss.plugin.dynamic.fluckbrowser",
            githubRepo = "risa-labs-inc/boss-plugin-fluck-browser",
            artifactPrefix = "boss-plugin-fluck-browser",
            loadPriority = 10
        ),
        SystemPluginInfo(
            pluginId = "ai.rever.boss.plugin.dynamic.editortab",
            githubRepo = "risa-labs-inc/boss-plugin-editor-tab",
            artifactPrefix = "boss-plugin-editor-tab",
            loadPriority = 10
        )
    )

    // Plugin infrastructure components
    private var _downloadCache: PluginDownloadCache? = null
    private var _localRepository: LocalPluginRepository? = null
    private var _remoteRepository: RemotePluginRepository? = null
    private var _repositoryManager: PluginRepositoryManager? = null
    private var _updateManager: PluginUpdateManager? = null
    private var _realtimeService: PluginStoreRealtimeService? = null

    /**
     * Download cache for plugin JARs.
     */
    val downloadCache: PluginDownloadCache?
        get() = _downloadCache

    /**
     * Local plugin repository.
     */
    val localRepository: LocalPluginRepository?
        get() = _localRepository

    /**
     * Remote plugin repository (BOSS Plugin Store).
     */
    val remoteRepository: RemotePluginRepository?
        get() = _remoteRepository

    /**
     * Repository manager aggregating all repositories.
     */
    val repositoryManager: PluginRepositoryManager?
        get() = _repositoryManager

    /**
     * Plugin update manager.
     */
    val updateManager: PluginUpdateManager?
        get() = _updateManager

    /**
     * Realtime service for live plugin store updates.
     */
    val realtimeService: PluginStoreRealtimeService?
        get() = _realtimeService

    /**
     * Initialize the plugin store infrastructure.
     *
     * This should be called early in the application lifecycle.
     */
    fun initialize() {
        if (initialized) {
            logger.debug(LogCategory.SYSTEM, "Plugin store already initialized")
            return
        }

        try {
            logger.info(LogCategory.SYSTEM, "Initializing plugin store infrastructure")

            // Create download cache
            _downloadCache = PluginDownloadCache(_cacheDir)

            // Create local repository
            _localRepository = LocalPluginRepository(_pluginDir)

            // Create repository manager
            _repositoryManager = PluginRepositoryManager().apply {
                addRepository(_localRepository!!)
            }

            // Initialize remote repository with Supabase credentials
            initializeRemoteRepository()

            // Create update manager
            _updateManager = PluginUpdateManager(
                repositoryManager = _repositoryManager!!,
                config = UpdateCheckerConfig(
                    checkIntervalMs = 3600000L // Check every hour
                )
            )

            // Create and start realtime service for live updates
            _realtimeService = PluginStoreRealtimeService()

            initialized = true
            logger.info(LogCategory.SYSTEM, "Plugin store initialization complete", mapOf(
                "pluginDir" to _pluginDir.absolutePath,
                "cacheDir" to _cacheDir.absolutePath,
                "hasRemoteRepo" to (_remoteRepository != null)
            ))

        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Failed to initialize plugin store", error = e)
        }
    }

    /**
     * Initialize the remote plugin repository with Supabase credentials.
     */
    private fun initializeRemoteRepository() {
        try {
            logger.info(LogCategory.NETWORK, "Initializing remote plugin repository")

            // Initialize the config with Supabase credentials
            PluginStoreConfig.initialize(
                functionUrl = SupabaseClientConfig.functionUrl,
                anonKey = SupabaseClientConfig.anonKey,
                accessToken = null // Will be set when user logs in
            )

            // Create remote repository
            _remoteRepository = RemotePluginRepository(_downloadCache!!)

            // Register with repository manager
            _repositoryManager?.addRepository(_remoteRepository!!)

            // Listen for auth state changes to update access token
            // Wait for Supabase to be initialized first
            scope.launch {
                // Wait for Supabase initialization before accessing auth
                SupabaseConfig.isInitialized.first { it }

                // Now safe to collect auth session status
                SupabaseConfig.auth.sessionStatus.collect { status: SessionStatus ->
                    val token = when (status) {
                        is SessionStatus.Authenticated -> status.session.accessToken
                        else -> null
                    }
                    logger.debug(LogCategory.AUTH, "Updating plugin store access token", mapOf(
                        "hasToken" to (token != null)
                    ))
                    PluginStoreConfig.accessToken = token
                }
            }

            // Check health in background
            scope.launch {
                val isHealthy = _remoteRepository?.checkHealth() ?: false
                logger.info(LogCategory.NETWORK, "Remote plugin store health check", mapOf(
                    "healthy" to isHealthy
                ))
            }

            // Start realtime service for live updates
            _realtimeService?.start()

            logger.info(LogCategory.NETWORK, "Remote plugin repository initialized")

        } catch (e: Exception) {
            logger.error(LogCategory.NETWORK, "Failed to initialize remote repository", error = e)
            // Continue without remote repository - local plugins will still work
        }
    }

    /**
     * Check if the plugin store is initialized.
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Get the plugin directory path.
     */
    fun getPluginDir(): File = _pluginDir

    /**
     * Get the cache directory path.
     */
    fun getCacheDir(): File = _cacheDir

    /**
     * Refresh all repositories.
     */
    suspend fun refresh() {
        _repositoryManager?.refreshAll()
    }

    /**
     * Clear the download cache.
     *
     * @return Number of files removed
     */
    fun clearCache(): Int {
        return _downloadCache?.clearCache() ?: 0
    }

    /**
     * Get cache statistics.
     *
     * @return Map containing cache size and file count
     */
    fun getCacheStats(): Map<String, Any> {
        val cache = _downloadCache ?: return emptyMap()
        return mapOf(
            "sizeBytes" to cache.getCacheSize(),
            "pluginCount" to cache.getCachedPluginCount(),
            "fileCount" to cache.getCachedFileCount()
        )
    }

    /**
     * Clean up and shutdown the plugin store.
     */
    fun shutdown() {
        logger.info(LogCategory.SYSTEM, "Shutting down plugin store")
        _realtimeService?.dispose()
        _updateManager?.dispose()
        PluginStoreConfig.clear()
        _downloadCache = null
        _localRepository = null
        _remoteRepository = null
        _repositoryManager = null
        _updateManager = null
        _realtimeService = null
        initialized = false
    }

    /**
     * Set the callback to be invoked when plugins change in realtime.
     * This should be called by the PluginManagerComponent to refresh its state.
     */
    fun setOnPluginsChangedCallback(callback: suspend () -> Unit) {
        _realtimeService?.onRefreshRequested = callback
    }

    /**
     * Ensure all system plugins are installed.
     * If a system plugin is missing, it will be auto-downloaded from GitHub releases.
     * This ensures core functionality is always available.
     */
    private suspend fun ensureSystemPluginsInstalled() {
        logger.info(LogCategory.SYSTEM, "Checking system plugins installation", mapOf(
            "systemPluginCount" to systemPlugins.size
        ))

        val installedPlugins = PluginPersistence.getInstalledPlugins()
        val installedIds = installedPlugins.map { it.pluginId }.toSet()

        for (systemPlugin in systemPlugins) {
            try {
                // Check if plugin JAR exists
                val existingEntry = installedPlugins.find { it.pluginId == systemPlugin.pluginId }
                val jarExists = existingEntry?.let { File(it.jarPath).exists() } ?: false

                if (installedIds.contains(systemPlugin.pluginId) && jarExists) {
                    logger.debug(LogCategory.SYSTEM, "System plugin already installed", mapOf(
                        "pluginId" to systemPlugin.pluginId
                    ))
                    continue
                }

                logger.info(LogCategory.SYSTEM, "System plugin missing - downloading from GitHub", mapOf(
                    "pluginId" to systemPlugin.pluginId,
                    "repo" to systemPlugin.githubRepo
                ))

                // Download from GitHub releases
                val downloaded = downloadSystemPluginFromGitHub(systemPlugin)
                if (downloaded) {
                    logger.info(LogCategory.SYSTEM, "Successfully downloaded system plugin", mapOf(
                        "pluginId" to systemPlugin.pluginId
                    ))
                } else {
                    logger.warn(LogCategory.SYSTEM, "Failed to download system plugin", mapOf(
                        "pluginId" to systemPlugin.pluginId
                    ))
                }
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Error ensuring system plugin installed", mapOf(
                    "pluginId" to systemPlugin.pluginId,
                    "error" to (e.message ?: "unknown")
                ), e)
            }
        }
    }

    /**
     * Download a system plugin from GitHub releases.
     *
     * @param plugin The system plugin info
     * @return true if download was successful, false otherwise
     */
    private suspend fun downloadSystemPluginFromGitHub(plugin: SystemPluginInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.github.com/repos/${plugin.githubRepo}/releases/latest"
                logger.debug(LogCategory.SYSTEM, "Fetching latest release from GitHub", mapOf(
                    "url" to apiUrl
                ))

                // Fetch release info
                val connection = URL(apiUrl).openConnection().apply {
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "BossConsole")
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseText = connection.getInputStream().bufferedReader().readText()

                // Parse JSON to find the JAR asset
                val tagNameMatch = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(responseText)
                val tagName = tagNameMatch?.groupValues?.get(1) ?: "unknown"

                // Find the JAR download URL
                val jarUrlMatch = Regex(""""browser_download_url"\s*:\s*"([^"]+${plugin.artifactPrefix}[^"]*\.jar)"""")
                    .find(responseText)

                if (jarUrlMatch == null) {
                    logger.warn(LogCategory.SYSTEM, "No JAR asset found in GitHub release", mapOf(
                        "pluginId" to plugin.pluginId,
                        "repo" to plugin.githubRepo,
                        "tag" to tagName
                    ))
                    return@withContext false
                }

                val jarUrl = jarUrlMatch.groupValues[1]
                val jarFileName = jarUrl.substringAfterLast("/")
                val destFile = File(_pluginDir, jarFileName)

                logger.info(LogCategory.SYSTEM, "Downloading system plugin JAR", mapOf(
                    "pluginId" to plugin.pluginId,
                    "version" to tagName,
                    "url" to jarUrl,
                    "dest" to destFile.absolutePath
                ))

                // Remove old versions of this plugin
                _pluginDir.listFiles()?.filter {
                    it.name.startsWith(plugin.artifactPrefix) && it.name.endsWith(".jar")
                }?.forEach { oldFile ->
                    logger.debug(LogCategory.SYSTEM, "Removing old version", mapOf(
                        "file" to oldFile.name
                    ))
                    oldFile.delete()
                }

                // Download the JAR
                URL(jarUrl).openStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Verify download
                if (!destFile.exists() || destFile.length() == 0L) {
                    logger.error(LogCategory.SYSTEM, "Downloaded JAR is empty or missing", mapOf(
                        "pluginId" to plugin.pluginId,
                        "file" to destFile.absolutePath
                    ))
                    return@withContext false
                }

                logger.info(LogCategory.SYSTEM, "Downloaded system plugin successfully", mapOf(
                    "pluginId" to plugin.pluginId,
                    "version" to tagName,
                    "file" to destFile.name,
                    "size" to destFile.length()
                ))

                // Register in persistence
                PluginPersistence.addInstalledPlugin(
                    pluginId = plugin.pluginId,
                    jarPath = destFile.absolutePath,
                    enabled = true,
                    installedVersion = tagName.removePrefix("v")
                )

                true
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Failed to download system plugin from GitHub", mapOf(
                    "pluginId" to plugin.pluginId,
                    "repo" to plugin.githubRepo,
                    "error" to (e.message ?: "unknown")
                ), e)
                false
            }
        }
    }

    /**
     * Load persisted plugins using the provided DynamicPluginManager.
     * This should be called during application startup after the DynamicPluginManager is initialized.
     *
     * Bundled plugins are loaded first (from bundled-plugins directory), then persisted plugins.
     * If any system plugins are missing, they are auto-downloaded from GitHub releases.
     *
     * @param dynamicPluginManager The plugin manager to use for loading
     * @return Map of plugin IDs to their load results
     */
    suspend fun loadPersistedPlugins(
        dynamicPluginManager: ai.rever.boss.components.plugin.DynamicPluginManager
    ): Map<String, Result<ai.rever.boss.components.plugin.DynamicPluginInfo>> {
        val results = mutableMapOf<String, Result<ai.rever.boss.components.plugin.DynamicPluginInfo>>()

        // 1. Copy bundled plugins to ~/.boss/plugins if not already present
        copyBundledPluginsToPluginDir(dynamicPluginManager)

        // 2. Ensure all system plugins are installed (auto-download if missing)
        ensureSystemPluginsInstalled()

        // 3. Load persisted plugins (including bundled ones now in plugin dir)
        val persistedPlugins = PluginPersistence.getInstalledPlugins()

        if (persistedPlugins.isEmpty()) {
            logger.info(LogCategory.SYSTEM, "No persisted plugins to load")
            return results
        }

        logger.info(LogCategory.SYSTEM, "Loading persisted plugins", mapOf(
            "count" to persistedPlugins.size
        ))

        val entries = persistedPlugins.map { entry ->
            ai.rever.boss.components.plugin.PersistedPluginEntry(
                pluginId = entry.pluginId,
                jarPath = entry.jarPath,
                enabled = entry.enabled
            )
        }

        val persistedResults = dynamicPluginManager.loadPersistedPlugins(entries)
        results.putAll(persistedResults)

        return results
    }

    /**
     * Copy bundled plugins from app resources to ~/.boss/plugins directory.
     * Only copies if the plugin is not already installed or if the bundled version is newer.
     */
    private fun copyBundledPluginsToPluginDir(
        dynamicPluginManager: ai.rever.boss.components.plugin.DynamicPluginManager
    ) {
        logger.info(LogCategory.SYSTEM, "Starting bundled plugin copy check", mapOf(
            "pluginDir" to _pluginDir.absolutePath
        ))

        val bundledDir = dynamicPluginManager.getBundledPluginsDirectory()
        logger.info(LogCategory.SYSTEM, "Bundled plugins directory", mapOf(
            "path" to bundledDir.absolutePath,
            "exists" to bundledDir.exists(),
            "isDirectory" to bundledDir.isDirectory
        ))

        if (!bundledDir.exists() || !bundledDir.isDirectory) {
            logger.warn(LogCategory.SYSTEM, "No bundled plugins directory found", mapOf(
                "path" to bundledDir.absolutePath
            ))
            return
        }

        val jarFiles = bundledDir.listFiles { file ->
            file.isFile && file.extension == "jar"
        } ?: run {
            logger.warn(LogCategory.SYSTEM, "listFiles returned null for bundled dir")
            return
        }

        if (jarFiles.isEmpty()) {
            logger.warn(LogCategory.SYSTEM, "No JAR files found in bundled plugins directory", mapOf(
                "path" to bundledDir.absolutePath
            ))
            return
        }

        logger.info(LogCategory.SYSTEM, "Found bundled plugins to check", mapOf(
            "count" to jarFiles.size,
            "files" to jarFiles.map { it.name },
            "bundledDir" to bundledDir.absolutePath
        ))

        for (jarFile in jarFiles) {
            try {
                logger.debug(LogCategory.SYSTEM, "Processing bundled plugin JAR", mapOf(
                    "file" to jarFile.name,
                    "path" to jarFile.absolutePath
                ))

                // Read manifest to get plugin ID and version
                val manifest = readPluginManifest(jarFile)
                if (manifest == null) {
                    logger.warn(LogCategory.SYSTEM, "Could not read manifest from bundled plugin", mapOf(
                        "file" to jarFile.name
                    ))
                    continue
                }

                val pluginId = manifest.pluginId
                val bundledVersion = manifest.version

                logger.info(LogCategory.SYSTEM, "Read bundled plugin manifest", mapOf(
                    "pluginId" to pluginId,
                    "version" to bundledVersion
                ))

                // Check if already installed in persistence
                val installedPlugins = PluginPersistence.getInstalledPlugins()
                val existingPlugin = installedPlugins.find { it.pluginId == pluginId }

                logger.debug(LogCategory.SYSTEM, "Checking existing installation", mapOf(
                    "pluginId" to pluginId,
                    "existsInPersistence" to (existingPlugin != null),
                    "totalInstalledPlugins" to installedPlugins.size
                ))

                // Find ALL existing JARs for this plugin in the plugin directory (by artifact prefix)
                // This handles cases where user manually added a newer version with different filename
                val artifactPrefix = jarFile.name.substringBeforeLast("-").substringBeforeLast("-")
                val existingJarsInPluginDir = _pluginDir.listFiles()?.filter {
                    it.name.startsWith(artifactPrefix) && it.name.endsWith(".jar")
                } ?: emptyList()

                logger.info(LogCategory.SYSTEM, "Bundled plugin installation check", mapOf(
                    "pluginId" to pluginId,
                    "artifactPrefix" to artifactPrefix,
                    "existsInPersistence" to (existingPlugin != null),
                    "existingJarsInDir" to existingJarsInPluginDir.map { it.name }
                ))

                // Check if any existing JAR has same or newer version
                var shouldSkip = false
                var highestExistingVersion: String? = null

                for (existingJar in existingJarsInPluginDir) {
                    val existingManifest = readPluginManifest(existingJar)
                    if (existingManifest != null) {
                        val existingVersion = existingManifest.version
                        if (highestExistingVersion == null || isNewerVersion(existingVersion, highestExistingVersion)) {
                            highestExistingVersion = existingVersion
                        }
                        if (!isNewerVersion(bundledVersion, existingVersion)) {
                            logger.info(LogCategory.SYSTEM, "Found existing JAR with same/newer version - skipping", mapOf(
                                "pluginId" to pluginId,
                                "bundledVersion" to bundledVersion,
                                "existingVersion" to existingVersion,
                                "existingJar" to existingJar.name
                            ))
                            shouldSkip = true
                            break
                        }
                    }
                }

                if (shouldSkip) {
                    continue
                }

                // Also check persistence path if no JARs found by prefix
                if (existingJarsInPluginDir.isEmpty() && existingPlugin != null) {
                    val existingJar = File(existingPlugin.jarPath)
                    if (existingJar.exists()) {
                        val existingManifest = readPluginManifest(existingJar)
                        if (existingManifest != null && !isNewerVersion(bundledVersion, existingManifest.version)) {
                            logger.info(LogCategory.SYSTEM, "Bundled plugin already installed with same/newer version - skipping", mapOf(
                                "pluginId" to pluginId,
                                "bundledVersion" to bundledVersion,
                                "installedVersion" to existingManifest.version
                            ))
                            continue
                        }
                    }
                }

                if (highestExistingVersion != null) {
                    logger.info(LogCategory.SYSTEM, "Bundled plugin is newer - will update", mapOf(
                        "pluginId" to pluginId,
                        "bundledVersion" to bundledVersion,
                        "highestExistingVersion" to highestExistingVersion
                    ))
                } else {
                    logger.info(LogCategory.SYSTEM, "No existing version found - will copy bundled plugin", mapOf(
                        "pluginId" to pluginId
                    ))
                }

                // Remove old versions before copying
                existingJarsInPluginDir.forEach { oldJar ->
                    logger.info(LogCategory.SYSTEM, "Removing old version before copy", mapOf(
                        "oldJar" to oldJar.name
                    ))
                    oldJar.delete()
                }

                // Copy to plugin directory
                val destFile = File(_pluginDir, jarFile.name)
                logger.info(LogCategory.SYSTEM, "Copying bundled plugin", mapOf(
                    "from" to jarFile.absolutePath,
                    "to" to destFile.absolutePath
                ))

                jarFile.copyTo(destFile, overwrite = true)

                logger.info(LogCategory.SYSTEM, "Copied bundled plugin to plugin directory", mapOf(
                    "pluginId" to pluginId,
                    "version" to bundledVersion,
                    "destPath" to destFile.absolutePath,
                    "fileSize" to destFile.length()
                ))

                // Register in persistence (addInstalledPlugin handles both add and update)
                PluginPersistence.addInstalledPlugin(
                    pluginId = pluginId,
                    jarPath = destFile.absolutePath,
                    enabled = existingPlugin?.enabled ?: true,
                    installedVersion = bundledVersion
                )

                logger.info(LogCategory.SYSTEM, "Registered bundled plugin in persistence", mapOf(
                    "pluginId" to pluginId
                ))

            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Error copying bundled plugin", mapOf(
                    "file" to jarFile.name,
                    "error" to (e.message ?: "unknown")
                ), e)
            }
        }

        logger.info(LogCategory.SYSTEM, "Finished bundled plugin copy check")
    }

    /**
     * Read plugin manifest from a JAR file.
     */
    private fun readPluginManifest(jarFile: File): ai.rever.boss.plugin.api.PluginManifest? {
        return try {
            java.util.jar.JarFile(jarFile).use { jar ->
                val entry = jar.getJarEntry("META-INF/boss-plugin/plugin.json")
                    ?: return null
                val content = jar.getInputStream(entry).bufferedReader().readText()
                manifestJson.decodeFromString<ai.rever.boss.plugin.api.PluginManifest>(content)
            }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Failed to read plugin manifest", mapOf(
                "file" to jarFile.name
            ), e)
            null
        }
    }

    /**
     * Check if version1 is newer than version2.
     * Simple semver comparison (major.minor.patch).
     */
    private fun isNewerVersion(version1: String, version2: String): Boolean {
        val v1Parts = version1.split(".").mapNotNull { it.toIntOrNull() }
        val v2Parts = version2.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(v1Parts.size, v2Parts.size)) {
            val v1 = v1Parts.getOrElse(i) { 0 }
            val v2 = v2Parts.getOrElse(i) { 0 }
            if (v1 > v2) return true
            if (v1 < v2) return false
        }
        return false
    }
}