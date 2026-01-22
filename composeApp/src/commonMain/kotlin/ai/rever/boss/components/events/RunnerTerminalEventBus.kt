package ai.rever.boss.components.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event for opening a runner terminal.
 */
data class RunnerTerminalOpenEvent(
    val terminalId: String,
    val command: String,
    val configId: String,
    val configName: String,
    val workingDirectory: String?,
    val isRerun: Boolean,
    val sourceWindowId: String  // Window that initiated the run (Issue #498)
)

/**
 * Event for stopping a runner terminal (Ctrl+C request).
 */
data class RunnerTerminalStopEvent(
    val terminalId: String,
    val configId: String
)

/**
 * Event for closing a runner terminal tab.
 */
data class RunnerTerminalCloseEvent(
    val terminalId: String
)

/**
 * Event bus for runner terminal operations.
 *
 * Issue #347: Runner should open in terminal sidebar panel with run/stop state management
 */
object RunnerTerminalEventBus {

    private val _openEvents = MutableSharedFlow<RunnerTerminalOpenEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val openEvents: SharedFlow<RunnerTerminalOpenEvent> = _openEvents.asSharedFlow()

    private val _stopEvents = MutableSharedFlow<RunnerTerminalStopEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val stopEvents: SharedFlow<RunnerTerminalStopEvent> = _stopEvents.asSharedFlow()

    private val _closeEvents = MutableSharedFlow<RunnerTerminalCloseEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val closeEvents: SharedFlow<RunnerTerminalCloseEvent> = _closeEvents.asSharedFlow()

    /**
     * Emit event to open a runner terminal.
     * @param sourceWindowId Window that initiated the run (Issue #498)
     */
    suspend fun openRunnerTerminal(
        terminalId: String,
        command: String,
        configId: String,
        configName: String,
        workingDirectory: String?,
        isRerun: Boolean,
        sourceWindowId: String
    ) {
        _openEvents.emit(
            RunnerTerminalOpenEvent(
                terminalId = terminalId,
                command = command,
                configId = configId,
                configName = configName,
                workingDirectory = workingDirectory,
                isRerun = isRerun,
                sourceWindowId = sourceWindowId
            )
        )
    }

    /**
     * Emit event to stop a runner terminal (Ctrl+C request).
     */
    suspend fun stopRunnerTerminal(terminalId: String, configId: String) {
        _stopEvents.emit(RunnerTerminalStopEvent(terminalId, configId))
    }

    /**
     * Emit event to close a runner terminal tab.
     */
    suspend fun closeRunnerTerminal(terminalId: String) {
        _closeEvents.emit(RunnerTerminalCloseEvent(terminalId))
    }
}
