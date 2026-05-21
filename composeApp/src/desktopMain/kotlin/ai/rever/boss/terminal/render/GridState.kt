package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.proto.services.CellRun
import ai.rever.boss.ipc.proto.services.CursorShape
import ai.rever.boss.ipc.proto.services.CursorState
import ai.rever.boss.ipc.proto.services.TerminalGridDelta
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicLong

/**
 * Host-side mirror of the cell grid owned by an out-of-process terminal
 * plugin. Pure logic — no Compose UI here, no bossterm imports.
 *
 * Apply [TerminalGridDelta]s with [applyDelta]; the renderer reads
 * [frame] (Compose-observable) to repaint. Cursor and revision are kept
 * separate so cursor blink doesn't invalidate the cell grid.
 *
 * Thread-safety: [applyDelta] is expected to be invoked from a single
 * stream-collector coroutine. Readers (Compose) can read concurrently
 * — the published [frame] reference is immutable.
 */
@Stable
class GridState {

    /** Snapshot exposed to the renderer. Immutable per emission. */
    @Stable
    data class Frame(
        val cols: Int,
        val rows: Int,
        val revision: Long,
        val isAlternateBuffer: Boolean,
        /** [rows] rows of [cols] cells, in row-major order. */
        val cells: Array<Cell>,
    ) {
        fun cellAt(row: Int, col: Int): Cell = cells[row * cols + col]

        override fun equals(other: Any?): Boolean =
            other is Frame && revision == other.revision && cols == other.cols && rows == other.rows

        override fun hashCode(): Int = revision.hashCode()
    }

    /**
     * One terminal cell. [text] is empty for a continuation half of a
     * wide grapheme. [styleIndex] is an index into a flyweight style
     * table held by [GridState] to keep [Cell] tiny.
     */
    @Stable
    data class Cell(
        val text: String,
        val styleIndex: Int,
    )

    @Stable
    data class CursorFrame(
        val row: Int,
        val col: Int,
        val visible: Boolean,
        val shape: CursorShape,
        val blink: Boolean,
    )

    var frame: Frame by mutableStateOf(EMPTY_FRAME)
        private set

    var cursor: CursorFrame by mutableStateOf(DEFAULT_CURSOR)
        private set

    private val styles = StyleTable()

    /** Last revision applied. Used to detect stream drops. */
    val lastRevision: Long get() = frame.revision

    private val lastSeenRevision = AtomicLong(-1L)

    /**
     * Apply [delta] to the mirror. Returns true if applied, false if
     * the caller should drop and resubscribe (e.g. force a full redraw).
     *
     * Rules:
     *  - A full redraw is always accepted; it resets the grid and the
     *    revision watermark to the delta's revision.
     *  - An incremental delta is accepted only when `rev == previous + 1`.
     *    Duplicates (`rev == previous`), out-of-order frames
     *    (`rev < previous`), and gaps (`rev > previous + 1`) are all
     *    rejected, and the watermark is left untouched.
     *  - An incremental delta arriving before any full redraw is also
     *    rejected — there is no baseline to apply on top of.
     */
    fun applyDelta(delta: TerminalGridDelta): Boolean {
        val rev = delta.revision
        val previous = lastSeenRevision.get()
        if (!delta.isFullRedraw) {
            if (previous < 0) {
                logger.trace(
                    LogCategory.TERMINAL,
                    "Rejecting incremental delta before any full redraw",
                    mapOf("revision" to rev),
                )
                return false
            }
            if (rev != previous + 1) {
                logger.trace(
                    LogCategory.TERMINAL,
                    "Rejecting non-contiguous incremental delta",
                    mapOf("revision" to rev, "expected" to previous + 1),
                )
                return false
            }
        }
        lastSeenRevision.set(rev)

        val cols = delta.cols.coerceAtLeast(1)
        val rows = delta.rows.coerceAtLeast(1)
        val previousFrame = frame
        val cells: Array<Cell> = if (
            delta.isFullRedraw ||
            previousFrame.cols != cols ||
            previousFrame.rows != rows
        ) {
            Array(rows * cols) { BLANK_CELL }
        } else {
            previousFrame.cells.copyOf()
        }

        for (run in delta.rowsChangedList) {
            writeRun(cells, cols, rows, run)
        }

        frame = Frame(
            cols = cols,
            rows = rows,
            revision = rev,
            isAlternateBuffer = delta.isAlternateBuffer,
            cells = cells,
        )
        return true
    }

