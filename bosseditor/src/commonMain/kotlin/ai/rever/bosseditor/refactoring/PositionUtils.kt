package ai.rever.bosseditor.refactoring

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.lsp.protocol.Position
import ai.rever.bosseditor.lsp.protocol.Range
import ai.rever.bosseditor.lsp.protocol.TextEdit

/**
 * Utility object for position and offset conversions in text documents.
 *
 * This utility handles:
 * - Different line ending types (LF, CRLF, CR)
 * - Conversion between EditorPosition, LSP Position, and character offsets
 * - Edit validation (overlap detection)
 * - UTF-16 code unit handling for LSP compliance
 *
 * ## UTF-16 Handling
 *
 * LSP Position uses UTF-16 code units for the `character` field. Kotlin/JVM strings
 * are internally UTF-16, so string indexing and length are already in UTF-16 code units.
 *
 * Characters outside the Basic Multilingual Plane (BMP), such as emoji, are represented
 * as surrogate pairs (2 UTF-16 code units). This class handles these correctly:
 * - String indices are UTF-16 code unit indices
 * - LSP Position.character values are UTF-16 code unit offsets within a line
 *
 * All methods properly handle CRLF line endings common on Windows.
 */
object PositionUtils {

    /**
     * Checks if the character at the given index is a high surrogate (first part of a surrogate pair).
     * Characters outside the BMP are represented as surrogate pairs in UTF-16.
     */
    fun isHighSurrogate(char: Char): Boolean = char.isHighSurrogate()

    /**
     * Checks if the character at the given index is a low surrogate (second part of a surrogate pair).
     */
    fun isLowSurrogate(char: Char): Boolean = char.isLowSurrogate()

    /**
     * Checks if the given offset is in the middle of a surrogate pair.
     * This can happen if an offset points to a low surrogate.
     *
     * @param content The text content
     * @param offset The UTF-16 code unit offset to check
     * @return true if the offset is in the middle of a surrogate pair
     */
    fun isInMiddleOfSurrogatePair(content: String, offset: Int): Boolean {
        if (offset <= 0 || offset >= content.length) return false
        return content[offset].isLowSurrogate() && content[offset - 1].isHighSurrogate()
    }

    /**
     * Adjusts an offset to not be in the middle of a surrogate pair.
     * If the offset is on a low surrogate, it's moved back to the high surrogate.
     *
     * @param content The text content
     * @param offset The UTF-16 code unit offset
     * @return The adjusted offset
     */
    fun adjustOffsetForSurrogatePair(content: String, offset: Int): Int {
        if (offset <= 0 || offset >= content.length) return offset
        return if (isInMiddleOfSurrogatePair(content, offset)) offset - 1 else offset
    }

    /**
     * Counts the number of Unicode code points in a string segment.
     * This differs from string length for strings containing characters outside the BMP.
     *
     * @param content The text content
     * @param startOffset Start offset (UTF-16 code units)
     * @param endOffset End offset (UTF-16 code units)
     * @return Number of Unicode code points
     */
    fun countCodePoints(content: String, startOffset: Int = 0, endOffset: Int = content.length): Int {
        val safeStart = startOffset.coerceIn(0, content.length)
        val safeEnd = endOffset.coerceIn(safeStart, content.length)

        var count = 0
        var i = safeStart
        while (i < safeEnd) {
            val char = content[i]
            if (char.isHighSurrogate() && i + 1 < safeEnd && content[i + 1].isLowSurrogate()) {
                // Surrogate pair - counts as one code point
                count++
                i += 2
            } else {
                count++
                i++
            }
        }
        return count
    }

    /**
     * Line ending types supported in text documents.
     */
    enum class LineEnding(val value: String) {
        /** Unix/Linux/macOS line ending */
        LF("\n"),
        /** Windows line ending */
        CRLF("\r\n"),
        /** Classic Mac OS line ending (rare) */
        CR("\r")
    }

    /**
     * Detects the predominant line ending type in the content.
     *
     * @param content The text content to analyze
     * @return The detected line ending type, defaults to LF if no line endings found
     */
    fun detectLineEnding(content: String): LineEnding {
        var lfCount = 0
        var crlfCount = 0
        var crCount = 0

        var i = 0
        while (i < content.length) {
            when {
                i < content.length - 1 && content[i] == '\r' && content[i + 1] == '\n' -> {
                    crlfCount++
                    i += 2
                }
                content[i] == '\n' -> {
                    lfCount++
                    i++
                }
                content[i] == '\r' -> {
                    crCount++
                    i++
                }
                else -> i++
            }
        }

        return when {
            crlfCount >= lfCount && crlfCount >= crCount -> if (crlfCount > 0) LineEnding.CRLF else LineEnding.LF
            lfCount >= crCount -> LineEnding.LF
            else -> LineEnding.CR
        }
    }

