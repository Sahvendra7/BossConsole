package ai.rever.boss.updater

import ai.rever.boss.utils.Version
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Central update manager that handles periodic update checks and state management
 */
class UpdateManager {
    private val logger = BossLogger.forComponent("UpdateManager")

    // Internal for access by VersionListManager
    internal val updateService = UpdateService()
    
    // Update state flows
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    
    private val _lastCheckTime = MutableStateFlow<kotlin.time.Instant?>(null)
    val lastCheckTime: StateFlow<kotlin.time.Instant?> = _lastCheckTime.asStateFlow()
    
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)

    // Background job for periodic checks
    private var periodicCheckJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    companion object {
        val instance = UpdateManager()
    }
    
    /**
     * Start periodic update checks
     */
    fun startPeriodicChecks() {
        periodicCheckJob?.cancel()
        periodicCheckJob = scope.launch {
            while (isActive) {
                try {
                    checkForUpdatesInternal()
                    delay(UpdateSettings.checkIntervalHours * 60 * 60 * 1000) // Convert hours to milliseconds
                } catch (e: Exception) {
                    logger.warn(LogCategory.SYSTEM, "Error in periodic update check", error = e)
                    delay(60 * 60 * 1000) // Retry in 1 hour on error
                }
            }
        }
    }
    
    /**
     * Stop periodic update checks
     */
    fun stopPeriodicChecks() {
        periodicCheckJob?.cancel()
        periodicCheckJob = null
    }
    
    /**
     * Manually check for updates
     */
    suspend fun checkForUpdates(): UpdateResult {
        return checkForUpdatesInternal()
    }
    
    private suspend fun checkForUpdatesInternal(): UpdateResult {
        return try {
            _updateState.value = UpdateState.CheckingForUpdates
            _lastCheckTime.value = Clock.System.now()
            
            val updateInfo = updateService.checkForUpdates()
            _updateInfo.value = updateInfo
            
            when {
                updateInfo.isNewerVersionAvailable -> {
                    _updateState.value = UpdateState.UpdateAvailable(updateInfo)
                    UpdateResult.UpdateAvailable(updateInfo)
                }
                else -> {
                    _updateState.value = UpdateState.UpToDate
                    UpdateResult.NoUpdateAvailable
                }
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
            UpdateResult.Error("Failed to check for updates", e)
        }
    }
    
    /**
     * Download the available update
     */
    suspend fun downloadUpdate(updateInfo: UpdateInfo): UpdateResult {
        return try {
            _updateState.value = UpdateState.Downloading(0f)

            val downloadPath = updateService.downloadUpdate(updateInfo) { progress ->
                _updateState.value = UpdateState.Downloading(progress)
            }

            if (downloadPath != null) {
                _updateState.value = UpdateState.ReadyToInstall(downloadPath)
                UpdateResult.UpdateAvailable(updateInfo.copy())
            } else {
                val errorMsg = "Failed to download update"
                _updateState.value = UpdateState.Error(errorMsg)
                UpdateResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = "Download failed: ${e.message}"
            _updateState.value = UpdateState.Error(errorMsg)
            UpdateResult.Error(errorMsg, e)
        }
    }

    /**
     * Download a specific version (for upgrades or downgrades)
     */
    suspend fun downloadSpecificVersion(versionInfo: VersionInfo): UpdateResult {
        return try {
            _updateState.value = UpdateState.Downloading(0f)

            // Convert VersionInfo to UpdateInfo
            val updateInfo = UpdateInfo(
                available = true,
                currentVersion = Version.CURRENT,
                latestVersion = versionInfo.version,
                releaseNotes = versionInfo.releaseNotes,
                downloadUrl = versionInfo.downloadUrl,
                assetSize = versionInfo.downloadSize,
                assetName = updateService.getExpectedAssetName(versionInfo.version)
            )

            val downloadPath = updateService.downloadUpdate(updateInfo) { progress ->
                _updateState.value = UpdateState.Downloading(progress)
            }

            if (downloadPath != null) {
                _updateState.value = UpdateState.ReadyToInstall(downloadPath)
                UpdateResult.UpdateAvailable(updateInfo)
            } else {
                val errorMsg = "Failed to download version ${versionInfo.version}"
                _updateState.value = UpdateState.Error(errorMsg)
                UpdateResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = "Download failed: ${e.message}"
            _updateState.value = UpdateState.Error(errorMsg)
            UpdateResult.Error(errorMsg, e)
        }
    }
    
    /**
     * Install the downloaded update
     */
    suspend fun installUpdate(downloadPath: String): Boolean {
        return try {
            _updateState.value = UpdateState.Installing
            
            val success = updateService.installUpdate(downloadPath)
            if (success) {
                _updateState.value = UpdateState.RestartRequired
            } else {
                _updateState.value = UpdateState.Error("Installation failed")
            }
            success
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Installation failed: ${e.message}")
            false
        }
    }
    
    /**
     * Get current application version
     */
    fun getCurrentVersion(): Version = Version.CURRENT
    
    /**
     * Check if enough time has passed since last check for automatic checking
     */
    fun shouldCheckForUpdates(): Boolean {
        val lastCheck = _lastCheckTime.value
        if (lastCheck == null) return true

        val now = Clock.System.now()
        val timeSinceLastCheck = now - lastCheck
        return timeSinceLastCheck.inWholeHours >= UpdateSettings.checkIntervalHours
    }
    
    /**
     * Reset update state to idle
     */
    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopPeriodicChecks()
        scope.cancel()
    }
}

/**
 * Update state sealed class
 */
sealed class UpdateState {
    object Idle : UpdateState()
    object CheckingForUpdates : UpdateState()
    object UpToDate : UpdateState()
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState() // 0.0 to 1.0
    data class ReadyToInstall(val downloadPath: String) : UpdateState()
    object Installing : UpdateState()
    object RestartRequired : UpdateState()
    data class Error(val message: String) : UpdateState()
}
