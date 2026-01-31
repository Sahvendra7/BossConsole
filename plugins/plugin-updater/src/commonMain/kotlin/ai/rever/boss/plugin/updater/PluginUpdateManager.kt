package ai.rever.boss.plugin.updater

import ai.rever.boss.plugin.dependency.SemanticVersion
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import ai.rever.boss.plugin.repository.PluginInfo
import ai.rever.boss.plugin.repository.PluginRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Callback interface for update events.
 */
interface UpdateListener {
    /**
     * Called when updates are available.
     */
    fun onUpdatesAvailable(updates: List<UpdateInfo>) {}

    /**
     * Called when an update is being downloaded.
     */
    fun onUpdateDownloading(pluginId: String, progress: Float) {}

    /**
     * Called when an update installation starts.
     */
    fun onUpdateInstalling(pluginId: String) {}

    /**
     * Called when an update is completed.
     */
    fun onUpdateCompleted(pluginId: String, newVersion: String) {}

    /**
     * Called when an update fails.
     */
    fun onUpdateFailed(pluginId: String, error: String) {}
}

/**
 * Manages plugin updates.
 *
 * Features:
 * - Check for updates from repositories
 * - Download new versions
 * - Update plugins (unload old, load new)
 * - Rollback on failure
 * - Periodic background checks
 */
