package ai.rever.boss.plugin.loader

import ai.rever.boss.plugin.api.LoadedPlugin
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginManifest
import ai.rever.boss.plugin.api.PluginManifestConstants
import ai.rever.boss.plugin.api.PluginState
import ai.rever.boss.plugin.api.Version
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Interface for loading and unloading plugins dynamically.
 */
interface DynamicPluginLoader {
    /**
     * Load a plugin from a JAR file.
     *
     * @param jarPath Path to the plugin JAR
     * @return Result containing the loaded plugin or an error
     */
    suspend fun loadPlugin(jarPath: String): Result<LoadedPlugin>

    /**
     * Unload a plugin.
     *
     * @param pluginId The ID of the plugin to unload
     * @param waitForGC Whether to wait and verify classloader garbage collection
     * @return Result indicating success or failure
     */
    suspend fun unloadPlugin(pluginId: String, waitForGC: Boolean = false): Result<Unit>

    /**
     * Get a loaded plugin by ID.
     *
     * @param pluginId The plugin ID
     * @return The loaded plugin, or null if not found
     */
    fun getPlugin(pluginId: String): LoadedPlugin?

    /**
     * Get all loaded plugins.
     */
    fun getLoadedPlugins(): List<LoadedPlugin>

    /**
     * Check if a plugin is loaded.
     *
     * @param pluginId The plugin ID
     * @return True if the plugin is loaded
     */
    fun isLoaded(pluginId: String): Boolean
}

/**
 * Default implementation of [DynamicPluginLoader].
 *
 * This implementation uses isolated classloaders per plugin and
 * follows IntelliJ IDEA patterns for dynamic plugin management.
 */
