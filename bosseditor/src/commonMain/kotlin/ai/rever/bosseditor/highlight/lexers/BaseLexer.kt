package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Base class for lexers with common tokenization utilities.
 *
 * Provides reusable patterns for:
 * - String parsing (single/double quoted, escape sequences)
 * - Number parsing (int, float, hex, binary)
 * - Comment parsing (single-line, multi-line, doc)
 * - Identifier parsing
 * - Whitespace handling
 */
abstract class BaseLexer : TokenProvider {

    /**
     * Tokenizes a line using the language-specific rules.
     */
    abstract override fun tokenizeLine(
        line: String,
        lineNumber: Int,
        startState: LexerState
    ): LineTokens

    // ========== Common Parsing Utilities ==========

    /**
     * Checks if a character is a valid identifier start.
     */
    protected open fun isIdentifierStart(char: Char): Boolean {
        return char.isLetter() || char == '_'
    }

    /**
     * Checks if a character is a valid identifier part.
     */
    protected open fun isIdentifierPart(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_'
    }

    /**
     * Reads an identifier starting at the given position.
     * Returns the end position (exclusive) or start if not an identifier.
     */
    protected fun readIdentifier(text: String, start: Int): Int {
        if (start >= text.length || !isIdentifierStart(text[start])) {
            return start
        }

        var pos = start + 1
        while (pos < text.length && isIdentifierPart(text[pos])) {
            pos++
        }
        return pos
    }

    /**
     * Reads a number starting at the given position.
     * Handles int, float, hex (0x), binary (0b), and underscores.
     * Returns the end position (exclusive) or start if not a number.
     */
    protected fun readNumber(text: String, start: Int): Int {
        if (start >= text.length) return start

        val char = text[start]
        if (!char.isDigit() && char != '.') return start

        // Handle . only if followed by digit
        if (char == '.' && (start + 1 >= text.length || !text[start + 1].isDigit())) {
            return start
        }

        var pos = start

        // Check for hex (0x) or binary (0b)
        if (char == '0' && pos + 1 < text.length) {
            val next = text[pos + 1].lowercaseChar()
            if (next == 'x') {
                // Hex number
                pos += 2
                while (pos < text.length && (text[pos].isHexDigit() || text[pos] == '_')) {
                    pos++
                }
                return readNumberSuffix(text, pos)
            } else if (next == 'b') {
                // Binary number
                pos += 2
                while (pos < text.length && (text[pos] == '0' || text[pos] == '1' || text[pos] == '_')) {
                    pos++
                }
                return readNumberSuffix(text, pos)
            }
        }

        // Integer part
        while (pos < text.length && (text[pos].isDigit() || text[pos] == '_')) {
            pos++
        }

        // Decimal part
        if (pos < text.length && text[pos] == '.') {
            val nextPos = pos + 1
            if (nextPos < text.length && text[nextPos].isDigit()) {
                pos = nextPos
                while (pos < text.length && (text[pos].isDigit() || text[pos] == '_')) {
                    pos++
                }
            }
        }

        // Exponent part
        if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
            val expStart = pos + 1
            var expPos = expStart
            if (expPos < text.length && (text[expPos] == '+' || text[expPos] == '-')) {
                expPos++
            }
            if (expPos < text.length && text[expPos].isDigit()) {
                pos = expPos
                while (pos < text.length && (text[pos].isDigit() || text[pos] == '_')) {
                    pos++
                }
            }
        }

