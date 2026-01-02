package ai.rever.bosseditor.core

/**
 * Simple offset-based range for text operations.
 *
 * Unlike EditorRange which uses line/column positions,
 * OffsetRange uses absolute character offsets.
 */
data class OffsetRange(
    val start: Int,
    val end: Int
) {
    init {
        require(start <= end) { "Start must be <= end: $start > $end" }
    }

    val length: Int get() = end - start

    val isEmpty: Boolean get() = start == end

    operator fun contains(offset: Int): Boolean = offset in start until end

    fun overlaps(other: OffsetRange): Boolean = start < other.end && end > other.start

    companion object {
        val EMPTY = OffsetRange(0, 0)
    }
}