    /**
     * Converts an EditorPosition to a character offset in the content.
     *
     * This method properly handles different line ending types.
     *
     * @param content The text content
     * @param position The editor position (0-based line and column)
     * @return The character offset
     */
    fun positionToOffset(content: String, position: EditorPosition): Int {
        return positionToOffset(content, position.line, position.column)
    }

    /**
     * Converts line and column to a character offset in the content.
     *
     * This method properly handles:
     * - Different line ending types (CRLF, LF, CR)
     * - UTF-16 surrogate pairs (won't split them)
     * - Out-of-bounds input (clamps to valid range)
     *
     * @param content The text content
     * @param line The 0-based line number
     * @param column The 0-based column number (UTF-16 code units)
     * @return The character offset (UTF-16 code unit index)
     */
    fun positionToOffset(content: String, line: Int, column: Int): Int {
        // Handle invalid input
        if (content.isEmpty()) return 0
        if (line < 0) return 0
        if (column < 0) return positionToOffset(content, line, 0)

        var currentLine = 0
        var offset = 0

        while (offset < content.length && currentLine < line) {
            when {
                // Check for CRLF first (must be before checking for \r alone)
                offset < content.length - 1 && content[offset] == '\r' && content[offset + 1] == '\n' -> {
                    currentLine++
                    offset += 2
                }
                content[offset] == '\n' -> {
                    currentLine++
                    offset++
                }
                content[offset] == '\r' -> {
                    currentLine++
                    offset++
                }
                else -> offset++
            }
        }

        // Add the column offset, but don't exceed content length or line length
        val lineEndOffset = findLineEndOffset(content, offset)
        var result = (offset + column).coerceAtMost(lineEndOffset).coerceAtMost(content.length)

        // Adjust if we landed in the middle of a surrogate pair
        result = adjustOffsetForSurrogatePair(content, result)

        return result
    }

    /**
     * Converts an LSP Position to a character offset in the content.
     *
     * LSP Position uses UTF-16 code units for the `character` field, which matches
     * Kotlin/JVM string indexing. This method handles:
     * - Different line endings (CRLF, LF, CR)
     * - Multi-byte characters (emoji, CJK, etc.)
     * - Invalid positions (returns closest valid offset)
     *
     * @param content The text content
     * @param position The LSP position (0-based line and UTF-16 code unit offset)
     * @return The character offset (UTF-16 code unit index)
     */
    fun lspPositionToOffset(content: String, position: Position): Int {
        // Validate input
        if (position.line < 0 || position.character < 0) {
            return 0
        }
        return positionToOffset(content, position.line, position.character)
    }

    /**
     * Converts a character offset to an EditorPosition.
     *
     * @param content The text content
     * @param offset The character offset
     * @return The editor position (0-based line and column)
     */
    fun offsetToPosition(content: String, offset: Int): EditorPosition {
        val (line, column) = offsetToLineColumn(content, offset)
        return EditorPosition(line, column)
    }

    /**
     * Converts a character offset to an LSP Position.
     *
     * The returned Position uses UTF-16 code units for the `character` field,
     * which is the LSP specification. The offset parameter should be a UTF-16
     * code unit index (which is the default for Kotlin/JVM string indexing).
     *
     * Note: If the offset falls in the middle of a surrogate pair, it will be
     * adjusted to the start of the pair.
     *
     * @param content The text content
     * @param offset The character offset (UTF-16 code unit index)
     * @return The LSP position (0-based line and UTF-16 code unit offset)
     */
    fun offsetToLspPosition(content: String, offset: Int): Position {
        // Adjust offset if it's in the middle of a surrogate pair
        val safeOffset = adjustOffsetForSurrogatePair(content, offset.coerceIn(0, content.length))
        val (line, column) = offsetToLineColumn(content, safeOffset)
        return Position(line = line, character = column)
    }

    /**
     * Converts a character offset to line and column.
     *
     * @param content The text content
     * @param offset The character offset
     * @return Pair of (line, column), both 0-based
     */
    fun offsetToLineColumn(content: String, offset: Int): Pair<Int, Int> {
        var line = 0
        var lineStart = 0
        var i = 0

        val safeOffset = offset.coerceAtMost(content.length)

        while (i < safeOffset) {
            when {
                // Check for CRLF first
                i < content.length - 1 && content[i] == '\r' && content[i + 1] == '\n' -> {
                    if (i + 2 <= safeOffset) {
                        line++
                        lineStart = i + 2
                        i += 2
                    } else {
                        break
                    }
                }
                content[i] == '\n' -> {
                    if (i + 1 <= safeOffset) {
                        line++
                        lineStart = i + 1
                    }
                    i++
                }
                content[i] == '\r' -> {
                    if (i + 1 <= safeOffset) {
                        line++
                        lineStart = i + 1
                    }
                    i++
                }
                else -> i++
            }
        }

        return line to (safeOffset - lineStart)
    }

