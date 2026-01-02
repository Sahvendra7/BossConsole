package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument

/**
 * Provides indent guide calculation for the editor.
 *
 * Indent guides are vertical lines drawn at indentation levels to help
 * visualize code structure. Like IntelliJ IDEA:
 * - Guides are drawn at each indentation level
 * - The guide containing the caret is highlighted
 * - Guides skip blank/whitespace-only lines
 *
 * ## Usage
 * ```kotlin
 * val indentGuides = IndentGuides(document, tabSize = 4)
 * val guides = indentGuides.calculateGuides()
 * for (guide in guides) {
 *     drawVerticalLine(guide.column, guide.startLine, guide.endLine)
 * }
 * ```
 */
class IndentGuides(
    private val document: EditorDocument,
    private val tabSize: Int = 4
) {
    /**
     * Calculates all indent guides for the document.
     *
     * @return List of indent guide descriptors
     */
    fun calculateGuides(): List<IndentGuide> {
        val text = document.getText()
        if (text.isEmpty()) return emptyList()

        val lineCount = document.lineCount
        if (lineCount == 0) return emptyList()

        // Calculate indentation level for each line
        val lineIndents = IntArray(lineCount)
        for (line in 0 until lineCount) {
            lineIndents[line] = getLineIndentLevel(line)
        }

        // Build indent guides using a stack-based approach similar to IntelliJ
        val guides = mutableListOf<IndentGuide>()

        // For each possible indent level, find continuous regions
        val maxIndent = lineIndents.maxOrNull() ?: 0

        for (indentLevel in 1..maxIndent) {
            var startLine: Int? = null

            for (line in 0 until lineCount) {
                val lineIndent = lineIndents[line]
                val isBlankLine = isBlankOrWhitespaceOnly(line)

                // A line participates in an indent guide if:
                // - Its indent is >= the guide's indent level, OR
                // - It's a blank line (guides continue through blank lines)
                val participates = if (isBlankLine) {
                    // Check if surrounding non-blank lines have sufficient indent
                    hasSufficientIndentAround(line, indentLevel, lineIndents)
                } else {
                    lineIndent >= indentLevel
                }

                if (participates) {
                    if (startLine == null) {
                        startLine = line
                    }
                } else {
                    if (startLine != null) {
                        // End of a guide region
                        val endLine = line - 1
                        if (endLine > startLine) {
                            guides.add(IndentGuide(
                                column = (indentLevel - 1) * tabSize,
                                startLine = startLine,
                                endLine = endLine,
                                indentLevel = indentLevel
                            ))
                        }
                        startLine = null
                    }
                }
            }

            // Handle guide that extends to end of document
            if (startLine != null) {
                val endLine = lineCount - 1
                if (endLine > startLine) {
                    guides.add(IndentGuide(
                        column = (indentLevel - 1) * tabSize,
                        startLine = startLine,
                        endLine = endLine,
                        indentLevel = indentLevel
                    ))
                }
            }
        }

        return guides.sortedWith(compareBy({ it.startLine }, { it.column }))
    }

    /**
     * Gets indent guides that are visible in a given line range.
     *
     * @param startLine First visible line
     * @param endLine Last visible line
     * @return List of guides visible in this range
     */
    fun getGuidesInRange(startLine: Int, endLine: Int): List<IndentGuide> {
        return calculateGuides().filter { guide ->
            guide.startLine <= endLine && guide.endLine >= startLine
        }
    }

    /**
     * Gets the indent guide that contains the given caret position.
     *
     * @param line The caret line
     * @param column The caret column
     * @return The guide containing the caret, or null if none
     */
    fun getGuideAtCaret(line: Int, column: Int): IndentGuide? {
        val guides = calculateGuides().filter { guide ->
            line >= guide.startLine && line <= guide.endLine
        }

        // Find the guide whose column matches the caret column most closely
        // (the guide just to the left of or at the caret)
        return guides
            .filter { it.column <= column }
            .maxByOrNull { it.column }
    }

    /**
     * Gets the indentation level (in spaces) for a line.
     */
    private fun getLineIndentLevel(line: Int): Int {
        if (line < 0 || line >= document.lineCount) return 0

        val lineText = document.getLineText(line)
        var spaces = 0

        for (char in lineText) {
            when (char) {
                ' ' -> spaces++
                '\t' -> spaces += tabSize - (spaces % tabSize)
                else -> break
            }
        }

        // Convert spaces to indent level
        return spaces / tabSize
    }

    /**
     * Checks if a line is blank or contains only whitespace.
     */
    private fun isBlankOrWhitespaceOnly(line: Int): Boolean {
        if (line < 0 || line >= document.lineCount) return true
        val lineText = document.getLineText(line)
        return lineText.isBlank()
    }

    /**
     * Checks if surrounding non-blank lines have sufficient indent.
     * This allows guides to continue through blank lines.
     */
    private fun hasSufficientIndentAround(
        blankLine: Int,
        indentLevel: Int,
        lineIndents: IntArray
    ): Boolean {
        // Look for a non-blank line above with sufficient indent
        var hasAbove = false
        for (i in (blankLine - 1) downTo 0) {
            if (!isBlankOrWhitespaceOnly(i)) {
                hasAbove = lineIndents[i] >= indentLevel
                break
            }
        }

        // Look for a non-blank line below with sufficient indent
        var hasBelow = false
        for (i in (blankLine + 1) until lineIndents.size) {
            if (!isBlankOrWhitespaceOnly(i)) {
                hasBelow = lineIndents[i] >= indentLevel
                break
            }
        }

        return hasAbove && hasBelow
    }
}

/**
 * Represents a single indent guide.
 *
 * @property column The column (in characters/spaces) where the guide is drawn
 * @property startLine The first line where this guide appears
 * @property endLine The last line where this guide appears (inclusive)
 * @property indentLevel The indentation level (1-based)
 */
data class IndentGuide(
    val column: Int,
    val startLine: Int,
    val endLine: Int,
    val indentLevel: Int
) {
    /**
     * Whether this guide contains the given line.
     */
    fun containsLine(line: Int): Boolean = line in startLine..endLine

    /**
     * Number of lines this guide spans.
     */
    val lineCount: Int get() = endLine - startLine + 1
}
