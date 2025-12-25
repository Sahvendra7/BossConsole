package ai.rever.boss.window

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop implementation of window appearance settings manager.
 * Persists settings to ~/.boss/window-appearance-settings.json
 */
actual object WindowAppearanceSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/window-appearance-settings.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _currentSettings = MutableStateFlow(loadSettings())
    actual val currentSettings: StateFlow<WindowAppearanceSettings> = _currentSettings.asStateFlow()

    actual fun updateSettings(settings: WindowAppearanceSettings) {
        _currentSettings.value = settings
        saveSettings(settings)
    }

    actual fun getDefaultSettings(): WindowAppearanceSettings {
        val os = System.getProperty("os.name").lowercase()
        val isMacOS = os.contains("mac")
        // Show title bar on macOS, hide on Linux/Windows
        return WindowAppearanceSettings(showTitleBar = isMacOS)
    }

    private fun loadSettings(): WindowAppearanceSettings {
        return try {
            if (settingsFile.exists()) {
                json.decodeFromString<WindowAppearanceSettings>(settingsFile.readText())
            } else {
                getDefaultSettings()
            }
        } catch (e: Exception) {
            println("WindowAppearanceSettingsManager: Failed to load settings: ${e.message}")
            getDefaultSettings()
        }
    }

    private fun saveSettings(settings: WindowAppearanceSettings) {
        try {
            settingsFile.parentFile?.mkdirs()
            settingsFile.writeText(json.encodeToString(WindowAppearanceSettings.serializer(), settings))
        } catch (e: Exception) {
            println("WindowAppearanceSettingsManager: Failed to save settings: ${e.message}")
        }
    }
}
