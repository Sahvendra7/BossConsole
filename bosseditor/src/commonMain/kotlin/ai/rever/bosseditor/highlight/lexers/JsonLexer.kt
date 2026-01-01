package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * JSON syntax highlighting lexer.
 */
class JsonLexer : BaseLexer() {

    override val languageId: String = "json"
    override val fileExtensions: List<String> = listOf("json", "jsonc", "json5")

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState
        var isKey = true // Track if we're expecting a key

        while (pos < line.length) {
            val char = line[pos]

            when (state) {
                LexerState.IN_MULTILINE_STRING -> {
                    // JSON strings can't span lines, but we handle it for robustness
                    val endPos = readJsonString(line, pos - 1)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // JSONC comment support
                        matchesAt(line, pos, "//") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        matchesAt(line, pos, "/*") -> {
                            val (endPos, complete) = readBlockComment(line, pos + 2)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                            pos = endPos
                            if (!complete) state = LexerState.IN_BLOCK_COMMENT
                        }

                        char == '"' -> {
                            val endPos = readJsonString(line, pos)
                            val tokenType = if (isKey && isFollowedByColon(line, endPos)) {
                                TokenType.PROPERTY
                            } else {
                                TokenType.STRING
                            }
                            tokens.add(Token(pos, endPos, tokenType))
                            pos = endPos
                        }

                        char == ':' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                            isKey = false
                            pos++
                        }

                        char == ',' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                            isKey = true
                            pos++
                        }

                        char == '{' || char == '[' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            isKey = char == '{'
                            pos++
                        }

                        char == '}' || char == ']' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            isKey = false
                            pos++
                        }

                        char.isDigit() || char == '-' || char == '+' -> {
                            val endPos = readJsonNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        isIdentifierStart(char) -> {
                            val endPos = readIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos)
                            val tokenType = when (identifier) {
                                "true", "false" -> TokenType.BOOLEAN
                                "null" -> TokenType.NULL
                                else -> TokenType.IDENTIFIER
                            }
                            tokens.add(Token(pos, endPos, tokenType))
                            pos = endPos
                        }

                        else -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                            pos++
                        }
                    }
                }

                LexerState.IN_BLOCK_COMMENT -> {
                    val (endPos, complete) = readBlockComment(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                else -> pos++
            }
        }

        return LineTokens(tokens, state)
    }

    private fun readJsonString(line: String, start: Int): Int {
        if (start >= line.length || line[start] != '"') return start
        var pos = start + 1
        while (pos < line.length) {
            when (line[pos]) {
                '"' -> return pos + 1
                '\\' -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }

    private fun readJsonNumber(line: String, start: Int): Int {
        var pos = start
        // Optional minus
        if (pos < line.length && line[pos] == '-') pos++
        // Integer part
        while (pos < line.length && line[pos].isDigit()) pos++
        // Decimal part
        if (pos < line.length && line[pos] == '.') {
            pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }
        // Exponent part
        if (pos < line.length && (line[pos] == 'e' || line[pos] == 'E')) {
            pos++
            if (pos < line.length && (line[pos] == '+' || line[pos] == '-')) pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }
        return pos
    }

    private fun isFollowedByColon(line: String, pos: Int): Boolean {
        var p = pos
        while (p < line.length && line[p].isWhitespace()) p++
        return p < line.length && line[p] == ':'
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        return when (identifier) {
            "true", "false" -> TokenType.BOOLEAN
            "null" -> TokenType.NULL
            else -> TokenType.IDENTIFIER
        }
    }
}
