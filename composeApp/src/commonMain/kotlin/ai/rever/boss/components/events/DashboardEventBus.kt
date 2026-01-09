package ai.rever.boss.components.events

import ai.rever.boss.dashboard.SplitTemplate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus for Dashboard actions triggered from Fluck tabs.
 * When a Fluck tab shows Dashboard (empty URL), actions are emitted here
 * and handled by BossApp to perform the actual operations.
 */
object DashboardEventBus {
    // File operations
    private val _openFileEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val openFileEvents: SharedFlow<String> = _openFileEvents.asSharedFlow()

    // URL navigation (opens in new tab, not current Fluck tab)
    private val _openUrlInNewTabEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val openUrlInNewTabEvents: SharedFlow<String> = _openUrlInNewTabEvents.asSharedFlow()

    // Tab operations
    private val _newTabEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    val newTabEvents: SharedFlow<Unit> = _newTabEvents.asSharedFlow()

    private val _newTerminalEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    val newTerminalEvents: SharedFlow<Unit> = _newTerminalEvents.asSharedFlow()

    // Dialog triggers
    private val _showProjectDialogEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    val showProjectDialogEvents: SharedFlow<Unit> = _showProjectDialogEvents.asSharedFlow()

    private val _showFileDialogEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    val showFileDialogEvents: SharedFlow<Unit> = _showFileDialogEvents.asSharedFlow()

    private val _showNewProjectEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    val showNewProjectEvents: SharedFlow<Unit> = _showNewProjectEvents.asSharedFlow()

    // Split templates
    private val _applySplitTemplateEvents = MutableSharedFlow<SplitTemplate>(extraBufferCapacity = 10)
    val applySplitTemplateEvents: SharedFlow<SplitTemplate> = _applySplitTemplateEvents.asSharedFlow()

    // Plugin activation
    private val _activatePluginEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val activatePluginEvents: SharedFlow<String> = _activatePluginEvents.asSharedFlow()

    // Emit functions
    suspend fun openFile(path: String) = _openFileEvents.emit(path)
    suspend fun openUrlInNewTab(url: String) = _openUrlInNewTabEvents.emit(url)
    suspend fun newTab() = _newTabEvents.emit(Unit)
    suspend fun newTerminal() = _newTerminalEvents.emit(Unit)
    suspend fun showProjectDialog() = _showProjectDialogEvents.emit(Unit)
    suspend fun showFileDialog() = _showFileDialogEvents.emit(Unit)
    suspend fun showNewProject() = _showNewProjectEvents.emit(Unit)
    suspend fun applySplitTemplate(template: SplitTemplate) = _applySplitTemplateEvents.emit(template)
    suspend fun activatePlugin(pluginId: String) = _activatePluginEvents.emit(pluginId)
}
