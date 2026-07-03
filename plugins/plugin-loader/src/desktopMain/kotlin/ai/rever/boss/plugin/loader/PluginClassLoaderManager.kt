package ai.rever.boss.plugin.loader

import ai.rever.boss.plugin.api.PluginManifest
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import java.io.File
import java.lang.ref.WeakReference
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages classloaders for dynamically loaded plugins.
 *
 * Each plugin gets its own isolated classloader. This manager tracks
 * active classloaders and provides lifecycle management including
 * proper cleanup and garbage collection verification.
 */
class PluginClassLoaderManager(
    /**
     * Parent classloader for all plugin classloaders.
     * Usually the application classloader.
     */
    private val parentClassLoader: ClassLoader = PluginClassLoaderManager::class.java.classLoader
) {
    private val logger = BossLogger.forComponent("PluginClassLoaderManager")

    /**
     * Active classloaders by plugin ID.
     */
    private val activeClassLoaders = ConcurrentHashMap<String, PluginClassLoader>()

    /**
     * Weak references to classloaders being unloaded (for GC verification).
     */
    private val unloadingClassLoaders = ConcurrentHashMap<String, WeakReference<PluginClassLoader>>()

    /**
     * Get the number of active classloaders.
     */
    val activeCount: Int get() = activeClassLoaders.size

    /**
     * Get the number of classloaders being unloaded.
     */
    val unloadingCount: Int get() = unloadingClassLoaders.size

    /**
     * Create a new classloader for a plugin.
     *
     * @param manifest The plugin manifest
     * @param jarPath Path to the plugin JAR file
     * @param dependencyJars Additional dependency JARs to include in the classpath
     * @return The created classloader
     * @throws PluginLoadException if a classloader already exists for this plugin
     */
    fun createClassLoader(
        manifest: PluginManifest,
        jarPath: String,
        dependencyJars: List<String> = emptyList()
    ): PluginClassLoader {
        val pluginId = manifest.pluginId

        if (activeClassLoaders.containsKey(pluginId)) {
            throw PluginLoadException(
                "Classloader already exists for plugin: $pluginId",
                pluginId
            )
        }

        logger.info(LogCategory.SYSTEM, "Creating classloader for plugin", mapOf(
            "pluginId" to pluginId,
            "jarPath" to jarPath,
            "dependencies" to dependencyJars.size
        ))

        // Build URLs for the classloader
        val urls = mutableListOf<URL>()

        // Add main JAR
        val mainJar = File(jarPath)
        if (!mainJar.exists()) {
            throw PluginLoadException(
                "Plugin JAR not found: $jarPath",
                pluginId
            )
        }
        urls.add(mainJar.toURI().toURL())

        // Add dependency JARs
        for (depPath in dependencyJars) {
            val depJar = File(depPath)
            if (depJar.exists()) {
                urls.add(depJar.toURI().toURL())
            } else {
                logger.warn(LogCategory.SYSTEM, "Dependency JAR not found", mapOf(
                    "pluginId" to pluginId,
                    "path" to depPath
                ))
            }
        }

        // Combine default shared packages with manifest-specified packages
        val sharedPackages = PluginClassLoader.defaultSharedPackages +
            manifest.sharedPackages.map { if (it.endsWith(".")) it else "$it." }.toSet()

        // Create the classloader
        val classLoader = PluginClassLoader(
            pluginId = pluginId,
            urls = urls.toTypedArray(),
            parent = parentClassLoader,
            sharedPackages = sharedPackages
        )

        activeClassLoaders[pluginId] = classLoader
        return classLoader
    }

    /**
     * Get the classloader for a plugin.
     *
     * @param pluginId The plugin ID
     * @return The classloader, or null if not found
     */
    fun getClassLoader(pluginId: String): PluginClassLoader? {
        return activeClassLoaders[pluginId]
    }

    /**
     * Check if a classloader exists for a plugin.
     *
     * @param pluginId The plugin ID
     * @return True if a classloader exists
     */
    fun hasClassLoader(pluginId: String): Boolean {
        return activeClassLoaders.containsKey(pluginId)
    }

    /**
     * Prepare a classloader for unloading.
     *
     * This marks the classloader as unloading and removes it from the active set.
     * The classloader should be closed after this.
     *
     * @param pluginId The plugin ID
     * @return The classloader to close, or null if not found
     */
    fun prepareUnload(pluginId: String): PluginClassLoader? {
        val classLoader = activeClassLoaders.remove(pluginId) ?: return null

        logger.info(LogCategory.SYSTEM, "Preparing classloader for unload", mapOf(
            "pluginId" to pluginId
        ))

        // Mark as unloading
        classLoader.markUnloading()

        // Keep weak reference for GC verification
        unloadingClassLoaders[pluginId] = WeakReference(classLoader)

        return classLoader
    }

    /**
     * Close and dispose a classloader.
     *
     * This should be called after plugin cleanup is complete.
     *
     * @param pluginId The plugin ID
     * @param classLoader The classloader to close
     */
    fun closeClassLoader(pluginId: String, classLoader: PluginClassLoader) {
        logger.info(LogCategory.SYSTEM, "Closing classloader", mapOf(
            "pluginId" to pluginId
        ))

        // Un-register before closing: leaving the entry in activeClassLoaders
        // permanently blocked the pluginId ("Classloader already exists") after
        // any load failure past classloader creation — e.g. a binary-incompatible
        // update — until app restart. Two-arg remove so a newer registration for
        // the same id is never clobbered by a late close of an older loader.
        activeClassLoaders.remove(pluginId, classLoader)

        try {
            classLoader.close()
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Error closing classloader", mapOf(
                "pluginId" to pluginId
            ), e)
        }
    }

    /**
     * Get the weak reference for verifying GC of an unloaded classloader.
     *
     * @param pluginId The plugin ID
     * @return Weak reference to the classloader, or null if not being tracked
     */
    fun getUnloadingReference(pluginId: String): WeakReference<PluginClassLoader>? {
        return unloadingClassLoaders[pluginId]
    }

    /**
     * Check if a classloader has been garbage collected.
     *
     * @param pluginId The plugin ID
     * @return True if the classloader was collected, false if still referenced or not tracked
     */
    fun isCollected(pluginId: String): Boolean {
        val ref = unloadingClassLoaders[pluginId] ?: return false
        val isCollected = ref.get() == null

        if (isCollected) {
            // Clean up the weak reference
            unloadingClassLoaders.remove(pluginId)
        }

        return isCollected
    }

    /**
     * Get all active plugin IDs.
     */
    fun getActivePluginIds(): Set<String> = activeClassLoaders.keys.toSet()

    /**
     * Get all classloader states for monitoring.
     */
    fun getStates(): Map<String, ClassLoaderState> {
        val states = mutableMapOf<String, ClassLoaderState>()

        // Active classloaders
        for ((pluginId, classLoader) in activeClassLoaders) {
            states[pluginId] = classLoader.state
        }

        // Unloading classloaders
        for ((pluginId, ref) in unloadingClassLoaders) {
            if (!states.containsKey(pluginId)) {
                val classLoader = ref.get()
                states[pluginId] = classLoader?.state ?: ClassLoaderState.UNLOADED
            }
        }

        return states
    }

    /**
     * Dispose all classloaders.
     *
     * This should be called when shutting down the plugin system.
     */
    fun disposeAll() {
        logger.info(LogCategory.SYSTEM, "Disposing all classloaders", mapOf(
            "count" to activeClassLoaders.size
        ))

        for ((pluginId, classLoader) in activeClassLoaders) {
            try {
                classLoader.close()
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Error disposing classloader", mapOf(
                    "pluginId" to pluginId
                ), e)
            }
        }

        activeClassLoaders.clear()
        unloadingClassLoaders.clear()
    }
}
