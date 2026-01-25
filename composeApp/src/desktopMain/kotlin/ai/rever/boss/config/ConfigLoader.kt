package ai.rever.boss.config

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File
import java.io.FileInputStream
import java.util.*

/**
 * Utility object for loading configuration from various sources.
 */
object ConfigLoader {
    private val logger = BossLogger.forComponent("ConfigLoader")
    private val properties = Properties()
    
    init {
        loadLocalProperties()
    }
    
    /**
     * Loads properties from local.properties file if it exists.
     * This file should not be committed to version control.
     */
    private fun loadLocalProperties() {
        try {
            // Try multiple locations where local.properties might be
            val possibleLocations = listOf(
                File("local.properties"),  // Current directory
                File("../local.properties"),  // Parent directory (when running from composeApp)
                File(System.getProperty("user.dir"), "local.properties"),
                File(System.getProperty("user.dir"), "../local.properties")
            )
            
            for (localPropertiesFile in possibleLocations) {
                if (localPropertiesFile.exists()) {
                    logger.debug(LogCategory.SYSTEM, "Loading local.properties", mapOf("path" to localPropertiesFile.absolutePath))
                    FileInputStream(localPropertiesFile).use { input ->
                        properties.load(input)
                    }
                    logger.debug(LogCategory.SYSTEM, "Loaded properties from local.properties", mapOf(
                        "count" to properties.size,
                        "hasSupabaseUrl" to properties.containsKey("SUPABASE_URL"),
                        "hasSupabaseAnonKey" to properties.containsKey("SUPABASE_ANON_KEY"),
                        "hasSupabaseFunctionUrl" to properties.containsKey("SUPABASE_FUNCTION_URL")
                    ))
                    return  // Stop after finding the first one
                }
            }

            logger.warn(LogCategory.SYSTEM, "local.properties not found in any of the expected locations")
        } catch (e: Exception) {
            // Silently ignore if file doesn't exist or can't be read
            logger.warn(LogCategory.SYSTEM, "Could not load local.properties", error = e)
        }
    }
    
    /**
     * Gets a configuration value from the following sources in order:
     * 1. System environment variable
     * 2. System property
     * 3. local.properties file
     * 4. Default value
     */
    fun getConfig(key: String, defaultValue: String? = null): String? {
        // First try environment variable
        System.getenv(key)?.let { return it }
        
        // Then try system property
        System.getProperty(key)?.let { return it }
        
        // Then try local.properties
        properties.getProperty(key)?.let { return it }
        
        // Finally return default
        return defaultValue
    }
}
