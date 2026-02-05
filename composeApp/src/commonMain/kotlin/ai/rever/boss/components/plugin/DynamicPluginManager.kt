package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.api.CanUnloadResult
import ai.rever.boss.plugin.api.DynamicPluginListener
import ai.rever.boss.plugin.api.LoadedPlugin
import ai.rever.boss.plugin.api.PanelRegistry
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.PluginManifest
import ai.rever.boss.plugin.api.PluginSandboxRef
import ai.rever.boss.plugin.api.PluginState
import ai.rever.boss.plugin.api.PluginUnloadAware
import ai.rever.boss.plugin.api.TabRegistry
import ai.rever.boss.plugin.loader.DynamicPluginLoader
import ai.rever.boss.plugin.loader.DynamicPluginLoaderImpl
import ai.rever.boss.plugin.loader.PluginUnloadException
import ai.rever.boss.plugin.sandbox.PluginSandboxManager
import ai.rever.boss.plugin.sandbox.SandboxConfig
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ai.rever.boss.services.auth.AuthStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Entry for a persisted plugin to be loaded on startup.
 */
data class PersistedPluginEntry(
    val pluginId: String,
    val jarPath: String,
    val enabled: Boolean
)

/**
 * Information about a dynamically managed plugin.
 */
data class DynamicPluginInfo(
    val manifest: PluginManifest,
    val jarPath: String,
    val state: PluginState,
    val loadedAt: Long,
    val enabled: Boolean,
    val errorMessage: String? = null
)

/**
 * Manager for dynamic plugin loading and unloading at runtime.
 *
 * This coordinates the full plugin lifecycle:
 * - Loading plugins from JAR files
 * - Creating sandboxed contexts with registration tracking
 * - Notifying listeners of lifecycle events
 * - Validating unload feasibility
 * - Cleaning up registrations on unload
 *
 * Follows IntelliJ IDEA patterns for dynamic plugin management.
 */
