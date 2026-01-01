package ai.rever.bosseditor.core

/**
 * A text document backed by a gap buffer for efficient editing.
 *
 * The gap buffer is a data structure that maintains a "gap" in the character array
 * at the current editing position. This allows O(1) insertions and deletions at
 * the cursor position, which is the most common operation in text editing.
 *
 * Key features:
 * - O(1) insert/delete at gap position
 * - O(n) insert/delete away from gap (gap must be moved)
 * - O(1) line lookup via line index
 * - Document change listeners for incremental updates
 */
class EditorDocument(initialText: String = "") {

    // Gap buffer storage
    private var buffer: CharArray = CharArray(initialText.length + INITIAL_GAP_SIZE)
    private var gapStart: Int = 0
    private var gapEnd: Int = INITIAL_GAP_SIZE

    // Line index: stores the offset of each line start
    // lineStarts[i] = offset of line i in the logical document
    private val lineStarts = mutableListOf(0)

    // Document listeners
    private val listeners = mutableListOf<DocumentListener>()

    // Document version for change tracking
    private var version: Long = 0L

    init {
        if (initialText.isNotEmpty()) {
            // Copy initial text after the gap
            initialText.toCharArray().copyInto(buffer, gapEnd)
            gapEnd = INITIAL_GAP_SIZE
            rebuildLineIndex()
        }
    }

    /**
     * The total number of characters in the document (excluding the gap).
     */
    val length: Int
        get() = buffer.size - (gapEnd - gapStart)

    /**
     * The number of lines in the document.
     */
    val lineCount: Int
        get() = lineStarts.size

    /**
     * The current document version. Incremented on every modification.
     */
    val documentVersion: Long
        get() = version

    /**
     * Returns the entire document text.
     */
    fun getText(): String {
        val result = StringBuilder(length)
        // Text before gap
        for (i in 0 until gapStart) {
            result.append(buffer[i])
        }
        // Text after gap
        for (i in gapEnd until buffer.size) {
            result.append(buffer[i])
        }
        return result.toString()
    }

    /**
     * Returns a substring of the document.
     */
    fun getText(startOffset: Int, endOffset: Int): String {
        require(startOffset >= 0) { "startOffset must be non-negative" }
        require(endOffset <= length) { "endOffset must be <= length" }
        require(startOffset <= endOffset) { "startOffset must be <= endOffset" }

        if (startOffset == endOffset) return ""

        val result = StringBuilder(endOffset - startOffset)
        for (i in startOffset until endOffset) {
            result.append(charAt(i))
        }
        return result.toString()
    }

    /**
     * Returns the character at the given offset.
     */
    fun charAt(offset: Int): Char {
        require(offset in 0 until length) { "Offset out of bounds: $offset" }
        return buffer[toBufferIndex(offset)]
    }

    /**
     * Returns the text of the specified line (without line terminator).
     */
    fun getLineText(lineNumber: Int): String {
        require(lineNumber in 0 until lineCount) { "Line number out of bounds: $lineNumber" }

        val startOffset = lineStarts[lineNumber]
        val endOffset = if (lineNumber + 1 < lineCount) {
            // End at the start of next line, excluding the newline
            val nextLineStart = lineStarts[lineNumber + 1]
            // Check if previous char is \n
            if (nextLineStart > 0 && charAt(nextLineStart - 1) == '\n') {
                nextLineStart - 1
            } else {
                nextLineStart
            }
        } else {
            length
        }

        return getText(startOffset, endOffset)
    }

    /**
     * Returns the length of the specified line (excluding line terminator).
     */
    fun getLineLength(lineNumber: Int): Int {
        require(lineNumber in 0 until lineCount) { "Line number out of bounds: $lineNumber" }

        val startOffset = lineStarts[lineNumber]
        val endOffset = if (lineNumber + 1 < lineCount) {
            val nextLineStart = lineStarts[lineNumber + 1]
            if (nextLineStart > 0 && charAt(nextLineStart - 1) == '\n') {
                nextLineStart - 1
            } else {
                nextLineStart
            }
        } else {
            length
        }

        return endOffset - startOffset
    }

    /**
     * Returns the start offset of the specified line.
     */
    fun getLineStartOffset(lineNumber: Int): Int {
        require(lineNumber in 0 until lineCount) { "Line number out of bounds: $lineNumber" }
        return lineStarts[lineNumber]
    }

    /**
     * Returns the end offset of the specified line (after the line terminator if present).
     */
    fun getLineEndOffset(lineNumber: Int): Int {
        require(lineNumber in 0 until lineCount) { "Line number out of bounds: $lineNumber" }
        return if (lineNumber + 1 < lineCount) {
            lineStarts[lineNumber + 1]
        } else {
            length
        }
    }

