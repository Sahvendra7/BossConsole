package ai.rever.boss.components.events

import ai.rever.boss.dashboard.SplitTemplate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Event data classes with sourceWindowId for multi-window support (Issue #506)
data class DashboardOpenFileEvent(val path: String, val sourceWindowId: String)
data class DashboardOpenUrlEvent(val url: String, val sourceWindowId: String)
data class DashboardNewTabEvent(val sourceWindowId: String)
data class DashboardNewTerminalEvent(val sourceWindowId: String)
data class DashboardShowProjectDialogEvent(val sourceWindowId: String)
data class DashboardShowFileDialogEvent(val sourceWindowId: String)
data class DashboardShowNewProjectEvent(val sourceWindowId: String)
data class DashboardApplySplitTemplateEvent(val template: SplitTemplate, val sourceWindowId: String)
data class DashboardActivatePluginEvent(val pluginId: String, val sourceWindowId: String)

/**
 * Event bus for Dashboard actions triggered from Fluck tabs.
 * When a Fluck tab shows Dashboard (empty URL), actions are emitted here
 * and handled by BossApp to perform the actual operations.
 *
 * Issue #506: All events include sourceWindowId for multi-window filtering.
 */
object DashboardEventBus {
    // File operations
    private val _openFileEvents = MutableSharedFlow<DashboardOpenFileEvent>(extraBufferCapacity = 10)
    val openFileEvents: SharedFlow<DashboardOpenFileEvent> = _openFileEvents.asSharedFlow()

    // URL navigation (opens in new tab, not current Fluck tab)
    private val _openUrlInNewTabEvents = MutableSharedFlow<DashboardOpenUrlEvent>(extraBufferCapacity = 10)
    val openUrlInNewTabEvents: SharedFlow<DashboardOpenUrlEvent> = _openUrlInNewTabEvents.asSharedFlow()

    // Tab operations
    private val _newTabEvents = MutableSharedFlow<DashboardNewTabEvent>(extraBufferCapacity = 10)
    val newTabEvents: SharedFlow<DashboardNewTabEvent> = _newTabEvents.asSharedFlow()

    private val _newTerminalEvents = MutableSharedFlow<DashboardNewTerminalEvent>(extraBufferCapacity = 10)
    val newTerminalEvents: SharedFlow<DashboardNewTerminalEvent> = _newTerminalEvents.asSharedFlow()

    // Dialog triggers
    private val _showProjectDialogEvents = MutableSharedFlow<DashboardShowProjectDialogEvent>(extraBufferCapacity = 10)
    val showProjectDialogEvents: SharedFlow<DashboardShowProjectDialogEvent> = _showProjectDialogEvents.asSharedFlow()

    private val _showFileDialogEvents = MutableSharedFlow<DashboardShowFileDialogEvent>(extraBufferCapacity = 10)
    val showFileDialogEvents: SharedFlow<DashboardShowFileDialogEvent> = _showFileDialogEvents.asSharedFlow()

    private val _showNewProjectEvents = MutableSharedFlow<DashboardShowNewProjectEvent>(extraBufferCapacity = 10)
    val showNewProjectEvents: SharedFlow<DashboardShowNewProjectEvent> = _showNewProjectEvents.asSharedFlow()

    // Split templates
    private val _applySplitTemplateEvents = MutableSharedFlow<DashboardApplySplitTemplateEvent>(extraBufferCapacity = 10)
    val applySplitTemplateEvents: SharedFlow<DashboardApplySplitTemplateEvent> = _applySplitTemplateEvents.asSharedFlow()

    // Plugin activation
    private val _activatePluginEvents = MutableSharedFlow<DashboardActivatePluginEvent>(extraBufferCapacity = 10)
    val activatePluginEvents: SharedFlow<DashboardActivatePluginEvent> = _activatePluginEvents.asSharedFlow()

    // Emit functions with sourceWindowId parameter (required for multi-window support)
    suspend fun openFile(path: String, sourceWindowId: String) =
        _openFileEvents.emit(DashboardOpenFileEvent(path, sourceWindowId))
    suspend fun openUrlInNewTab(url: String, sourceWindowId: String) =
        _openUrlInNewTabEvents.emit(DashboardOpenUrlEvent(url, sourceWindowId))
    suspend fun newTab(sourceWindowId: String) =
        _newTabEvents.emit(DashboardNewTabEvent(sourceWindowId))
    suspend fun newTerminal(sourceWindowId: String) =
        _newTerminalEvents.emit(DashboardNewTerminalEvent(sourceWindowId))
    suspend fun showProjectDialog(sourceWindowId: String) =
        _showProjectDialogEvents.emit(DashboardShowProjectDialogEvent(sourceWindowId))
    suspend fun showFileDialog(sourceWindowId: String) =
        _showFileDialogEvents.emit(DashboardShowFileDialogEvent(sourceWindowId))
    suspend fun showNewProject(sourceWindowId: String) =
        _showNewProjectEvents.emit(DashboardShowNewProjectEvent(sourceWindowId))
    suspend fun applySplitTemplate(template: SplitTemplate, sourceWindowId: String) =
        _applySplitTemplateEvents.emit(DashboardApplySplitTemplateEvent(template, sourceWindowId))
    suspend fun activatePlugin(pluginId: String, sourceWindowId: String) =
        _activatePluginEvents.emit(DashboardActivatePluginEvent(pluginId, sourceWindowId))
}
