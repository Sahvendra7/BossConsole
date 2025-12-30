package ai.rever.boss.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop implementation of TerminalLinkSettingsManager.
 * Persists settings to ~/.boss/terminal-link-settings.json
 *
 * Settings are loaded asynchronously on Dispatchers.IO to avoid blocking the main thread.
 * Default settings are provided immediately via StateFlow.
 *
 * Issue #346: Terminal link click prompt with remember preference
 */
actual object TerminalLinkSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/terminal-link-settings.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Coroutine scope for async operations - uses SupervisorJob so failures don't cancel other operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Default settings provided immediately, updated async when file is loaded
    private val _currentSettings = MutableStateFlow(TerminalLinkSettings())
    actual val currentSettings: StateFlow<TerminalLinkSettings> = _currentSettings.asStateFlow()

    init {
        // Load settings asynchronously to avoid blocking main thread
        scope.launch {
            loadSettingsAsync()
        }
    }

    /**
     * Load settings asynchronously on Dispatchers.IO.
     * Creates parent directories and default settings file if needed.
     */
    private suspend fun loadSettingsAsync() = withContext(Dispatchers.IO) {
        try {
            settingsFile.parentFile?.mkdirs()

            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<TerminalLinkSettings>(content)
                _currentSettings.value = settings
                println("[TerminalLinkSettings] Loaded settings: $settings")
            } else {
                // Create default settings file
                val content = json.encodeToString(TerminalLinkSettings.serializer(), _currentSettings.value)
                settingsFile.writeText(content)
                println("[TerminalLinkSettings] Created default settings file")
            }
        } catch (e: Exception) {
            println("[TerminalLinkSettings] Error loading settings: ${e.message}")
            // Keep default settings on error
        }
    }

    /**
     * Save current settings to persistent storage.
     */
    actual suspend fun saveSettings() = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(TerminalLinkSettings.serializer(), _currentSettings.value)
            settingsFile.writeText(content)
            println("[TerminalLinkSettings] Settings saved")
        } catch (e: Exception) {
            println("[TerminalLinkSettings] Error saving settings: ${e.message}")
        }
    }

    /**
     * Update settings and persist.
     */
    actual suspend fun updateSettings(settings: TerminalLinkSettings) {
        _currentSettings.value = settings
        saveSettings()
    }

    /**
     * Set the open mode preference.
     */
    actual suspend fun setOpenMode(mode: TerminalLinkOpenMode) {
        updateSettings(_currentSettings.value.copy(openMode = mode))
    }

    /**
     * Set the existing split target mode preference.
     */
    actual suspend fun setExistingSplitTarget(mode: ExistingSplitTargetMode) {
        updateSettings(_currentSettings.value.copy(existingSplitTarget = mode))
    }

    /**
     * Reset settings to defaults (ALWAYS_ASK).
     */
    actual suspend fun resetToDefault() {
        updateSettings(TerminalLinkSettings())
    }
}
