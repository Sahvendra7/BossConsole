package ai.rever.boss.performance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop implementation of Performance settings manager.
 * Follows the BOSS settings management pattern with:
 * - JSON persistence in ~/.boss/performance-settings.json
 * - Automatic directory creation
 * - Synchronous load on init, asynchronous save
 * - Graceful error handling with fallback to defaults
 */
actual object PerformanceSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/performance-settings.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _currentSettings = MutableStateFlow(PerformanceSettings())
    actual val currentSettings: StateFlow<PerformanceSettings> = _currentSettings.asStateFlow()

    init {
        settingsFile.parentFile?.mkdirs()
        loadSettingsSync()
    }

    private fun loadSettingsSync() {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<PerformanceSettings>(content)
                _currentSettings.value = settings
                println("[Performance] Loaded settings from ${settingsFile.absolutePath}")
            } else {
                println("[Performance] No settings file found, using defaults")
                _currentSettings.value = PerformanceSettings()
            }
        } catch (e: Exception) {
            println("[Performance] Failed to load settings: ${e.message}")
            _currentSettings.value = PerformanceSettings()
        }
    }

    actual suspend fun saveSettings() = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(PerformanceSettings.serializer(), _currentSettings.value)
            settingsFile.writeText(content)
            println("[Performance] Settings saved")
        } catch (e: Exception) {
            println("[Performance] Failed to save settings: ${e.message}")
        }
    }

    actual suspend fun updateSettings(settings: PerformanceSettings) {
        _currentSettings.value = settings
        saveSettings()
    }

    actual suspend fun toggleMonitoring() {
        val current = _currentSettings.value
        updateSettings(current.copy(enabled = !current.enabled))
    }

    actual suspend fun toggleIndicator() {
        val current = _currentSettings.value
        updateSettings(current.copy(showIndicator = !current.showIndicator))
    }

    actual suspend fun resetToDefault() {
        updateSettings(PerformanceSettings())
    }
}
