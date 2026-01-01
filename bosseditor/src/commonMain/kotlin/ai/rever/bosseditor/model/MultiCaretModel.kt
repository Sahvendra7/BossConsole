package ai.rever.bosseditor.model

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.OffsetRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a single caret with optional selection.
 *
 * @property position The caret position (line, column)
 * @property selection Optional selection range for this caret
 * @property id Unique identifier for this caret
 */
data class Caret(
    val position: EditorPosition,
    val selection: EditorRange? = null,
    val id: Int = 0
) {
    /**
     * Whether this caret has an active selection.
     */
    val hasSelection: Boolean
        get() = selection != null && !selection.isEmpty

    /**
     * Creates a copy with updated position, clearing selection.
     */
    fun moveTo(newPosition: EditorPosition): Caret =
        copy(position = newPosition, selection = null)

    /**
     * Creates a copy with selection from anchor to current position.
     */
    fun selectTo(anchor: EditorPosition, current: EditorPosition): Caret {
        val range = if (anchor <= current) {
            EditorRange(anchor, current)
        } else {
            EditorRange(current, anchor)
        }
        return copy(position = current, selection = range)
    }
}

/**
 * Manages multiple carets for simultaneous editing.
 *
 * Features:
 * - Add/remove carets at arbitrary positions
 * - Alt+Click to add a caret
 * - Column/block selection creating carets per line
 * - Merge overlapping carets
 * - Synchronized editing across all carets
 *
 * ## Usage
 * ```kotlin
 * val multiCaret = MultiCaretModel(document)
 *
 * // Add a secondary caret
 * multiCaret.addCaret(EditorPosition(5, 10))
 *
 * // Create block selection (one caret per line)
 * multiCaret.createBlockSelection(
 *     startLine = 3, endLine = 7,
 *     startColumn = 5, endColumn = 15
 * )
 *
 * // Type at all carets
 * multiCaret.insertAtAllCarets("text")
 * ```
 */
class MultiCaretModel(private val document: EditorDocument) {

    private var nextCaretId = 0

    private val _carets = MutableStateFlow(listOf(Caret(EditorPosition.ZERO, id = nextCaretId++)))
    val carets: StateFlow<List<Caret>> = _carets.asStateFlow()

    private val _primaryCaretIndex = MutableStateFlow(0)
    val primaryCaretIndex: StateFlow<Int> = _primaryCaretIndex.asStateFlow()

    /**
     * The primary (main) caret. This is the caret that was either created first
     * or is considered the "main" cursor for operations.
     */
    val primaryCaret: Caret
        get() = _carets.value.getOrElse(_primaryCaretIndex.value) { _carets.value.first() }

    /**
     * Number of active carets.
     */
    val caretCount: Int
        get() = _carets.value.size

    /**
     * Whether there are multiple carets active.
     */
    val hasMultipleCarets: Boolean
        get() = _carets.value.size > 1

    /**
     * All current caret positions.
     */
    val allPositions: List<EditorPosition>
        get() = _carets.value.map { it.position }

    /**
     * All current selections.
     */
    val allSelections: List<EditorRange>
        get() = _carets.value.mapNotNull { it.selection }

    /**
     * Sets a single caret, clearing all others.
     */
    fun setSingleCaret(position: EditorPosition) {
        val clamped = clampPosition(position)
        _carets.value = listOf(Caret(clamped, id = nextCaretId++))
        _primaryCaretIndex.value = 0
    }

    /**
     * Adds a new caret at the specified position.
     * If a caret already exists at this position, it's removed (toggle behavior).
     *
     * @param position Position to add/toggle caret
     * @return true if caret was added, false if removed
     */
    fun addCaret(position: EditorPosition): Boolean {
        val clamped = clampPosition(position)
        val currentCarets = _carets.value.toMutableList()

        // Check if caret already exists at this position
        val existingIndex = currentCarets.indexOfFirst { it.position == clamped }

        if (existingIndex >= 0 && currentCarets.size > 1) {
            // Remove existing caret (toggle), but keep at least one
            currentCarets.removeAt(existingIndex)
            if (_primaryCaretIndex.value >= currentCarets.size) {
                _primaryCaretIndex.value = currentCarets.size - 1
            }
            _carets.value = currentCarets
            return false
        } else if (existingIndex < 0) {
            // Add new caret
            currentCarets.add(Caret(clamped, id = nextCaretId++))
            _carets.value = sortAndMergeCarets(currentCarets)
            return true
        }

        return false
    }

    /**
     * Adds a caret with a selection.
     */
    fun addCaretWithSelection(position: EditorPosition, selection: EditorRange) {
        val clamped = clampPosition(position)
        val clampedSelection = EditorRange(
            clampPosition(selection.start),
            clampPosition(selection.end)
        )
        val currentCarets = _carets.value.toMutableList()
        currentCarets.add(Caret(clamped, clampedSelection, nextCaretId++))
        _carets.value = sortAndMergeCarets(currentCarets)
    }

