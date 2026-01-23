package ai.rever.boss.window

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handler for menu actions that need to be processed by BossApp.
 *
 * This provides a communication bridge between the MenuBar (in BossWindow)
 * and the app logic (in BossApp) which are at different levels in the
 * composition tree.
 *
 * Similar to WindowOperations, this uses a flow-based event system to
 * decouple the menu UI from the business logic.
 */
object MenuActionsHandler {
    private val _newTabEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val newTabEvents: SharedFlow<String> = _newTabEvents.asSharedFlow()

    private val _closeTabEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val closeTabEvents: SharedFlow<String> = _closeTabEvents.asSharedFlow()

    private val _zoomInEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val zoomInEvents: SharedFlow<String> = _zoomInEvents.asSharedFlow()

    private val _zoomOutEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val zoomOutEvents: SharedFlow<String> = _zoomOutEvents.asSharedFlow()

    private val _actualSizeEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val actualSizeEvents: SharedFlow<String> = _actualSizeEvents.asSharedFlow()

    private val _openProjectEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val openProjectEvents: SharedFlow<String> = _openProjectEvents.asSharedFlow()

    private val _openFileEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val openFileEvents: SharedFlow<String> = _openFileEvents.asSharedFlow()

    private val _newTerminalEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val newTerminalEvents: SharedFlow<String> = _newTerminalEvents.asSharedFlow()

    private val _selectWorkspaceEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val selectWorkspaceEvents: SharedFlow<String> = _selectWorkspaceEvents.asSharedFlow()

    private val _openSettingsEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val openSettingsEvents: SharedFlow<String> = _openSettingsEvents.asSharedFlow()

    // Global settings event (opens settings in any active window, optionally to a specific section)
    private val _globalOpenSettingsEvents = MutableSharedFlow<String?>(extraBufferCapacity = 10)
    val globalOpenSettingsEvents: SharedFlow<String?> = _globalOpenSettingsEvents.asSharedFlow()

    private val _toggleFocusModeEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val toggleFocusModeEvents: SharedFlow<String> = _toggleFocusModeEvents.asSharedFlow()

    private val _splitVerticallyEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val splitVerticallyEvents: SharedFlow<String> = _splitVerticallyEvents.asSharedFlow()

    private val _splitHorizontallyEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val splitHorizontallyEvents: SharedFlow<String> = _splitHorizontallyEvents.asSharedFlow()

