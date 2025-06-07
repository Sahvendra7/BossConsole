package ai.rever.boss.components.plugin.tab_types.fluck

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

@Serializable
data class BrowserSettingsData(
    val userAgent: String? = null,
    val customUserAgent: String? = null,
    val currentProfile: String = "browser-profile",
    val availableProfiles: List<String> = listOf("browser-profile")
)

object BrowserSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/browser-settings.json")
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    init {
        // Ensure directory exists
        settingsFile.parentFile?.mkdirs()
        
        // Load settings on initialization
        loadSettingsSync()
    }
    
    private fun loadSettingsSync() {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<BrowserSettingsData>(content)
                
                // Apply loaded settings
                BrowserSettings.userAgent = settings.userAgent
                BrowserSettings.customUserAgent = settings.customUserAgent
                BrowserSettings.currentProfile = settings.currentProfile
                
                // Update available profiles if we have more
                if (settings.availableProfiles.isNotEmpty()) {
                    BrowserSettings.availableProfiles.clear()
                    BrowserSettings.availableProfiles.addAll(settings.availableProfiles)
                }
            }
        } catch (e: Exception) {
            println("Failed to load browser settings: ${e.message}")
        }
    }
    
    suspend fun saveSettings() = withContext(Dispatchers.IO) {
        try {
            val settings = BrowserSettingsData(
                userAgent = BrowserSettings.userAgent,
                customUserAgent = BrowserSettings.customUserAgent,
                currentProfile = BrowserSettings.currentProfile,
                availableProfiles = BrowserSettings.availableProfiles.toList()
            )
            
            val content = json.encodeToString(settings)
            settingsFile.writeText(content)
        } catch (e: Exception) {
            println("Failed to save browser settings: ${e.message}")
        }
    }
    
    fun getProfilePath(profileName: String): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.boss/$profileName"
    }
    
    fun deleteProfile(profileName: String): Boolean {
        if (profileName == "browser-profile") {
            // Don't delete the default profile
            return false
        }
        
        try {
            val profileDir = File(getProfilePath(profileName))
            if (profileDir.exists()) {
                profileDir.deleteRecursively()
            }
            
            BrowserSettings.availableProfiles.remove(profileName)
            
            // If we deleted the current profile, switch to default
            if (BrowserSettings.currentProfile == profileName) {
                BrowserSettings.currentProfile = "browser-profile"
            }
            
            return true
        } catch (e: Exception) {
            println("Failed to delete profile $profileName: ${e.message}")
            return false
        }
    }
}