package ai.rever.boss.components.plugin.panels.bottom.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class TerminalSettingsData(
    val fontFamily: String = "MesloLGS NF",
    val fontSize: Int = 14,
    val colorScheme: String = "BOSS Dark",
    val shell: String = "/bin/zsh",
    val startupCommand: String = ""
)

object TerminalSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/terminal-settings.json")
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
                val settings = json.decodeFromString<TerminalSettingsData>(content)
                
                // Apply loaded settings
                TerminalSettings.fontFamily = settings.fontFamily
                TerminalSettings.fontSize = settings.fontSize
                TerminalSettings.colorScheme = settings.colorScheme
                TerminalSettings.shell = settings.shell
                TerminalSettings.startupCommand = settings.startupCommand
            }
        } catch (e: Exception) {
            println("Failed to load terminal settings: ${e.message}")
        }
    }
    
    suspend fun saveSettings() = withContext(Dispatchers.IO) {
        try {
            val settings = TerminalSettingsData(
                fontFamily = TerminalSettings.fontFamily,
                fontSize = TerminalSettings.fontSize,
                colorScheme = TerminalSettings.colorScheme,
                shell = TerminalSettings.shell,
                startupCommand = TerminalSettings.startupCommand
            )
            
            val content = json.encodeToString(settings)
            settingsFile.writeText(content)
        } catch (e: Exception) {
            println("Failed to save terminal settings: ${e.message}")
        }
    }
}
