package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.proto.services.CursorState
import ai.rever.boss.ipc.proto.services.SendCompositionRequest
import ai.rever.boss.ipc.proto.services.SendKeyEventRequest
import ai.rever.boss.ipc.proto.services.SendMouseEventRequest
import ai.rever.boss.ipc.proto.services.ShellEvent
import ai.rever.boss.ipc.proto.services.TerminalGridDelta
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the source of terminal grid frames seen by the host
 * renderer. The real implementation ([TerminalGridClient]) wraps a gRPC
 * coroutine stub against the child plugin's `TerminalService`. A
 * canned-data implementation ([StubTerminalGridSource]) is used during
 * Phase B to validate the renderer without spawning a child JVM.
 *
 * Streams are *cold* — collecting them subscribes; cancelling the
 * collector closes the underlying RPC. Input methods are fire-and-forget
 * `suspend` calls.
 */
interface TerminalGridSource {
    val sessionId: String

    fun gridFrames(): Flow<TerminalGridDelta>
    fun cursorFrames(): Flow<CursorState>
    fun shellEvents(): Flow<ShellEvent>

    suspend fun sendKey(request: SendKeyEventRequest)
    suspend fun sendComposition(request: SendCompositionRequest)
    suspend fun sendMouse(request: SendMouseEventRequest)
}