    /**
     * Removes the caret at the given index.
     * At least one caret is always kept.
     */
    fun removeCaret(index: Int) {
        val currentCarets = _carets.value.toMutableList()
        if (currentCarets.size > 1 && index in currentCarets.indices) {
            currentCarets.removeAt(index)
            if (_primaryCaretIndex.value >= currentCarets.size) {
                _primaryCaretIndex.value = currentCarets.size - 1
            }
            _carets.value = currentCarets
        }
    }

    /**
     * Clears all secondary carets, keeping only the primary.
     */
    fun clearSecondaryCarets() {
        val primary = primaryCaret
        _carets.value = listOf(primary.copy(id = nextCaretId++))
        _primaryCaretIndex.value = 0
    }

    /**
     * Moves all carets in the given direction.
     */
    fun moveAllCarets(movement: (Caret) -> EditorPosition) {
        val newCarets = _carets.value.map { caret ->
            caret.moveTo(clampPosition(movement(caret)))
        }
        _carets.value = sortAndMergeCarets(newCarets)
    }

    /**
     * Extends selection for all carets.
     */
    fun extendSelectionForAll(getNewPosition: (Caret) -> EditorPosition) {
        val newCarets = _carets.value.map { caret ->
            val anchor = caret.selection?.let {
                // Use existing anchor
                if (caret.position == it.end) it.start else it.end
            } ?: caret.position

            val newPos = clampPosition(getNewPosition(caret))
            caret.selectTo(anchor, newPos)
        }
        _carets.value = sortAndMergeCarets(newCarets)
    }

    /**
     * Starts selection at current position for all carets.
     */
    fun startSelectionForAll() {
        val newCarets = _carets.value.map { caret ->
            caret.copy(selection = EditorRange(caret.position, caret.position))
        }
        _carets.value = newCarets
    }

    /**
     * Clears selection for all carets.
     */
    fun clearAllSelections() {
        val newCarets = _carets.value.map { caret ->
            caret.copy(selection = null)
        }
        _carets.value = newCarets
    }

    /**
     * Creates a block/column selection.
     * This creates one caret per line within the block.
     *
     * @param startLine First line of the block
     * @param endLine Last line of the block
     * @param startColumn Start column of the block
     * @param endColumn End column of the block
     */
    fun createBlockSelection(
        startLine: Int,
        endLine: Int,
        startColumn: Int,
        endColumn: Int
    ) {
        val minLine = minOf(startLine, endLine).coerceIn(0, document.lineCount - 1)
        val maxLine = maxOf(startLine, endLine).coerceIn(0, document.lineCount - 1)
        val minCol = minOf(startColumn, endColumn).coerceAtLeast(0)
        val maxCol = maxOf(startColumn, endColumn)

        val carets = mutableListOf<Caret>()

        for (line in minLine..maxLine) {
            val lineLength = document.getLineLength(line)
            // Only add caret if line is long enough to have content in the block
            val lineMinCol = minCol.coerceAtMost(lineLength)
            val lineMaxCol = maxCol.coerceAtMost(lineLength)

            val selection = if (lineMinCol < lineMaxCol) {
                EditorRange(
                    EditorPosition(line, lineMinCol),
                    EditorPosition(line, lineMaxCol)
                )
            } else {
                null
            }

            carets.add(Caret(
                position = EditorPosition(line, lineMaxCol.coerceAtMost(lineLength)),
                selection = selection,
                id = nextCaretId++
            ))
        }

        _carets.value = carets
        _primaryCaretIndex.value = 0
    }

    /**
     * Gets the offset ranges for all selections.
     */
    fun getAllSelectionOffsets(): List<OffsetRange> {
        return _carets.value.mapNotNull { caret ->
            caret.selection?.let { sel ->
                val startOffset = document.positionToOffset(sel.start)
                val endOffset = document.positionToOffset(sel.end)
                OffsetRange(startOffset, endOffset)
            }
        }
    }

    /**
     * Gets all caret offsets in the document.
     */
    fun getAllCaretOffsets(): List<Int> {
        return _carets.value.map { document.positionToOffset(it.position) }
    }

    /**
     * Updates carets after text insertion.
     * Carets after the insertion point are shifted.
     *
     * @param insertOffset Where text was inserted
     * @param insertLength Length of inserted text
     * @param insertedLines Number of newlines inserted
     */
    fun adjustAfterInsert(insertOffset: Int, insertLength: Int, insertedLines: Int) {
        val newCarets = _carets.value.map { caret ->
            val caretOffset = document.positionToOffset(caret.position)
            if (caretOffset >= insertOffset) {
                val newOffset = caretOffset + insertLength
                val newPosition = document.offsetToPosition(newOffset)
                caret.copy(position = newPosition, selection = null)
            } else {
                caret
            }
        }
        _carets.value = sortAndMergeCarets(newCarets)
    }

