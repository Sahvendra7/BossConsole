package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.proto.services.CellAttr
import ai.rever.boss.ipc.proto.services.CellRun
import ai.rever.boss.ipc.proto.services.CellStyle
import ai.rever.boss.ipc.proto.services.CursorShape
import ai.rever.boss.ipc.proto.services.CursorState
import ai.rever.boss.ipc.proto.services.SendCompositionRequest
import ai.rever.boss.ipc.proto.services.SendKeyEventRequest
import ai.rever.boss.ipc.proto.services.SendMouseEventRequest
import ai.rever.boss.ipc.proto.services.ShellEvent
import ai.rever.boss.ipc.proto.services.ShellEventType
import ai.rever.boss.ipc.proto.services.TerminalGridDelta
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Canned [TerminalGridSource] used during Phase B to validate the host
 * renderer without a running child JVM. Emits an initial full redraw,
 * then a slow "blinking" line so the renderer's delta path is exercised.
 *
 * Keystrokes sent via [sendKey] are echoed into the next emitted frame
 * — useful as a quick interactive smoke test (focus the panel, type,
 * see characters appear).
 */
class StubTerminalGridSource(
    override val sessionId: String = "stub",
    private val cols: Int = 80,
    private val rows: Int = 24,
) : TerminalGridSource {

    private val keystrokes = StringBuilder()

    override fun gridFrames(): Flow<TerminalGridDelta> = flow {
        var rev = 0L
        emit(fullRedraw(rev++))

        // Cursor row pulses at ~2 Hz; key-echo line repaints on demand.
        var phase = 0
        while (true) {
            delay(500)
            emit(blinkFrame(rev++, phase % 2 == 0))
            phase++
        }
    }

    override fun cursorFrames(): Flow<CursorState> = flow {
        // Cursor visible at the end of the echo row, no movement.
        emit(
            CursorState.newBuilder()
                .setSessionId(sessionId)
                .setRow(rows - 1)
                .setCol(0)
                .setVisible(true)
                .setShape(CursorShape.CURSOR_SHAPE_BLOCK)
                .setBlink(true)
                .build(),
        )
    }

    override fun shellEvents(): Flow<ShellEvent> = flow {
        emit(
            ShellEvent.newBuilder()
                .setSessionId(sessionId)
                .setType(ShellEventType.SHELL_EVENT_TYPE_PROMPT_STARTED)
                .setTimestampMs(System.currentTimeMillis())
                .build(),
        )
    }

    override suspend fun sendKey(request: SendKeyEventRequest) {
        if (request.isPress && request.text.isNotEmpty()) {
            synchronized(keystrokes) { keystrokes.append(request.text) }
        }
    }

    override suspend fun sendComposition(request: SendCompositionRequest) {
        // Stub does not implement IME composition state.
    }

    override suspend fun sendMouse(request: SendMouseEventRequest) {
        // Stub does not implement mouse interaction.
    }

    private fun fullRedraw(rev: Long): TerminalGridDelta {
        val builder = TerminalGridDelta.newBuilder()
            .setSessionId(sessionId)
            .setRevision(rev)
            .setCols(cols)
            .setRows(rows)
            .setIsFullRedraw(true)

        builder.addRowsChanged(textRun(row = 0, col = 0, "BOSS terminal — out-of-process renderer (stub)"))
        builder.addRowsChanged(
            CellRun.newBuilder()
                .setRow(2)
                .setCol(0)
                .setText("$ echo hello world")
                .also { addGraphemeStarts(it, "$ echo hello world") }
                .setStyle(
                    CellStyle.newBuilder()
                        .setFgRgba(0x4FC3F7FFu.toInt())
                        .setAttrs(CellAttr.CELL_ATTR_BOLD_VALUE)
                        .build(),
                )
                .build(),
        )
        builder.addRowsChanged(textRun(row = 3, col = 0, "hello world"))
        return builder.build()
    }

    private fun blinkFrame(rev: Long, lit: Boolean): TerminalGridDelta {
        val typed = synchronized(keystrokes) { keystrokes.toString() }
            .ifEmpty { "(focus + type to drive sendKey)" }
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
                    .setText("typed: $typed")
                    .also { addGraphemeStarts(it, "typed: $typed") }
                    .setStyle(CellStyle.newBuilder().setFgRgba(color).build())
                    .build(),
            )
            .build()
    }

    private fun textRun(row: Int, col: Int, text: String): CellRun =
        CellRun.newBuilder()
            .setRow(row)
            .setCol(col)
            .setText(text)
            .also { addGraphemeStarts(it, text) }
            .build()

    private fun addGraphemeStarts(builder: CellRun.Builder, text: String) {
        // ASCII-only stub: each codepoint is one grapheme is one byte.
        for (i in text.indices) builder.addGraphemeStarts(i)
    }
}
