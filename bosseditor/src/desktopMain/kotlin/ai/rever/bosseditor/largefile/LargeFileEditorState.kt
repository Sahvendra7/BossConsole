package ai.rever.bosseditor.largefile

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.ScrollOffset
import ai.rever.bosseditor.core.VisibleViewport
import ai.rever.bosseditor.fold.VisualLineMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.io.File

/**
 * Simplified editor state for read-only large file viewing.
 *
 * This class provides basic navigation and viewing capabilities for large files
 * without the full editing features (undo, multi-caret, folding) that require
 * the entire document in memory.
 *
 * Features:
 * - Caret positioning and selection
 * - Scroll state management
 * - Read-only text access via LargeFileDocument
 *
 * Not supported (read-only):
 * - Text editing (insert, delete, replace)
 * - Undo/redo
 * - Multi-caret editing
 * - Code folding
 */
class LargeFileEditorState(
    file: File,
    val filePath: String? = file.absolutePath
) : Closeable {

    val document: LargeFileDocument = LargeFileDocument(file)

    // Caret position
    private val _caretPosition = MutableStateFlow(EditorPosition.ZERO)
    val caretPosition: StateFlow<EditorPosition> = _caretPosition.asStateFlow()

    // Selection range (null = no selection)
    private val _selection = MutableStateFlow<EditorRange?>(null)
    val selection: StateFlow<EditorRange?> = _selection.asStateFlow()

    // Always unmodified (read-only)
    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    // Scroll position
    private val _scrollOffset = MutableStateFlow(ScrollOffset(0, 0))
    val scrollOffset: StateFlow<ScrollOffset> = _scrollOffset.asStateFlow()

    // Visible viewport
    private val _visibleViewport = MutableStateFlow(VisibleViewport(0, 30))
    val visibleViewport: StateFlow<VisibleViewport> = _visibleViewport.asStateFlow()

    // Visual line mapper (no folding support, 1:1 mapping)
    private val _visualLineMapper = MutableStateFlow(VisualLineMapper.noFolds(document.lineCount))
    val visualLineMapper: StateFlow<VisualLineMapper> = _visualLineMapper.asStateFlow()

    // Legacy property
    var visibleLineRange: IntRange = 0..0
        private set

    /**
     * Current caret offset in the document.
     */
    val caretOffset: Int
        get() = document.positionToOffset(caretPosition.value)

    /**
     * Whether there is an active selection.
     */
    val hasSelection: Boolean
        get() = selection.value?.let { !it.isEmpty } ?: false

    /**
     * Selected text, or empty string if no selection.
     */
    val selectedText: String
        get() {
            val sel = selection.value ?: return ""
            if (sel.isEmpty) return ""
            val startOffset = document.positionToOffset(sel.start)
            val endOffset = document.positionToOffset(sel.end)
            return document.getText(startOffset, endOffset)
        }

    // --- Caret operations ---

    fun moveCaret(position: EditorPosition, extendSelection: Boolean = false) {
        val clampedPosition = clampPosition(position)

        if (extendSelection) {
            extendSelectionTo(clampedPosition)
        } else {
            clearSelection()
        }

        _caretPosition.value = clampedPosition
    }

    fun moveCaretToOffset(offset: Int, extendSelection: Boolean = false) {
        val clampedOffset = offset.coerceIn(0, document.length)
        val position = document.offsetToPosition(clampedOffset)
        moveCaret(position, extendSelection)
    }

    fun moveCaretBy(lineDelta: Int, columnDelta: Int, extendSelection: Boolean = false) {
        val current = caretPosition.value
        val newLine = (current.line + lineDelta).coerceIn(0, document.lineCount - 1)
        val lineLength = document.getLineLength(newLine)
        val newColumn = (current.column + columnDelta).coerceIn(0, lineLength)
        moveCaret(EditorPosition(newLine, newColumn), extendSelection)
    }

    fun moveCaretToStart(extendSelection: Boolean = false) {
        moveCaret(EditorPosition.ZERO, extendSelection)
    }

    fun moveCaretToEnd(extendSelection: Boolean = false) {
        val lastLine = document.lineCount - 1
        val lastColumn = document.getLineLength(lastLine)
        moveCaret(EditorPosition(lastLine, lastColumn), extendSelection)
    }

    fun moveCaretToLineStart(extendSelection: Boolean = false) {
        moveCaret(caretPosition.value.toLineStart(), extendSelection)
    }

    fun moveCaretToLineEnd(extendSelection: Boolean = false) {
        val line = caretPosition.value.line
        val lineLength = document.getLineLength(line)
        moveCaret(EditorPosition(line, lineLength), extendSelection)
    }

    // --- Selection operations ---

    fun setSelection(range: EditorRange?) {
        _selection.value = range?.let {
            val start = clampPosition(it.start)
            val end = clampPosition(it.end)
            if (start <= end) EditorRange(start, end) else EditorRange(end, start)
        }
    }

    fun selectAll() {
        val start = EditorPosition.ZERO
        val end = EditorPosition(
            document.lineCount - 1,
            document.getLineLength(document.lineCount - 1)
        )
        setSelection(EditorRange(start, end))
        _caretPosition.value = end
    }

    fun clearSelection() {
        if (_selection.value != null) {
            _selection.value = null
        }
    }

    /**
     * Select the word at the current caret position.
     * Uses word boundary detection (letters, digits, underscore).
     */
    fun selectWord() {
        val line = _caretPosition.value.line
        val column = _caretPosition.value.column
        val lineText = document.getLineText(line)

        if (lineText.isEmpty()) return

        // Find word boundaries
        var start = column.coerceAtMost(lineText.length - 1).coerceAtLeast(0)
        var end = start

        // If we're at a non-word character, try to select surrounding whitespace or punctuation
        if (start < lineText.length && !isWordChar(lineText[start])) {
            // Just position caret, don't select
            return
        }

        // Expand backward
        while (start > 0 && isWordChar(lineText[start - 1])) {
            start--
        }

        // Expand forward
        while (end < lineText.length && isWordChar(lineText[end])) {
            end++
        }

        if (start < end) {
            setSelection(EditorRange(
                EditorPosition(line, start),
                EditorPosition(line, end)
            ))
            _caretPosition.value = EditorPosition(line, end)
        }
    }

    /**
     * Select the entire current line.
     */
    fun selectLine() {
        val line = _caretPosition.value.line
        val lineLength = document.getLineLength(line)
        setSelection(EditorRange(
            EditorPosition(line, 0),
            EditorPosition(line, lineLength)
        ))
        _caretPosition.value = EditorPosition(line, lineLength)
    }

    private fun isWordChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_'
    }

    // --- Scroll operations ---

    fun setScrollOffset(offset: ScrollOffset) {
        _scrollOffset.value = offset
    }

    fun scrollToLine(line: Int, lineHeight: Float, viewportHeight: Float) {
        val targetLine = line.coerceIn(0, (document.lineCount - 1).coerceAtLeast(0))

        val lineY = targetLine * lineHeight
        val viewportLines = (viewportHeight / lineHeight).toInt()
        val centerOffset = (viewportLines / 2) * lineHeight

        val newScrollY = (lineY - centerOffset).coerceAtLeast(0f).toInt()

        _scrollOffset.value = ScrollOffset(
            x = _scrollOffset.value.x,
            y = newScrollY
        )
    }

    fun updateVisibleLineRange(
        firstLine: Int,
        lineCount: Int,
        lineHeight: Float = 0f,
        viewportHeight: Float = 0f,
        viewportWidth: Float = 0f,
        contentWidth: Float = 0f,
        charWidth: Float = 0f
    ) {
        val clampedFirst = firstLine.coerceAtLeast(0)
        val clampedLast = (firstLine + lineCount).coerceAtMost(document.lineCount - 1)
        visibleLineRange = clampedFirst..clampedLast
        _visibleViewport.value = VisibleViewport(
            firstVisibleLine = clampedFirst,
            visibleLineCount = lineCount,
            lineHeight = lineHeight,
            viewportHeight = viewportHeight,
            viewportWidth = viewportWidth,
            contentWidth = contentWidth,
            charWidth = charWidth
        )
    }

    // --- Read-only operations (no-ops or exceptions) ---

    fun insertText(text: String) {
        // No-op for read-only document
    }

    fun deleteBackward() {
        // No-op for read-only document
    }

    fun deleteForward() {
        // No-op for read-only document
    }

    fun deleteSelection() {
        // No-op for read-only document
    }

    fun setText(text: String) {
        // No-op for read-only document
    }

    fun undo(): Boolean = false

    fun redo(): Boolean = false

    fun markAsSaved() {
        // No-op for read-only document
    }

    override fun close() {
        document.close()
    }

    // --- Private helpers ---

    private fun clampPosition(position: EditorPosition): EditorPosition {
        val line = position.line.coerceIn(0, (document.lineCount - 1).coerceAtLeast(0))
        val lineLength = if (document.lineCount > 0) document.getLineLength(line) else 0
        val column = position.column.coerceIn(0, lineLength)
        return if (line == position.line && column == position.column) position
        else EditorPosition(line, column)
    }

    private fun extendSelectionTo(newPosition: EditorPosition) {
        val currentSel = selection.value
        val anchor = currentSel?.let {
            if (caretPosition.value == it.start) it.end else it.start
        } ?: caretPosition.value

        val newSel = if (anchor <= newPosition) {
            EditorRange(anchor, newPosition)
        } else {
            EditorRange(newPosition, anchor)
        }

        _selection.value = newSel
    }

    companion object {
        /**
         * Creates a LargeFileEditorState if the file is large enough, otherwise returns null.
         */
        fun createIfLargeFile(file: File): LargeFileEditorState? {
            return if (LargeFileDocument.shouldUseLargeFileAdapter(file)) {
                LargeFileEditorState(file)
            } else {
                null
            }
        }

        /**
         * Creates a LargeFileEditorState if the file path points to a large file.
         */
        fun createIfLargeFile(filePath: String): LargeFileEditorState? {
            return createIfLargeFile(File(filePath))
        }
    }
}