    /**
     * Converts a text offset to a position (line, column).
     */
    fun offsetToPosition(offset: Int): EditorPosition {
        require(offset in 0..length) { "Offset out of bounds: $offset" }

        // Binary search for the line containing this offset
        var low = 0
        var high = lineStarts.size - 1

        while (low < high) {
            val mid = (low + high + 1) / 2
            if (lineStarts[mid] <= offset) {
                low = mid
            } else {
                high = mid - 1
            }
        }

        val line = low
        val column = offset - lineStarts[line]
        return EditorPosition(line, column)
    }

    /**
     * Converts a position (line, column) to a text offset.
     */
    fun positionToOffset(position: EditorPosition): Int {
        return positionToOffset(position.line, position.column)
    }

    /**
     * Converts a position (line, column) to a text offset.
     */
    fun positionToOffset(line: Int, column: Int): Int {
        require(line in 0 until lineCount) { "Line out of bounds: $line" }
        val lineStart = lineStarts[line]
        val lineLen = getLineLength(line)
        val clampedColumn = column.coerceIn(0, lineLen)
        return lineStart + clampedColumn
    }

    /**
     * Inserts text at the specified offset.
     */
    fun insert(offset: Int, text: String) {
        if (text.isEmpty()) return
        require(offset in 0..length) { "Offset out of bounds: $offset" }

        val oldLength = length
        val oldVersion = version

        // Move gap to insertion point
        moveGap(offset)

        // Ensure gap is large enough
        ensureGapCapacity(text.length)

        // Insert text into gap
        text.toCharArray().copyInto(buffer, gapStart)
        gapStart += text.length

        // Update line index
        updateLineIndexAfterInsert(offset, text)

        version++

        // Notify listeners
        notifyListeners(DocumentChange(
            offset = offset,
            oldLength = 0,
            newLength = text.length,
            oldText = "",
            newText = text,
            oldVersion = oldVersion,
            newVersion = version
        ))
    }

    /**
     * Deletes text at the specified range.
     */
    fun delete(startOffset: Int, endOffset: Int) {
        if (startOffset == endOffset) return
        require(startOffset >= 0) { "startOffset must be non-negative" }
        require(endOffset <= length) { "endOffset must be <= length" }
        require(startOffset < endOffset) { "startOffset must be < endOffset" }

        val oldText = getText(startOffset, endOffset)
        val oldVersion = version

        // Move gap to deletion start
        moveGap(startOffset)

        // Expand gap to cover deleted text
        gapEnd += (endOffset - startOffset)

        // Update line index
        updateLineIndexAfterDelete(startOffset, oldText)

        version++

        // Notify listeners
        notifyListeners(DocumentChange(
            offset = startOffset,
            oldLength = oldText.length,
            newLength = 0,
            oldText = oldText,
            newText = "",
            oldVersion = oldVersion,
            newVersion = version
        ))
    }

    /**
     * Replaces text at the specified range.
     */
    fun replace(startOffset: Int, endOffset: Int, newText: String) {
        if (startOffset == endOffset && newText.isEmpty()) return
        require(startOffset >= 0) { "startOffset must be non-negative" }
        require(endOffset <= length) { "endOffset must be <= length" }
        require(startOffset <= endOffset) { "startOffset must be <= endOffset" }

        val oldText = if (startOffset < endOffset) getText(startOffset, endOffset) else ""
        val oldVersion = version

        // Move gap to replacement start
        moveGap(startOffset)

        // Expand gap to cover old text
        gapEnd += (endOffset - startOffset)

        // Ensure gap is large enough for new text
        ensureGapCapacity(newText.length)

        // Insert new text
        newText.toCharArray().copyInto(buffer, gapStart)
        gapStart += newText.length

        // Rebuild line index for the affected region
        rebuildLineIndex()

        version++

        // Notify listeners
        notifyListeners(DocumentChange(
            offset = startOffset,
            oldLength = oldText.length,
            newLength = newText.length,
            oldText = oldText,
            newText = newText,
            oldVersion = oldVersion,
            newVersion = version
        ))
    }

    /**
     * Sets the entire document text.
     */
    fun setText(text: String) {
        val oldText = getText()
        val oldVersion = version

        // Reset buffer
        buffer = CharArray(text.length + INITIAL_GAP_SIZE)
        text.toCharArray().copyInto(buffer, INITIAL_GAP_SIZE)
        gapStart = 0
        gapEnd = INITIAL_GAP_SIZE

        // Rebuild line index
        rebuildLineIndex()

        version++

        // Notify listeners
        notifyListeners(DocumentChange(
            offset = 0,
            oldLength = oldText.length,
            newLength = text.length,
            oldText = oldText,
            newText = text,
            oldVersion = oldVersion,
            newVersion = version
        ))
    }

    /**
     * Adds a document change listener.
     */
    fun addDocumentListener(listener: DocumentListener) {
        listeners.add(listener)
    }

    /**
     * Removes a document change listener.
     */
    fun removeDocumentListener(listener: DocumentListener) {
        listeners.remove(listener)
    }

