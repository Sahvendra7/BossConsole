package ai.rever.boss.run

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop implementation of RunnerSettingsManager.
 * Persists settings to ~/.boss/runner-settings.json
 *
 * Issue #347: Runner settings persistence
 */
actual object RunnerSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/runner-settings.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _currentSettings = MutableStateFlow(RunnerSettings())
    actual val currentSettings: StateFlow<RunnerSettings> = _currentSettings.asStateFlow()

    init {
        settingsFile.parentFile?.mkdirs()
        loadSettingsSync()
    }

    /**
     * Load settings synchronously on initialization.
     */
    private fun loadSettingsSync() {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<RunnerSettings>(content)
                _currentSettings.value = settings
                println("[RunnerSettings] Loaded settings: $settings")
            } else {
                // Create default settings file
                saveSettingsSync()
                println("[RunnerSettings] Created default settings file")
            }
        } catch (e: Exception) {
            println("[RunnerSettings] Error loading settings: ${e.message}")
            _currentSettings.value = RunnerSettings()
        }
    }

    /**
     * Save settings synchronously (for init).
     */
    private fun saveSettingsSync() {
        try {
            val content = json.encodeToString(RunnerSettings.serializer(), _currentSettings.value)
            settingsFile.writeText(content)
        } catch (e: Exception) {
            println("[RunnerSettings] Error saving settings: ${e.message}")
        }
    }

    /**
     * Save current settings to persistent storage.
     */
    actual suspend fun saveSettings() = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(RunnerSettings.serializer(), _currentSettings.value)
            settingsFile.writeText(content)
            println("[RunnerSettings] Settings saved")
        } catch (e: Exception) {
            println("[RunnerSettings] Error saving settings: ${e.message}")
        }
    }

    /**
     * Update settings and persist.
     */
    actual suspend fun updateSettings(settings: RunnerSettings) {
        _currentSettings.value = settings
        saveSettings()
    }

    /**
     * Reset settings to defaults.
     */
    actual suspend fun resetToDefault() {
        updateSettings(RunnerSettings())
    }

    /**
     * Update only the terminal target setting.
     */
    actual suspend fun setTerminalTarget(target: RunnerTerminalTarget) {
        updateSettings(_currentSettings.value.copy(terminalTarget = target))
    }

    /**
     * Update only the focus on run setting.
     */
    actual suspend fun setFocusOnRun(enabled: Boolean) {
        updateSettings(_currentSettings.value.copy(focusOnRun = enabled))
    }

    /**
     * Update only the notify on exit setting.
     */
    actual suspend fun setNotifyOnExit(enabled: Boolean) {
        updateSettings(_currentSettings.value.copy(notifyOnExit = enabled))
    }

    /**
     * Update only the re-run delay setting.
     */
    actual suspend fun setRerunDelayMs(delayMs: Long) {
        // Clamp to valid range (0-2000ms)
        val clampedDelay = delayMs.coerceIn(0, 2000)
        updateSettings(_currentSettings.value.copy(rerunDelayMs = clampedDelay))
    }
}
