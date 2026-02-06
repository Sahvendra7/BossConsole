package ai.rever.boss.components.wizard.plugin

import ai.rever.boss.components.plugin.DynamicPluginManager
import ai.rever.boss.plugin.PluginPersistence
import ai.rever.boss.plugin.PluginStoreSetup
import ai.rever.boss.plugin.repository.PluginWithSource
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Service for installing plugins during the wizard flow.
 *
 * Handles downloading plugins from the repository and installing them
 * through the DynamicPluginManager.
 */
class PluginInstallService(
    private val dynamicPluginManager: DynamicPluginManager
) {
    private val logger = BossLogger.forComponent("PluginInstallService")

    /**
     * Install multiple plugins with progress reporting.
     *
     * @param pluginIds List of plugin IDs to install
     * @param onProgress Callback for progress updates (0.0 to 1.0, status message)
     * @return Result containing installation result with both successful and failed plugin IDs
     */
    suspend fun installPlugins(
        pluginIds: List<String>,
        onProgress: (Float, String) -> Unit
    ): Result<PluginInstallResult> = withContext(Dispatchers.IO) {
        val installedIds = mutableListOf<String>()
        val failedIds = mutableListOf<Pair<String, String>>() // pluginId to error message

        if (pluginIds.isEmpty()) {
            onProgress(1f, "No plugins to install")
            return@withContext Result.success(PluginInstallResult(emptyList(), emptyList()))
        }

        val repositoryManager = PluginStoreSetup.repositoryManager
        if (repositoryManager == null) {
            return@withContext Result.failure(Exception("Plugin repository not initialized"))
        }

        val pluginDir = PluginStoreSetup.getPluginDir()
        val totalPlugins = pluginIds.size

        for ((index, pluginId) in pluginIds.withIndex()) {
            val progress = index.toFloat() / totalPlugins
            onProgress(progress, "Installing $pluginId...")

            try {
                logger.info(LogCategory.SYSTEM, "Installing plugin from wizard", mapOf(
                    "pluginId" to pluginId,
                    "progress" to "${index + 1}/$totalPlugins"
                ))

                // Check if already installed
                if (dynamicPluginManager.isInstalled(pluginId)) {
                    logger.info(LogCategory.SYSTEM, "Plugin already installed, skipping", mapOf(
                        "pluginId" to pluginId
                    ))
                    installedIds.add(pluginId)
                    continue
                }

                // Get plugin info from repository
                val pluginResult = repositoryManager.getPlugin(pluginId)
                val pluginWithSource: PluginWithSource? = pluginResult.getOrNull()

                if (pluginWithSource == null) {
                    logger.warn(LogCategory.SYSTEM, "Plugin not found in repository", mapOf(
                        "pluginId" to pluginId
                    ))
                    failedIds.add(pluginId to "Plugin not found in repository")
                    continue
                }

                val pluginInfo = pluginWithSource.plugin

                // Download the plugin
                onProgress(progress + (0.3f / totalPlugins), "Downloading $pluginId...")
                val targetPath = File(pluginDir, "${pluginId}-${pluginInfo.version}.jar").absolutePath
                val downloadResult = repositoryManager.downloadPlugin(pluginId, pluginInfo.version, targetPath)
                val jarPath: String? = downloadResult.getOrNull()

                if (jarPath == null) {
                    val error = downloadResult.exceptionOrNull()?.message ?: "Download failed"
                    logger.error(LogCategory.SYSTEM, "Failed to download plugin", mapOf(
                        "pluginId" to pluginId,
                        "error" to error
                    ))
                    failedIds.add(pluginId to error)
                    continue
                }

                // Install the plugin
                onProgress(progress + (0.6f / totalPlugins), "Loading $pluginId...")
                val installResult = dynamicPluginManager.installPlugin(jarPath, enabled = true)

                if (installResult.isSuccess) {
                    // Persist the installation
                    PluginPersistence.addInstalledPlugin(
                        pluginId = pluginId,
                        jarPath = jarPath,
                        enabled = true,
                        sourceUrl = pluginInfo.downloadUrl,
                        installedVersion = pluginInfo.version
                    )

                    logger.info(LogCategory.SYSTEM, "Plugin installed successfully", mapOf(
                        "pluginId" to pluginId
                    ))
                    installedIds.add(pluginId)
                } else {
                    val error = installResult.exceptionOrNull()?.message ?: "Installation failed"
                    logger.error(LogCategory.SYSTEM, "Failed to install plugin", mapOf(
                        "pluginId" to pluginId,
                        "error" to error
                    ))
                    failedIds.add(pluginId to error)
                }

            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Exception installing plugin", mapOf(
                    "pluginId" to pluginId
                ), e)
                failedIds.add(pluginId to (e.message ?: "Unknown error"))
            }
        }

        onProgress(1f, "Installation complete")

        // Log summary
        logger.info(LogCategory.SYSTEM, "Plugin installation complete", mapOf(
            "total" to totalPlugins,
            "installed" to installedIds.size,
            "failed" to failedIds.size
        ))

        if (failedIds.isNotEmpty()) {
            logger.warn(LogCategory.SYSTEM, "Some plugins failed to install", mapOf(
                "failed" to failedIds.map { "${it.first}: ${it.second}" }.joinToString("; ")
            ))
        }

        // Return result with both installed and failed IDs
        Result.success(PluginInstallResult(installedIds, failedIds))
    }

    companion object {
        /**
         * Create a PluginInstallService with the given DynamicPluginManager.
         */
        fun create(dynamicPluginManager: DynamicPluginManager): PluginInstallService {
            return PluginInstallService(dynamicPluginManager)
        }
    }
}
