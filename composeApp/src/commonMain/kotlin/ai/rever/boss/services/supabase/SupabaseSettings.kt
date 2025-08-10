package ai.rever.boss.services.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Settings manager for Supabase configuration
 */
@Serializable
data class SupabaseSettings(
    val supabaseUrl: String = "",
    val supabaseAnonKey: String = "",
    val isConfigured: Boolean = false
)

object SupabaseSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/supabase_settings.json")
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    init {
        // Ensure the .boss directory exists
        settingsFile.parentFile?.mkdirs()
    }
    
    /**
     * Load Supabase settings from file
     */
    suspend fun loadSettings(): SupabaseSettings = withContext(Dispatchers.IO) {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                json.decodeFromString<SupabaseSettings>(content)
            } else {
                SupabaseSettings()
            }
        } catch (e: Exception) {
            println("Error loading Supabase settings: ${e.message}")
            SupabaseSettings()
        }
    }
    
    /**
     * Save Supabase settings to file
     */
    suspend fun saveSettings(settings: SupabaseSettings) = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(settings)
            settingsFile.writeText(content)
            println("Supabase settings saved successfully")
        } catch (e: Exception) {
            println("Error saving Supabase settings: ${e.message}")
            throw e
        }
    }
    
    /**
     * Initialize Supabase with saved settings
     */
    suspend fun initializeFromSavedSettings(): Boolean {
        val settings = loadSettings()
        return if (settings.isConfigured && settings.supabaseUrl.isNotBlank() && settings.supabaseAnonKey.isNotBlank()) {
            try {
                SupabaseConfig.initialize(settings.supabaseUrl, settings.supabaseAnonKey)
                true
            } catch (e: Exception) {
                println("Failed to initialize Supabase from saved settings: ${e.message}")
                false
            }
        } else {
            false
        }
    }
    
    /**
     * Update and save settings, then initialize Supabase
     */
    suspend fun configureAndInitialize(url: String, anonKey: String): Result<Unit> {
        return try {
            // Save settings
            val settings = SupabaseSettings(
                supabaseUrl = url,
                supabaseAnonKey = anonKey,
                isConfigured = true
            )
            saveSettings(settings)
            
            // Initialize Supabase
            SupabaseConfig.initialize(url, anonKey)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Clear saved settings
     */
    suspend fun clearSettings() = withContext(Dispatchers.IO) {
        try {
            if (settingsFile.exists()) {
                settingsFile.delete()
            }
            SupabaseConfig.clear()
            println("Supabase settings cleared")
        } catch (e: Exception) {
            println("Error clearing Supabase settings: ${e.message}")
        }
    }
}