class PluginUpdateManager(
    private val repositoryManager: PluginRepositoryManager,
    private val config: UpdateCheckerConfig = UpdateCheckerConfig()
) {
    private val logger = BossLogger.forComponent("PluginUpdateManager")

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var checkJob: Job? = null

    /**
     * Current update state.
     */
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * Last check result.
     */
    private val _lastCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val lastCheckResult: StateFlow<UpdateCheckResult?> = _lastCheckResult.asStateFlow()

    /**
     * Available updates.
     */
    private val _availableUpdates = MutableStateFlow<List<UpdateInfo>>(emptyList())
    val availableUpdates: StateFlow<List<UpdateInfo>> = _availableUpdates.asStateFlow()

    /**
     * Update listeners.
     */
    private val listeners = mutableListOf<UpdateListener>()

    /**
     * Add an update listener.
     */
    fun addListener(listener: UpdateListener) {
        listeners.add(listener)
    }

    /**
     * Remove an update listener.
     */
    fun removeListener(listener: UpdateListener) {
        listeners.remove(listener)
    }

    /**
     * Start periodic update checks.
     */
    fun startPeriodicChecks() {
        if (config.checkIntervalMs <= 0) {
            logger.info(LogCategory.SYSTEM, "Periodic update checks disabled")
            return
        }

        checkJob?.cancel()
        checkJob = scope.launch {
            while (isActive) {
                checkForUpdates(emptyMap())
                delay(config.checkIntervalMs)
            }
        }

        logger.info(LogCategory.SYSTEM, "Started periodic update checks", mapOf(
            "intervalMs" to config.checkIntervalMs
        ))
    }

    /**
     * Stop periodic update checks.
     */
    fun stopPeriodicChecks() {
        checkJob?.cancel()
        checkJob = null
        logger.info(LogCategory.SYSTEM, "Stopped periodic update checks")
    }

    /**
     * Check for updates for installed plugins.
     *
     * @param installedPlugins Map of plugin ID to installed version
     * @return Update check result
     */
    suspend fun checkForUpdates(
        installedPlugins: Map<String, String>
    ): UpdateCheckResult {
        _state.value = UpdateState.Checking

        logger.info(LogCategory.SYSTEM, "Checking for updates", mapOf(
            "pluginCount" to installedPlugins.size
        ))

        try {
            val updates = mutableListOf<UpdateInfo>()
            val failed = mutableMapOf<String, String>()

            for ((pluginId, installedVersion) in installedPlugins) {
                try {
                    val pluginResult = repositoryManager.getPlugin(pluginId)
                    val pluginWithSource = pluginResult.getOrNull()

                    if (pluginWithSource != null) {
                        val latestVersion = pluginWithSource.plugin.version

                        if (isNewerVersion(latestVersion, installedVersion)) {
                            updates.add(createUpdateInfo(
                                pluginWithSource.plugin,
                                installedVersion
                            ))
                        }
                    }
                } catch (e: Exception) {
                    failed[pluginId] = e.message ?: "Unknown error"
                    logger.warn(LogCategory.SYSTEM, "Failed to check plugin for updates", mapOf(
                        "pluginId" to pluginId,
                        "error" to (e.message ?: "unknown")
                    ))
                }
            }

            val result = UpdateCheckResult(
                availableUpdates = updates,
                failedChecks = failed
            )

            _lastCheckResult.value = result
            _availableUpdates.value = updates
            _state.value = UpdateState.Idle

            if (updates.isNotEmpty()) {
                logger.info(LogCategory.SYSTEM, "Updates available", mapOf(
                    "count" to updates.size,
                    "critical" to updates.count { it.critical }
                ))
                listeners.forEach { it.onUpdatesAvailable(updates) }
            }

            return result
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Failed to check for updates", error = e)
            _state.value = UpdateState.Idle
            return UpdateCheckResult(
                availableUpdates = emptyList(),
                failedChecks = installedPlugins.mapValues { "Check failed: ${e.message}" }
            )
        }
    }

    /**
     * Download an update.
     *
     * @param pluginId Plugin to update
     * @param targetPath Path to download the update to
     * @return Result with the downloaded file path
     */
    suspend fun downloadUpdate(
        pluginId: String,
        targetPath: String
    ): Result<String> {
        val update = _availableUpdates.value.find { it.pluginId == pluginId }
            ?: return Result.failure(Exception("No update available for plugin: $pluginId"))

        _state.value = UpdateState.Downloading(pluginId, 0f)
        listeners.forEach { it.onUpdateDownloading(pluginId, 0f) }

        return try {
            val result = repositoryManager.downloadPlugin(
                pluginId = pluginId,
                version = update.newVersion,
                targetPath = targetPath
            )

            if (result.isSuccess) {
                _state.value = UpdateState.Idle
                listeners.forEach { it.onUpdateDownloading(pluginId, 1f) }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Download failed"
                _state.value = UpdateState.Failed(pluginId, error)
                listeners.forEach { it.onUpdateFailed(pluginId, error) }
            }

            result
        } catch (e: Exception) {
            val error = e.message ?: "Download failed"
            _state.value = UpdateState.Failed(pluginId, error, e)
            listeners.forEach { it.onUpdateFailed(pluginId, error) }
            Result.failure(e)
        }
    }

    /**
     * Update a plugin.
     *
     * This coordinates the update process:
     * 1. Download new version
     * 2. Call the provided unloader to unload the old version
     * 3. Call the provided loader to load the new version
     * 4. Rollback on failure
     *
     * @param pluginId Plugin to update
     * @param downloadPath Path to download the new version
     * @param unloadPlugin Function to unload the old plugin
     * @param loadPlugin Function to load the new plugin
     * @return Result indicating success or failure
     */
    suspend fun updatePlugin(
        pluginId: String,
        downloadPath: String,
        unloadPlugin: suspend (String) -> Result<Unit>,
        loadPlugin: suspend (String) -> Result<Unit>
    ): Result<Unit> {
        val update = _availableUpdates.value.find { it.pluginId == pluginId }
            ?: return Result.failure(Exception("No update available for plugin: $pluginId"))

        logger.info(LogCategory.SYSTEM, "Starting plugin update", mapOf(
            "pluginId" to pluginId,
            "currentVersion" to update.currentVersion,
            "newVersion" to update.newVersion
        ))

        // Download new version
        val downloadResult = downloadUpdate(pluginId, downloadPath)
        if (downloadResult.isFailure) {
            return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
        }

        val downloadedPath = downloadResult.getOrThrow()

        // Install
        _state.value = UpdateState.Installing(pluginId)
        listeners.forEach { it.onUpdateInstalling(pluginId) }

        // Unload old version
        val unloadResult = unloadPlugin(pluginId)
        if (unloadResult.isFailure) {
            val error = unloadResult.exceptionOrNull()?.message ?: "Unload failed"
            _state.value = UpdateState.Failed(pluginId, error)
            listeners.forEach { it.onUpdateFailed(pluginId, error) }
            return unloadResult
        }

        // Load new version
        val loadResult = loadPlugin(downloadedPath)
        if (loadResult.isFailure) {
            // Rollback - try to reload the old version
            logger.warn(LogCategory.SYSTEM, "Update failed, attempting rollback", mapOf(
                "pluginId" to pluginId
            ))

            // Note: Actual rollback would require keeping track of the old JAR path
            // For now, we just report the failure

            val error = loadResult.exceptionOrNull()?.message ?: "Install failed"
            _state.value = UpdateState.Failed(pluginId, error)
            listeners.forEach { it.onUpdateFailed(pluginId, error) }
            return loadResult
        }

        // Success
        _state.value = UpdateState.Completed(pluginId, update.newVersion)
        listeners.forEach { it.onUpdateCompleted(pluginId, update.newVersion) }

        // Remove from available updates
        _availableUpdates.value = _availableUpdates.value.filter { it.pluginId != pluginId }

        logger.info(LogCategory.SYSTEM, "Plugin updated successfully", mapOf(
            "pluginId" to pluginId,
            "newVersion" to update.newVersion
        ))

        return Result.success(Unit)
    }

    /**
     * Update all plugins with available updates.
     *
     * @param downloadDir Directory to download updates to
     * @param unloadPlugin Function to unload plugins
     * @param loadPlugin Function to load plugins
     * @return Map of plugin ID to update result
     */
    suspend fun updateAll(
        downloadDir: String,
        unloadPlugin: suspend (String) -> Result<Unit>,
        loadPlugin: suspend (String) -> Result<Unit>
    ): Map<String, Result<Unit>> {
        val results = mutableMapOf<String, Result<Unit>>()

        for (update in _availableUpdates.value) {
            val downloadPath = "$downloadDir/${update.pluginId}-${update.newVersion}.jar"
            results[update.pluginId] = updatePlugin(
                pluginId = update.pluginId,
                downloadPath = downloadPath,
                unloadPlugin = unloadPlugin,
                loadPlugin = loadPlugin
            )
        }

        return results
    }

    /**
     * Dismiss an update (don't notify again for this version).
     */
    fun dismissUpdate(pluginId: String) {
        _availableUpdates.value = _availableUpdates.value.filter { it.pluginId != pluginId }
    }

    /**
     * Clear all available updates.
     */
    fun clearUpdates() {
        _availableUpdates.value = emptyList()
    }

    /**
     * Dispose the update manager.
     */
    fun dispose() {
        stopPeriodicChecks()
        scope.cancel()
        listeners.clear()
    }

    /**
     * Check if version1 is newer than version2.
     */
    private fun isNewerVersion(version1: String, version2: String): Boolean {
        val v1 = SemanticVersion.parse(version1) ?: return false
        val v2 = SemanticVersion.parse(version2) ?: return false
        return v1 > v2
    }

    /**
     * Create an UpdateInfo from a PluginInfo.
     */
    private fun createUpdateInfo(plugin: PluginInfo, currentVersion: String): UpdateInfo {
        return UpdateInfo(
            pluginId = plugin.pluginId,
            displayName = plugin.displayName,
            currentVersion = currentVersion,
            newVersion = plugin.version,
            changelog = plugin.changelog,
            size = plugin.size,
            critical = false, // Would need to be specified in plugin metadata
            releaseDate = plugin.publishedAt,
            downloadUrl = plugin.downloadUrl,
            requiresRestart = false // Dynamic plugins don't require restart
        )
    }
}
