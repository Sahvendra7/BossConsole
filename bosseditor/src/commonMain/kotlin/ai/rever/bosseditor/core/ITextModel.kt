package ai.rever.bosseditor.core

/**
 * Interface for text document access.
 *
 * This abstraction allows EditorState to work with both:
 * - EditorDocument: In-memory gap buffer for normal files
 * - LargeFileDocument: Page-based lazy loading for large files (read-only)
 *
 * All implementations must provide efficient O(1) or O(log n) line lookup.
 */
interface ITextModel {

    /**
     * Total number of characters in the document.
     */
    val length: Int

    /**
     * Number of lines in the document.
     */
    val lineCount: Int

    /**
     * Current document version. Incremented on every modification.
     */
    val documentVersion: Long

    /**
     * Whether this document is read-only.
     * Large file documents are always read-only.
     */
    val isReadOnly: Boolean
        get() = false

    /**
     * Returns the entire document text.
     * Warning: For large files, this may throw or return partial content.
     */
    fun getText(): String

    /**
     * Returns a substring of the document.
     */
    fun getText(startOffset: Int, endOffset: Int): String

    /**
     * Returns the character at the given offset.
     */
    fun charAt(offset: Int): Char

    /**
     * Returns the text of the specified line (without line terminator).
     */
    fun getLineText(lineNumber: Int): String

    /**
     * Returns the length of the specified line (excluding line terminator).
     */
    fun getLineLength(lineNumber: Int): Int

    /**
     * Returns the start offset of the specified line.
     */
    fun getLineStartOffset(lineNumber: Int): Int

    /**
     * Returns the end offset of the specified line (after line terminator if present).
     */
    fun getLineEndOffset(lineNumber: Int): Int

    /**
     * Converts a text offset to a position (line, column).
     */
    fun offsetToPosition(offset: Int): EditorPosition

    /**
     * Converts a position (line, column) to a text offset.
     */
    fun positionToOffset(position: EditorPosition): Int

    /**
     * Converts a position (line, column) to a text offset.
     */
    fun positionToOffset(line: Int, column: Int): Int

    /**
     * Adds a document change listener.
     */
    fun addDocumentListener(listener: DocumentListener)

    /**
     * Removes a document change listener.
     */
    fun removeDocumentListener(listener: DocumentListener)

    // --- Mutation operations (optional for read-only documents) ---

    /**
     * Inserts text at the specified offset.
     * @throws UnsupportedOperationException if document is read-only
     */
    fun insert(offset: Int, text: String)

    /**
     * Deletes text at the specified range.
     * @throws UnsupportedOperationException if document is read-only
     */
    fun delete(startOffset: Int, endOffset: Int)

    /**
     * Replaces text at the specified range.
     * @throws UnsupportedOperationException if document is read-only
     */
    fun replace(startOffset: Int, endOffset: Int, newText: String)

    /**
     * Sets the entire document text.
     * @throws UnsupportedOperationException if document is read-only
     */
    fun setText(text: String)
}
