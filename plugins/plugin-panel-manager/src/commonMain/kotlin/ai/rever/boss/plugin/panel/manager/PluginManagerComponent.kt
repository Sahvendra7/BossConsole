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
    val jarPath: String
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
    val error: String? = null
)

/**
 * Interface for plugin management operations.
 */
interface PluginManagerOperations {
    /**
     * Install a plugin from a JAR path.
     */
    suspend fun installPlugin(jarPath: String): Result<Unit>

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
}

/**
 * Decompose component for the Plugin Manager panel.
 */
class PluginManagerComponent(
    componentContext: ComponentContext,
    private val operations: PluginManagerOperations
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
     * Refresh all data.
     */
    fun refresh() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                operations.refresh()
                operations.checkForUpdates()
                _state.value = _state.value.copy(isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Refresh failed"
                )
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
