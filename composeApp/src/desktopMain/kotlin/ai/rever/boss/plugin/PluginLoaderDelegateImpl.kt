package ai.rever.boss.plugin

import ai.rever.boss.components.plugin.DynamicPluginManager
import ai.rever.boss.components.plugin.MicrokernelRuntime
import ai.rever.boss.components.window_panel.SplitViewStateRegistry
import ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
import ai.rever.boss.plugin.api.LoadedPluginInfo
import ai.rever.boss.plugin.api.PluginLoaderDelegate
import ai.rever.boss.plugin.api.PluginState
import ai.rever.boss.plugin.repository.remote.PluginStoreConfig
import ai.rever.boss.plugin.sandbox.TabSandboxRegistry
import ai.rever.boss.plugin.sandbox.ui.PluginCrashRegistry
import ai.rever.boss.services.auth.AuthStateManager
import ai.rever.boss.utils.ApplicationRestarter
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File
import javax.swing.SwingUtilities

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
            // Clean up the JAR that the installer just downloaded so it doesn't
            // linger in the plugins directory and confuse a future scan.
            runCatching { File(jarPath).delete() }
            logger.info(LogCategory.SYSTEM, "Refusing to install microkernel runtime as a plugin", mapOf(
                "jarPath" to jarPath
            ))
            throw IllegalArgumentException(
                "The Microkernel Runtime is a system component, not a user-installable plugin. " +
                    "It is managed automatically when Microkernel Mode is enabled — no manual install needed."
            )
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

    override fun getRunningInstanceCount(pluginId: String): Int {
        return findOpenTabs(pluginId).size
    }

    override suspend fun resetPluginInstances(pluginId: String): Int {
        return try {
            // Enumerate every open tab of this plugin across all panels/windows BEFORE
            // touching the loaded plugin, so the typeId → sandbox mapping is still intact.
            val tabs = findOpenTabs(pluginId)
            logger.info(LogCategory.SYSTEM, "Resetting plugin instances", mapOf(
                "pluginId" to pluginId,
                "instances" to tabs.size.toString()
            ))
            // Close the stale tab UIs on the EDT and wait for them to detach, then reload
            // so the freshly-installed version is what's loaded when the user reopens.
            // removeTabById is host-side and doesn't touch plugin classes.
            if (tabs.isNotEmpty()) {
                runOnEdtAndWait {
                    tabs.forEach { (component, tabId) ->
                        try {
                            component.removeTabById(tabId)
                        } catch (e: Throwable) {
                            logger.warn(LogCategory.SYSTEM, "removeTabById threw during reset", mapOf(
                                "pluginId" to pluginId,
                                "tabId" to tabId
                            ), e)
                        }
                    }
                }
            }
            reloadPlugin(pluginId)
            tabs.size
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception resetting plugin instances", error = e)
            0
        }
    }

    override fun restartApplication() {
        logger.info(LogCategory.SYSTEM, "Restarting application to apply plugin update")
        ApplicationRestarter.scheduleRestart()
    }

    /**
     * All currently-open tabs belonging to [pluginId], across every panel and window,
     * as (owning tabs component, tabId) pairs. A tab belongs to the plugin when its
     * type is sandboxed by that plugin (see [TabSandboxRegistry]). This counts inactive
     * (background) tabs too — not just the visible one in each panel.
     */
    private fun findOpenTabs(pluginId: String): List<Pair<BossTabsComponent, String>> {
        return SplitViewStateRegistry.getAllStates().values.flatMap { state ->
            state.getAllPanels().flatMap { panel ->
                val component = panel.tabsComponent
                component.tabsState.value.tabs
                    .filter { tab -> TabSandboxRegistry.getSandbox(tab.typeId)?.pluginId == pluginId }
                    .map { tab -> component to tab.id }
            }
        }
    }

    /** Run [block] on the Swing EDT and block until it completes. Safe to call off-EDT. */
    private fun runOnEdtAndWait(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block()
        else SwingUtilities.invokeAndWait(block)
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
