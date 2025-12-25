package ai.rever.boss.window

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop implementation of window appearance settings manager.
 * Manages loading and saving of window appearance settings.
 * Follows the BOSS settings management pattern with:
 * - JSON persistence in ~/.boss/window-appearance-settings.json
 * - Automatic directory creation
 * - Synchronous load on init
 * - Graceful error handling with fallback to defaults
 */
actual object WindowAppearanceSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/window-appearance-settings.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _currentSettings = MutableStateFlow(WindowAppearanceSettings())
    actual val currentSettings: StateFlow<WindowAppearanceSettings> = _currentSettings.asStateFlow()

    init {
        // Ensure directory exists
        settingsFile.parentFile?.mkdirs()

        // Load settings on initialization
        loadSettingsSync()
    }

    /**
     * Load settings synchronously on startup.
     * If file doesn't exist, uses platform-specific defaults and saves them.
     */
    private fun loadSettingsSync() {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<WindowAppearanceSettings>(content)
                _currentSettings.value = settings
                println("[WindowAppearance] Loaded settings from ${settingsFile.absolutePath}")
            } else {
                // First run - create default settings file with platform-specific defaults
                val defaults = getDefaultSettings()
                _currentSettings.value = defaults
                saveSettings(defaults)
                println("[WindowAppearance] Created default settings at ${settingsFile.absolutePath}")
            }
        } catch (e: Exception) {
            println("[WindowAppearance] Failed to load settings: ${e.message}")
            _currentSettings.value = getDefaultSettings()
        }
    }

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

    private fun saveSettings(settings: WindowAppearanceSettings) {
        try {
            settingsFile.parentFile?.mkdirs()
            settingsFile.writeText(json.encodeToString(WindowAppearanceSettings.serializer(), settings))
        } catch (e: Exception) {
            println("[WindowAppearance] Failed to save settings: ${e.message}")
        }
    }
}