        return readNumberSuffix(text, pos)
    }

    /**
     * Reads optional number suffix (L, f, F, u, U, etc.)
     */
    private fun readNumberSuffix(text: String, pos: Int): Int {
        var p = pos
        if (p < text.length) {
            val c = text[p].lowercaseChar()
            if (c == 'l' || c == 'f' || c == 'd' || c == 'u') {
                p++
                // Handle UL, uL, etc.
                if (p < text.length && text[p].lowercaseChar() == 'l') {
                    p++
                }
            }
        }
        return p
    }

    private fun Char.isHexDigit(): Boolean {
        return isDigit() || this in 'a'..'f' || this in 'A'..'F'
    }

    /**
     * Reads a single-quoted string (char literal).
     * Returns the end position (exclusive) or start if not a char.
     */
    protected fun readCharLiteral(text: String, start: Int): Int {
        if (start >= text.length || text[start] != '\'') return start

        var pos = start + 1
        while (pos < text.length) {
            when (text[pos]) {
                '\'' -> return pos + 1
                '\\' -> pos += 2 // Skip escape sequence
                else -> pos++
            }
        }
        return pos // Unterminated
    }

    /**
     * Reads a double-quoted string.
     * Returns the end position (exclusive) or start if not a string.
     */
    protected fun readStringLiteral(text: String, start: Int): Int {
        if (start >= text.length || text[start] != '"') return start

        var pos = start + 1
        while (pos < text.length) {
            when (text[pos]) {
                '"' -> return pos + 1
                '\\' -> pos += 2 // Skip escape sequence
                else -> pos++
            }
        }
        return pos // Unterminated
    }

    /**
     * Reads a triple-quoted raw string (Kotlin, Python).
     * Returns pair of (endPosition, isComplete).
     */
    protected fun readRawString(text: String, start: Int): Pair<Int, Boolean> {
        // Check for opening """
        if (start + 2 >= text.length ||
            text[start] != '"' ||
            text[start + 1] != '"' ||
            text[start + 2] != '"'
        ) {
            return start to true
        }

        var pos = start + 3
        while (pos + 2 < text.length) {
            if (text[pos] == '"' && text[pos + 1] == '"' && text[pos + 2] == '"') {
                return (pos + 3) to true
            }
            pos++
        }

        // Check if ends at line end
        return text.length to false // Continues on next line
    }

    /**
     * Reads until end of single-line comment.
     */
    protected fun readLineComment(text: String, start: Int): Int {
        return text.length // Always goes to end of line
    }

    /**
     * Reads until end of block comment or end of line.
     * Returns pair of (endPosition, isComplete).
     */
    protected fun readBlockComment(text: String, start: Int, endMarker: String = "*/"): Pair<Int, Boolean> {
        var pos = start
        while (pos + endMarker.length <= text.length) {
            if (text.substring(pos, pos + endMarker.length) == endMarker) {
                return (pos + endMarker.length) to true
            }
            pos++
        }
        return text.length to false // Continues on next line
    }

    /**
     * Skips whitespace starting at the given position.
     * Returns the position of the first non-whitespace character.
     */
    protected fun skipWhitespace(text: String, start: Int): Int {
        var pos = start
        while (pos < text.length && text[pos].isWhitespace()) {
            pos++
        }
        return pos
    }

    /**
     * Creates a token if start < end.
     */
    protected fun createToken(
        start: Int,
        end: Int,
        type: TokenType,
        modifiers: Set<TokenModifier> = emptySet()
    ): Token? {
        return if (start < end) Token(start, end, type, modifiers) else null
    }

    /**
     * Determines token type for a keyword or identifier.
     * Override in subclasses to add language-specific keywords.
     */
    protected open fun classifyIdentifier(identifier: String): TokenType {
        return TokenType.IDENTIFIER
    }

    /**
     * Checks if text at position matches a string.
     */
    protected fun matchesAt(text: String, pos: Int, target: String): Boolean {
        if (pos + target.length > text.length) return false
        return text.substring(pos, pos + target.length) == target
    }

    /**
     * Finds escape sequences within a string and returns tokens for them.
     */
    protected fun tokenizeStringEscapes(
        text: String,
        stringStart: Int,
        stringEnd: Int,
        baseType: TokenType = TokenType.STRING
    ): List<Token> {
        val tokens = mutableListOf<Token>()
        var pos = stringStart
        var tokenStart = stringStart

        while (pos < stringEnd) {
            if (text[pos] == '\\' && pos + 1 < stringEnd) {
                // Add string token before escape
                if (tokenStart < pos) {
                    tokens.add(Token(tokenStart, pos, baseType))
                }
                // Add escape token
                val escapeEnd = pos + 2
                tokens.add(Token(pos, escapeEnd, TokenType.STRING_ESCAPE))
                pos = escapeEnd
                tokenStart = pos
            } else {
                pos++
            }
        }

        // Add remaining string token
        if (tokenStart < stringEnd) {
            tokens.add(Token(tokenStart, stringEnd, baseType))
        }

        return tokens
    }
}
