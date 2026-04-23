package ai.rever.boss.plugin

import ai.rever.boss.components.plugin.DynamicPluginManager
import ai.rever.boss.components.plugin.MicrokernelRuntime
import ai.rever.boss.plugin.api.LoadedPluginInfo
import ai.rever.boss.plugin.api.PluginLoaderDelegate
import ai.rever.boss.plugin.api.PluginState
import ai.rever.boss.plugin.repository.remote.PluginStoreConfig
import ai.rever.boss.plugin.sandbox.ui.PluginCrashRegistry
import ai.rever.boss.services.auth.AuthStateManager
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File

/**
 * Implementation of PluginLoaderDelegate that wraps DynamicPluginManager.
 *
 * This delegate is registered via context.registerPluginAPI() and allows
 * dynamic plugins (like plugin-manager) to interact with the plugin system.
 */
class PluginLoaderDelegateImpl(
    private val dynamicPluginManager: DynamicPluginManager
) : PluginLoaderDelegate {

    private val logger = BossLogger.forComponent("PluginLoaderDelegate")

    override suspend fun loadPlugin(jarPath: String): LoadedPluginInfo? {
        // Never try to load the microkernel runtime via the plugin-install
        // path — it's a classpath dependency for OOP child JVMs, not a
        // loadable plugin. DefaultPlugin.loadExternalPlugins already skips
        // it on directory scan, but plugin-manager install/update flows
        // reach us directly with a JAR path and would otherwise trip the
        // binary-compatibility validator on core JDK classes.
        //
        // We check by pluginId (from the manifest) rather than filename
        // because the plugin store downloads with a pluginId-based name
        // (`ai_rever_boss_microkernel_runtime_1.0.10.jar`) while the
        // Gradle build output uses the artifact prefix
        // (`boss-microkernel-runtime-1.0.10-all.jar`). Either name needs
        // to be rejected.
        if (isMicrokernelRuntimeJar(jarPath)) {
            logger.debug(LogCategory.SYSTEM, "Refusing to load microkernel runtime as a plugin", mapOf(
                "jarPath" to jarPath
            ))
            return null
        }
        return try {
            logger.info(LogCategory.SYSTEM, "Loading plugin via delegate", mapOf("jarPath" to jarPath))
            val result = dynamicPluginManager.installPlugin(jarPath, enabled = true)
            if (result.isSuccess) {
                val loadedPlugin = result.getOrNull()
                loadedPlugin?.let { info ->
                    LoadedPluginInfo(
                        pluginId = info.manifest.pluginId,
                        displayName = info.manifest.displayName,
                        version = info.manifest.version,
                        description = info.manifest.description,
                        author = info.manifest.author,
                        url = info.manifest.url,
                        type = info.manifest.type.name.lowercase(),
                        apiVersion = info.manifest.apiVersion,
                        minBossVersion = info.manifest.minBossVersion,
                        isSystemPlugin = info.manifest.systemPlugin,
                        canUnload = info.manifest.canUnload,
                        loadPriority = info.manifest.loadPriority,
                        isEnabled = info.enabled,
                        healthy = info.state == PluginState.LOADED,
                        jarPath = info.jarPath,
                        installedAt = System.currentTimeMillis(),
                        requiresAdmin = info.manifest.requiresAdmin
                    )
                }
            } else {
                logger.error(LogCategory.SYSTEM, "Failed to load plugin", error = result.exceptionOrNull())
                null
            }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception loading plugin", error = e)
            null
        }
    }

    override suspend fun unloadPlugin(pluginId: String): Boolean {
        return try {
            logger.info(LogCategory.SYSTEM, "Unloading plugin via delegate", mapOf("pluginId" to pluginId))
            val result = dynamicPluginManager.uninstallPlugin(pluginId, force = false)
            result.isSuccess
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception unloading plugin", error = e)
            false
        }
    }

    override suspend fun reloadPlugin(pluginId: String): LoadedPluginInfo? {
        return try {
            logger.info(LogCategory.SYSTEM, "Reloading plugin via delegate", mapOf("pluginId" to pluginId))

            // Get the JAR path before unloading
            val pluginInfo = dynamicPluginManager.getPluginInfo(pluginId)
            val jarPath = pluginInfo?.jarPath

            if (jarPath == null) {
                logger.warn(LogCategory.SYSTEM, "Cannot reload - JAR path not found", mapOf("pluginId" to pluginId))
                return null
            }

            // Unload
            val unloadResult = dynamicPluginManager.uninstallPlugin(pluginId, force = true)
            if (unloadResult.isFailure) {
                logger.warn(LogCategory.SYSTEM, "Failed to unload for reload", mapOf("pluginId" to pluginId))
                return null
            }

            // Reload
            loadPlugin(jarPath)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception reloading plugin", error = e)
            null
        }
    }

    override fun getLoadedPlugins(): List<LoadedPluginInfo> {
        return try {
            val isAdmin = AuthStateManager.currentUser.value?.isAdmin == true
            dynamicPluginManager.getVisibleInstalledPlugins().map { info ->
                // Use manifest.canUnload instead of calling suspend checkCanUnload
                LoadedPluginInfo(
                    pluginId = info.manifest.pluginId,
                    displayName = info.manifest.displayName,
                    version = info.manifest.version,
                    description = info.manifest.description,
                    author = info.manifest.author,
                    url = info.manifest.url,
                    type = info.manifest.type.name.lowercase(),
                    apiVersion = info.manifest.apiVersion,
                    minBossVersion = info.manifest.minBossVersion,
                    isSystemPlugin = info.manifest.systemPlugin,
                    canUnload = info.manifest.canUnload,
                    loadPriority = info.manifest.loadPriority,
                    isEnabled = info.enabled,
                    healthy = info.state == PluginState.LOADED,
                    jarPath = info.jarPath,
                    installedAt = 0L,
                    requiresAdmin = info.manifest.requiresAdmin,
                    isIncompatible = PluginCrashRegistry.isIncompatible(info.manifest.pluginId)
                )
            }.filter { !it.requiresAdmin || isAdmin }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception getting loaded plugins", error = e)
            emptyList()
        }
    }

    override fun isPluginLoaded(pluginId: String): Boolean {
        return dynamicPluginManager.getPluginInfo(pluginId) != null
    }

    override fun getPluginsDirectory(): String {
        return PluginStoreSetup.getPluginDir().absolutePath
    }

    override fun getBundledPluginsDirectory(): String {
        return File(System.getProperty("user.dir"), "bundled-plugins").absolutePath
    }

    override fun isCurrentUserAdmin(): Boolean {
        return PluginStoreConfig.isAdmin
    }

    override suspend fun enablePlugin(pluginId: String): Boolean {
        return try {
            logger.info(LogCategory.SYSTEM, "Enabling plugin via delegate", mapOf("pluginId" to pluginId))
            val result = dynamicPluginManager.enablePlugin(pluginId)
            if (result.isSuccess) {
                PluginPersistence.setPluginEnabled(pluginId, true)
            }
            result.isSuccess
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception enabling plugin", error = e)
            false
        }
    }

    override suspend fun disablePlugin(pluginId: String): Boolean {
        return try {
            logger.info(LogCategory.SYSTEM, "Disabling plugin via delegate", mapOf("pluginId" to pluginId))
            val result = dynamicPluginManager.disablePlugin(pluginId)
            if (result.isSuccess) {
                PluginPersistence.setPluginEnabled(pluginId, false)
            }
            result.isSuccess
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception disabling plugin", error = e)
            false
        }
    }

    override fun getAccessToken(): String? {
        return PluginStoreConfig.accessToken
    }

    /**
     * True if the JAR at [jarPath] is the microkernel runtime. Checks the
     * filename against both naming conventions (Gradle `{prefix}-…` and
     * plugin-store `{pluginId-with-underscores}_…`) and falls back to a
     * manifest read for anything else that manages to slip through — this
     * is cheap (just reads one file inside the JAR) and it's the last line
     * of defense before the binary-compatibility validator.
     */
    private fun isMicrokernelRuntimeJar(jarPath: String): Boolean {
        val fileName = File(jarPath).name
        if (fileName.startsWith(MicrokernelRuntime.ARTIFACT_PREFIX)) return true
        val pluginIdPrefix = MicrokernelRuntime.PLUGIN_ID.replace('.', '_')
        if (fileName.startsWith(pluginIdPrefix)) return true
        return try {
            val manifest = ai.rever.boss.plugin.loader.PluginManifestReader.readFromJar(jarPath)
            manifest.pluginId == MicrokernelRuntime.PLUGIN_ID
        } catch (_: Exception) {
            false
        }
    }
}
