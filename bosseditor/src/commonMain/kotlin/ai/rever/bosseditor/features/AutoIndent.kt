package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument

/**
 * Auto-indentation handler for the editor.
 *
 * Provides smart indentation when pressing Enter:
 * - Maintains current line's indentation level
 * - Increases indentation after opening braces/brackets
 * - Handles language-specific indentation patterns
 *
 * ## Usage
 * ```kotlin
 * val autoIndent = AutoIndent(document)
 * val newLineText = autoIndent.getNewLineIndent(caretOffset)
 * document.insert(caretOffset, newLineText)
 * ```
 */
class AutoIndent(
    private val document: EditorDocument,
    private val config: AutoIndentConfig = AutoIndentConfig()
) {
    /**
     * Characters that increase indentation when line ends with them.
     */
    private val indentIncreaseChars = setOf('{', '(', '[', ':')

    /**
     * Characters that decrease indentation when typed at line start.
     */
    private val indentDecreaseChars = setOf('}', ')', ']')

    /**
     * Gets the text to insert for a new line at the given offset.
     *
     * Returns newline + appropriate indentation based on context.
     *
     * @param offset The caret position where Enter was pressed
     * @return The text to insert (newline + indentation)
     */
    fun getNewLineIndent(offset: Int): String {
        val lineNumber = document.offsetToPosition(offset).line
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(lineStart, lineEnd)

        // Get current line's indentation
        val currentIndent = getLineIndentation(lineText)

        // Check if we need to increase indentation
        val textBeforeCaret = lineText.substring(0, (offset - lineStart).coerceIn(0, lineText.length))
        val trimmedBefore = textBeforeCaret.trimEnd()

        val shouldIncrease = trimmedBefore.isNotEmpty() &&
                indentIncreaseChars.contains(trimmedBefore.last())

        // Build the new line text
        return buildString {
            append('\n')
            append(currentIndent)
            if (shouldIncrease) {
                append(config.indentString)
            }
        }
    }

    /**
     * Gets the text to insert for a new line with smart brace handling.
     *
     * When Enter is pressed between { and }, creates properly indented block:
     * ```
     * if (condition) {|}  ->  if (condition) {
     *                             |
     *                         }
     * ```
     *
     * @param offset The caret position where Enter was pressed
     * @return Pair of (text to insert, caret offset adjustment)
     */
    fun getNewLineIndentWithBraceHandling(offset: Int): Pair<String, Int> {
        val lineNumber = document.offsetToPosition(offset).line
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(lineStart, lineEnd)

        val posInLine = offset - lineStart
        val textBeforeCaret = lineText.substring(0, posInLine.coerceIn(0, lineText.length))
        val textAfterCaret = lineText.substring(posInLine.coerceIn(0, lineText.length))

        val currentIndent = getLineIndentation(lineText)
        val trimmedBefore = textBeforeCaret.trimEnd()
        val trimmedAfter = textAfterCaret.trimStart()

        // Check for {} pair (cursor between braces)
        if (trimmedBefore.endsWith('{') && trimmedAfter.startsWith('}')) {
            val innerIndent = currentIndent + config.indentString
            val text = buildString {
                append('\n')
                append(innerIndent)
                append('\n')
                append(currentIndent)
            }
            // Caret should be at end of inner indent line
            val caretOffset = 1 + innerIndent.length
            return Pair(text, caretOffset)
        }

        // Check for () pair
        if (trimmedBefore.endsWith('(') && trimmedAfter.startsWith(')')) {
            val innerIndent = currentIndent + config.indentString
            val text = buildString {
                append('\n')
                append(innerIndent)
                append('\n')
                append(currentIndent)
            }
            val caretOffset = 1 + innerIndent.length
            return Pair(text, caretOffset)
        }

        // Check for [] pair
        if (trimmedBefore.endsWith('[') && trimmedAfter.startsWith(']')) {
            val innerIndent = currentIndent + config.indentString
            val text = buildString {
                append('\n')
                append(innerIndent)
                append('\n')
                append(currentIndent)
            }
            val caretOffset = 1 + innerIndent.length
            return Pair(text, caretOffset)
        }

        // Standard new line
        val standardIndent = getNewLineIndent(offset)
        return Pair(standardIndent, standardIndent.length)
    }

    /**
     * Calculates the indentation adjustment when a closing character is typed.
     *
     * If a closing brace/bracket is typed at the start of a line (after whitespace only),
     * the line should be dedented.
     *
     * @param offset The position where the character was typed
     * @param char The character that was typed
     * @return The number of indent units to remove (negative means dedent)
     */
    fun getIndentAdjustmentForChar(offset: Int, char: Char): Int {
        if (!indentDecreaseChars.contains(char)) {
            return 0
        }

        val lineNumber = document.offsetToPosition(offset).line
        val lineStart = document.getLineStartOffset(lineNumber)
        val textBeforeChar = document.getText(lineStart, offset)

        // Only dedent if the character is at the start of the line (after whitespace)
        if (textBeforeChar.isNotBlank()) {
            return 0
        }

        return -1 // Dedent by one level
    }

    /**
     * Re-indents a line based on its content and context.
     *
     * @param lineNumber The line to re-indent
     * @return The new indentation string, or null if no change needed
     */
    fun calculateLineIndent(lineNumber: Int): String? {
        if (lineNumber < 0 || lineNumber >= document.lineCount) {
            return null
        }

        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(lineStart, lineEnd)
        val trimmedLine = lineText.trimStart()

        if (trimmedLine.isEmpty()) {
            return null
        }

        // Get previous non-empty line's indentation as base
        var baseIndent = ""
        for (i in (lineNumber - 1) downTo 0) {
            val prevStart = document.getLineStartOffset(i)
            val prevEnd = document.getLineEndOffset(i)
            val prevText = document.getText(prevStart, prevEnd)
            if (prevText.isNotBlank()) {
                baseIndent = getLineIndentation(prevText)
                val trimmedPrev = prevText.trimEnd()
                // Increase indent if previous line ends with indent-increase char
                if (trimmedPrev.isNotEmpty() && indentIncreaseChars.contains(trimmedPrev.last())) {
                    baseIndent += config.indentString
                }
                break
            }
        }

        // Decrease indent if line starts with closing char
        if (trimmedLine.isNotEmpty() && indentDecreaseChars.contains(trimmedLine.first())) {
            baseIndent = dedent(baseIndent)
        }

        val currentIndent = getLineIndentation(lineText)
        return if (baseIndent != currentIndent) baseIndent else null
    }

    /**
     * Extracts the leading whitespace from a line.
     */
    private fun getLineIndentation(lineText: String): String {
        val indent = StringBuilder()
        for (char in lineText) {
            if (char == ' ' || char == '\t') {
                indent.append(char)
            } else {
                break
            }
        }
        return indent.toString()
    }

    /**
     * Removes one level of indentation.
     */
    private fun dedent(indent: String): String {
        return when {
            indent.endsWith(config.indentString) -> {
                indent.dropLast(config.indentString.length)
            }
            indent.endsWith("\t") -> {
                indent.dropLast(1)
            }
            indent.length >= config.tabSize -> {
                indent.dropLast(config.tabSize)
            }
            else -> ""
        }
    }
}

/**
 * Configuration for auto-indentation behavior.
 */
data class AutoIndentConfig(
    /**
     * Whether to use tabs or spaces for indentation.
     */
    val useTabs: Boolean = false,

    /**
     * Number of spaces per tab/indent level.
     */
    val tabSize: Int = 4,

    /**
     * Whether to enable smart indent (context-aware).
     */
    val smartIndent: Boolean = true
) {
    /**
     * The string used for one level of indentation.
     */
    val indentString: String = if (useTabs) "\t" else " ".repeat(tabSize)
}

/**
 * Extension to get indent level (number of indent units) from a line.
 */
fun String.getIndentLevel(tabSize: Int = 4): Int {
    var level = 0
    var spaces = 0
    for (char in this) {
        when (char) {
            '\t' -> {
                level++
                spaces = 0
            }
            ' ' -> {
                spaces++
                if (spaces >= tabSize) {
                    level++
                    spaces = 0
                }
            }
            else -> break
        }
    }
    return level
}
