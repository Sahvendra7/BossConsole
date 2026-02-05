package ai.rever.boss.plugin.panel.manager

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.repository.PluginInfo
import ai.rever.boss.plugin.updater.UpdateInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Data extracted from a plugin's manifest.
 * Used when extracting manifest from JAR or fetching from GitHub.
 */
data class ExtractedManifest(
    val pluginId: String,
    val displayName: String,
    val version: String,
    val description: String,
    val author: String?,
    val url: String?
)

/**
 * State for an installed plugin in the UI.
 */
data class InstalledPluginState(
    val pluginId: String,
    val displayName: String,
    val version: String,
    val description: String,
    val enabled: Boolean,
    val healthy: Boolean,
    val canUnload: Boolean,
    val jarPath: String,
    val url: String? = null,
    val requiresAdmin: Boolean = false
)

/**
 * State for the Plugin Manager panel.
 */
data class PluginManagerState(
    val currentTab: PluginManagerTab = PluginManagerTab.INSTALLED,
    val installedPlugins: List<InstalledPluginState> = emptyList(),
    val availablePlugins: List<PluginInfo> = emptyList(),
    val updates: List<UpdateInfo> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null,
    val isStoreAdmin: Boolean = false
)

/**
 * Interface for plugin management operations.
 */
interface PluginManagerOperations {
    /**
     * Install a plugin from a JAR path.
     *
     * @param jarPath Path to the JAR file
     * @param sourceUrl Optional source URL (e.g., GitHub repo URL) for tracking updates
     * @param version Optional version string from manifest
     */
    suspend fun installPlugin(jarPath: String, sourceUrl: String? = null, version: String? = null): Result<Unit>

    /**
     * Uninstall a plugin.
     */
    suspend fun uninstallPlugin(pluginId: String): Result<Unit>

    /**
     * Enable a plugin.
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit>

    /**
     * Disable a plugin.
     */
    suspend fun disablePlugin(pluginId: String): Result<Unit>

    /**
     * Update a plugin.
     */
    suspend fun updatePlugin(pluginId: String): Result<Unit>

    /**
     * Update all plugins.
     */
    suspend fun updateAllPlugins(): Map<String, Result<Unit>>

    /**
     * Refresh the plugin lists.
     */
    suspend fun refresh()

    /**
     * Check for updates.
     */
    suspend fun checkForUpdates()

    /**
     * Open a file picker to select a plugin JAR.
     */
    suspend fun browseForPlugin(): String?

    /**
     * Install a plugin from the remote repository.
     *
     * @param pluginId The plugin ID to install
     * @param version Optional specific version (latest if null)
     */
    suspend fun installFromRemote(pluginId: String, version: String? = null): Result<Unit>

    /**
     * Extract manifest information from a JAR file.
     *
     * @param jarPath Path to the JAR file
     * @return ExtractedManifest if successful, null otherwise
     */
    suspend fun extractManifestFromJar(jarPath: String): ExtractedManifest?

    /**
     * Fetch and optionally build a plugin from GitHub.
     *
     * @param githubUrl URL to the GitHub repository
     * @param buildIfNoRelease Whether to build from source if no release is found
     * @param onProgress Progress callback (0.0 to 1.0)
     * @param onStatus Status message callback
     * @return Result containing the JAR path and extracted manifest
     */
    suspend fun fetchFromGitHub(
        githubUrl: String,
        buildIfNoRelease: Boolean,
        onProgress: (Float) -> Unit,
        onStatus: (String) -> Unit
    ): Result<Pair<String, ExtractedManifest>>

    /**
     * Publish a plugin to the plugin store.
     *
     * @param jarPath Path to the JAR file
     * @param pluginId Plugin identifier
     * @param displayName Display name for the plugin
     * @param version Version string
     * @param homepageUrl URL to the plugin's homepage
     * @param authorName Author name (required)
     * @param description Optional description
     * @param changelog Optional changelog/release notes
     * @param tags List of tags for categorization
     * @param iconUrl Optional URL to the plugin icon
     * @param pluginType Plugin type (panel, tab, or hybrid)
     * @param apiVersion Required BOSS Plugin API version
     * @param minBossVersion Minimum BOSS application version required
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Result containing the published plugin ID
     */
    suspend fun publishPlugin(
        jarPath: String,
        pluginId: String,
        displayName: String,
        version: String,
        homepageUrl: String,
        authorName: String,
        description: String?,
        changelog: String?,
        tags: List<String>,
        iconUrl: String?,
        pluginType: String,
        apiVersion: String,
        minBossVersion: String,
        onProgress: (Float) -> Unit
    ): Result<String>

