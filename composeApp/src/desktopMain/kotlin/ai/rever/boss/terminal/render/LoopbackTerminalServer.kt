package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.BossIpcServer
import ai.rever.boss.ipc.IpcAddressResolver
import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.CellAttr
import ai.rever.boss.ipc.proto.services.CellRun
import ai.rever.boss.ipc.proto.services.CellStyle
import ai.rever.boss.ipc.proto.services.CursorShape
import ai.rever.boss.ipc.proto.services.CursorState
import ai.rever.boss.ipc.proto.services.SendCompositionRequest
import ai.rever.boss.ipc.proto.services.SendKeyEventRequest
import ai.rever.boss.ipc.proto.services.SendMouseEventRequest
import ai.rever.boss.ipc.proto.services.SetThemeRequest
import ai.rever.boss.ipc.proto.services.ShellEvent
import ai.rever.boss.ipc.proto.services.ShellEventType
import ai.rever.boss.ipc.proto.services.StreamCursorRequest
import ai.rever.boss.ipc.proto.services.StreamGridRequest
import ai.rever.boss.ipc.proto.services.StreamScrollbackRequest
import ai.rever.boss.ipc.proto.services.StreamShellEventsRequest
import ai.rever.boss.ipc.proto.services.TerminalGridDelta
import ai.rever.boss.ipc.proto.services.TerminalServiceGrpcKt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-process loopback `TerminalService` server. Hosts the same canned
 * terminal content that [StubTerminalGridSource] emits, but exposes it
 * over real gRPC so the host's [TerminalGridConnection] / channel /
 * stub pipeline gets exercised end-to-end inside BossConsole's JVM
 * without spawning a child plugin.
 *
 * Intended for tests and Phase C dev tooling. Production code paths
 * obtain real channels from the OOP plugin spawner, not from here.
 */
class LoopbackTerminalServer(
    private val cols: Int = 80,
    private val rows: Int = 24,
    /** Set to a stable token if you want multiple loopback servers per JVM. */
    instanceTag: String = "default",
) : AutoCloseable {

    val address: String = IpcAddressResolver.resolveAddress("loopback-terminal", instanceTag)
    private val service = TerminalServiceImpl()
    private val server = BossIpcServer(address).addService(service)
    private var started = false

    /** Start the gRPC server. Idempotent. */
    fun start(): LoopbackTerminalServer {
        if (!started) {
            server.start()
            started = true
        }
        return this
    }

    /** Number of `sendKeyEvent` calls received since [start]. Useful for assertions. */
    val keyEventCount: Int get() = service.keyEventCount

    /** Most recent typed-text accumulated from `sendKeyEvent`. */
    val lastTypedText: String get() = service.typed()

    override fun close() {
        if (started) {
            server.stop()
            started = false
        }
    }

    private inner class TerminalServiceImpl :
        TerminalServiceGrpcKt.TerminalServiceCoroutineImplBase() {

        private val keystrokes = StringBuilder()
        private val _keyEventCount = AtomicInteger(0)

        val keyEventCount: Int get() = _keyEventCount.get()
        fun typed(): String = synchronized(keystrokes) { keystrokes.toString() }

        override fun streamGrid(request: StreamGridRequest): Flow<TerminalGridDelta> = flow {
            val sessionId = request.sessionId
            var rev = 0L
            emit(fullRedraw(sessionId, rev++))
            var phase = 0
            while (true) {
                delay(500)
                emit(blinkFrame(sessionId, rev++, phase % 2 == 0))
                phase++
            }
        }

        override fun streamCursor(request: StreamCursorRequest): Flow<CursorState> = flow {
            emit(
                CursorState.newBuilder()
                    .setSessionId(request.sessionId)
                    .setRow(rows - 1)
                    .setCol(0)
                    .setVisible(true)
                    .setShape(CursorShape.CURSOR_SHAPE_BLOCK)
                    .setBlink(true)
                    .build(),
            )
        }

        override fun streamScrollback(request: StreamScrollbackRequest): Flow<Nothing> = flow {
            // Empty scrollback in the canned data set.
        }

        override fun streamShellEvents(request: StreamShellEventsRequest): Flow<ShellEvent> = flow {
            emit(
                ShellEvent.newBuilder()
                    .setSessionId(request.sessionId)
                    .setType(ShellEventType.SHELL_EVENT_TYPE_PROMPT_STARTED)
                    .setTimestampMs(System.currentTimeMillis())
                    .build(),
            )
        }

        override suspend fun sendKeyEvent(request: SendKeyEventRequest): Empty {
            _keyEventCount.incrementAndGet()
            if (request.isPress && request.text.isNotEmpty()) {
                synchronized(keystrokes) { keystrokes.append(request.text) }
            }
            return Empty.getDefaultInstance()
        }

        override suspend fun sendComposition(request: SendCompositionRequest): Empty =
            Empty.getDefaultInstance()

        override suspend fun sendMouseEvent(request: SendMouseEventRequest): Empty =
            Empty.getDefaultInstance()

        override suspend fun setTheme(request: SetThemeRequest): Empty =
            Empty.getDefaultInstance()

        private fun fullRedraw(sessionId: String, rev: Long): TerminalGridDelta {
            val builder = TerminalGridDelta.newBuilder()
                .setSessionId(sessionId)
                .setRevision(rev)
                .setCols(cols)
                .setRows(rows)
                .setIsFullRedraw(true)

            builder.addRowsChanged(plainRun(0, 0, "BOSS terminal — loopback gRPC server (Phase C)"))
            builder.addRowsChanged(
                CellRun.newBuilder()
                    .setRow(2)
                    .setCol(0)
                    .setText("$ echo over-the-wire")
                    .also { addAsciiGraphemeStarts(it, "$ echo over-the-wire") }
                    .setStyle(
                        CellStyle.newBuilder()
                            .setFgRgba(0x4FC3F7FFu.toInt())
                            .setAttrs(CellAttr.CELL_ATTR_BOLD_VALUE)
                            .build(),
                    )
                    .build(),
            )
            builder.addRowsChanged(plainRun(3, 0, "over-the-wire"))
            return builder.build()
        }

        private fun blinkFrame(sessionId: String, rev: Long, lit: Boolean): TerminalGridDelta {
            val typed = typed().ifEmpty { "(focus + type to drive sendKeyEvent over gRPC)" }
            val color = if (lit) 0xFFEB3BFFu.toInt() else 0x9E9E9EFFu.toInt()
            return TerminalGridDelta.newBuilder()
                .setSessionId(sessionId)
                .setRevision(rev)
                .setCols(cols)
                .setRows(rows)
                .addRowsChanged(
                    CellRun.newBuilder()
                        .setRow(rows - 1)
                        .setCol(0)
                        .setText("typed (wire): $typed")
                        .also { addAsciiGraphemeStarts(it, "typed (wire): $typed") }
                        .setStyle(CellStyle.newBuilder().setFgRgba(color).build())
                        .build(),
                )
                .build()
        }

        private fun plainRun(row: Int, col: Int, text: String): CellRun =
            CellRun.newBuilder()
                .setRow(row)
                .setCol(col)
                .setText(text)
                .also { addAsciiGraphemeStarts(it, text) }
                .setStyle(CellStyle.getDefaultInstance())
                .build()

        private fun addAsciiGraphemeStarts(builder: CellRun.Builder, text: String) {
            for (i in text.indices) builder.addGraphemeStarts(i)
        }
    }
}
