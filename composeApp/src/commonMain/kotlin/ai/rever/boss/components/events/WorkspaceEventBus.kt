package ai.rever.boss.components.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class WorkspaceLoadEvent(
    val workspacePath: String
)

object WorkspaceEventBus {
    private val _workspaceLoadEvents = MutableSharedFlow<WorkspaceLoadEvent>(
        replay = 0,  // Don't replay past events to new subscribers (new windows)
        extraBufferCapacity = 10  // Buffer up to 10 events if collector not ready yet
    )
    val workspaceLoadEvents: SharedFlow<WorkspaceLoadEvent> = _workspaceLoadEvents.asSharedFlow()

    suspend fun loadWorkspace(workspacePath: String) {
        _workspaceLoadEvents.emit(WorkspaceLoadEvent(workspacePath))
    }
}
