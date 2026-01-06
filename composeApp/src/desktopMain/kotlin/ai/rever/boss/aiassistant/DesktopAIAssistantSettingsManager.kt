package ai.rever.boss.aiassistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop implementation of AIAssistantSettingsManager.
 * Persists settings to ~/.boss/ai-assistant-settings.json
 *
 * Settings are loaded asynchronously on Dispatchers.IO to avoid blocking the main thread.
 * Default settings are provided immediately via StateFlow.
 * Use [awaitLoaded] if you need to ensure settings are loaded from disk.
 *
 * Issue #445: Terminal context menu for AI coding assistants
 */
actual object AIAssistantSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/ai-assistant-settings.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Coroutine scope for async operations - uses SupervisorJob so failures don't cancel other operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Loading state - true once settings have been loaded from disk (or failed)
    private val _isLoaded = MutableStateFlow(false)

    /**
     * Whether settings have been loaded from disk.
     * Check this or use [awaitLoaded] before relying on persisted values.
     */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    // Default settings provided immediately, updated async when file is loaded
    private val _currentSettings = MutableStateFlow(AIAssistantSettings())
    actual val currentSettings: StateFlow<AIAssistantSettings> = _currentSettings.asStateFlow()

    init {
        // Load settings asynchronously to avoid blocking main thread
        scope.launch {
            loadSettingsAsync()
        }
    }

    /**
     * Suspend until settings have been loaded from disk.
     * Safe to call multiple times - returns immediately if already loaded.
     */
    suspend fun awaitLoaded() {
        if (_isLoaded.value) return
        _isLoaded.first { it }
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
                val settings = json.decodeFromString<AIAssistantSettings>(content)
                _currentSettings.value = settings
                println("[AIAssistantSettings] Loaded settings")
            } else {
                // Create default settings file
                val content = json.encodeToString(AIAssistantSettings.serializer(), _currentSettings.value)
                settingsFile.writeText(content)
                println("[AIAssistantSettings] Created default settings file")
            }
        } catch (e: Exception) {
            println("[AIAssistantSettings] Error loading settings: ${e.message}")
            // Keep default settings on error
        } finally {
            _isLoaded.value = true
        }
    }

    /**
     * Save settings to persistent storage.
     * Returns true if save succeeded, false otherwise.
     */
    private suspend fun saveSettingsToDisk(settings: AIAssistantSettings): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(AIAssistantSettings.serializer(), settings)
            settingsFile.writeText(content)
            println("[AIAssistantSettings] Settings saved")
            true
        } catch (e: Exception) {
            println("[AIAssistantSettings] Error saving settings: ${e.message}")
            false
        }
    }

    /**
     * Save current settings to persistent storage.
     */
    actual suspend fun saveSettings() {
        saveSettingsToDisk(_currentSettings.value)
    }

    /**
     * Update settings and persist.
     * Saves to disk first, then updates StateFlow to ensure consistency on crash.
     */
    actual suspend fun updateSettings(settings: AIAssistantSettings) {
        // Save to disk first to ensure persistence on crash
        if (saveSettingsToDisk(settings)) {
            _currentSettings.value = settings
        } else {
            // If disk save failed, still update in-memory so app continues working
            // but log warning about potential data loss on restart
            println("[AIAssistantSettings] Warning: Settings updated in-memory but disk save failed")
            _currentSettings.value = settings
        }
    }

    /**
     * Reset settings to defaults.
     */
    actual suspend fun resetToDefault() {
        updateSettings(AIAssistantSettings())
    }

    /**
     * Update configuration for a specific assistant.
     */
    actual suspend fun updateAssistantConfig(config: AIAssistantConfig) {
        updateSettings(_currentSettings.value.updateConfig(config))
    }

    /**
     * Enable or disable YOLO mode for an assistant.
     */
    actual suspend fun setYoloEnabled(assistant: AIAssistant, enabled: Boolean) {
        val currentConfig = _currentSettings.value.getConfig(assistant)
        updateAssistantConfig(currentConfig.copy(yoloEnabled = enabled))
    }

    /**
     * Set a custom command for an assistant.
     */
    actual suspend fun setCustomCommand(assistant: AIAssistant, command: String?) {
        val currentConfig = _currentSettings.value.getConfig(assistant)
        updateAssistantConfig(currentConfig.copy(customCommand = command))
    }

    /**
     * Enable or disable an assistant in the menu.
     */
    actual suspend fun setAssistantEnabled(assistant: AIAssistant, enabled: Boolean) {
        val currentConfig = _currentSettings.value.getConfig(assistant)
        updateAssistantConfig(currentConfig.copy(enabled = enabled))
    }

    /**
     * Set whether to show unavailable assistants in menu.
     */
    actual suspend fun setShowUnavailableAssistants(show: Boolean) {
        updateSettings(_currentSettings.value.copy(showUnavailableAssistants = show))
    }
}
