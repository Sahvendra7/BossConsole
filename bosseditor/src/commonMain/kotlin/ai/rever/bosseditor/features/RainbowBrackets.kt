package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument

/**
 * Provides rainbow bracket colorization for the editor.
 *
 * Assigns colors to matching bracket pairs based on their nesting depth.
 * The color cycles through 4 colors (0-3) as nesting increases:
 * - Depth 0: Color 1 (outermost)
 * - Depth 1: Color 2
 * - Depth 2: Color 3
 * - Depth 3: Color 4
 * - Depth 4: Color 1 (cycles back)
 * - ...
 *
 * ## Usage
 * ```kotlin
 * val rainbow = RainbowBrackets(document)
 * val brackets = rainbow.getRainbowBrackets()
 * for (bracket in brackets) {
 *     highlight(bracket.offset, colors.getRainbowBracketColor(bracket.depth))
 * }
 * ```
 */
class RainbowBrackets(
    private val document: EditorDocument
) {
    private val bracketMatcher = BracketMatcher(document)

    /**
     * Gets all brackets with their rainbow color depth.
     * The depth determines which color to use (cycles every 4 levels).
     *
     * Uses the same matching algorithm as findMatchingBracket() for correctness.
     *
     * @return List of rainbow brackets sorted by offset
     */
    fun getRainbowBrackets(): List<RainbowBracket> {
        val text = document.getText()
        if (text.isEmpty()) return emptyList()

        val result = mutableListOf<RainbowBracket>()

        // Single unified stack for ALL bracket types (like BracketMatcher.findAllBracketPairs)
        // Each entry: (offset, char, depth)
        val stack = mutableListOf<Triple<Int, Char, Int>>()

        var i = 0
        while (i < text.length) {
            // Skip strings
            if (text[i] == '"') {
                i = skipString(text, i)
                continue
            }

            // Skip chars
            if (text[i] == '\'') {
                i = skipChar(text, i)
                continue
            }

            // Skip comments
            if (i + 1 < text.length && text[i] == '/') {
                if (text[i + 1] == '/') {
                    i = skipLineComment(text, i)
                    continue
                } else if (text[i + 1] == '*') {
                    i = skipBlockComment(text, i)
                    continue
                }
            }

            val char = text[i]

            when (char) {
                '(', '[', '{' -> {
                    // Opening bracket: depth = current stack size
                    val depth = stack.size
                    result.add(RainbowBracket(i, char, depth))
                    stack.add(Triple(i, char, depth))
                }
                ')', ']', '}' -> {
                    // Closing bracket: find matching opening bracket by type
                    val expectedOpen = when (char) {
                        ')' -> '('
                        ']' -> '['
                        '}' -> '{'
                        else -> char
                    }
                    // Find last matching opening bracket (same as BracketMatcher)
                    val matchIndex = stack.indexOfLast { it.second == expectedOpen }
                    if (matchIndex >= 0) {
                        val (_, _, depth) = stack.removeAt(matchIndex)
                        result.add(RainbowBracket(i, char, depth))
                    }
                }
            }

            i++
        }

        return result.sortedBy { it.offset }
    }

    // Helper methods for skipping strings, chars, and comments

    private fun skipString(text: String, start: Int): Int {
        // Handle triple-quoted strings
        if (start + 2 < text.length && text[start + 1] == '"' && text[start + 2] == '"') {
            var i = start + 3
            while (i + 2 < text.length) {
                if (text[i] == '"' && text[i + 1] == '"' && text[i + 2] == '"') {
                    return i + 3
                }
                i++
            }
            return text.length
        }

        // Regular string
        var i = start + 1
        while (i < text.length) {
            if (text[i] == '"' && text[i - 1] != '\\') {
                return i + 1
            }
            if (text[i] == '\\' && i + 1 < text.length) {
                i++ // Skip escaped char
            }
            i++
        }
        return text.length
    }

    private fun skipChar(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            if (text[i] == '\'' && text[i - 1] != '\\') {
                return i + 1
            }
            if (text[i] == '\\' && i + 1 < text.length) {
                i++
            }
            i++
        }
        return text.length
    }

    private fun skipLineComment(text: String, start: Int): Int {
        var i = start + 2
        while (i < text.length && text[i] != '\n') {
            i++
        }
        return i + 1
    }

    private fun skipBlockComment(text: String, start: Int): Int {
        var i = start + 2
        while (i + 1 < text.length) {
            if (text[i] == '*' && text[i + 1] == '/') {
                return i + 2
            }
            i++
        }
        return text.length
    }

    /**
     * Gets rainbow brackets for a specific line.
     * More efficient for per-line rendering.
     *
     * @param line The line number (0-indexed)
     * @return List of rainbow brackets on this line
     */
    fun getRainbowBracketsForLine(line: Int): List<RainbowBracket> {
        val lineStartOffset = document.getLineStartOffset(line)
        val lineEndOffset = if (line < document.lineCount - 1) {
            document.getLineStartOffset(line + 1)
        } else {
            document.length
        }

        return getRainbowBrackets().filter { bracket ->
            bracket.offset in lineStartOffset until lineEndOffset
        }
    }

    /**
     * Gets the color depth for a specific offset if it's a bracket.
     *
     * @param offset The document offset
     * @return The depth for coloring, or null if not a bracket
     */
    fun getDepthAtOffset(offset: Int): Int? {
        return getRainbowBrackets().find { it.offset == offset }?.depth
    }

}

/**
 * A bracket with its rainbow color depth.
 *
 * @property offset The offset in the document
 * @property char The bracket character
 * @property depth The nesting depth (0-based), use with getRainbowBracketColor(depth)
 */
data class RainbowBracket(
    val offset: Int,
    val char: Char,
    val depth: Int
)