    // --- Private implementation ---

    private fun toBufferIndex(logicalIndex: Int): Int {
        return if (logicalIndex < gapStart) logicalIndex else logicalIndex + (gapEnd - gapStart)
    }

    private fun moveGap(targetPosition: Int) {
        if (targetPosition == gapStart) return

        val gapSize = gapEnd - gapStart

        if (targetPosition < gapStart) {
            // Move gap left: shift text right
            val moveCount = gapStart - targetPosition
            buffer.copyInto(buffer, gapEnd - moveCount, targetPosition, gapStart)
            gapStart = targetPosition
            gapEnd = gapStart + gapSize
        } else {
            // Move gap right: shift text left
            val moveCount = targetPosition - gapStart
            buffer.copyInto(buffer, gapStart, gapEnd, gapEnd + moveCount)
            gapStart = targetPosition
            gapEnd = gapStart + gapSize
        }
    }

    private fun ensureGapCapacity(requiredSize: Int) {
        val gapSize = gapEnd - gapStart
        if (gapSize >= requiredSize) return

        // Grow buffer
        val newGapSize = maxOf(requiredSize, gapSize * 2, MIN_GAP_SIZE)
        val additionalSpace = newGapSize - gapSize
        val newBuffer = CharArray(buffer.size + additionalSpace)

        // Copy text before gap
        buffer.copyInto(newBuffer, 0, 0, gapStart)
        // Copy text after gap to new position
        buffer.copyInto(newBuffer, gapEnd + additionalSpace, gapEnd, buffer.size)

        buffer = newBuffer
        gapEnd += additionalSpace
    }

    /**
     * Rebuilds the entire line index from scratch.
     *
     * Performance: O(n) where n is document length. This is called when newlines
     * are inserted or deleted, as incremental updates are complex for multi-line edits.
     *
     * For large documents (10k+ lines), consider implementing incremental updates
     * for better performance on single-line edits with newlines.
     */
    private fun rebuildLineIndex() {
        lineStarts.clear()
        lineStarts.add(0)

        for (i in 0 until length) {
            if (charAt(i) == '\n') {
                lineStarts.add(i + 1)
            }
        }
    }

    private fun updateLineIndexAfterInsert(offset: Int, text: String) {
        // Count newlines in inserted text
        val newlines = mutableListOf<Int>()
        for ((i, char) in text.withIndex()) {
            if (char == '\n') {
                newlines.add(offset + i + 1)
            }
        }

        if (newlines.isEmpty()) {
            // No newlines: just shift line starts after insertion point
            for (i in lineStarts.indices) {
                if (lineStarts[i] > offset) {
                    lineStarts[i] += text.length
                }
            }
        } else {
            // Has newlines: need to insert new line entries and shift
            rebuildLineIndex() // Simpler to rebuild for now
        }
    }

    private fun updateLineIndexAfterDelete(offset: Int, deletedText: String) {
        if ('\n' in deletedText) {
            rebuildLineIndex() // Simpler to rebuild when lines are affected
        } else {
            // No newlines deleted: just shift line starts after deletion point
            for (i in lineStarts.indices) {
                if (lineStarts[i] > offset) {
                    lineStarts[i] -= deletedText.length
                }
            }
        }
    }

    private fun notifyListeners(change: DocumentChange) {
        // Create a copy to avoid ConcurrentModificationException if listeners
        // are added/removed during notification
        val listenersCopy = listeners.toList()
        for (listener in listenersCopy) {
            listener.documentChanged(change)
        }
    }

    companion object {
        /**
         * Initial gap size in characters. 256 provides a good balance between
         * memory overhead and reducing gap moves for typical editing patterns
         * (most edits are small insertions/deletions at cursor position).
         */
        private const val INITIAL_GAP_SIZE = 256

        /**
         * Minimum gap size when expanding. 64 characters ensures we don't
         * reallocate too frequently for small sequential edits while keeping
         * memory overhead reasonable.
         */
        private const val MIN_GAP_SIZE = 64
    }
}

/**
 * Represents a change to the document.
 */
data class DocumentChange(
    val offset: Int,
    val oldLength: Int,
    val newLength: Int,
    val oldText: String,
    val newText: String,
    val oldVersion: Long,
    val newVersion: Long
) {
    /**
     * Returns true if this change is an insertion (no text deleted).
     */
    val isInsert: Boolean get() = oldLength == 0 && newLength > 0

    /**
     * Returns true if this change is a deletion (no text inserted).
     */
    val isDelete: Boolean get() = oldLength > 0 && newLength == 0

    /**
     * Returns true if this change is a replacement.
     */
    val isReplace: Boolean get() = oldLength > 0 && newLength > 0
}

/**
 * Listener for document changes.
 */
fun interface DocumentListener {
    fun documentChanged(change: DocumentChange)
}
