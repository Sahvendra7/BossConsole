package ai.rever.boss.plugin

import ai.rever.boss.components.plugin.DynamicPluginManager
import ai.rever.boss.plugin.panel.manager.InstalledPluginState
import ai.rever.boss.plugin.panel.manager.PluginManagerComponent
import ai.rever.boss.plugin.panel.manager.PluginManagerOperations
import ai.rever.boss.plugin.repository.PluginInfo
import ai.rever.boss.plugin.repository.PluginRepositoryManager
import ai.rever.boss.plugin.updater.PluginUpdateManager
import ai.rever.boss.plugin.updater.UpdateInfo
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

/**
 * Desktop implementation of PluginManagerOperations.
 *
 * Connects the Plugin Manager UI to the plugin store infrastructure:
 * - DynamicPluginManager for local plugin operations
 * - RepositoryManager for remote plugin browsing and downloading
 * - UpdateManager for update checking
 */
class PluginManagerOperationsImpl(
    private val dynamicPluginManager: DynamicPluginManager,
    private val repositoryManagerProvider: () -> PluginRepositoryManager?,
    private val updateManagerProvider: () -> PluginUpdateManager?,
    private val onInstalledPluginsChanged: (List<InstalledPluginState>) -> Unit,
    private val onAvailablePluginsChanged: (List<PluginInfo>) -> Unit,
    private val onUpdatesChanged: (List<UpdateInfo>) -> Unit
) : PluginManagerOperations {

    private val logger = BossLogger.forComponent("PluginManagerOperationsImpl")

    private val repositoryManager: PluginRepositoryManager?
        get() = repositoryManagerProvider()

    private val updateManager: PluginUpdateManager?
        get() = updateManagerProvider()

    override suspend fun installPlugin(jarPath: String): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Installing plugin from JAR", mapOf(
                "jarPath" to jarPath
            ))

            val result = dynamicPluginManager.installPlugin(jarPath, enabled = true)
            if (result.isSuccess) {
                logger.info(LogCategory.SYSTEM, "Plugin installed successfully", mapOf(
                    "pluginId" to result.getOrNull()?.manifest?.pluginId
                ))
                Result.success(Unit)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown install error")
                logger.error(LogCategory.SYSTEM, "Failed to install plugin", error = error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception installing plugin", error = e)
            Result.failure(e)
        }
    }

    override suspend fun uninstallPlugin(pluginId: String): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Uninstalling plugin", mapOf(
                "pluginId" to pluginId
            ))

            val result = dynamicPluginManager.uninstallPlugin(pluginId, force = false)
            if (result.isSuccess) {
                logger.info(LogCategory.SYSTEM, "Plugin uninstalled successfully", mapOf(
                    "pluginId" to pluginId
                ))
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown uninstall error")
                logger.error(LogCategory.SYSTEM, "Failed to uninstall plugin", error = error)
            }
            result
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception uninstalling plugin", error = e)
            Result.failure(e)
        }
    }

    override suspend fun enablePlugin(pluginId: String): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Enabling plugin", mapOf(
                "pluginId" to pluginId
            ))
            dynamicPluginManager.enablePlugin(pluginId)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception enabling plugin", error = e)
            Result.failure(e)
        }
    }

    override suspend fun disablePlugin(pluginId: String): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Disabling plugin", mapOf(
                "pluginId" to pluginId
            ))
            dynamicPluginManager.disablePlugin(pluginId)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception disabling plugin", error = e)
            Result.failure(e)
        }
    }

    override suspend fun updatePlugin(pluginId: String): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Updating plugin", mapOf(
                "pluginId" to pluginId
            ))

            val manager = updateManager
                ?: return Result.failure(Exception("Update manager not available"))

            // Find the update info
            val updates = manager.availableUpdates.value
            val updateInfo = updates.find { it.pluginId == pluginId }
                ?: return Result.failure(Exception("No update available for plugin: $pluginId"))

            // Download from remote repository
            val repoManager = repositoryManager
                ?: return Result.failure(Exception("Repository manager not available"))

            val remoteRepo = repoManager.getRepository("supabase-store")
                ?: return Result.failure(Exception("Remote repository not available"))

            // Get target path
            val pluginDir = PluginStoreSetup.getPluginDir()
            val targetPath = File(pluginDir, "${pluginId}_${updateInfo.newVersion}.jar").absolutePath

            // Download the plugin
            val downloadResult = remoteRepo.downloadPlugin(pluginId, updateInfo.newVersion, targetPath)
            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
            }

            // Uninstall old version
            val uninstallResult = dynamicPluginManager.uninstallPlugin(pluginId, force = true)
            if (uninstallResult.isFailure) {
                // Delete downloaded file
                File(targetPath).delete()
                return Result.failure(uninstallResult.exceptionOrNull() ?: Exception("Uninstall failed"))
            }

            // Install new version
            val installResult = dynamicPluginManager.installPlugin(targetPath, enabled = true)
            if (installResult.isFailure) {
                return Result.failure(installResult.exceptionOrNull() ?: Exception("Install failed"))
            }

            logger.info(LogCategory.SYSTEM, "Plugin updated successfully", mapOf(
                "pluginId" to pluginId,
                "oldVersion" to updateInfo.currentVersion,
                "newVersion" to updateInfo.newVersion
            ))

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception updating plugin", error = e)
            Result.failure(e)
        }
    }

    override suspend fun updateAllPlugins(): Map<String, Result<Unit>> {
        val results = mutableMapOf<String, Result<Unit>>()

        val manager = updateManager ?: return results
        val updates = manager.availableUpdates.value

        for (update in updates) {
            results[update.pluginId] = updatePlugin(update.pluginId)
        }

        return results
    }

    override suspend fun refresh() {
        try {
            logger.debug(LogCategory.SYSTEM, "Refreshing plugin lists")

            // Refresh installed plugins
            val installedPlugins = dynamicPluginManager.getInstalledPlugins()
            val installedStates = installedPlugins.map { info ->
                val canUnloadResult = dynamicPluginManager.checkCanUnload(info.manifest.pluginId)
                InstalledPluginState(
                    pluginId = info.manifest.pluginId,
                    displayName = info.manifest.displayName,
                    version = info.manifest.version,
                    description = info.manifest.description,
                    enabled = info.enabled,
                    healthy = info.state == ai.rever.boss.plugin.api.PluginState.LOADED,
                    canUnload = canUnloadResult.isAllowed,
                    jarPath = info.jarPath
                )
            }
            onInstalledPluginsChanged(installedStates)

            // Refresh available plugins from remote repository
            repositoryManager?.let { repoManager ->
                val remoteRepo = repoManager.getRepository("supabase-store")
                if (remoteRepo != null && remoteRepo.isAvailable) {
                    val listResult = remoteRepo.listPlugins()
                    if (listResult.isSuccess) {
                        onAvailablePluginsChanged(listResult.getOrThrow())
                    }
                }
            }

            logger.debug(LogCategory.SYSTEM, "Plugin lists refreshed", mapOf(
                "installedCount" to installedStates.size
            ))
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Error refreshing plugin lists", error = e)
        }
    }

    override suspend fun checkForUpdates() {
        try {
            logger.debug(LogCategory.SYSTEM, "Checking for plugin updates")

            updateManager?.let { manager ->
                // Build map of installed plugins for update check
                val installedPlugins = dynamicPluginManager.getInstalledPlugins()
                    .associate { it.manifest.pluginId to it.manifest.version }
                manager.checkForUpdates(installedPlugins)
                val updates = manager.availableUpdates.value
                onUpdatesChanged(updates)

                logger.debug(LogCategory.SYSTEM, "Update check complete", mapOf(
                    "updatesAvailable" to updates.size
                ))
            }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Error checking for updates", error = e)
        }
    }

    override suspend fun browseForPlugin(): String? {
        return withContext(Dispatchers.Main) {
            try {
                val dialog = FileDialog(null as Frame?, "Select Plugin JAR", FileDialog.LOAD)
                dialog.filenameFilter = FilenameFilter { _, name ->
                    name.endsWith(".jar", ignoreCase = true)
                }
                dialog.isVisible = true

                val directory = dialog.directory
                val file = dialog.file

                if (directory != null && file != null) {
                    File(directory, file).absolutePath
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Error showing file picker", error = e)
                null
            }
        }
    }

    override suspend fun installFromRemote(pluginId: String, version: String?): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Installing plugin from remote", mapOf(
                "pluginId" to pluginId,
                "version" to (version ?: "latest")
            ))

            val repoManager = repositoryManager
                ?: return Result.failure(Exception("Repository manager not available"))

            val remoteRepo = repoManager.getRepository("supabase-store")
                ?: return Result.failure(Exception("Remote repository not available"))

            // Download to plugin directory
            val pluginDir = PluginStoreSetup.getPluginDir()
            val targetVersion = version ?: "latest"
            val targetPath = File(pluginDir, "${pluginId.replace(".", "_")}_$targetVersion.jar").absolutePath

            // Download the plugin
            val downloadResult = remoteRepo.downloadPlugin(pluginId, version, targetPath)
            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
            }

            logger.info(LogCategory.SYSTEM, "Plugin downloaded from remote", mapOf(
                "pluginId" to pluginId,
                "path" to targetPath
            ))

            // Install using dynamic plugin manager
            val installResult = dynamicPluginManager.installPlugin(targetPath, enabled = true)
            if (installResult.isFailure) {
                return Result.failure(installResult.exceptionOrNull() ?: Exception("Install failed"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception installing plugin from remote", error = e)
            Result.failure(e)
        }
    }
}

