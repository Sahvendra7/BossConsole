package ai.rever.boss.components.events

import ai.rever.boss.plugin.run.RunnerTerminalOpenEvent
import ai.rever.boss.plugin.run.RunnerTerminalStopEvent
import ai.rever.boss.plugin.run.RunnerTerminalCloseEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Re-export event types via typealiases for backward compatibility
typealias RunnerTerminalOpenEvent = ai.rever.boss.plugin.run.RunnerTerminalOpenEvent
typealias RunnerTerminalStopEvent = ai.rever.boss.plugin.run.RunnerTerminalStopEvent
typealias RunnerTerminalCloseEvent = ai.rever.boss.plugin.run.RunnerTerminalCloseEvent

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
     * @param sourceWindowId Window that initiated the stop (required for multi-window support)
     */
    suspend fun stopRunnerTerminal(terminalId: String, configId: String, sourceWindowId: String) {
        _stopEvents.emit(RunnerTerminalStopEvent(terminalId, configId, sourceWindowId))
    }

    /**
     * Emit event to close a runner terminal tab.
     * @param sourceWindowId Window that initiated the close (required for multi-window support)
     */
    suspend fun closeRunnerTerminal(terminalId: String, sourceWindowId: String) {
        _closeEvents.emit(RunnerTerminalCloseEvent(terminalId, sourceWindowId))
    }
}
