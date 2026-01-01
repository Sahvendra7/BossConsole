package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.OffsetRange

/**
 * Marks all occurrences of the word under the cursor.
 *
 * This feature highlights all instances of the currently selected word
 * or the word at the caret position, similar to IDE behavior.
 *
 * ## Usage
 * ```kotlin
 * val markOccurrences = MarkOccurrences(document)
 * val occurrences = markOccurrences.findOccurrences(caretOffset)
 * // Render highlights for each occurrence
 * ```
 */
class MarkOccurrences(
    private val document: EditorDocument,
    private val config: MarkOccurrencesConfig = MarkOccurrencesConfig()
) {
    /**
     * Characters that are considered part of a word.
     */
    private val wordChars = config.wordCharacters.toList().toSet()

    /**
     * Finds all occurrences of the word at the given offset.
     *
     * @param offset The caret position
     * @return List of ranges where the word occurs, empty if no word at offset
     */
    fun findOccurrences(offset: Int): List<OffsetRange> {
        val wordRange = getWordAtOffset(offset) ?: return emptyList()
        val word = document.getText(wordRange.start, wordRange.end)

        if (word.length < config.minWordLength) {
            return emptyList()
        }

        return findAllOccurrences(word)
    }

    /**
     * Finds all occurrences of the selected text.
     *
     * @param selection The current selection range
     * @return List of ranges where the text occurs
     */
    fun findOccurrencesOfSelection(selection: OffsetRange): List<OffsetRange> {
        if (selection.isEmpty) {
            return emptyList()
        }

        val selectedText = document.getText(selection.start, selection.end)

        // Don't highlight if selection spans multiple lines (unless configured)
        if (!config.highlightMultilineSelection && selectedText.contains('\n')) {
            return emptyList()
        }

        if (selectedText.length < config.minWordLength) {
            return emptyList()
        }

        return findAllOccurrences(selectedText)
    }

    /**
     * Gets the word at the given offset.
     *
     * @param offset The position to check
     * @return The range of the word, or null if not on a word
     */
    fun getWordAtOffset(offset: Int): OffsetRange? {
        if (offset < 0 || offset > document.length) {
            return null
        }

        val lineNumber = document.offsetToPosition(offset).line
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(lineStart, lineEnd)

        val posInLine = offset - lineStart

        // Check if we're on a word character
        if (posInLine >= lineText.length) {
            // At end of line, check previous char
            if (posInLine > 0 && isWordChar(lineText[posInLine - 1])) {
                return findWordBounds(lineText, posInLine - 1, lineStart)
            }
            return null
        }

        val charAtOffset = lineText[posInLine]
        if (!isWordChar(charAtOffset)) {
            // Check if previous char is a word char (cursor at end of word)
            if (posInLine > 0 && isWordChar(lineText[posInLine - 1])) {
                return findWordBounds(lineText, posInLine - 1, lineStart)
            }
            return null
        }

        return findWordBounds(lineText, posInLine, lineStart)
    }

    /**
     * Finds the bounds of the word containing the given position.
     */
    private fun findWordBounds(lineText: String, posInLine: Int, lineStart: Int): OffsetRange {
        var start = posInLine
        var end = posInLine

        // Find start of word
        while (start > 0 && isWordChar(lineText[start - 1])) {
            start--
        }

        // Find end of word
        while (end < lineText.length && isWordChar(lineText[end])) {
            end++
        }

        return OffsetRange(lineStart + start, lineStart + end)
    }

    /**
     * Finds all occurrences of the given text in the document.
     */
    private fun findAllOccurrences(text: String): List<OffsetRange> {
        val occurrences = mutableListOf<OffsetRange>()
        val docText = document.getText(0, document.length)

        var searchStart = 0
        while (searchStart < docText.length) {
            val index = if (config.caseSensitive) {
                docText.indexOf(text, searchStart)
            } else {
                docText.lowercase().indexOf(text.lowercase(), searchStart)
            }

            if (index == -1) break

            // Check whole word boundary if required
            if (config.wholeWord) {
                val isWordStart = index == 0 || !isWordChar(docText[index - 1])
                val isWordEnd = index + text.length >= docText.length ||
                        !isWordChar(docText[index + text.length])

                if (isWordStart && isWordEnd) {
                    occurrences.add(OffsetRange(index, index + text.length))
                }
            } else {
                occurrences.add(OffsetRange(index, index + text.length))
            }

            searchStart = index + 1

            // Limit number of occurrences for performance
            if (occurrences.size >= config.maxOccurrences) {
                break
            }
        }

        return occurrences
    }

    /**
     * Checks if a character is considered part of a word.
     */
    private fun isWordChar(char: Char): Boolean {
        return char in wordChars || char.isLetterOrDigit()
    }

    /**
     * Gets occurrences only within the visible range for performance.
     *
     * @param offset The caret position
     * @param visibleStart Start offset of visible area
     * @param visibleEnd End offset of visible area
     * @return List of visible occurrences
     */
    fun findVisibleOccurrences(
        offset: Int,
        visibleStart: Int,
        visibleEnd: Int
    ): List<OffsetRange> {
        val wordRange = getWordAtOffset(offset) ?: return emptyList()
        val word = document.getText(wordRange.start, wordRange.end)

        if (word.length < config.minWordLength) {
            return emptyList()
        }

        return findOccurrencesInRange(word, visibleStart, visibleEnd)
    }

    /**
     * Finds occurrences within a specific range.
     */
    private fun findOccurrencesInRange(
        text: String,
        rangeStart: Int,
        rangeEnd: Int
    ): List<OffsetRange> {
        val occurrences = mutableListOf<OffsetRange>()
        val searchText = document.getText(
            rangeStart.coerceAtLeast(0),
            rangeEnd.coerceAtMost(document.length)
        )

        var searchPos = 0
        while (searchPos < searchText.length) {
            val index = if (config.caseSensitive) {
                searchText.indexOf(text, searchPos)
            } else {
                searchText.lowercase().indexOf(text.lowercase(), searchPos)
            }

            if (index == -1) break

            val absoluteStart = rangeStart + index
            val absoluteEnd = absoluteStart + text.length

            // Check whole word boundary if required
            if (config.wholeWord) {
                val charBefore = if (absoluteStart > 0) {
                    document.getText(absoluteStart - 1, absoluteStart).firstOrNull()
                } else null
                val charAfter = if (absoluteEnd < document.length) {
                    document.getText(absoluteEnd, absoluteEnd + 1).firstOrNull()
                } else null

                val isWordStart = charBefore == null || !isWordChar(charBefore)
                val isWordEnd = charAfter == null || !isWordChar(charAfter)

                if (isWordStart && isWordEnd) {
                    occurrences.add(OffsetRange(absoluteStart, absoluteEnd))
                }
            } else {
                occurrences.add(OffsetRange(absoluteStart, absoluteEnd))
            }

            searchPos = index + 1

            if (occurrences.size >= config.maxOccurrences) {
                break
            }
        }

        return occurrences
    }
}

