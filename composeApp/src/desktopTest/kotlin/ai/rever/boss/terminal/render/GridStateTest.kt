package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.proto.services.CellAttr
import ai.rever.boss.ipc.proto.services.CellRun
import ai.rever.boss.ipc.proto.services.CellStyle
import ai.rever.boss.ipc.proto.services.CursorShape
import ai.rever.boss.ipc.proto.services.CursorState
import ai.rever.boss.ipc.proto.services.TerminalGridDelta
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GridStateTest {

    @Test
    fun `full redraw populates the grid from blank`() {
        val state = GridState()
        val delta = delta(rev = 0, cols = 10, rows = 3, fullRedraw = true) {
            addRowsChanged(asciiRun(row = 1, col = 0, "hello", fgRgba = 0xFFFFFFFFu.toInt()))
        }

        assertTrue(state.applyDelta(delta))
        val frame = state.frame
        assertEquals(10, frame.cols)
        assertEquals(3, frame.rows)
        assertEquals(0L, frame.revision)
        assertEquals("h", frame.cellAt(1, 0).text)
        assertEquals("o", frame.cellAt(1, 4).text)
        // Untouched cells stay blank.
        assertEquals(" ", frame.cellAt(0, 0).text)
        assertEquals(" ", frame.cellAt(1, 5).text)
    }

    @Test
    fun `incremental delta preserves previously written rows`() {
        val state = GridState()
        state.applyDelta(
            delta(rev = 0, cols = 10, rows = 2, fullRedraw = true) {
                addRowsChanged(asciiRun(0, 0, "line-one"))
            },
        )

        state.applyDelta(
            delta(rev = 1, cols = 10, rows = 2, fullRedraw = false) {
                addRowsChanged(asciiRun(1, 0, "line-two"))
            },
        )

        val frame = state.frame
        assertEquals(1L, frame.revision)
        assertEquals("l", frame.cellAt(0, 0).text)
        assertEquals("e", frame.cellAt(0, 3).text)
        // line-two[5] is 't'
        assertEquals("t", frame.cellAt(1, 5).text)
    }

    @Test
    fun `revision gap is rejected so caller can resubscribe`() {
        val state = GridState()
        state.applyDelta(
            delta(rev = 0, cols = 4, rows = 1, fullRedraw = true) {
                addRowsChanged(asciiRun(0, 0, "abcd"))
            },
        )

        val gapped = delta(rev = 3, cols = 4, rows = 1, fullRedraw = false) {
            addRowsChanged(asciiRun(0, 0, "wxyz"))
        }
        assertFalse(state.applyDelta(gapped))

        // Old contents untouched after the rejection.
        assertEquals("a", state.frame.cellAt(0, 0).text)
        assertEquals(0L, state.frame.revision)
    }

    @Test
    fun `duplicate revision is rejected and watermark not lowered`() {
        val state = GridState()
        state.applyDelta(delta(rev = 5, cols = 4, rows = 1, fullRedraw = true) {
            addRowsChanged(asciiRun(0, 0, "abcd"))
        })

        // Duplicate of the current revision must be rejected; watermark stays at 5
        // so the next contiguous incremental (rev=6) still applies cleanly.
        val dup = delta(rev = 5, cols = 4, rows = 1, fullRedraw = false) {
            addRowsChanged(asciiRun(0, 0, "xxxx"))
        }
        assertFalse(state.applyDelta(dup))
        assertEquals("a", state.frame.cellAt(0, 0).text)
        assertEquals(5L, state.frame.revision)

        val next = delta(rev = 6, cols = 4, rows = 1, fullRedraw = false) {
            addRowsChanged(asciiRun(0, 0, "next"))
        }
        assertTrue(state.applyDelta(next))
        assertEquals("n", state.frame.cellAt(0, 0).text)
        assertEquals(6L, state.frame.revision)
    }

    @Test
    fun `out-of-order incremental is rejected without lowering watermark`() {
        val state = GridState()
        state.applyDelta(delta(rev = 5, cols = 4, rows = 1, fullRedraw = true) {
            addRowsChanged(asciiRun(0, 0, "abcd"))
        })

        // rev=4 (older than current) — must not be accepted, must not lower the
        // watermark from 5. Then a real follow-on rev=6 must still apply.
        val older = delta(rev = 4, cols = 4, rows = 1, fullRedraw = false) {
            addRowsChanged(asciiRun(0, 0, "old!"))
        }
        assertFalse(state.applyDelta(older))
        assertEquals(5L, state.frame.revision)

        val next = delta(rev = 6, cols = 4, rows = 1, fullRedraw = false) {
            addRowsChanged(asciiRun(0, 0, "next"))
        }
        assertTrue(state.applyDelta(next))
        assertEquals(6L, state.frame.revision)
    }

    @Test
    fun `incremental before any full redraw is rejected`() {
        val state = GridState()
        // No prior frame — sender must lead with a full redraw. A naked
        // incremental has no baseline and must be rejected.
        val incremental = delta(rev = 0, cols = 4, rows = 1, fullRedraw = false) {
            addRowsChanged(asciiRun(0, 0, "abcd"))
        }
        assertFalse(state.applyDelta(incremental))
        // Frame stays at the empty default.
        assertEquals(-1L, state.lastRevision)
    }

    @Test
    fun `full redraw recovers from a revision gap`() {
        val state = GridState()
        state.applyDelta(delta(0, 4, 1, fullRedraw = true) { addRowsChanged(asciiRun(0, 0, "abcd")) })
        state.applyDelta(delta(99, 4, 1, fullRedraw = false) { addRowsChanged(asciiRun(0, 0, "skip")) })
        // Sender notices the gap → emits a full redraw at the new revision.
        assertTrue(
            state.applyDelta(delta(100, 4, 1, fullRedraw = true) { addRowsChanged(asciiRun(0, 0, "back")) }),
        )
        assertEquals("b", state.frame.cellAt(0, 0).text)
        assertEquals("k", state.frame.cellAt(0, 3).text)
    }

    @Test
    fun `cells inherit style attrs from CellRun`() {
        val state = GridState()
        state.applyDelta(
            delta(0, 6, 1, fullRedraw = true) {
                addRowsChanged(
                    CellRun.newBuilder()
                        .setRow(0)
                        .setCol(0)
                        .setText("bold!")
                        .addAllGraphemeStarts(listOf(0, 1, 2, 3, 4))
                        .setStyle(
                            CellStyle.newBuilder()
                                .setFgRgba(0xFF0000FFu.toInt())
                                .setAttrs(CellAttr.CELL_ATTR_BOLD_VALUE)
                                .build(),
                        )
                        .build(),
                )
            },
        )

        val cell = state.frame.cellAt(0, 0)
        val style = state.styleOf(cell)
        assertEquals(0xFF0000FFu.toInt(), style.fgRgba)
        assertEquals(CellAttr.CELL_ATTR_BOLD_VALUE, style.attrs)
    }

    @Test
    fun `applyCursor publishes a stable cursor frame`() {
        val state = GridState()
        state.applyCursor(
            CursorState.newBuilder()
                .setSessionId("s")
                .setRow(4)
                .setCol(9)
                .setVisible(true)
                .setShape(CursorShape.CURSOR_SHAPE_BAR)
                .setBlink(false)
                .build(),
        )
        val c = state.cursor
        assertEquals(4, c.row)
        assertEquals(9, c.col)
        assertEquals(CursorShape.CURSOR_SHAPE_BAR, c.shape)
        assertFalse(c.blink)
        assertTrue(c.visible)
    }

    @Test
    fun `reset clears revision tracking so a subsequent rev1 is accepted`() {
        val state = GridState()
        state.applyDelta(delta(0, 4, 1, fullRedraw = true) { addRowsChanged(asciiRun(0, 0, "abcd")) })
        state.reset()
        // After reset, the very next delta (any rev, fullRedraw) must apply.
        assertTrue(state.applyDelta(delta(7, 4, 1, fullRedraw = true) { addRowsChanged(asciiRun(0, 0, "next")) }))
        assertEquals("n", state.frame.cellAt(0, 0).text)
        assertEquals(7L, state.frame.revision)
    }

    @Test
    fun `style interning shares an index for identical styles across rows`() {
        val state = GridState()
        val style = CellStyle.newBuilder().setFgRgba(0xABCDEFFFu.toInt()).build()
        state.applyDelta(
            delta(0, 5, 2, fullRedraw = true) {
                addRowsChanged(styledRun(0, 0, "hello", style))
                addRowsChanged(styledRun(1, 0, "world", style))
            },
        )

        val a = state.frame.cellAt(0, 0)
        val b = state.frame.cellAt(1, 0)
        assertEquals(a.styleIndex, b.styleIndex)
    }

    private fun delta(
        rev: Long,
        cols: Int,
        rows: Int,
        fullRedraw: Boolean,
        block: TerminalGridDelta.Builder.() -> Unit,
    ): TerminalGridDelta = TerminalGridDelta.newBuilder()
        .setSessionId("s")
        .setRevision(rev)
        .setCols(cols)
        .setRows(rows)
        .setIsFullRedraw(fullRedraw)
        .apply(block)
        .build()

    private fun asciiRun(row: Int, col: Int, text: String, fgRgba: Int = 0): CellRun =
        styledRun(row, col, text, CellStyle.newBuilder().setFgRgba(fgRgba).build())

    private fun styledRun(row: Int, col: Int, text: String, style: CellStyle): CellRun {
        val builder = CellRun.newBuilder()
            .setRow(row)
            .setCol(col)
            .setText(text)
            .setStyle(style)
        for (i in text.indices) builder.addGraphemeStarts(i)
        return builder.build()
    }
}
