package ai.rever.boss.components.configuration

import ai.rever.boss.utils.SystemUtils

/**
 * Manages file-based configuration storage
 */
expect class ConfigurationFileManager() {
    /**
     * Get the default configuration directory path
     */
    fun getDefaultConfigurationDirectory(): String
    
    /**
     * Ensure the configuration directory exists
     */
    suspend fun ensureConfigurationDirectory(): Boolean
    
    /**
     * Save a configuration to a file
     */
    suspend fun saveConfiguration(config: LayoutConfiguration, fileName: String? = null): String?
    
    /**
     * Load a configuration from a file
     */
    suspend fun loadConfiguration(fileName: String): LayoutConfiguration?
    
    /**
     * List all saved configuration files
     */
    suspend fun listConfigurations(): List<ConfigurationFileInfo>
    
    /**
     * Delete a configuration file
     */
    suspend fun deleteConfiguration(fileName: String): Boolean
    
    /**
     * Get full path for a configuration file
     */
    fun getConfigurationFilePath(fileName: String): String
}

/**
 * Information about a configuration file
 */
data class ConfigurationFileInfo(
    val fileName: String,
    val filePath: String,
    val lastModified: Long,
    val size: Long
)

/**
 * Common configuration file manager functionality
 */
object ConfigurationFileManagerCommon {
    /**
     * Get the default configuration directory name
     */
    fun getDefaultConfigDirectoryName(): String = "BOSS/configurations"
    
    /**
     * Generate a filename from configuration name
     */
    fun generateFileName(configName: String): String {
        // Replace spaces and special characters with underscores
        val sanitized = configName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return "$sanitized.json"
    }
    
    /**
     * Extract configuration name from filename
     */
    fun extractConfigName(fileName: String): String {
        return fileName.removeSuffix(".json").replace("_", " ")
    }
}