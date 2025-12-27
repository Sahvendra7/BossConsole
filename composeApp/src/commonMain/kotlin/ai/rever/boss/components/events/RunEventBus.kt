package ai.rever.boss.components.events

import ai.rever.boss.run.RunConfiguration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event emitted when a run configuration should be executed.
 *
 * @property configuration The run configuration to execute
 * @property debug Whether to run in debug mode (future feature)
 */
data class RunExecuteEvent(
    val configuration: RunConfiguration,
    val debug: Boolean = false
)

/**
 * Event emitted when running processes should be stopped.
 *
 * @property configId Optional config ID to stop, null means stop all
 */
data class RunStopEvent(
    val configId: String? = null
)

/**
 * Event emitted when a project should be scanned for run configurations.
 *
 * @property projectPath The path to the project to scan
 */
data class RunScanEvent(
    val projectPath: String
)

/**
 * Event bus for handling run-related events across all windows.
 *
 * Coordinates run configuration execution, stopping processes, and project scanning.
 * Each window's BossApp listens for events and the active window handles them.
 */
object RunEventBus {
    private val _executeEvents = MutableSharedFlow<RunExecuteEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val executeEvents: SharedFlow<RunExecuteEvent> = _executeEvents.asSharedFlow()

    private val _stopEvents = MutableSharedFlow<RunStopEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val stopEvents: SharedFlow<RunStopEvent> = _stopEvents.asSharedFlow()

    private val _scanEvents = MutableSharedFlow<RunScanEvent>(
        replay = 0,
        extraBufferCapacity = 5
    )
    val scanEvents: SharedFlow<RunScanEvent> = _scanEvents.asSharedFlow()

    /**
     * Emit a run execute event.
     *
     * @param configuration The run configuration to execute
     * @param debug Whether to run in debug mode
     */
    suspend fun execute(configuration: RunConfiguration, debug: Boolean = false) {
        _executeEvents.emit(RunExecuteEvent(configuration, debug))
    }

    /**
     * Emit a stop event.
     *
     * @param configId Optional config ID to stop, null means stop all
     */
    suspend fun stop(configId: String? = null) {
        _stopEvents.emit(RunStopEvent(configId))
    }

    /**
     * Emit a scan event to discover run configurations.
     *
     * @param projectPath The project path to scan
     */
    suspend fun scanProject(projectPath: String) {
        _scanEvents.emit(RunScanEvent(projectPath))
    }
}
