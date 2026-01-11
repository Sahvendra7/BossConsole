package ai.rever.boss.components.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event for opening a git command in the terminal.
 *
 * @param command The full git command to execute (including 'git' prefix)
 * @param workingDirectory The working directory for the command
 * @param operationName Human-readable name for the operation (e.g., "Pull", "Push")
 */
data class GitTerminalOpenEvent(
    val command: String,
    val workingDirectory: String,
    val operationName: String
)

/**
 * Event bus for running Git commands in the terminal.
 *
 * This allows git operations to be executed in the terminal panel,
 * providing real-time output visibility for operations like pull, push,
 * merge, rebase, etc.
 *
 * Similar to RunnerTerminalEventBus but for git operations.
 */
object GitTerminalEventBus {

    private val _openEvents = MutableSharedFlow<GitTerminalOpenEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val openEvents: SharedFlow<GitTerminalOpenEvent> = _openEvents.asSharedFlow()

    /**
     * Emit event to open a git command in the terminal.
     *
     * @param command The full git command to execute
     * @param workingDirectory The working directory for the command
     * @param operationName Human-readable name for the operation
     */
    suspend fun openGitTerminal(
        command: String,
        workingDirectory: String,
        operationName: String
    ) {
        _openEvents.emit(
            GitTerminalOpenEvent(
                command = command,
                workingDirectory = workingDirectory,
                operationName = operationName
            )
        )
    }
}