/**
 * Factory for creating PluginManagerOperations instances bound to a component.
 */
object PluginManagerOperationsFactory {

    /**
     * Create an operations provider for the PluginManagerPanelPlugin.
     *
     * The returned factory creates an operations instance that is bound to
     * a specific component through callbacks. This allows the operations
     * to update the component's state after operations complete.
     *
     * @param dynamicPluginManager The dynamic plugin manager
     * @param getComponent Function that returns the component (resolved lazily)
     * @return Factory function that creates PluginManagerOperationsImpl instances
     */
    fun createProvider(
        dynamicPluginManager: DynamicPluginManager,
        getComponent: () -> PluginManagerComponent?
    ): () -> PluginManagerOperations {
        return {
            PluginManagerOperationsImpl(
                dynamicPluginManager = dynamicPluginManager,
                repositoryManagerProvider = { PluginStoreSetup.repositoryManager },
                updateManagerProvider = { PluginStoreSetup.updateManager },
                onInstalledPluginsChanged = { plugins ->
                    getComponent()?.updateInstalledPlugins(plugins)
                },
                onAvailablePluginsChanged = { plugins ->
                    getComponent()?.updateAvailablePlugins(plugins)
                },
                onUpdatesChanged = { updates ->
                    getComponent()?.updateAvailableUpdates(updates)
                }
            )
        }
    }
}
