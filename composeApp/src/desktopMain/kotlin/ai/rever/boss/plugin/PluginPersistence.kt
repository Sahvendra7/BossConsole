package ai.rever.boss.plugin

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists installed plugin state to disk.
 *
 * Stores a JSON file in ~/.boss/plugins/installed.json that tracks:
 * - Which plugins are installed
 * - Their JAR paths
 * - Whether they are enabled
 */
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
        val enabled: Boolean = true
    )

    @Serializable
    data class InstalledPluginsConfig(
        val plugins: MutableList<InstalledPluginEntry> = mutableListOf()
    )

    private var config: InstalledPluginsConfig? = null

    /**
     * Load the installed plugins configuration from disk.
     */
    fun loadConfig(): InstalledPluginsConfig {
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
     * Save the configuration to disk.
     */
    private fun saveConfig() {
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
     * Add an installed plugin to the config.
     */
    fun addInstalledPlugin(pluginId: String, jarPath: String, enabled: Boolean = true) {
        val cfg = loadConfig()
        // Remove existing entry if present
        cfg.plugins.removeIf { it.pluginId == pluginId }
        // Add new entry
        cfg.plugins.add(InstalledPluginEntry(pluginId, jarPath, enabled))
        saveConfig()
        logger.info(LogCategory.SYSTEM, "Added plugin to installed config", mapOf(
            "pluginId" to pluginId,
            "jarPath" to jarPath
        ))
    }

    /**
     * Remove an installed plugin from the config.
     */
    fun removeInstalledPlugin(pluginId: String) {
        val cfg = loadConfig()
        val removed = cfg.plugins.removeIf { it.pluginId == pluginId }
        if (removed) {
            saveConfig()
            logger.info(LogCategory.SYSTEM, "Removed plugin from installed config", mapOf(
                "pluginId" to pluginId
            ))
        }
    }

    /**
     * Update the enabled state of a plugin.
     */
    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        val cfg = loadConfig()
        val entry = cfg.plugins.find { it.pluginId == pluginId }
        if (entry != null) {
            val index = cfg.plugins.indexOf(entry)
            cfg.plugins[index] = entry.copy(enabled = enabled)
            saveConfig()
            logger.debug(LogCategory.SYSTEM, "Updated plugin enabled state", mapOf(
                "pluginId" to pluginId,
                "enabled" to enabled
            ))
        }
    }

    /**
     * Get all installed plugins.
     */
    fun getInstalledPlugins(): List<InstalledPluginEntry> {
        return loadConfig().plugins.toList()
    }

    /**
     * Check if a plugin is installed.
     */
    fun isInstalled(pluginId: String): Boolean {
        return loadConfig().plugins.any { it.pluginId == pluginId }
    }

    /**
     * Clear all installed plugins (for testing).
     */
    fun clear() {
        config = InstalledPluginsConfig()
        saveConfig()
    }
}