class DynamicPluginLoaderImpl(
    private val classLoaderManager: PluginClassLoaderManager = PluginClassLoaderManager()
) : DynamicPluginLoader {

    private val logger = BossLogger.forComponent("DynamicPluginLoader")

    /**
     * Loaded plugins by ID.
     */
    private val loadedPlugins = ConcurrentHashMap<String, LoadedPlugin>()

    /**
     * Current BOSS application version. Must be set before loading plugins
     * that have minBossVersion requirements.
     */
    var currentBossVersion: String? = null

    // JAR reading, bytecode validation, classloading, and instantiation are
    // heavy; callers typically run on Dispatchers.Main, so keep it all on IO.
    override suspend fun loadPlugin(jarPath: String): Result<LoadedPlugin> = withContext(Dispatchers.IO) {
        try {
            logger.info(LogCategory.SYSTEM, "Loading plugin from JAR", mapOf(
                "jarPath" to jarPath
            ))

            // Read and validate manifest
            val manifest = PluginManifestReader.readFromJar(jarPath)
            val pluginId = manifest.pluginId

            // Check if already loaded
            if (loadedPlugins.containsKey(pluginId)) {
                return@withContext Result.failure(PluginLoadException(
                    "${PluginLoadException.ALREADY_LOADED_PREFIX}: $pluginId",
                    pluginId
                ))
            }

            // Check API version compatibility
            if (!isApiVersionCompatible(manifest.apiVersion)) {
                return@withContext Result.failure(PluginApiVersionException(
                    "Plugin requires API version ${manifest.apiVersion}, but current version is ${PluginManifestConstants.CURRENT_API_VERSION}",
                    pluginId,
                    manifest.apiVersion,
                    PluginManifestConstants.CURRENT_API_VERSION
                ))
            }

            // Check minimum BOSS version compatibility
            val minBossVersion = manifest.minBossVersion
            if (!minBossVersion.isNullOrBlank()) {
                val currentVersion = currentBossVersion
                if (currentVersion == null) {
                    logger.warn(LogCategory.SYSTEM, "Skipping minBossVersion validation - currentBossVersion not set", mapOf(
                        "pluginId" to pluginId,
                        "requiredVersion" to minBossVersion
                    ))
                } else if (!isBossVersionCompatible(minBossVersion, currentVersion)) {
                    return@withContext Result.failure(PluginBossVersionException(
                        "Plugin requires BOSS version $minBossVersion or later, but current version is $currentVersion",
                        pluginId,
                        minBossVersion,
                        currentVersion
                    ))
                }
            }

            // Create classloader
            val classLoader = classLoaderManager.createClassLoader(manifest, jarPath)

            // Binary compatibility check
            val validation = BinaryCompatibilityValidator.validate(classLoader, jarPath)
            if (!validation.isCompatible) {
                classLoaderManager.closeClassLoader(pluginId, classLoader)
                return@withContext Result.failure(PluginBinaryIncompatibilityException(
                    "Plugin '$pluginId' has binary incompatibilities: ${validation.errors.first()}",
                    pluginId,
                    manifest
                ))
            }

            // Load main class
            val pluginClass = try {
                classLoader.loadClass(manifest.mainClass)
            } catch (e: ClassNotFoundException) {
                classLoaderManager.closeClassLoader(pluginId, classLoader)
                return@withContext Result.failure(PluginClassException(
                    "Plugin main class not found: ${manifest.mainClass}",
                    pluginId,
                    manifest.mainClass,
                    e
                ))
            }

            // Verify it implements Plugin interface
            if (!Plugin::class.java.isAssignableFrom(pluginClass)) {
                classLoaderManager.closeClassLoader(pluginId, classLoader)
                return@withContext Result.failure(PluginClassException(
                    "Main class does not implement Plugin interface: ${manifest.mainClass}",
                    pluginId,
                    manifest.mainClass
                ))
            }

            // Instantiate plugin
            val pluginInstance = try {
                // Try to get singleton instance (Kotlin object) first
                val instanceField = try {
                    pluginClass.getDeclaredField("INSTANCE")
                } catch (e: NoSuchFieldException) {
                    null
                }

                if (instanceField != null) {
                    instanceField.isAccessible = true
                    instanceField.get(null) as Plugin
                } else {
                    // Try no-arg constructor
                    pluginClass.getDeclaredConstructor().newInstance() as Plugin
                }
            } catch (e: Exception) {
                classLoaderManager.closeClassLoader(pluginId, classLoader)
                return@withContext Result.failure(PluginClassException(
                    "Failed to instantiate plugin: ${e.message}",
                    pluginId,
                    manifest.mainClass,
                    e
                ))
            }

            // Create loaded plugin record
            val loadedPlugin = LoadedPlugin(
                manifest = manifest,
                instance = pluginInstance,
                classLoader = classLoader,
                jarPath = jarPath,
                state = PluginState.LOADED
            )

            loadedPlugins[pluginId] = loadedPlugin

            logger.info(LogCategory.SYSTEM, "Plugin loaded successfully", mapOf(
                "pluginId" to pluginId,
                "version" to manifest.version,
                "mainClass" to manifest.mainClass
            ))

            Result.success(loadedPlugin)
        } catch (e: PluginLoadException) {
            logger.error(LogCategory.SYSTEM, "Failed to load plugin", mapOf(
                "jarPath" to jarPath,
                "error" to (e.message ?: "unknown")
            ), e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Unexpected error loading plugin", mapOf(
                "jarPath" to jarPath
            ), e)
            Result.failure(PluginLoadException(
                "Unexpected error loading plugin: ${e.message}",
                cause = e
            ))
        }
    }

    override suspend fun unloadPlugin(pluginId: String, waitForGC: Boolean): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Unloading plugin", mapOf(
                "pluginId" to pluginId,
                "waitForGC" to waitForGC
            ))

            val loadedPlugin = loadedPlugins[pluginId]
                ?: return Result.failure(PluginLoadException(
                    "Plugin not found: $pluginId",
                    pluginId
                ))

            // Check if the plugin can be unloaded (system plugins may be protected)
            if (!loadedPlugin.manifest.canUnload) {
                logger.warn(LogCategory.SYSTEM, "Cannot unload system plugin", mapOf(
                    "pluginId" to pluginId,
                    "systemPlugin" to loadedPlugin.manifest.systemPlugin
                ))
                return Result.failure(PluginUnloadException(
                    "Cannot unload system plugin: $pluginId (canUnload=false)",
                    pluginId,
                    listOf("System plugin is protected from unloading")
                ))
            }

            // Update state
            loadedPlugins[pluginId] = loadedPlugin.copy(state = PluginState.UNLOADING)

            // Dispose plugin instance
            try {
                loadedPlugin.instance.dispose()
            } catch (e: Exception) {
                logger.warn(LogCategory.SYSTEM, "Error disposing plugin", mapOf(
                    "pluginId" to pluginId,
                    "error" to (e.message ?: "unknown")
                ))
            }

            // Remove from loaded plugins
            loadedPlugins.remove(pluginId)

            // Prepare classloader for unload
            val classLoader = classLoaderManager.prepareUnload(pluginId)
            if (classLoader != null) {
                classLoaderManager.closeClassLoader(pluginId, classLoader)

                // Optionally wait for GC
                if (waitForGC) {
                    val gcRef = classLoaderManager.getUnloadingReference(pluginId)
                    if (gcRef != null) {
                        val gcResult = ClassLoaderGCWatcher.waitForGC(pluginId, gcRef)
                        if (!gcResult.isSuccess) {
                            logger.warn(LogCategory.SYSTEM, "Classloader may not have been garbage collected", mapOf(
                                "pluginId" to pluginId
                            ))
                        }
                    }
                }
            }

            logger.info(LogCategory.SYSTEM, "Plugin unloaded successfully", mapOf(
                "pluginId" to pluginId
            ))

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Error unloading plugin", mapOf(
                "pluginId" to pluginId
            ), e)
            Result.failure(PluginUnloadException(
                "Error unloading plugin: ${e.message}",
                pluginId,
                cause = e
            ))
        }
    }

    override fun getPlugin(pluginId: String): LoadedPlugin? {
        return loadedPlugins[pluginId]
    }

    override fun getLoadedPlugins(): List<LoadedPlugin> {
        return loadedPlugins.values.toList()
    }

    override fun isLoaded(pluginId: String): Boolean {
        return loadedPlugins.containsKey(pluginId)
    }

    /**
     * Check if the plugin's API version is compatible with the current version.
     */
    private fun isApiVersionCompatible(requiredVersion: String): Boolean {
        val required = parseVersion(requiredVersion)
        val current = parseVersion(PluginManifestConstants.CURRENT_API_VERSION)

        // Major version must match, and current minor must be >= required
        return required.first == current.first && current.second >= required.second
    }

    /**
     * Parse a version string into (major, minor) pair.
     */
    private fun parseVersion(version: String): Pair<Int, Int> {
        val parts = version.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major to minor
    }

    /**
     * Check if the current BOSS version meets the plugin's minimum version requirement.
     * Uses semantic versioning comparison with proper prerelease handling.
     *
     * Note: If version parsing fails, the plugin is allowed to load with a warning logged.
     * This "fail-open" approach prevents blocking plugins due to malformed version strings,
     * while still logging the issue for investigation.
     */
    private fun isBossVersionCompatible(requiredVersion: String, currentVersion: String): Boolean {
        val required = Version.parse(requiredVersion)
        if (required == null) {
            logger.warn(LogCategory.SYSTEM, "Failed to parse required version, allowing plugin", mapOf(
                "requiredVersion" to requiredVersion
            ))
            return true
        }

        val current = Version.parse(currentVersion)
        if (current == null) {
            logger.warn(LogCategory.SYSTEM, "Failed to parse current version, allowing plugin", mapOf(
                "currentVersion" to currentVersion
            ))
            return true
        }

        return current >= required
    }

    /**
     * Get the classloader manager for advanced operations.
     */
    fun getClassLoaderManager(): PluginClassLoaderManager = classLoaderManager

    /**
     * Load all bundled plugins from a directory.
     *
     * Bundled plugins are system plugins that ship with BossConsole.
     * They are loaded in priority order (lower loadPriority values load first).
     *
     * @param bundledDir Directory containing bundled plugin JARs
     * @return List of successfully loaded plugins, sorted by load priority
     */
    suspend fun loadBundledPlugins(bundledDir: java.io.File): List<LoadedPlugin> {
        if (!bundledDir.exists() || !bundledDir.isDirectory) {
            logger.debug(LogCategory.SYSTEM, "Bundled plugins directory not found", mapOf(
                "path" to bundledDir.absolutePath
            ))
            return emptyList()
        }

        val jarFiles = bundledDir.listFiles { file ->
            file.isFile && file.extension == "jar"
        } ?: emptyArray()

        if (jarFiles.isEmpty()) {
            logger.debug(LogCategory.SYSTEM, "No bundled plugins found", mapOf(
                "path" to bundledDir.absolutePath
            ))
            return emptyList()
        }

        logger.info(LogCategory.SYSTEM, "Loading bundled plugins", mapOf(
            "count" to jarFiles.size,
            "path" to bundledDir.absolutePath
        ))

        // Load plugins and collect successful ones
        val loadedBundled = mutableListOf<LoadedPlugin>()
        for (jarFile in jarFiles) {
            try {
                val result = loadPlugin(jarFile.absolutePath)
                if (result.isSuccess) {
                    loadedBundled.add(result.getOrThrow())
                } else {
                    logger.error(LogCategory.SYSTEM, "Failed to load bundled plugin", mapOf(
                        "file" to jarFile.name,
                        "error" to (result.exceptionOrNull()?.message ?: "unknown")
                    ))
                }
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Exception loading bundled plugin", mapOf(
                    "file" to jarFile.name
                ), e)
            }
        }

        // Sort by load priority (lower values first)
        return loadedBundled.sortedBy { it.manifest.loadPriority }
    }

    /**
     * Check if a plugin is a system/bundled plugin.
     *
     * @param pluginId The plugin ID to check
     * @return True if the plugin is a system plugin
     */
    fun isSystemPlugin(pluginId: String): Boolean {
        return loadedPlugins[pluginId]?.manifest?.systemPlugin == true
    }

    /**
     * Check if a plugin can be unloaded.
     *
     * @param pluginId The plugin ID to check
     * @return True if the plugin can be unloaded
     */
    fun canUnloadPlugin(pluginId: String): Boolean {
        return loadedPlugins[pluginId]?.manifest?.canUnload != false
    }

    /**
     * Dispose all loaded plugins and classloaders.
     */
    suspend fun disposeAll() {
        logger.info(LogCategory.SYSTEM, "Disposing all plugins", mapOf(
            "count" to loadedPlugins.size
        ))

        // Unload all plugins
        for (pluginId in loadedPlugins.keys.toList()) {
            unloadPlugin(pluginId, waitForGC = false)
        }

        classLoaderManager.disposeAll()
    }
}
