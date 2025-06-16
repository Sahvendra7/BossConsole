package ai.rever.boss.components.plugin.panels.right_top

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop implementation of environment variable access
 */
actual fun getEnvironmentVariable(name: String): String? {
    return System.getenv(name)
}

/**
 * Desktop implementation of LLM Settings Manager
 */
actual object LLMSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/llm_settings.json")
    
    actual suspend fun loadSettings() {
        withContext(Dispatchers.IO) {
            try {
                if (settingsFile.exists()) {
                    val json = settingsFile.readText()
                    LLMSettings.loadFromJson(json)
                }
            } catch (e: Exception) {
                println("Error loading LLM settings: ${e.message}")
            }
        }
    }
    
    actual suspend fun saveSettings() {
        withContext(Dispatchers.IO) {
            try {
                settingsFile.parentFile?.mkdirs()
                settingsFile.writeText(LLMSettings.toJson())
            } catch (e: Exception) {
                println("Error saving LLM settings: ${e.message}")
            }
        }
    }
}