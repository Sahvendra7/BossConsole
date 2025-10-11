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
            // Try multiple locations where local.properties might be
            val possibleLocations = listOf(
                File("local.properties"),  // Current directory
                File("../local.properties"),  // Parent directory (when running from composeApp)
                File(System.getProperty("user.dir"), "local.properties"),
                File(System.getProperty("user.dir"), "../local.properties")
            )
            
            for (localPropertiesFile in possibleLocations) {
                if (localPropertiesFile.exists()) {
                    println("Loading local.properties from: ${localPropertiesFile.absolutePath}")
                    FileInputStream(localPropertiesFile).use { input ->
                        properties.load(input)
                    }
                    println("Loaded ${properties.size} properties from local.properties")
                    // Debug: Show if Supabase keys are loaded
                    if (properties.containsKey("SUPABASE_URL")) {
                        println("  - SUPABASE_URL found in local.properties")
                    }
                    if (properties.containsKey("SUPABASE_ANON_KEY")) {
                        println("  - SUPABASE_ANON_KEY found in local.properties")
                    }
                    if (properties.containsKey("SUPABASE_FUNCTION_URL")) {
                        println("  - SUPABASE_FUNCTION_URL found in local.properties")
                    }
                    return  // Stop after finding the first one
                }
            }
            
            println("Warning: local.properties not found in any of the expected locations")
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