    /**
     * Check if the current user has admin privileges.
     */
    suspend fun isCurrentUserAdmin(): Boolean

    /**
     * Admin: Delete a plugin from the store.
     */
    suspend fun adminDeletePlugin(pluginId: String): Result<Unit>

    /**
     * Admin: Set a plugin's published status.
     */
    suspend fun adminSetPluginPublished(pluginId: String, published: Boolean): Result<Unit>

    /**
     * Admin: Set a plugin's verified status.
     */
    suspend fun adminSetPluginVerified(pluginId: String, verified: Boolean): Result<Unit>
}

/**
 * Decompose component for the Plugin Manager panel.
 */
class PluginManagerComponent(
    componentContext: ComponentContext,
    private val operations: PluginManagerOperations,
    private val onOpenUrl: ((String) -> Unit)? = null
) : PanelComponentWithUI, ComponentContext by componentContext {

    override val panelInfo: PanelInfo = PluginManagerInfo

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(PluginManagerState())
    val state: StateFlow<PluginManagerState> = _state.asStateFlow()

    init {
        // Load initial data
        scope.launch {
            refresh()
        }
    }

    /**
     * Select a tab.
     */
    fun selectTab(tab: PluginManagerTab) {
        _state.value = _state.value.copy(currentTab = tab)
    }

    /**
     * Update search query.
     */
    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    /**
     * Install a plugin from a file picker.
     */
    fun installFromFilePicker() {
        scope.launch {
            val jarPath = operations.browseForPlugin()
            if (jarPath != null) {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val result = operations.installPlugin(jarPath)
                if (result.isFailure) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Install failed"
                    )
                } else {
                    refresh()
                }
            }
        }
    }

    /**
     * Install a plugin from the remote repository.
     */
    fun installFromRemote(pluginId: String) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = operations.installFromRemote(pluginId, null)
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Install failed"
                )
            } else {
                refresh()
            }
        }
    }

    /**
     * Install a plugin from a GitHub repository URL.
     */
    fun installFromGitHub(githubUrl: String) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = operations.fetchFromGitHub(
                    githubUrl = githubUrl,
                    buildIfNoRelease = true,
                    onProgress = { /* TODO: could add progress indicator */ },
                    onStatus = { /* TODO: could show status */ }
                )
                if (result.isFailure) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "GitHub install failed"
                    )
                } else {
                    val (jarPath, manifest) = result.getOrThrow()
                    // Pass GitHub URL and version for update tracking
                    val installResult = operations.installPlugin(
                        jarPath = jarPath,
                        sourceUrl = githubUrl.trim(),
                        version = manifest.version
                    )
                    if (installResult.isFailure) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = installResult.exceptionOrNull()?.message ?: "Install failed"
                        )
                    } else {
                        refresh()
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "GitHub install failed"
                )
            }
        }
    }

    /**
     * Uninstall a plugin with confirmation.
     */
    fun uninstallPlugin(pluginId: String) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = operations.uninstallPlugin(pluginId)
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Uninstall failed"
                )
            } else {
                refresh()
            }
        }
    }

    /**
     * Toggle plugin enabled state.
     */
    fun togglePluginEnabled(pluginId: String, enabled: Boolean) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = if (enabled) {
                operations.enablePlugin(pluginId)
            } else {
                operations.disablePlugin(pluginId)
            }
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Toggle failed"
                )
            } else {
                refresh()
            }
        }
    }

    /**
     * Update a single plugin.
     */
    fun updatePlugin(pluginId: String) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = operations.updatePlugin(pluginId)
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Update failed"
                )
            } else {
                refresh()
            }
        }
    }

    /**
     * Update all plugins.
     */
    fun updateAllPlugins() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val results = operations.updateAllPlugins()
            val failures = results.filter { it.value.isFailure }
            if (failures.isNotEmpty()) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to update ${failures.size} plugin(s)"
                )
            } else {
                refresh()
            }
        }
    }

    /**
     * Publish a plugin to the store.
     */
    fun publishPlugin(
        jarPath: String,
        pluginId: String,
        displayName: String,
        version: String,
        homepageUrl: String,
        authorName: String,
        description: String?,
        changelog: String?,
        tags: List<String>,
        iconUrl: String?,
        pluginType: String,
        apiVersion: String,
        minBossVersion: String,
        onProgress: (Float) -> Unit = {},
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = operations.publishPlugin(
                    jarPath = jarPath,
                    pluginId = pluginId,
                    displayName = displayName,
                    version = version,
                    homepageUrl = homepageUrl,
                    authorName = authorName,
                    description = description,
                    changelog = changelog,
                    tags = tags,
                    iconUrl = iconUrl,
                    pluginType = pluginType,
                    apiVersion = apiVersion,
                    minBossVersion = minBossVersion,
                    onProgress = onProgress
                )
                _state.value = _state.value.copy(isLoading = false)
                if (result.isSuccess) {
                    onSuccess(result.getOrThrow())
                    refresh()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Publish failed"
                    _state.value = _state.value.copy(error = errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Publish failed"
                )
                onError(e.message ?: "Publish failed")
            }
        }
    }

    /**
     * Browse for a plugin JAR file.
     */
    fun browseForPluginJar(onResult: (String?) -> Unit) {
        scope.launch {
            val jarPath = operations.browseForPlugin()
            onResult(jarPath)
        }
    }

    /**
     * Extract manifest from a JAR file.
     */
    fun extractManifest(jarPath: String, onResult: (ExtractedManifest?) -> Unit) {
        scope.launch {
            val manifest = operations.extractManifestFromJar(jarPath)
            onResult(manifest)
        }
    }

    /**
     * Fetch a plugin from GitHub for publishing.
     * Returns the JAR path and extracted manifest on success.
     */
    fun fetchFromGitHubForPublish(
        githubUrl: String,
        onProgress: (Float) -> Unit,
        onStatus: (String) -> Unit,
        onSuccess: (jarPath: String, manifest: ExtractedManifest) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val result = operations.fetchFromGitHub(
                    githubUrl = githubUrl,
                    buildIfNoRelease = true,
                    onProgress = onProgress,
                    onStatus = onStatus
                )
                if (result.isSuccess) {
                    val (jarPath, manifest) = result.getOrThrow()
                    onSuccess(jarPath, manifest)
                } else {
                    onError(result.exceptionOrNull()?.message ?: "Failed to fetch from GitHub")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to fetch from GitHub")
            }
        }
    }

    /**
     * Refresh all data.
     */
    fun refresh() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                operations.refresh()
                operations.checkForUpdates()
                // Check admin status
                val isAdmin = try {
                    operations.isCurrentUserAdmin()
                } catch (e: Exception) {
                    false
                }
                _state.value = _state.value.copy(isLoading = false, isStoreAdmin = isAdmin)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Refresh failed"
                )
            }
        }
    }

    /**
     * Delete a plugin from the store (admin only).
     */
    fun deleteFromStore(pluginId: String) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = operations.adminDeletePlugin(pluginId)
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Delete failed"
                )
            } else {
                refresh()
            }
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Open a URL in a new browser tab.
     * Used to open plugin homepage when clicking on a plugin card.
     */
    fun openUrl(url: String) {
        if (url.isNotBlank()) {
            onOpenUrl?.invoke(url)
        }
    }

    /**
     * Update the state with new plugin data.
     */
    fun updateInstalledPlugins(plugins: List<InstalledPluginState>) {
        _state.value = _state.value.copy(installedPlugins = plugins)
    }

    /**
     * Update the state with available plugins.
     */
    fun updateAvailablePlugins(plugins: List<PluginInfo>) {
        _state.value = _state.value.copy(availablePlugins = plugins)
    }

    /**
     * Update the state with available updates.
     */
    fun updateAvailableUpdates(updates: List<UpdateInfo>) {
        _state.value = _state.value.copy(updates = updates)
    }

    @Composable
    override fun Content() {
        PluginManagerContent(this)
    }

    /**
     * Called when the component should be disposed.
     * Cancels the coroutine scope.
     */
    fun dispose() {
        scope.cancel()
    }
}
