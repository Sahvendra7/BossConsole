package ai.rever.boss.components.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event emitted when a workspace should be loaded.
 *
 * @property workspacePath Path to the workspace file
 * @property sourceWindowId The window that should load the workspace (required for multi-window support)
 */
data class WorkspaceLoadEvent(
    val workspacePath: String,
    val sourceWindowId: String
)

/**
 * Event bus for workspace-related events.
 *
 * Issue #506: Added sourceWindowId for multi-window support.
 */
object WorkspaceEventBus {
    private val _workspaceLoadEvents = MutableSharedFlow<WorkspaceLoadEvent>(
        replay = 0,  // Don't replay past events to new subscribers (new windows)
        extraBufferCapacity = 10  // Buffer up to 10 events if collector not ready yet
    )
    val workspaceLoadEvents: SharedFlow<WorkspaceLoadEvent> = _workspaceLoadEvents.asSharedFlow()

    /**
     * Emit a workspace load event.
     *
     * @param workspacePath Path to the workspace file
     * @param sourceWindowId The window that should load the workspace (required for multi-window support)
     */
    suspend fun loadWorkspace(workspacePath: String, sourceWindowId: String) {
        _workspaceLoadEvents.emit(WorkspaceLoadEvent(workspacePath, sourceWindowId))
    }
}