    fun applyCursor(state: CursorState) {
        cursor = CursorFrame(
            row = state.row,
            col = state.col,
            visible = state.visible,
            shape = if (state.shape == CursorShape.CURSOR_SHAPE_UNSPECIFIED) CursorShape.CURSOR_SHAPE_BLOCK else state.shape,
            blink = state.blink,
        )
    }

    /** Look up the style backing a cell. */
    fun styleOf(cell: Cell): ResolvedStyle = styles[cell.styleIndex]

    /** Reset to the empty state. Used when reconnecting after a drop. */
    fun reset() {
        lastSeenRevision.set(-1L)
        styles.reset()
        frame = EMPTY_FRAME
        cursor = DEFAULT_CURSOR
    }

    private fun writeRun(cells: Array<Cell>, cols: Int, rows: Int, run: CellRun) {
        val row = run.row
        if (row < 0 || row >= rows) return
        val resolved = ResolvedStyle(
            fgRgba = run.style.fgRgba,
            bgRgba = run.style.bgRgba,
            attrs = run.style.attrs,
        )
        val styleIdx = styles.intern(resolved)

        val text = run.text
        val starts = run.graphemeStartsList
        if (text.isEmpty() || starts.isEmpty()) return

        var col = run.col
        for (i in starts.indices) {
            if (col >= cols) break
            val from = starts[i]
            val to = if (i + 1 < starts.size) starts[i + 1] else text.length
            if (from < 0 || to > text.length || from >= to) continue
            val grapheme = text.substring(from, to)
            cells[row * cols + col] = Cell(grapheme, styleIdx)
            col++
        }
    }

    companion object {
        private val logger = BossLogger.forComponent("TerminalGridState")
        private val BLANK_CELL = Cell(text = " ", styleIndex = 0)
        private val DEFAULT_CURSOR =
            CursorFrame(0, 0, visible = true, shape = CursorShape.CURSOR_SHAPE_BLOCK, blink = true)
        private val EMPTY_FRAME = Frame(
            cols = 1,
            rows = 1,
            revision = -1L,
            isAlternateBuffer = false,
            cells = arrayOf(Cell(" ", 0)),
        )
    }
}

/** Style values resolved from proto, kept in a flyweight table inside [GridState]. */
@Stable
data class ResolvedStyle(
    val fgRgba: Int,
    val bgRgba: Int,
    val attrs: Int,
)

private class StyleTable {
    private val byIndex = ArrayList<ResolvedStyle>().apply {
        add(DEFAULT_STYLE) // index 0 reserved for blank cells
    }
    private val byStyle = HashMap<ResolvedStyle, Int>().apply {
        put(DEFAULT_STYLE, 0)
    }

    fun intern(style: ResolvedStyle): Int =
        byStyle.getOrPut(style) {
            byIndex.add(style)
            byIndex.size - 1
        }

    operator fun get(index: Int): ResolvedStyle =
        byIndex.getOrElse(index) { DEFAULT_STYLE }

    fun reset() {
        byIndex.clear()
        byStyle.clear()
        byIndex.add(DEFAULT_STYLE)
        byStyle[DEFAULT_STYLE] = 0
    }

    companion object {
        private val DEFAULT_STYLE = ResolvedStyle(fgRgba = 0, bgRgba = 0, attrs = 0)
    }
}