/**
 * Configuration for mark occurrences behavior.
 */
data class MarkOccurrencesConfig(
    /**
     * Minimum word length to trigger highlighting.
     */
    val minWordLength: Int = 2,

    /**
     * Maximum number of occurrences to highlight.
     */
    val maxOccurrences: Int = 1000,

    /**
     * Whether matching should be case-sensitive.
     */
    val caseSensitive: Boolean = true,

    /**
     * Whether to match whole words only.
     */
    val wholeWord: Boolean = true,

    /**
     * Whether to highlight multi-line selections.
     */
    val highlightMultilineSelection: Boolean = false,

    /**
     * Additional characters considered part of a word.
     */
    val wordCharacters: String = "_$"
)

/**
 * Result of mark occurrences calculation.
 */
data class OccurrenceResult(
    /**
     * The word/text being highlighted.
     */
    val text: String,

    /**
     * All occurrence ranges.
     */
    val occurrences: List<OffsetRange>,

    /**
     * The range of the word under cursor (the "origin").
     */
    val originRange: OffsetRange?
) {
    /**
     * Total count of occurrences.
     */
    val count: Int get() = occurrences.size

    /**
     * Whether there are any occurrences besides the origin.
     */
    val hasOtherOccurrences: Boolean get() = occurrences.size > 1
}
