package ai.rever.boss.plugin

import ai.rever.boss.config.SupabaseClientConfig
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
import java.io.File

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

    private var initialized = false

    /**
     * Local plugin directory (installed plugins).
     */
    private val _pluginDir: File by lazy {
        File(System.getProperty("user.home"), ".boss/plugins").apply { mkdirs() }
    }

    /**
     * Download cache directory.
     */
    private val _cacheDir: File by lazy {
        File(System.getProperty("user.home"), ".boss/plugin-cache").apply { mkdirs() }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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
     * Load persisted plugins using the provided DynamicPluginManager.
     * This should be called during application startup after the DynamicPluginManager is initialized.
     *
     * @param dynamicPluginManager The plugin manager to use for loading
     * @return Map of plugin IDs to their load results
     */
    suspend fun loadPersistedPlugins(
        dynamicPluginManager: ai.rever.boss.components.plugin.DynamicPluginManager
    ): Map<String, Result<ai.rever.boss.components.plugin.DynamicPluginInfo>> {
        val persistedPlugins = PluginPersistence.getInstalledPlugins()

        if (persistedPlugins.isEmpty()) {
            logger.info(LogCategory.SYSTEM, "No persisted plugins to load")
            return emptyMap()
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

        return dynamicPluginManager.loadPersistedPlugins(entries)
    }
}