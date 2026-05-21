package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.proto.services.CursorState
import ai.rever.boss.ipc.proto.services.SendCompositionRequest
import ai.rever.boss.ipc.proto.services.SendKeyEventRequest
import ai.rever.boss.ipc.proto.services.SendMouseEventRequest
import ai.rever.boss.ipc.proto.services.ShellEvent
import ai.rever.boss.ipc.proto.services.StreamCursorRequest
import ai.rever.boss.ipc.proto.services.StreamGridRequest
import ai.rever.boss.ipc.proto.services.StreamShellEventsRequest
import ai.rever.boss.ipc.proto.services.TerminalGridDelta
import ai.rever.boss.ipc.proto.services.TerminalServiceGrpcKt
import kotlinx.coroutines.flow.Flow

/**
 * gRPC client wrapper for the OOP terminal plugin's `TerminalService`.
 *
 * Direction note: this is the opposite of the existing `kernel/services`
 * bridge pattern. There, the kernel hosts a service and plugins call in.
 * Here, the child plugin hosts `TerminalService` (the PTY + bossterm-core
 * live there) and this class is the kernel-side client.
 *
 * Stays thin on purpose — stream lifecycle, retries, and reconnect on
 * revision-gap belong to whatever orchestrator wires multiple sessions
 * together (Phase C work).
 */
class TerminalGridClient(
    override val sessionId: String,
    private val stub: TerminalServiceGrpcKt.TerminalServiceCoroutineStub,
    private val maxFpsHint: Int = 60,
) : TerminalGridSource {

    override fun gridFrames(): Flow<TerminalGridDelta> =
        stub.streamGrid(
            StreamGridRequest.newBuilder()
                .setSessionId(sessionId)
                .setMaxFpsHint(maxFpsHint)
                .build(),
        )

    override fun cursorFrames(): Flow<CursorState> =
        stub.streamCursor(
            StreamCursorRequest.newBuilder().setSessionId(sessionId).build(),
        )

    override fun shellEvents(): Flow<ShellEvent> =
        stub.streamShellEvents(
            StreamShellEventsRequest.newBuilder().setSessionId(sessionId).build(),
        )

    override suspend fun sendKey(request: SendKeyEventRequest) {
        stub.sendKeyEvent(request)
    }

    override suspend fun sendComposition(request: SendCompositionRequest) {
        stub.sendComposition(request)
    }

    override suspend fun sendMouse(request: SendMouseEventRequest) {
        stub.sendMouseEvent(request)
    }
}
