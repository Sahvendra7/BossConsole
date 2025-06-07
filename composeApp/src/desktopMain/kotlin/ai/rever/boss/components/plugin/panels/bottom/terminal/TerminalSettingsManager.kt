package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

// Global settings object
object TerminalSettings {
    var fontFamily: String = "MesloLGS NF"
    var fontSize: Int = 14
    var colorScheme: String = "BOSS Dark"
    var shell: String = "/bin/zsh"
    var startupCommand: String = ""
    
    // Color scheme colors
    fun getBackgroundColor(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_FFFFFF)
        "Solarized Dark" -> Color(0xFF_002B36)
        "Solarized Light" -> Color(0xFF_FDF6E3)
        "Dracula" -> Color(0xFF_282A36)
        "Tomorrow Night" -> Color(0xFF_1D1F21)
        else -> Color(0xFF_1E1E1E) // BOSS Dark
    }
    
    fun getForegroundColor(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_000000)
        "Solarized Dark" -> Color(0xFF_839496)
        "Solarized Light" -> Color(0xFF_657B83)
        "Dracula" -> Color(0xFF_F8F8F2)
        "Tomorrow Night" -> Color(0xFF_C5C8C6)
        else -> Color(0xFF_D4D4D4) // BOSS Dark
    }
    
    fun getCursorColor(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_000000)
        "Solarized Dark" -> Color(0xFF_93A1A1)
        "Solarized Light" -> Color(0xFF_586E75)
        "Dracula" -> Color(0xFF_F8F8F2)
        "Tomorrow Night" -> Color(0xFF_C5C8C6)
        else -> Color(0xFF_FFFFFF) // BOSS Dark
    }
    
    fun getSelectionColor(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_B3D9FF)
        "Solarized Dark" -> Color(0xFF_586E75)
        "Solarized Light" -> Color(0xFF_EEE8D5)
        "Dracula" -> Color(0xFF_44475A)
        "Tomorrow Night" -> Color(0xFF_373B41)
        else -> Color(0xFF_264F78) // BOSS Dark
    }
    
    // ANSI color palette
    fun getAnsiBlack(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_000000)
        "Solarized Dark" -> Color(0xFF_073642)
        "Solarized Light" -> Color(0xFF_073642)
        "Dracula" -> Color(0xFF_21222C)
        "Tomorrow Night" -> Color(0xFF_1D1F21)
        else -> Color(0xFF_000000) // BOSS Dark
    }
    
    fun getAnsiRed(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_CD3131)
        "Solarized Dark" -> Color(0xFF_DC322F)
        "Solarized Light" -> Color(0xFF_DC322F)
        "Dracula" -> Color(0xFF_FF5555)
        "Tomorrow Night" -> Color(0xFF_CC6666)
        else -> Color(0xFF_CD3131) // BOSS Dark
    }
    
    fun getAnsiGreen(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_0DBC79)
        "Solarized Dark" -> Color(0xFF_859900)
        "Solarized Light" -> Color(0xFF_859900)
        "Dracula" -> Color(0xFF_50FA7B)
        "Tomorrow Night" -> Color(0xFF_B5BD68)
        else -> Color(0xFF_0DBC79) // BOSS Dark
    }
    
    fun getAnsiYellow(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_E5E510)
        "Solarized Dark" -> Color(0xFF_B58900)
        "Solarized Light" -> Color(0xFF_B58900)
        "Dracula" -> Color(0xFF_F1FA8C)
        "Tomorrow Night" -> Color(0xFF_F0C674)
        else -> Color(0xFF_E5E510) // BOSS Dark
    }
    
    fun getAnsiBlue(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_2472C8)
        "Solarized Dark" -> Color(0xFF_268BD2)
        "Solarized Light" -> Color(0xFF_268BD2)
        "Dracula" -> Color(0xFF_BD93F9)
        "Tomorrow Night" -> Color(0xFF_81A2BE)
        else -> Color(0xFF_2472C8) // BOSS Dark
    }
    
    fun getAnsiMagenta(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_BC3FBC)
        "Solarized Dark" -> Color(0xFF_D33682)
        "Solarized Light" -> Color(0xFF_D33682)
        "Dracula" -> Color(0xFF_FF79C6)
        "Tomorrow Night" -> Color(0xFF_B294BB)
        else -> Color(0xFF_BC3FBC) // BOSS Dark
    }
    
    fun getAnsiCyan(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_11A8CD)
        "Solarized Dark" -> Color(0xFF_2AA198)
        "Solarized Light" -> Color(0xFF_2AA198)
        "Dracula" -> Color(0xFF_8BE9FD)
        "Tomorrow Night" -> Color(0xFF_8ABEB7)
        else -> Color(0xFF_11A8CD) // BOSS Dark
    }
    
    fun getAnsiWhite(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_E5E5E5)
        "Solarized Dark" -> Color(0xFF_EEE8D5)
        "Solarized Light" -> Color(0xFF_EEE8D5)
        "Dracula" -> Color(0xFF_F8F8F2)
        "Tomorrow Night" -> Color(0xFF_C5C8C6)
        else -> Color(0xFF_E5E5E5) // BOSS Dark
    }
}

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