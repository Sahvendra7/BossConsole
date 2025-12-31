package ai.rever.bosseditor.core

/**
 * Represents a position in the editor document.
 *
 * @property line The 0-based line number
 * @property column The 0-based column (character offset within the line)
 */
data class EditorPosition(
    val line: Int,
    val column: Int
) : Comparable<EditorPosition> {

    init {
        require(line >= 0) { "Line must be non-negative: $line" }
        require(column >= 0) { "Column must be non-negative: $column" }
    }

    override fun compareTo(other: EditorPosition): Int {
        val lineComparison = line.compareTo(other.line)
        return if (lineComparison != 0) lineComparison else column.compareTo(other.column)
    }

    /**
     * Returns true if this position is at the start of a line (column 0).
     */
    val isLineStart: Boolean get() = column == 0

    /**
     * Returns a new position at the start of this line.
     */
    fun toLineStart(): EditorPosition = if (column == 0) this else copy(column = 0)

    /**
     * Returns a new position with the column offset by the given delta.
     * The column is clamped to be non-negative.
     */
    fun offsetColumn(delta: Int): EditorPosition {
        val newColumn = (column + delta).coerceAtLeast(0)
        return if (newColumn == column) this else copy(column = newColumn)
    }

    /**
     * Returns a new position with the line offset by the given delta.
     * The line is clamped to be non-negative.
     */
    fun offsetLine(delta: Int): EditorPosition {
        val newLine = (line + delta).coerceAtLeast(0)
        return if (newLine == line) this else copy(line = newLine)
    }

    companion object {
        /**
         * The origin position (0, 0).
         */
        val ZERO = EditorPosition(0, 0)
    }
}

/**
 * Represents a range in the editor document.
 *
 * @property start The start position (inclusive)
 * @property end The end position (exclusive)
 */
data class EditorRange(
    val start: EditorPosition,
    val end: EditorPosition
) {
    init {
        require(start <= end) { "Start must be <= end: $start > $end" }
    }

    /**
     * Returns true if this range is empty (start == end).
     */
    val isEmpty: Boolean get() = start == end

    /**
     * Returns true if this range spans multiple lines.
     */
    val isMultiLine: Boolean get() = start.line != end.line

    /**
     * Returns true if this range contains the given position.
     */
    operator fun contains(position: EditorPosition): Boolean {
        return position >= start && position < end
    }

    /**
     * Returns the intersection of this range with another, or null if they don't overlap.
     */
    fun intersect(other: EditorRange): EditorRange? {
        val newStart = maxOf(start, other.start)
        val newEnd = minOf(end, other.end)
        return if (newStart < newEnd) EditorRange(newStart, newEnd) else null
    }

    /**
     * Returns true if this range overlaps with another.
     */
    fun overlaps(other: EditorRange): Boolean {
        return start < other.end && end > other.start
    }

    companion object {
        /**
         * Creates a range from a single position (empty range).
         */
        fun cursor(position: EditorPosition) = EditorRange(position, position)

        /**
         * Creates a range covering an entire line.
         */
        fun line(lineNumber: Int, lineLength: Int) = EditorRange(
            EditorPosition(lineNumber, 0),
            EditorPosition(lineNumber, lineLength)
        )
    }
}

/**
 * Represents a text offset (character index from the start of the document).
 * This is an inline class for type safety.
 */
@JvmInline
value class TextOffset(val value: Int) : Comparable<TextOffset> {
    init {
        require(value >= 0) { "Text offset must be non-negative: $value" }
    }

    override fun compareTo(other: TextOffset): Int = value.compareTo(other.value)

    operator fun plus(delta: Int) = TextOffset((value + delta).coerceAtLeast(0))
    operator fun minus(delta: Int) = TextOffset((value - delta).coerceAtLeast(0))
    operator fun minus(other: TextOffset) = value - other.value

    companion object {
        val ZERO = TextOffset(0)
    }
}