class DynamicPluginManager(
    private val panelRegistry: PanelRegistry,
    private val tabRegistry: TabRegistry,
    private val sandboxManager: PluginSandboxManager,
    private val createSandboxedContext: (pluginId: String, config: SandboxConfig) -> PluginContext
) {
    private val logger = BossLogger.forComponent("DynamicPluginManager")

    /**
     * Scope for manager operations.
     */
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Mutex for plugin operations to prevent race conditions.
     */
    private val mutex = Mutex()

    /**
     * The underlying plugin loader.
     */
    private val pluginLoader: DynamicPluginLoader = DynamicPluginLoaderImpl()

    /**
     * Tracks registrations by plugin for cleanup.
     */
    private val registrationTracker = PluginRegistrationTracker()

    /**
     * Tracking contexts by plugin ID.
     */
    private val trackingContexts = ConcurrentHashMap<String, TrackingPluginContext>()

    /**
     * Listeners for plugin lifecycle events.
     */
    private val listeners = CopyOnWriteArrayList<WeakReference<DynamicPluginListener>>()

    /**
     * Components that need to be notified before plugin unload.
     */
    private val unloadAwareComponents = CopyOnWriteArrayList<WeakReference<PluginUnloadAware>>()

    /**
     * Current state of all dynamic plugins.
     */
    private val _pluginStates = MutableStateFlow<Map<String, DynamicPluginInfo>>(emptyMap())
    val pluginStates: StateFlow<Map<String, DynamicPluginInfo>> = _pluginStates.asStateFlow()

    /**
     * Current admin status of the user.
     */
    private val _isAdmin = MutableStateFlow(false)

    /**
     * Admin-only plugins that are hidden due to non-admin status.
     * These plugins are loaded but not registered in the panel registry.
     */
    private val hiddenAdminPlugins = ConcurrentHashMap<String, DynamicPluginInfo>()

    init {
        // Observe admin status changes
        managerScope.launch(Dispatchers.Main) {
            AuthStateManager.currentUser
                .map { user -> user?.isAdmin == true }
                .distinctUntilChanged()
                .collect { isAdmin ->
                    val wasAdmin = _isAdmin.value
                    _isAdmin.value = isAdmin

                    // Only handle change if it actually changed
                    if (wasAdmin != isAdmin) {
                        handleAdminStatusChange(isAdmin)
                    }
                }
        }
    }

    /**
     * Add a listener for plugin lifecycle events.
     */
    fun addListener(listener: DynamicPluginListener) {
        cleanupDeadReferences(listeners)
        listeners.add(WeakReference(listener))
    }

    /**
     * Remove a listener.
     */
    fun removeListener(listener: DynamicPluginListener) {
        listeners.removeIf { it.get() == null || it.get() === listener }
    }

    /**
     * Register a component that needs to be notified before plugin unload.
     */
    fun registerUnloadAware(component: PluginUnloadAware) {
        cleanupDeadReferences(unloadAwareComponents)
        unloadAwareComponents.add(WeakReference(component))
    }

    /**
     * Unregister an unload-aware component.
     */
    fun unregisterUnloadAware(component: PluginUnloadAware) {
        unloadAwareComponents.removeIf { it.get() == null || it.get() === component }
    }

    /**
     * Install a plugin from a JAR file.
     *
     * @param jarPath Path to the plugin JAR
     * @param enabled Whether to enable the plugin after loading
     * @return Result containing the plugin info or an error
     */
    suspend fun installPlugin(jarPath: String, enabled: Boolean = true): Result<DynamicPluginInfo> {
        return mutex.withLock {
            try {
                logger.info(LogCategory.SYSTEM, "Installing plugin", mapOf(
                    "jarPath" to jarPath
                ))

                // Load the plugin
                val loadResult = pluginLoader.loadPlugin(jarPath)
                if (loadResult.isFailure) {
                    val error = loadResult.exceptionOrNull()
                    notifyListeners { it.pluginLoadFailed(null, error ?: Exception("Unknown error")) }
                    return@withLock Result.failure(error ?: Exception("Unknown error"))
                }

                val loadedPlugin = loadResult.getOrThrow()
                val manifest = loadedPlugin.manifest

                // Notify listeners before registration
                notifyListeners { it.beforePluginLoaded(manifest) }

                // Create tracking context
                val sandboxConfig = SandboxConfig(
                    maxThreads = manifest.sandbox.maxThreads,
                    maxRestartAttempts = manifest.sandbox.maxRestartAttempts,
                    heartbeatIntervalMs = manifest.sandbox.heartbeatIntervalMs
                )

                val baseContext = createSandboxedContext(manifest.pluginId, sandboxConfig)
                val trackingContext = TrackingPluginContext(
                    pluginId = manifest.pluginId,
                    delegate = baseContext,
                    tracker = registrationTracker,
                    pluginManifest = manifest
                )
                trackingContexts[manifest.pluginId] = trackingContext

                // Check if plugin requires admin and user is not admin
                val isAdmin = _isAdmin.value
                val shouldHideAdminPlugin = manifest.requiresAdmin && !isAdmin

                // Register the plugin (unless it's an admin-only plugin and user is not admin)
                if (enabled && !shouldHideAdminPlugin) {
                    try {
                        loadedPlugin.instance.register(trackingContext)
                    } catch (e: Exception) {
                        logger.error(LogCategory.SYSTEM, "Error registering plugin", mapOf(
                            "pluginId" to manifest.pluginId
                        ), e)

                        // Cleanup on failure
                        trackingContext.unregisterAll()
                        trackingContexts.remove(manifest.pluginId)
                        pluginLoader.unloadPlugin(manifest.pluginId)

                        notifyListeners { it.pluginLoadFailed(manifest, e) }
                        return@withLock Result.failure(e)
                    }
                }

                // Create plugin info
                val info = DynamicPluginInfo(
                    manifest = manifest,
                    jarPath = jarPath,
                    state = if (enabled && !shouldHideAdminPlugin) PluginState.LOADED else PluginState.DISABLED,
                    loadedAt = System.currentTimeMillis(),
                    enabled = enabled
                )

                // Track hidden admin plugins
                if (shouldHideAdminPlugin && enabled) {
                    hiddenAdminPlugins[manifest.pluginId] = info
                    logger.info(LogCategory.SYSTEM, "Admin-only plugin hidden (user is not admin)", mapOf(
                        "pluginId" to manifest.pluginId
                    ))
                }

                // Update state
                updatePluginState(manifest.pluginId, info)

                // Notify listeners
                notifyListeners { it.pluginLoaded(manifest) }

                logger.info(LogCategory.SYSTEM, "Plugin installed successfully", mapOf(
                    "pluginId" to manifest.pluginId,
                    "version" to manifest.version
                ))

                Result.success(info)
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Failed to install plugin", mapOf(
                    "jarPath" to jarPath
                ), e)
                Result.failure(e)
            }
        }
    }

    /**
     * Check if a plugin can be unloaded without issues.
     *
     * @param pluginId The plugin ID
     * @return Result indicating whether unloading is allowed
     */
    suspend fun checkCanUnload(pluginId: String): CanUnloadResult {
        val reasons = mutableListOf<String>()

        // Check with all unload-aware components
        for (ref in unloadAwareComponents) {
            val component = ref.get() ?: continue
            val result = component.checkCanUnload(pluginId)
            if (result is CanUnloadResult.NotAllowed) {
                reasons.addAll(result.reasons)
            }
        }

        // Check for dependent plugins
        val loadedPlugins = pluginLoader.getLoadedPlugins()
        for (plugin in loadedPlugins) {
            val hasDependency = plugin.manifest.dependencies.any { it.pluginId == pluginId }
            if (hasDependency) {
                reasons.add("Plugin '${plugin.manifest.displayName}' depends on this plugin")
            }
        }

        return if (reasons.isEmpty()) {
            CanUnloadResult.Ok
        } else {
            CanUnloadResult.NotAllowed(reasons)
        }
    }

    /**
     * Uninstall a plugin.
     *
     * @param pluginId The plugin ID
     * @param force Force unload even if checkCanUnload returns NotAllowed
     * @param waitForGC Whether to wait for classloader garbage collection
     * @return Result indicating success or failure
     */
    suspend fun uninstallPlugin(
        pluginId: String,
        force: Boolean = false,
        waitForGC: Boolean = false
    ): Result<Unit> {
        return mutex.withLock {
            try {
                logger.info(LogCategory.SYSTEM, "Uninstalling plugin", mapOf(
                    "pluginId" to pluginId,
                    "force" to force
                ))

                val loadedPlugin = pluginLoader.getPlugin(pluginId)
                if (loadedPlugin == null) {
                    return@withLock Result.failure(
                        PluginUnloadException("Plugin not found: $pluginId", pluginId)
                    )
                }

                val manifest = loadedPlugin.manifest

                // Check if unload is allowed
                if (!force) {
                    val canUnload = checkCanUnload(pluginId)
                    if (!canUnload.isAllowed) {
                        val reasons = (canUnload as CanUnloadResult.NotAllowed).reasons
                        return@withLock Result.failure(
                            PluginUnloadException(
                                "Cannot unload plugin: ${reasons.joinToString("; ")}",
                                pluginId,
                                reasons
                            )
                        )
                    }
                }

                // Notify listeners before unload
                notifyListeners { it.beforePluginUnload(manifest) }

                // Prepare unload-aware components
                for (ref in unloadAwareComponents) {
                    val component = ref.get() ?: continue
                    try {
                        component.prepareForUnload(pluginId)
                    } catch (e: Exception) {
                        logger.warn(LogCategory.SYSTEM, "Error preparing component for unload", mapOf(
                            "pluginId" to pluginId
                        ))
                    }
                }

                // Unregister all panels and tabs
                val trackingContext = trackingContexts.remove(pluginId)
                trackingContext?.unregisterAll()

                // Remove sandbox
                managerScope.launch {
                    sandboxManager.removeSandbox(pluginId)
                }

                // Unload the plugin
                val unloadResult = pluginLoader.unloadPlugin(pluginId, waitForGC)
                if (unloadResult.isFailure) {
                    notifyListeners { it.pluginUnloadFailed(manifest, unloadResult.exceptionOrNull()!!) }
                    return@withLock unloadResult
                }

                // Update state
                removePluginState(pluginId)

                // Notify listeners
                notifyListeners { it.pluginUnloaded(manifest) }

                logger.info(LogCategory.SYSTEM, "Plugin uninstalled successfully", mapOf(
                    "pluginId" to pluginId
                ))

                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Failed to uninstall plugin", mapOf(
                    "pluginId" to pluginId
                ), e)
                Result.failure(e)
            }
        }
    }

    /**
     * Enable a disabled plugin.
     *
     * @param pluginId The plugin ID
     * @return Result indicating success or failure
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit> {
        return mutex.withLock {
            try {
                val loadedPlugin = pluginLoader.getPlugin(pluginId)
                    ?: return@withLock Result.failure(Exception("Plugin not found: $pluginId"))

                val trackingContext = trackingContexts[pluginId]
                    ?: return@withLock Result.failure(Exception("No context for plugin: $pluginId"))

                // Register the plugin
                loadedPlugin.instance.register(trackingContext)

                // Enable sandbox
                sandboxManager.enablePlugin(pluginId)

                // Update state
                val currentInfo = _pluginStates.value[pluginId]
                if (currentInfo != null) {
                    updatePluginState(pluginId, currentInfo.copy(
                        state = PluginState.LOADED,
                        enabled = true
                    ))
                }

                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Failed to enable plugin", mapOf(
                    "pluginId" to pluginId
                ), e)
                Result.failure(e)
            }
        }
    }

    /**
     * Disable an enabled plugin without unloading it.
     *
     * @param pluginId The plugin ID
     * @return Result indicating success or failure
     */
    suspend fun disablePlugin(pluginId: String): Result<Unit> {
        return mutex.withLock {
            try {
                val trackingContext = trackingContexts[pluginId]
                    ?: return@withLock Result.failure(Exception("No context for plugin: $pluginId"))

                // Unregister all panels and tabs
                trackingContext.unregisterAll()

                // Disable sandbox
                sandboxManager.disablePlugin(pluginId)

                // Update state
                val currentInfo = _pluginStates.value[pluginId]
                if (currentInfo != null) {
                    updatePluginState(pluginId, currentInfo.copy(
                        state = PluginState.DISABLED,
                        enabled = false
                    ))
                }

                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Failed to disable plugin", mapOf(
                    "pluginId" to pluginId
                ), e)
                Result.failure(e)
            }
        }
    }

    /**
     * Get information about a plugin.
     */
    fun getPluginInfo(pluginId: String): DynamicPluginInfo? {
        return _pluginStates.value[pluginId]
    }

    /**
     * Get all installed plugins.
     */
    fun getInstalledPlugins(): List<DynamicPluginInfo> {
        return _pluginStates.value.values.toList()
    }

    /**
     * Check if a plugin is installed.
     */
    fun isInstalled(pluginId: String): Boolean {
        return _pluginStates.value.containsKey(pluginId)
    }

    /**
     * Get installed plugins visible to the current user.
     * Filters out admin-only plugins if the user is not an admin.
     */
    fun getVisibleInstalledPlugins(): List<DynamicPluginInfo> {
        val isAdmin = _isAdmin.value
        return _pluginStates.value.values.filter { info ->
            !info.manifest.requiresAdmin || isAdmin
        }
    }

    /**
     * Get the current admin status.
     */
    fun isCurrentUserAdmin(): Boolean {
        return _isAdmin.value
    }

    /**
     * Get the registration tracker.
     */
    fun getRegistrationTracker(): PluginRegistrationTracker = registrationTracker

    /**
     * Load plugins from persisted state.
     * This should be called during application startup to restore previously installed plugins.
     *
     * @param plugins List of plugin entries with JAR paths and enabled states
     * @return Map of plugin IDs to their load results
     */
    suspend fun loadPersistedPlugins(
        plugins: List<PersistedPluginEntry>
    ): Map<String, Result<DynamicPluginInfo>> {
        val results = mutableMapOf<String, Result<DynamicPluginInfo>>()

        logger.info(LogCategory.SYSTEM, "Loading persisted plugins", mapOf(
            "count" to plugins.size
        ))

        for (entry in plugins) {
            try {
                val jarFile = java.io.File(entry.jarPath)
                if (!jarFile.exists()) {
                    logger.warn(LogCategory.SYSTEM, "Persisted plugin JAR not found", mapOf(
                        "pluginId" to entry.pluginId,
                        "jarPath" to entry.jarPath
                    ))
                    results[entry.pluginId] = Result.failure(Exception("JAR file not found: ${entry.jarPath}"))
                    continue
                }

                val result = installPlugin(entry.jarPath, enabled = entry.enabled)
                results[entry.pluginId] = result

                if (result.isSuccess) {
                    logger.info(LogCategory.SYSTEM, "Loaded persisted plugin", mapOf(
                        "pluginId" to entry.pluginId,
                        "enabled" to entry.enabled
                    ))
                } else {
                    logger.error(LogCategory.SYSTEM, "Failed to load persisted plugin", mapOf(
                        "pluginId" to entry.pluginId,
                        "error" to (result.exceptionOrNull()?.message ?: "unknown")
                    ))
                }
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Exception loading persisted plugin", mapOf(
                    "pluginId" to entry.pluginId
                ), e)
                results[entry.pluginId] = Result.failure(e)
            }
        }

        logger.info(LogCategory.SYSTEM, "Finished loading persisted plugins", mapOf(
            "total" to plugins.size,
            "successful" to results.count { it.value.isSuccess },
            "failed" to results.count { it.value.isFailure }
        ))

        return results
    }

    /**
     * Dispose the manager and all plugins.
     */
    suspend fun dispose() {
        logger.info(LogCategory.SYSTEM, "Disposing DynamicPluginManager")

        // Uninstall all plugins
        for (pluginId in _pluginStates.value.keys.toList()) {
            uninstallPlugin(pluginId, force = true)
        }

        // Cancel scope
        managerScope.cancel()
    }

    /**
     * Handle admin status change - show/hide admin plugins accordingly.
     */
    private suspend fun handleAdminStatusChange(isAdmin: Boolean) {
        mutex.withLock {
            if (isAdmin) {
                // User became admin - re-register hidden admin plugins
                logger.info(LogCategory.SYSTEM, "User gained admin status, re-registering hidden admin plugins", mapOf(
                    "hiddenCount" to hiddenAdminPlugins.size
                ))

                for ((pluginId, info) in hiddenAdminPlugins) {
                    if (info.enabled) {
                        val trackingContext = trackingContexts[pluginId]
                        val loadedPlugin = pluginLoader.getPlugin(pluginId)

                        if (trackingContext != null && loadedPlugin != null) {
                            try {
                                loadedPlugin.instance.register(trackingContext)
                                // Update state to reflect active status
                                updatePluginState(pluginId, info.copy(state = PluginState.LOADED))
                                logger.info(LogCategory.SYSTEM, "Re-registered admin plugin", mapOf(
                                    "pluginId" to pluginId
                                ))
                            } catch (e: Exception) {
                                logger.error(LogCategory.SYSTEM, "Failed to re-register admin plugin", mapOf(
                                    "pluginId" to pluginId
                                ), e)
                            }
                        }
                    }
                }
                hiddenAdminPlugins.clear()
            } else {
                // User lost admin status - unregister and hide admin plugins
                logger.info(LogCategory.SYSTEM, "User lost admin status, hiding admin plugins")

                val adminPlugins = _pluginStates.value.filter { (_, info) ->
                    info.manifest.requiresAdmin && info.enabled
                }

                for ((pluginId, info) in adminPlugins) {
                    val trackingContext = trackingContexts[pluginId]
                    if (trackingContext != null) {
                        trackingContext.unregisterAll()
                        hiddenAdminPlugins[pluginId] = info
                        // Update state to reflect hidden status
                        updatePluginState(pluginId, info.copy(state = PluginState.DISABLED))
                        logger.info(LogCategory.SYSTEM, "Hidden admin plugin", mapOf(
                            "pluginId" to pluginId
                        ))
                    }
                }
            }
        }
    }

    private fun updatePluginState(pluginId: String, info: DynamicPluginInfo) {
        _pluginStates.value = _pluginStates.value + (pluginId to info)
    }

    private fun removePluginState(pluginId: String) {
        _pluginStates.value = _pluginStates.value - pluginId
    }

    private fun <T> cleanupDeadReferences(list: CopyOnWriteArrayList<WeakReference<T>>) {
        list.removeIf { it.get() == null }
    }

    private fun notifyListeners(action: (DynamicPluginListener) -> Unit) {
        listeners.removeIf { ref ->
            val listener = ref.get()
            if (listener != null) {
                try {
                    action(listener)
                } catch (e: Exception) {
                    logger.warn(LogCategory.SYSTEM, "Error notifying listener", mapOf(
                        "error" to (e.message ?: "unknown")
                    ))
                }
                false // Keep reference
            } else {
                true // Remove dead reference
            }
        }
    }
}
