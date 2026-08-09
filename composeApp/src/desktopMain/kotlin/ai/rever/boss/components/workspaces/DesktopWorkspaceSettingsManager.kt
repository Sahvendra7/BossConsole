package ai.rever.boss.components.workspaces

import ai.rever.boss.plugin.pathutils.BossDirectories
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
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
    private val logger = BossLogger.forComponent("WorkspaceSettingsManager")
    private val settingsFile = BossDirectories.resolve("workspace-settings.json")
    private val json =
        Json {
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

    private suspend fun loadSettingsAsync() =
        withContext(Dispatchers.IO) {
            try {
                settingsFile.parentFile?.mkdirs()

                if (settingsFile.exists()) {
                    val content = settingsFile.readText()
                    val settings = json.decodeFromString<WorkspaceSettings>(content)
                    val migrated = WorkspaceSettingsMigrations.migrate(settings)
                    _currentSettings.value = migrated ?: settings
                    if (migrated != null) {
                        logger.info(
                            LogCategory.SYSTEM,
                            "Migrated workspace settings",
                            mapOf(
                                "fromVersion" to settings.settingsVersion.toString(),
                                "defaultWorkspaceId" to migrated.defaultWorkspaceId,
                            ),
                        )
                        saveSettings()
                    } else {
                        logger.debug(LogCategory.SYSTEM, "Loaded settings")
                    }
                } else {
                    _currentSettings.value =
                        _currentSettings.value.copy(settingsVersion = WorkspaceSettings.CURRENT_SETTINGS_VERSION)
                    val content = json.encodeToString(WorkspaceSettings.serializer(), _currentSettings.value)
                    settingsFile.writeText(content)
                    logger.debug(LogCategory.SYSTEM, "Created default settings file")
                }
            } catch (e: Exception) {
                logger.warn(LogCategory.SYSTEM, "Error loading settings", error = e)
            }
        }

    actual suspend fun saveSettings() =
        withContext(Dispatchers.IO) {
            try {
                val content = json.encodeToString(WorkspaceSettings.serializer(), _currentSettings.value)
                settingsFile.writeText(content)
                logger.debug(LogCategory.SYSTEM, "Settings saved")
            } catch (e: Exception) {
                logger.warn(LogCategory.SYSTEM, "Error saving settings", error = e)
            }
        }

    actual suspend fun updateSettings(settings: WorkspaceSettings) {
        _currentSettings.value = settings
        saveSettings()
    }

    actual suspend fun setDefaultWorkspaceId(workspaceId: String) {
        // Stamp the schema version too: an explicit choice must never be rewritten
        // by a migration on the next launch.
        updateSettings(
            _currentSettings.value.copy(
                defaultWorkspaceId = workspaceId,
                settingsVersion = WorkspaceSettings.CURRENT_SETTINGS_VERSION,
            ),
        )
    }

    actual fun getDefaultWorkspace(): LayoutWorkspace? {
        val workspaceId = _currentSettings.value.defaultWorkspaceId
        if (workspaceId == "none") return null
        return PredefinedWorkspaces.allWorkspaces.find { it.id == workspaceId }
    }
}