    /**
     * Converts a 0-based line number to the character offset of the line's start.
     *
     * @param content The text content
     * @param line The 0-based line number
     * @return The character offset of the line's start
     */
    fun lineToOffset(content: String, line: Int): Int {
        return positionToOffset(content, line, 0)
    }

    /**
     * Finds the character offset of the start of the line containing the given offset.
     *
     * @param content The text content
     * @param offset The character offset
     * @return The offset of the line start
     */
    fun findLineStart(content: String, offset: Int): Int {
        if (offset <= 0) return 0

        var pos = (offset - 1).coerceAtMost(content.length - 1)
        while (pos >= 0) {
            val c = content[pos]
            if (c == '\n' || c == '\r') {
                return pos + 1
            }
            pos--
        }
        return 0
    }

    /**
     * Finds the character offset of the end of the line containing the given offset.
     * This returns the position just before the line terminator (or end of content).
     *
     * @param content The text content
     * @param offset The character offset
     * @return The offset of the line end (exclusive of line terminator)
     */
    fun findLineEndOffset(content: String, offset: Int): Int {
        var pos = offset.coerceAtLeast(0)
        while (pos < content.length) {
            val c = content[pos]
            if (c == '\n' || c == '\r') {
                return pos
            }
            pos++
        }
        return content.length
    }

    /**
     * Finds the character offset after the line terminator of the line containing the given offset.
     * If this is the last line, returns the content length.
     *
     * @param content The text content
     * @param offset The character offset
     * @return The offset after the line terminator
     */
    fun findLineEnd(content: String, offset: Int): Int {
        var pos = offset.coerceAtLeast(0)
        while (pos < content.length) {
            when {
                pos < content.length - 1 && content[pos] == '\r' && content[pos + 1] == '\n' -> {
                    return pos + 2
                }
                content[pos] == '\n' || content[pos] == '\r' -> {
                    return pos + 1
                }
                else -> pos++
            }
        }
        return content.length
    }

    /**
     * Converts a character offset to a 0-based line number.
     *
     * @param content The text content
     * @param offset The character offset
     * @return The 0-based line number
     */
    fun offsetToLine(content: String, offset: Int): Int {
        val (line, _) = offsetToLineColumn(content, offset)
        return line
    }

    /**
     * Validates that the given text edits do not overlap.
     *
     * Edits must not overlap for safe application. This function checks
     * if any two edits have overlapping ranges.
     *
     * @param edits The list of text edits to validate
     * @return true if no edits overlap, false if overlapping edits are found
     */
    fun validateNoOverlap(edits: List<TextEdit>): Boolean {
        if (edits.size <= 1) return true

        // Sort edits by start position
        val sorted = edits.sortedWith(
            compareBy<TextEdit> { it.range.start.line }
                .thenBy { it.range.start.character }
        )

        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]

            if (rangesOverlap(current.range, next.range)) {
                return false
            }
        }

        return true
    }

    /**
     * Checks if two LSP ranges overlap.
     *
     * @param a The first range
     * @param b The second range
     * @return true if the ranges overlap
     */
    fun rangesOverlap(a: Range, b: Range): Boolean {
        // Range a ends before b starts
        if (positionBefore(a.end, b.start) || positionEqual(a.end, b.start)) {
            return false
        }
        // Range b ends before a starts
        if (positionBefore(b.end, a.start) || positionEqual(b.end, a.start)) {
            return false
        }
        return true
    }

    /**
     * Checks if position a is strictly before position b.
     */
    private fun positionBefore(a: Position, b: Position): Boolean {
        return a.line < b.line || (a.line == b.line && a.character < b.character)
    }

    /**
     * Checks if two positions are equal.
     */
    private fun positionEqual(a: Position, b: Position): Boolean {
        return a.line == b.line && a.character == b.character
    }

    /**
     * Gets the line content at the specified line number.
     *
     * @param content The text content
     * @param line The 0-based line number
     * @return The line content (without line terminator), or empty string if line doesn't exist
     */
    fun getLine(content: String, line: Int): String {
        val start = lineToOffset(content, line)
        if (start >= content.length) return ""

        val end = findLineEndOffset(content, start)
        return content.substring(start, end)
    }

    /**
     * Counts the number of lines in the content.
     *
     * @param content The text content
     * @return The number of lines (at least 1 for non-empty content)
     */
    fun countLines(content: String): Int {
        if (content.isEmpty()) return 1

        var count = 1
        var i = 0
        while (i < content.length) {
            when {
                i < content.length - 1 && content[i] == '\r' && content[i + 1] == '\n' -> {
                    count++
                    i += 2
                }
                content[i] == '\n' || content[i] == '\r' -> {
                    count++
                    i++
                }
                else -> i++
            }
        }
        return count
    }
}
