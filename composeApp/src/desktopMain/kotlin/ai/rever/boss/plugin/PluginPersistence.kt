package ai.rever.boss.plugin

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object PluginPersistence {
    private val logger = BossLogger.forComponent("PluginPersistence")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val configFile: File by lazy {
        File(PluginStoreSetup.getPluginDir(), "installed.json")
    }

    @Serializable
    data class InstalledPluginEntry(
        val pluginId: String,
        val jarPath: String,
        val enabled: Boolean = true,
        val sourceUrl: String? = null,
        val installedVersion: String? = null
    )

    @Serializable
    data class InstalledPluginsConfig(
        val plugins: MutableList<InstalledPluginEntry> = mutableListOf()
    )

    private var config: InstalledPluginsConfig? = null

    // Lock for synchronizing config access to prevent race conditions
    private val configLock = Any()

    /**
     * Internal load without synchronization - for use inside synchronized blocks.
     */
    private fun loadConfigInternal(): InstalledPluginsConfig {
        if (config != null) return config!!

        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                config = json.decodeFromString<InstalledPluginsConfig>(content)
                logger.info(LogCategory.SYSTEM, "Loaded installed plugins config", mapOf(
                    "count" to (config?.plugins?.size ?: 0)
                ))
                config!!
            } else {
                logger.debug(LogCategory.SYSTEM, "No installed plugins config found, creating new")
                config = InstalledPluginsConfig()
                config!!
            }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Failed to load installed plugins config", error = e)
            config = InstalledPluginsConfig()
            config!!
        }
    }

    /**
     * Internal save without synchronization - for use inside synchronized blocks.
     */
    private fun saveConfigInternal() {
        try {
            val cfg = config ?: return
            configFile.parentFile?.mkdirs()
            configFile.writeText(json.encodeToString(cfg))
            logger.debug(LogCategory.SYSTEM, "Saved installed plugins config", mapOf(
                "count" to cfg.plugins.size
            ))
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Failed to save installed plugins config", error = e)
        }
    }

    /**
     * Load the installed plugins configuration from disk.
     */
    fun loadConfig(): InstalledPluginsConfig {
        synchronized(configLock) {
            return loadConfigInternal()
        }
    }

    /**
     * Save the configuration to disk.
     */
    private fun saveConfig() {
        synchronized(configLock) {
            saveConfigInternal()
        }
    }

    /**
     * Add an installed plugin to the config.
     * Thread-safe: entire read-modify-write operation is atomic.
     */
    fun addInstalledPlugin(
        pluginId: String,
        jarPath: String,
        enabled: Boolean = true,
        sourceUrl: String? = null,
        installedVersion: String? = null
    ) {
        synchronized(configLock) {
            val cfg = loadConfigInternal()
            // Remove existing entry if present
            cfg.plugins.removeIf { it.pluginId == pluginId }
            // Add new entry
            cfg.plugins.add(InstalledPluginEntry(pluginId, jarPath, enabled, sourceUrl, installedVersion))
            saveConfigInternal()
            logger.info(LogCategory.SYSTEM, "Added plugin to installed config", mapOf(
                "pluginId" to pluginId,
                "jarPath" to jarPath,
                "sourceUrl" to (sourceUrl ?: "none")
            ))
        }
    }

    /**
     * Get the source URL for an installed plugin.
     */
    fun getSourceUrl(pluginId: String): String? {
        synchronized(configLock) {
            return loadConfigInternal().plugins.find { it.pluginId == pluginId }?.sourceUrl
        }
    }

    /**
     * Update source URL for an installed plugin.
     * Thread-safe: entire read-modify-write operation is atomic.
     */
    fun updateSourceUrl(pluginId: String, sourceUrl: String) {
        synchronized(configLock) {
            val cfg = loadConfigInternal()
            val entry = cfg.plugins.find { it.pluginId == pluginId }
            if (entry != null) {
                val index = cfg.plugins.indexOf(entry)
                cfg.plugins[index] = entry.copy(sourceUrl = sourceUrl)
                saveConfigInternal()
            }
        }
    }

    /**
     * Remove an installed plugin from the config.
     * Thread-safe: entire read-modify-write operation is atomic.
     */
    fun removeInstalledPlugin(pluginId: String) {
        synchronized(configLock) {
            val cfg = loadConfigInternal()
            val removed = cfg.plugins.removeIf { it.pluginId == pluginId }
            if (removed) {
                saveConfigInternal()
                logger.info(LogCategory.SYSTEM, "Removed plugin from installed config", mapOf(
                    "pluginId" to pluginId
                ))
            }
        }
    }

    /**
     * Update the enabled state of a plugin.
     * Thread-safe: entire read-modify-write operation is atomic.
     */
    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        synchronized(configLock) {
            val cfg = loadConfigInternal()
            val entry = cfg.plugins.find { it.pluginId == pluginId }
            if (entry != null) {
                val index = cfg.plugins.indexOf(entry)
                cfg.plugins[index] = entry.copy(enabled = enabled)
                saveConfigInternal()
                logger.debug(LogCategory.SYSTEM, "Updated plugin enabled state", mapOf(
                    "pluginId" to pluginId,
                    "enabled" to enabled
                ))
            }
        }
    }

    /**
     * Get all installed plugins.
     */
    fun getInstalledPlugins(): List<InstalledPluginEntry> {
        synchronized(configLock) {
            return loadConfigInternal().plugins.toList()
        }
    }

    /**
     * Check if a plugin is installed.
     */
    fun isInstalled(pluginId: String): Boolean {
        synchronized(configLock) {
            return loadConfigInternal().plugins.any { it.pluginId == pluginId }
        }
    }

    /**
     * Clear all installed plugins (for testing).
     * Thread-safe: entire operation is atomic.
     */
    fun clear() {
        synchronized(configLock) {
            config = InstalledPluginsConfig()
            saveConfigInternal()
        }
    }
}
