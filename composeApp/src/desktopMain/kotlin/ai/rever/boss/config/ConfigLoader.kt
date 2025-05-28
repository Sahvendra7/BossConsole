package ai.rever.boss.config

import java.io.File
import java.io.FileInputStream
import java.util.*

/**
 * Utility object for loading configuration from various sources.
 */
object ConfigLoader {
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
            val localPropertiesFile = File("local.properties")
            if (localPropertiesFile.exists()) {
                FileInputStream(localPropertiesFile).use { input ->
                    properties.load(input)
                }
            }
        } catch (e: Exception) {
            // Silently ignore if file doesn't exist or can't be read
            println("Warning: Could not load local.properties: ${e.message}")
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