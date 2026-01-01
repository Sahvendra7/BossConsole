package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument

/**
 * Provides bracket matching functionality for the editor.
 *
 * Supports matching for:
 * - Parentheses: ( )
 * - Square brackets: [ ]
 * - Curly braces: { }
 * - Angle brackets: < > (optional, for generics)
 *
 * The matcher respects:
 * - String literals (brackets inside strings are ignored)
 * - Character literals (brackets inside chars are ignored)
 * - Comments (brackets inside comments are ignored)
 *
 * ## Usage
 * ```kotlin
 * val matcher = BracketMatcher(document)
 *
 * // Find matching bracket
 * val match = matcher.findMatchingBracket(caretOffset)
 * if (match != null) {
 *     highlight(match.offset)
 * }
 * ```
 */
class BracketMatcher(
    private val document: EditorDocument
) {
    /**
     * Configuration for bracket matching.
     */
    var config: BracketMatcherConfig = BracketMatcherConfig()

    /**
     * Finds the matching bracket for the bracket at or near the given offset.
     *
     * @param offset The caret offset
     * @return The matching bracket info, or null if no match found
     */
    fun findMatchingBracket(offset: Int): BracketMatch? {
        val text = document.getText()
        if (offset < 0 || offset > text.length) return null

        // Check character at offset
        if (offset < text.length) {
            val charAtOffset = text[offset]
            if (isBracket(charAtOffset)) {
                return findMatch(text, offset, charAtOffset)
            }
        }

        // Check character before offset (caret might be after bracket)
        if (offset > 0) {
            val charBefore = text[offset - 1]
            if (isBracket(charBefore)) {
                return findMatch(text, offset - 1, charBefore)
            }
        }

        return null
    }

    /**
     * Finds all bracket pairs in the document.
     * Useful for bracket highlighting or validation.
     */
    fun findAllBracketPairs(): List<BracketPair> {
        val text = document.getText()
        val pairs = mutableListOf<BracketPair>()
        val stack = mutableListOf<BracketInfo>()

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
            if (isOpenBracket(char)) {
                stack.add(BracketInfo(i, char))
            } else if (isCloseBracket(char)) {
                val expected = getMatchingOpen(char)
                // Find matching open bracket in stack
                val matchIndex = stack.indexOfLast { it.char == expected }
                if (matchIndex >= 0) {
                    val open = stack.removeAt(matchIndex)
                    pairs.add(BracketPair(open.offset, i, open.char, char))
                }
            }

            i++
        }

        return pairs
    }

    /**
     * Finds unmatched brackets in the document.
     * Useful for error highlighting.
     */
    fun findUnmatchedBrackets(): List<UnmatchedBracket> {
        val text = document.getText()
        val unmatched = mutableListOf<UnmatchedBracket>()
        val stack = mutableListOf<BracketInfo>()

        var i = 0
        while (i < text.length) {
            // Skip strings, chars, comments
            if (text[i] == '"') {
                i = skipString(text, i)
                continue
            }
            if (text[i] == '\'') {
                i = skipChar(text, i)
                continue
            }
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
            if (isOpenBracket(char)) {
                stack.add(BracketInfo(i, char))
            } else if (isCloseBracket(char)) {
                val expected = getMatchingOpen(char)
                val matchIndex = stack.indexOfLast { it.char == expected }
                if (matchIndex >= 0) {
                    stack.removeAt(matchIndex)
                } else {
                    // Unmatched closing bracket
                    unmatched.add(UnmatchedBracket(i, char, isOpening = false))
                }
            }

            i++
        }

        // Remaining stack items are unmatched opening brackets
        for (info in stack) {
            unmatched.add(UnmatchedBracket(info.offset, info.char, isOpening = true))
        }

        return unmatched.sortedBy { it.offset }
    }

    /**
     * Gets the bracket depth at a given offset.
     * Useful for indentation guides.
     */
    fun getBracketDepth(offset: Int, bracketChar: Char = '{'): Int {
        val text = document.getText()
        var depth = 0
        val openChar = if (isOpenBracket(bracketChar)) bracketChar else getMatchingOpen(bracketChar)
        val closeChar = getMatchingClose(openChar)

        var i = 0
        while (i < offset && i < text.length) {
            // Skip strings, chars, comments
            if (text[i] == '"') {
                i = skipString(text, i)
                continue
            }
            if (text[i] == '\'') {
                i = skipChar(text, i)
                continue
            }
            if (i + 1 < text.length && text[i] == '/') {
                if (text[i + 1] == '/') {
                    i = skipLineComment(text, i)
                    continue
                } else if (text[i + 1] == '*') {
                    i = skipBlockComment(text, i)
                    continue
                }
            }

            if (text[i] == openChar) depth++
            else if (text[i] == closeChar) depth--

            i++
        }

        return depth.coerceAtLeast(0)
    }

    // Private methods

    private fun findMatch(text: String, offset: Int, bracket: Char): BracketMatch? {
        val isOpening = isOpenBracket(bracket)
        val matchingBracket = if (isOpening) getMatchingClose(bracket) else getMatchingOpen(bracket)
        val direction = if (isOpening) 1 else -1
        val searchStart = offset + direction

        var depth = 1
        var i = searchStart

        while (i >= 0 && i < text.length && depth > 0) {
            // Skip strings
            if (text[i] == '"') {
                i = if (direction > 0) skipString(text, i) else skipStringBackward(text, i)
                continue
            }

            // Skip chars
            if (text[i] == '\'') {
                i = if (direction > 0) skipChar(text, i) else skipCharBackward(text, i)
                continue
            }

            // Skip comments (only when going forward)
            if (direction > 0 && i + 1 < text.length && text[i] == '/') {
                if (text[i + 1] == '/') {
                    i = skipLineComment(text, i)
                    continue
                } else if (text[i + 1] == '*') {
                    i = skipBlockComment(text, i)
                    continue
                }
            }

            val char = text[i]
            if (char == bracket) {
                depth++
            } else if (char == matchingBracket) {
                depth--
                if (depth == 0) {
                    return BracketMatch(
                        sourceBracket = bracket,
                        sourceOffset = offset,
                        matchingBracket = matchingBracket,
                        matchingOffset = i
                    )
                }
            }

            i += direction
        }

        return null
    }

    private fun isBracket(char: Char): Boolean {
        return char in BRACKETS || (config.matchAngleBrackets && char in ANGLE_BRACKETS)
    }

    private fun isOpenBracket(char: Char): Boolean {
        return char in OPEN_BRACKETS || (config.matchAngleBrackets && char == '<')
    }

    private fun isCloseBracket(char: Char): Boolean {
        return char in CLOSE_BRACKETS || (config.matchAngleBrackets && char == '>')
    }

    private fun getMatchingClose(open: Char): Char = when (open) {
        '(' -> ')'
        '[' -> ']'
        '{' -> '}'
        '<' -> '>'
        else -> open
    }

    private fun getMatchingOpen(close: Char): Char = when (close) {
        ')' -> '('
        ']' -> '['
        '}' -> '{'
        '>' -> '<'
        else -> close
    }

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

    private fun skipStringBackward(text: String, start: Int): Int {
        // Simplified backward skip - find start of string
        var i = start - 1
        while (i >= 0) {
            if (text[i] == '"' && (i == 0 || text[i - 1] != '\\')) {
                return i - 1
            }
            i--
        }
        return -1
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

    private fun skipCharBackward(text: String, start: Int): Int {
        var i = start - 1
        while (i >= 0) {
            if (text[i] == '\'' && (i == 0 || text[i - 1] != '\\')) {
                return i - 1
            }
            i--
        }
        return -1
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

    private data class BracketInfo(val offset: Int, val char: Char)

    companion object {
        private val OPEN_BRACKETS = setOf('(', '[', '{')
        private val CLOSE_BRACKETS = setOf(')', ']', '}')
        private val BRACKETS = OPEN_BRACKETS + CLOSE_BRACKETS
        private val ANGLE_BRACKETS = setOf('<', '>')
    }
}

/**
 * Configuration for bracket matching.
 */
data class BracketMatcherConfig(
    /**
     * Whether to match angle brackets (< >).
     * Can cause false positives with comparison operators.
     */
    val matchAngleBrackets: Boolean = false
)

/**
 * Result of finding a matching bracket.
 */
data class BracketMatch(
    val sourceBracket: Char,
    val sourceOffset: Int,
    val matchingBracket: Char,
    val matchingOffset: Int
)

/**
 * A matched pair of brackets.
 */
data class BracketPair(
    val openOffset: Int,
    val closeOffset: Int,
    val openChar: Char,
    val closeChar: Char
)

/**
 * An unmatched bracket.
 */
data class UnmatchedBracket(
    val offset: Int,
    val char: Char,
    val isOpening: Boolean
)