    private val _revealPluginEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 10)
    val revealPluginEvents: SharedFlow<Pair<String, String>> = _revealPluginEvents.asSharedFlow()

    private val _reloadBrowserEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val reloadBrowserEvents: SharedFlow<String> = _reloadBrowserEvents.asSharedFlow()

    private val _saveWorkspaceEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val saveWorkspaceEvents: SharedFlow<String> = _saveWorkspaceEvents.asSharedFlow()

    private val _openCodebaseEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val openCodebaseEvents: SharedFlow<String> = _openCodebaseEvents.asSharedFlow()

    private val _navigatePanelLeftEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val navigatePanelLeftEvents: SharedFlow<String> = _navigatePanelLeftEvents.asSharedFlow()

    private val _navigatePanelRightEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val navigatePanelRightEvents: SharedFlow<String> = _navigatePanelRightEvents.asSharedFlow()

    private val _navigatePanelUpEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val navigatePanelUpEvents: SharedFlow<String> = _navigatePanelUpEvents.asSharedFlow()

    private val _navigatePanelDownEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val navigatePanelDownEvents: SharedFlow<String> = _navigatePanelDownEvents.asSharedFlow()

    // State for enabling/disabling split menu items per window (windowId -> hasActiveTabs)
    private val _splitEnabledState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val splitEnabledState: StateFlow<Map<String, Boolean>> = _splitEnabledState.asStateFlow()

    /**
     * Update whether split is enabled for a window.
     * Split should be enabled when there are tabs in the active panel.
     *
     * @param windowId The window ID
     * @param enabled Whether split should be enabled
     */
    fun updateSplitEnabled(windowId: String, enabled: Boolean) {
        _splitEnabledState.value = _splitEnabledState.value + (windowId to enabled)
    }

    /**
     * Check if split is enabled for a window.
     *
     * @param windowId The window ID
     * @return True if split is enabled (has active tabs), false otherwise
     */
    fun isSplitEnabled(windowId: String): Boolean {
        return _splitEnabledState.value[windowId] ?: false
    }

    /**
     * Trigger a "New Tab" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerNewTab(windowId: String) {
        _newTabEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Close Tab" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerCloseTab(windowId: String) {
        _closeTabEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Zoom In" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerZoomIn(windowId: String) {
        _zoomInEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Zoom Out" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerZoomOut(windowId: String) {
        _zoomOutEvents.tryEmit(windowId)
    }

    /**
     * Trigger an "Actual Size" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerActualSize(windowId: String) {
        _actualSizeEvents.tryEmit(windowId)
    }

    /**
     * Trigger an "Open Project" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerOpenProject(windowId: String) {
        _openProjectEvents.tryEmit(windowId)
    }

    /**
     * Trigger an "Open File" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerOpenFile(windowId: String) {
        _openFileEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "New Terminal" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerNewTerminal(windowId: String) {
        _newTerminalEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Select Workspace" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerSelectWorkspace(windowId: String) {
        _selectWorkspaceEvents.tryEmit(windowId)
    }

    /**
     * Trigger an "Open Settings" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerOpenSettings(windowId: String) {
        _openSettingsEvents.tryEmit(windowId)
    }

    /**
     * Trigger a global "Open Settings" action.
     * This opens settings in any active window (used by terminal panels, etc.)
     *
     * @param section Optional section name to navigate to (e.g., "TERMINAL", "FLUCK")
     */
    fun triggerGlobalOpenSettings(section: String? = null) {
        _globalOpenSettingsEvents.tryEmit(section)
    }

    /**
     * Trigger a "Toggle Focus Mode" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerToggleFocusMode(windowId: String) {
        _toggleFocusModeEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Split Vertically" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerSplitVertically(windowId: String) {
        _splitVerticallyEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Split Horizontally" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerSplitHorizontally(windowId: String) {
        _splitHorizontallyEvents.tryEmit(windowId)
    }


    private val _applyWorkspaceEvents = MutableSharedFlow<Pair<String, ai.rever.boss.components.workspaces.LayoutWorkspace>>(extraBufferCapacity = 10)
    val applyWorkspaceEvents: SharedFlow<Pair<String, ai.rever.boss.components.workspaces.LayoutWorkspace>> = _applyWorkspaceEvents.asSharedFlow()

    /**
     * Trigger an "Apply Workspace" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     * @param workspace The workspace to apply
     */
    fun triggerApplyWorkspace(windowId: String, workspace: ai.rever.boss.components.workspaces.LayoutWorkspace) {
        _applyWorkspaceEvents.tryEmit(Pair(windowId, workspace))
    }

    /**
     * Trigger a "Reveal Plugin" action for the specified window and plugin.
     *
     * @param windowId The ID of the window where the action was triggered
     * @param pluginId The ID of the plugin to reveal
     */
    fun triggerRevealPlugin(windowId: String, pluginId: String) {
        _revealPluginEvents.tryEmit(Pair(windowId, pluginId))
    }

    /**
     * Trigger a "Reload Browser" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerReloadBrowser(windowId: String) {
        _reloadBrowserEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Save Workspace" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerSaveWorkspace(windowId: String) {
        _saveWorkspaceEvents.tryEmit(windowId)
    }

    /**
     * Trigger an "Open Codebase" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerOpenCodebase(windowId: String) {
        _openCodebaseEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Navigate Panel Left" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerNavigatePanelLeft(windowId: String) {
        _navigatePanelLeftEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Navigate Panel Right" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerNavigatePanelRight(windowId: String) {
        _navigatePanelRightEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Navigate Panel Up" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerNavigatePanelUp(windowId: String) {
        _navigatePanelUpEvents.tryEmit(windowId)
    }

    /**
     * Trigger a "Navigate Panel Down" action for the specified window.
     *
     * @param windowId The ID of the window where the action was triggered
     */
    fun triggerNavigatePanelDown(windowId: String) {
        _navigatePanelDownEvents.tryEmit(windowId)
    }
}
