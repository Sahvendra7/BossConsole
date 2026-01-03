package ai.rever.boss.components.workspaces

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
 * Desktop implementation of WorkspaceSettingsManager.
 * Persists settings to ~/.boss/workspace-settings.json
 */
actual object WorkspaceSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/workspace-settings.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currentSettings = MutableStateFlow(WorkspaceSettings())
    actual val currentSettings: StateFlow<WorkspaceSettings> = _currentSettings.asStateFlow()

    init {
        scope.launch {
            loadSettingsAsync()
        }
    }

    private suspend fun loadSettingsAsync() = withContext(Dispatchers.IO) {
        try {
            settingsFile.parentFile?.mkdirs()

            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<WorkspaceSettings>(content)
                _currentSettings.value = settings
                println("[WorkspaceSettings] Loaded settings: $settings")
            } else {
                val content = json.encodeToString(WorkspaceSettings.serializer(), _currentSettings.value)
                settingsFile.writeText(content)
                println("[WorkspaceSettings] Created default settings file")
            }
        } catch (e: Exception) {
            println("[WorkspaceSettings] Error loading settings: ${e.message}")
        }
    }

    actual suspend fun saveSettings() = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(WorkspaceSettings.serializer(), _currentSettings.value)
            settingsFile.writeText(content)
            println("[WorkspaceSettings] Settings saved")
        } catch (e: Exception) {
            println("[WorkspaceSettings] Error saving settings: ${e.message}")
        }
    }

    actual suspend fun updateSettings(settings: WorkspaceSettings) {
        _currentSettings.value = settings
        saveSettings()
    }

    actual suspend fun setDefaultWorkspaceId(workspaceId: String) {
        updateSettings(_currentSettings.value.copy(defaultWorkspaceId = workspaceId))
    }

    actual fun getDefaultWorkspace(): LayoutWorkspace? {
        val workspaceId = _currentSettings.value.defaultWorkspaceId
        if (workspaceId == "none") return null
        return PredefinedWorkspaces.allWorkspaces.find { it.id == workspaceId }
    }
}
