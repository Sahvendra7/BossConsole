@file:Suppress("UNUSED")
package ai.rever.boss.components.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Re-export from plugin-git-types module for backward compatibility.
 * New code should import directly from ai.rever.boss.plugin.git
 */
typealias GitTerminalOpenEvent = ai.rever.boss.plugin.git.GitTerminalOpenEvent

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
     * @param sourceWindowId The window ID that initiated the event
     */
    suspend fun openGitTerminal(
        command: String,
        workingDirectory: String,
        operationName: String,
        sourceWindowId: String
    ) {
        _openEvents.emit(
            GitTerminalOpenEvent(
                command = command,
                workingDirectory = workingDirectory,
                operationName = operationName,
                sourceWindowId = sourceWindowId
            )
        )
    }
}