    /**
     * Updates carets after text deletion.
     * Carets in the deleted range move to the start of the range.
     * Carets after the deletion are shifted back.
     *
     * @param deleteStart Start offset of deletion
     * @param deleteEnd End offset of deletion
     */
    fun adjustAfterDelete(deleteStart: Int, deleteEnd: Int) {
        val deleteLength = deleteEnd - deleteStart
        val newCarets = _carets.value.map { caret ->
            val caretOffset = document.positionToOffset(caret.position)
            val newOffset = when {
                caretOffset <= deleteStart -> caretOffset
                caretOffset >= deleteEnd -> caretOffset - deleteLength
                else -> deleteStart // Caret was in deleted range
            }
            val newPosition = document.offsetToPosition(newOffset.coerceIn(0, document.length))
            caret.copy(position = newPosition, selection = null)
        }
        _carets.value = sortAndMergeCarets(newCarets)
    }

    /**
     * Updates the primary caret position directly.
     */
    fun updatePrimaryCaret(position: EditorPosition, selection: EditorRange? = null) {
        val currentCarets = _carets.value.toMutableList()
        val primaryIndex = _primaryCaretIndex.value

        if (primaryIndex in currentCarets.indices) {
            currentCarets[primaryIndex] = Caret(
                clampPosition(position),
                selection?.let { EditorRange(clampPosition(it.start), clampPosition(it.end)) },
                currentCarets[primaryIndex].id
            )
            _carets.value = sortAndMergeCarets(currentCarets)
        }
    }

    /**
     * Sets carets from a list of positions.
     */
    fun setCaretsFromPositions(positions: List<EditorPosition>) {
        if (positions.isEmpty()) return

        val carets = positions.map { pos ->
            Caret(clampPosition(pos), id = nextCaretId++)
        }
        _carets.value = sortAndMergeCarets(carets)
        _primaryCaretIndex.value = 0
    }

    // --- Private helpers ---

    private fun clampPosition(position: EditorPosition): EditorPosition {
        val lineCount = document.lineCount.coerceAtLeast(1)
        val line = position.line.coerceIn(0, lineCount - 1)
        val lineLength = if (document.lineCount > 0) document.getLineLength(line) else 0
        val column = position.column.coerceIn(0, lineLength)

        return if (line == position.line && column == position.column) {
            position
        } else {
            EditorPosition(line, column)
        }
    }

    /**
     * Sorts carets by position and merges overlapping carets.
     */
    private fun sortAndMergeCarets(carets: List<Caret>): List<Caret> {
        if (carets.size <= 1) return carets

        // Sort by position
        val sorted = carets.sortedWith(compareBy({ it.position.line }, { it.position.column }))

        // Merge overlapping or duplicate carets
        val merged = mutableListOf<Caret>()
        var current = sorted[0]

        for (i in 1 until sorted.size) {
            val next = sorted[i]

            if (current.position == next.position) {
                // Same position - merge selections if any
                val mergedSelection = mergeSelections(current.selection, next.selection)
                current = current.copy(selection = mergedSelection)
            } else if (selectionsOverlap(current.selection, next.selection)) {
                // Overlapping selections - merge them
                val mergedSelection = mergeSelections(current.selection, next.selection)
                current = next.copy(selection = mergedSelection)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        return merged
    }

    private fun selectionsOverlap(sel1: EditorRange?, sel2: EditorRange?): Boolean {
        if (sel1 == null || sel2 == null) return false
        return !(sel1.end <= sel2.start || sel2.end <= sel1.start)
    }

    private fun mergeSelections(sel1: EditorRange?, sel2: EditorRange?): EditorRange? {
        if (sel1 == null) return sel2
        if (sel2 == null) return sel1

        val start = if (sel1.start <= sel2.start) sel1.start else sel2.start
        val end = if (sel1.end >= sel2.end) sel1.end else sel2.end
        return EditorRange(start, end)
    }
}

/**
 * Represents a rectangular (block/column) selection.
 */
data class BlockSelection(
    val startLine: Int,
    val endLine: Int,
    val startColumn: Int,
    val endColumn: Int
) {
    /**
     * Number of lines in the block.
     */
    val lineCount: Int
        get() = kotlin.math.abs(endLine - startLine) + 1

    /**
     * Width of the block in columns.
     */
    val width: Int
        get() = kotlin.math.abs(endColumn - startColumn)

    /**
     * Normalized block with start <= end for both dimensions.
     */
    fun normalize(): BlockSelection {
        return BlockSelection(
            minOf(startLine, endLine),
            maxOf(startLine, endLine),
            minOf(startColumn, endColumn),
            maxOf(startColumn, endColumn)
        )
    }

    /**
     * Gets the selection range for a specific line within the block.
     */
    fun getRangeForLine(line: Int, lineLength: Int): OffsetRange? {
        val normalized = normalize()
        if (line < normalized.startLine || line > normalized.endLine) return null

        val startCol = normalized.startColumn.coerceAtMost(lineLength)
        val endCol = normalized.endColumn.coerceAtMost(lineLength)

        return if (startCol < endCol) {
            OffsetRange(startCol, endCol)
        } else {
            null
        }
    }
}
