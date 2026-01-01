package ai.rever.bosseditor.model

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.EditorPosition

/**
 * Handles text operations across multiple carets.
 *
 * Operations are applied in reverse document order (bottom to top) to avoid
 * offset invalidation issues when modifying text.
 */
class MultiCaretOperations(
    private val document: EditorDocument,
    private val multiCaretModel: MultiCaretModel
) {

    /**
     * Inserts text at all caret positions.
     * Each caret gets the same text inserted.
     *
     * @param text The text to insert
     * @return List of new caret positions after insertion
     */
    fun insertAtAllCarets(text: String): List<EditorPosition> {
        if (text.isEmpty()) return multiCaretModel.allPositions

        val carets = multiCaretModel.carets.value.sortedByDescending {
            document.positionToOffset(it.position)
        }

        val newPositions = mutableListOf<EditorPosition>()

        for (caret in carets) {
            // Delete selection first if any
            if (caret.hasSelection) {
                val sel = caret.selection!!
                val startOffset = document.positionToOffset(sel.start)
                val endOffset = document.positionToOffset(sel.end)
                document.delete(startOffset, endOffset)
            }

            // Insert text
            val offset = if (caret.hasSelection) {
                document.positionToOffset(caret.selection!!.start)
            } else {
                document.positionToOffset(caret.position)
            }

            document.insert(offset, text)

            // Calculate new position after insertion
            val newOffset = offset + text.length
            newPositions.add(0, document.offsetToPosition(newOffset))
        }

        // Update caret positions
        multiCaretModel.setCaretsFromPositions(newPositions)
        return newPositions
    }

    /**
     * Inserts different text at each caret (for paste with multiple selections).
     *
     * @param texts List of texts, one per caret
     */
    fun insertDifferentAtCarets(texts: List<String>) {
        val carets = multiCaretModel.carets.value

        if (texts.size != carets.size) {
            // Fall back to inserting same text at all carets
            if (texts.isNotEmpty()) {
                insertAtAllCarets(texts.joinToString("\n"))
            }
            return
        }

        // Sort carets with their corresponding texts
        val caretsWithTexts = carets.zip(texts)
            .sortedByDescending { document.positionToOffset(it.first.position) }

        val newPositions = mutableListOf<EditorPosition>()

        for ((caret, text) in caretsWithTexts) {
            // Delete selection first if any
            if (caret.hasSelection) {
                val sel = caret.selection!!
                val startOffset = document.positionToOffset(sel.start)
                val endOffset = document.positionToOffset(sel.end)
                document.delete(startOffset, endOffset)
            }

            // Insert text
            val offset = if (caret.hasSelection) {
                document.positionToOffset(caret.selection!!.start)
            } else {
                document.positionToOffset(caret.position)
            }

            document.insert(offset, text)

            // Calculate new position
            val newOffset = offset + text.length
            newPositions.add(0, document.offsetToPosition(newOffset))
        }

        multiCaretModel.setCaretsFromPositions(newPositions)
    }

    /**
     * Deletes the character before each caret (backspace).
     */
    fun backspaceAtAllCarets() {
        val carets = multiCaretModel.carets.value.sortedByDescending {
            document.positionToOffset(it.position)
        }

        val newPositions = mutableListOf<EditorPosition>()

        for (caret in carets) {
            if (caret.hasSelection) {
                // Delete selection
                val sel = caret.selection!!
                val startOffset = document.positionToOffset(sel.start)
                val endOffset = document.positionToOffset(sel.end)
                document.delete(startOffset, endOffset)
                newPositions.add(0, sel.start)
            } else {
                val offset = document.positionToOffset(caret.position)
                if (offset > 0) {
                    document.delete(offset - 1, offset)
                    newPositions.add(0, document.offsetToPosition(offset - 1))
                } else {
                    newPositions.add(0, caret.position)
                }
            }
        }

        multiCaretModel.setCaretsFromPositions(newPositions)
    }

    /**
     * Deletes the character after each caret (delete key).
     */
    fun deleteAtAllCarets() {
        val carets = multiCaretModel.carets.value.sortedByDescending {
            document.positionToOffset(it.position)
        }

        val newPositions = mutableListOf<EditorPosition>()

        for (caret in carets) {
            if (caret.hasSelection) {
                // Delete selection
                val sel = caret.selection!!
                val startOffset = document.positionToOffset(sel.start)
                val endOffset = document.positionToOffset(sel.end)
                document.delete(startOffset, endOffset)
                newPositions.add(0, sel.start)
            } else {
                val offset = document.positionToOffset(caret.position)
                if (offset < document.length) {
                    document.delete(offset, offset + 1)
                }
                newPositions.add(0, caret.position)
            }
        }

        multiCaretModel.setCaretsFromPositions(newPositions)
    }

    /**
     * Deletes the word before each caret.
     */
    fun deleteWordBeforeAllCarets() {
        val carets = multiCaretModel.carets.value.sortedByDescending {
            document.positionToOffset(it.position)
        }

        val newPositions = mutableListOf<EditorPosition>()

        for (caret in carets) {
            if (caret.hasSelection) {
                // Delete selection
                val sel = caret.selection!!
                val startOffset = document.positionToOffset(sel.start)
                val endOffset = document.positionToOffset(sel.end)
                document.delete(startOffset, endOffset)
                newPositions.add(0, sel.start)
            } else {
                val offset = document.positionToOffset(caret.position)
                val wordStart = findPreviousWordBoundary(offset)
                if (wordStart < offset) {
                    document.delete(wordStart, offset)
                    newPositions.add(0, document.offsetToPosition(wordStart))
                } else {
                    newPositions.add(0, caret.position)
                }
            }
        }

        multiCaretModel.setCaretsFromPositions(newPositions)
    }

    /**
     * Deletes the word after each caret.
     */
    fun deleteWordAfterAllCarets() {
        val carets = multiCaretModel.carets.value.sortedByDescending {
            document.positionToOffset(it.position)
        }

        val newPositions = mutableListOf<EditorPosition>()

        for (caret in carets) {
            if (caret.hasSelection) {
                // Delete selection
                val sel = caret.selection!!
                val startOffset = document.positionToOffset(sel.start)
                val endOffset = document.positionToOffset(sel.end)
                document.delete(startOffset, endOffset)
                newPositions.add(0, sel.start)
            } else {
                val offset = document.positionToOffset(caret.position)
                val wordEnd = findNextWordBoundary(offset)
                if (wordEnd > offset) {
                    document.delete(offset, wordEnd)
                }
                newPositions.add(0, caret.position)
            }
        }

        multiCaretModel.setCaretsFromPositions(newPositions)
    }

    /**
     * Gets the combined text from all selections.
     * Each selection's text is separated by newlines.
     */
    fun getSelectedTexts(): List<String> {
        val carets = multiCaretModel.carets.value.sortedBy {
            document.positionToOffset(it.position)
        }

        return carets.mapNotNull { caret ->
            if (caret.hasSelection) {
                val sel = caret.selection!!
                val startOffset = document.positionToOffset(sel.start)
                val endOffset = document.positionToOffset(sel.end)
                document.getText(startOffset, endOffset)
            } else {
                null
            }
        }
    }

    /**
     * Gets combined selected text as a single string.
     */
    fun getCombinedSelectedText(): String {
        return getSelectedTexts().joinToString("\n")
    }

    /**
     * Deletes all selected text across all carets.
     */
    fun deleteAllSelections() {
        val carets = multiCaretModel.carets.value
            .filter { it.hasSelection }
            .sortedByDescending { document.positionToOffset(it.position) }

        val newPositions = multiCaretModel.carets.value.map { caret ->
            if (caret.hasSelection) {
                val sel = caret.selection!!
                val startOffset = document.positionToOffset(sel.start)
                val endOffset = document.positionToOffset(sel.end)
                document.delete(startOffset, endOffset)
                sel.start
            } else {
                caret.position
            }
        }

        multiCaretModel.setCaretsFromPositions(newPositions)
    }

    /**
     * Duplicates the current line(s) at each caret.
     */
    fun duplicateLinesAtAllCarets() {
        val carets = multiCaretModel.carets.value.sortedByDescending {
            it.position.line
        }

        // Get unique lines to duplicate
        val linesToDuplicate = carets.map { it.position.line }.distinct()

        for (line in linesToDuplicate) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val lineText = document.getText(lineStart, lineEnd)

            // Insert duplicate after the line
            val insertPos = if (lineText.endsWith('\n')) {
                lineEnd
            } else {
                document.insert(lineEnd, "\n")
                lineEnd + 1
            }

            val textToInsert = if (lineText.endsWith('\n')) {
                lineText
            } else {
                lineText + "\n"
            }

            document.insert(insertPos, textToInsert)
        }

        // Move carets down
        val newPositions = multiCaretModel.carets.value.map { caret ->
            EditorPosition(caret.position.line + 1, caret.position.column)
        }
        multiCaretModel.setCaretsFromPositions(newPositions)
    }

    /**
     * Adds a caret above the current caret(s).
     */
    fun addCaretAbove() {
        val carets = multiCaretModel.carets.value

        for (caret in carets) {
            if (caret.position.line > 0) {
                val newLine = caret.position.line - 1
                val lineLength = document.getLineLength(newLine)
                val column = minOf(caret.position.column, lineLength)
                multiCaretModel.addCaret(EditorPosition(newLine, column))
            }
        }
    }

    /**
     * Adds a caret below the current caret(s).
     */
    fun addCaretBelow() {
        val carets = multiCaretModel.carets.value

        for (caret in carets) {
            if (caret.position.line < document.lineCount - 1) {
                val newLine = caret.position.line + 1
                val lineLength = document.getLineLength(newLine)
                val column = minOf(caret.position.column, lineLength)
                multiCaretModel.addCaret(EditorPosition(newLine, column))
            }
        }
    }

    /**
     * Selects the next occurrence of the current word/selection.
     * Adds a new caret at that occurrence.
     */
    fun selectNextOccurrence() {
        val primaryCaret = multiCaretModel.primaryCaret

        // Get text to search for
        val searchText = if (primaryCaret.hasSelection) {
            val sel = primaryCaret.selection!!
            val startOffset = document.positionToOffset(sel.start)
            val endOffset = document.positionToOffset(sel.end)
            document.getText(startOffset, endOffset)
        } else {
            // Get word at caret
            val offset = document.positionToOffset(primaryCaret.position)
            getWordAt(offset) ?: return
        }

        if (searchText.isEmpty()) return

        // Find next occurrence
        val startSearchOffset = if (primaryCaret.hasSelection) {
            document.positionToOffset(primaryCaret.selection!!.end)
        } else {
            document.positionToOffset(primaryCaret.position)
        }

        val text = document.getText(0, document.length)
        var foundIndex = text.indexOf(searchText, startSearchOffset)

        // Wrap around if not found
        if (foundIndex < 0) {
            foundIndex = text.indexOf(searchText)
        }

        if (foundIndex >= 0) {
            // Check if already have a caret/selection at this location
            val existingCarets = multiCaretModel.carets.value
            val alreadyExists = existingCarets.any { caret ->
                val caretOffset = document.positionToOffset(caret.position)
                caret.hasSelection && document.positionToOffset(caret.selection!!.start) == foundIndex
            }

            if (!alreadyExists) {
                val startPos = document.offsetToPosition(foundIndex)
                val endPos = document.offsetToPosition(foundIndex + searchText.length)
                multiCaretModel.addCaretWithSelection(endPos, ai.rever.bosseditor.core.EditorRange(startPos, endPos))
            }
        }
    }

    /**
     * Selects all occurrences of the current word/selection.
     */
    fun selectAllOccurrences() {
        val primaryCaret = multiCaretModel.primaryCaret

        // Get text to search for
        val searchText = if (primaryCaret.hasSelection) {
            val sel = primaryCaret.selection!!
            val startOffset = document.positionToOffset(sel.start)
            val endOffset = document.positionToOffset(sel.end)
            document.getText(startOffset, endOffset)
        } else {
            val offset = document.positionToOffset(primaryCaret.position)
            getWordAt(offset) ?: return
        }

        if (searchText.isEmpty()) return

        val text = document.getText(0, document.length)
        var index = 0
        val positions = mutableListOf<Pair<EditorPosition, ai.rever.bosseditor.core.EditorRange>>()

        while (true) {
            val foundIndex = text.indexOf(searchText, index)
            if (foundIndex < 0) break

            val startPos = document.offsetToPosition(foundIndex)
            val endPos = document.offsetToPosition(foundIndex + searchText.length)
            positions.add(endPos to ai.rever.bosseditor.core.EditorRange(startPos, endPos))

            index = foundIndex + 1
        }

        if (positions.isNotEmpty()) {
            multiCaretModel.setSingleCaret(positions[0].first)
            multiCaretModel.updatePrimaryCaret(positions[0].first, positions[0].second)

            for (i in 1 until positions.size) {
                multiCaretModel.addCaretWithSelection(positions[i].first, positions[i].second)
            }
        }
    }

    // --- Private helpers ---

    private fun findPreviousWordBoundary(offset: Int): Int {
        var pos = offset
        if (pos > 0) pos--

        // Skip whitespace
        while (pos > 0 && !isWordChar(document.charAt(pos))) {
            pos--
        }

        // Find start of word
        while (pos > 0 && isWordChar(document.charAt(pos - 1))) {
            pos--
        }

        return pos
    }

    private fun findNextWordBoundary(offset: Int): Int {
        var pos = offset

        // If on whitespace, skip whitespace first then skip the word
        if (pos < document.length && !isWordChar(document.charAt(pos))) {
            while (pos < document.length && !isWordChar(document.charAt(pos))) {
                pos++
            }
            while (pos < document.length && isWordChar(document.charAt(pos))) {
                pos++
            }
        } else {
            // If on a word, just skip to end of word
            while (pos < document.length && isWordChar(document.charAt(pos))) {
                pos++
            }
        }

        return pos
    }

    private fun getWordAt(offset: Int): String? {
        if (document.length == 0) return null
        val clampedOffset = offset.coerceIn(0, document.length - 1)

        if (!isWordChar(document.charAt(clampedOffset))) {
            return null
        }

        var start = clampedOffset
        var end = clampedOffset

        while (start > 0 && isWordChar(document.charAt(start - 1))) {
            start--
        }

        while (end < document.length && isWordChar(document.charAt(end))) {
            end++
        }

        return if (start < end) {
            document.getText(start, end)
        } else {
            null
        }
    }

    private fun isWordChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_'
    }
}
