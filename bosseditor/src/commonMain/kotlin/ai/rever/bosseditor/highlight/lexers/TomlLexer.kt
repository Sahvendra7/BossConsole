package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * TOML syntax highlighting lexer.
 */
class TomlLexer : BaseLexer() {

    override val languageId: String = "toml"
    override val fileExtensions: List<String> = listOf("toml")

    companion object {
        private val BUILTIN_TYPES = setOf(
            "true", "false", "inf", "+inf", "-inf", "nan", "+nan", "-nan"
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState

        while (pos < line.length) {
            val char = line[pos]

            when (state) {
                LexerState.IN_MULTILINE_STRING -> {
                    val (endPos, complete) = continueMultilineString(line, pos, "\"\"\"")
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.IN_RAW_STRING -> {
                    val (endPos, complete) = continueMultilineString(line, pos, "'''")
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Comment
                        char == '#' -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Table header [[array]] or [table]
                        char == '[' -> {
                            val (headerTokens, endPos) = tokenizeTableHeader(line, pos)
                            tokens.addAll(headerTokens)
                            pos = endPos
                        }

                        // Multi-line basic string
                        matchesAt(line, pos, "\"\"\"") -> {
                            val (endPos, complete) = readMultilineString(line, pos + 3, "\"\"\"")
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                            if (!complete) state = LexerState.IN_MULTILINE_STRING
                        }

                        // Multi-line literal string
                        matchesAt(line, pos, "'''") -> {
                            val (endPos, complete) = readMultilineString(line, pos + 3, "'''")
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                            if (!complete) state = LexerState.IN_RAW_STRING
                        }

                        // Basic string
                        char == '"' -> {
                            val endPos = readBasicString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Literal string
                        char == '\'' -> {
                            val endPos = readLiteralString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Key or bare key at start of assignment
                        isKeyStart(char) -> {
                            val (keyTokens, endPos) = tokenizeKey(line, pos)
                            tokens.addAll(keyTokens)
                            pos = endPos

                            // Look for =
                            pos = skipWhitespace(line, pos)
                            if (pos < line.length && line[pos] == '=') {
                                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                                pos++
                                pos = skipWhitespace(line, pos)

                                // Value
                                if (pos < line.length) {
                                    val (valueTokens, valueEnd) = tokenizeValue(line, pos)
                                    tokens.addAll(valueTokens)
                                    pos = valueEnd
                                }
                            }
                        }

                        // Inline table
                        char == '{' -> {
                            val (tableTokens, endPos) = tokenizeInlineTable(line, pos)
                            tokens.addAll(tableTokens)
                            pos = endPos
                        }

                        // Array
                        char == '[' -> {
                            val (arrayTokens, endPos) = tokenizeArray(line, pos)
                            tokens.addAll(arrayTokens)
                            pos = endPos
                        }

                        // Number (could be date/time too)
                        char.isDigit() || char == '+' || char == '-' -> {
                            val (numTokens, endPos) = tokenizeNumberOrDateTime(line, pos)
                            tokens.addAll(numTokens)
                            pos = endPos
                        }

                        // Boolean or special values
                        char.isLetter() -> {
                            val wordEnd = readWord(line, pos)
                            val word = line.substring(pos, wordEnd)
                            val tokenType = when (word) {
                                "true", "false" -> TokenType.BOOLEAN
                                "inf", "+inf", "-inf", "nan", "+nan", "-nan" -> TokenType.NUMBER
                                else -> TokenType.IDENTIFIER
                            }
                            tokens.add(Token(pos, wordEnd, tokenType))
                            pos = wordEnd
                        }

                        char == ',' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                            pos++
                        }

                        else -> {
                            tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                            pos++
                        }
                    }
                }

                else -> pos++
            }
        }

        return LineTokens(tokens, state)
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        return when (identifier) {
            "true", "false" -> TokenType.BOOLEAN
            "inf", "+inf", "-inf", "nan", "+nan", "-nan" -> TokenType.NUMBER
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizeTableHeader(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start

        // Check for array of tables [[...]]
        val isArrayOfTables = pos + 1 < line.length && line[pos + 1] == '['

        // Opening bracket(s)
        if (isArrayOfTables) {
            tokens.add(Token(pos, pos + 2, TokenType.BRACKET))
            pos += 2
        } else {
            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
            pos++
        }

        // Table name (dotted key)
        pos = skipWhitespace(line, pos)
        while (pos < line.length && line[pos] != ']' && line[pos] != '#') {
            when {
                line[pos].isWhitespace() -> pos = skipWhitespace(line, pos)
                line[pos] == '.' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                }
                line[pos] == '"' -> {
                    val endPos = readBasicString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.TYPE))
                    pos = endPos
                }
                line[pos] == '\'' -> {
                    val endPos = readLiteralString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.TYPE))
                    pos = endPos
                }
                isKeyStart(line[pos]) -> {
                    val keyEnd = readBareKey(line, pos)
                    tokens.add(Token(pos, keyEnd, TokenType.TYPE))
                    pos = keyEnd
                }
                else -> pos++
            }
        }

        // Closing bracket(s)
        if (isArrayOfTables) {
            if (pos + 1 < line.length && line[pos] == ']' && line[pos + 1] == ']') {
                tokens.add(Token(pos, pos + 2, TokenType.BRACKET))
                pos += 2
            } else if (pos < line.length && line[pos] == ']') {
                tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                pos++
            }
        } else {
            if (pos < line.length && line[pos] == ']') {
                tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                pos++
            }
        }

        return tokens to pos
    }

    private fun tokenizeKey(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start

        while (pos < line.length) {
            val char = line[pos]
            when {
                char.isWhitespace() -> pos = skipWhitespace(line, pos)
                char == '.' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                }
                char == '=' -> break
                char == '"' -> {
                    val endPos = readBasicString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.PROPERTY))
                    pos = endPos
                }
                char == '\'' -> {
                    val endPos = readLiteralString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.PROPERTY))
                    pos = endPos
                }
                isKeyStart(char) -> {
                    val keyEnd = readBareKey(line, pos)
                    tokens.add(Token(pos, keyEnd, TokenType.PROPERTY))
                    pos = keyEnd
                }
                else -> break
            }
        }

        return tokens to pos
    }

    private fun tokenizeValue(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start

        if (pos >= line.length) return tokens to pos

        val char = line[pos]

        when {
            // Multi-line basic string
            matchesAt(line, pos, "\"\"\"") -> {
                val endIdx = line.indexOf("\"\"\"", pos + 3)
                if (endIdx >= 0) {
                    tokens.add(Token(pos, endIdx + 3, TokenType.STRING))
                    pos = endIdx + 3
                } else {
                    tokens.add(Token(pos, line.length, TokenType.STRING))
                    pos = line.length
                }
            }

            // Multi-line literal string
            matchesAt(line, pos, "'''") -> {
                val endIdx = line.indexOf("'''", pos + 3)
                if (endIdx >= 0) {
                    tokens.add(Token(pos, endIdx + 3, TokenType.STRING))
                    pos = endIdx + 3
                } else {
                    tokens.add(Token(pos, line.length, TokenType.STRING))
                    pos = line.length
                }
            }

            // Basic string
            char == '"' -> {
                val endPos = readBasicString(line, pos)
                tokens.add(Token(pos, endPos, TokenType.STRING))
                pos = endPos
            }

            // Literal string
            char == '\'' -> {
                val endPos = readLiteralString(line, pos)
                tokens.add(Token(pos, endPos, TokenType.STRING))
                pos = endPos
            }

            // Inline table
            char == '{' -> {
                val (tableTokens, endPos) = tokenizeInlineTable(line, pos)
                tokens.addAll(tableTokens)
                pos = endPos
            }

            // Array
            char == '[' -> {
                val (arrayTokens, endPos) = tokenizeArray(line, pos)
                tokens.addAll(arrayTokens)
                pos = endPos
            }

            // Boolean
            matchesAt(line, pos, "true") -> {
                tokens.add(Token(pos, pos + 4, TokenType.BOOLEAN))
                pos += 4
            }
            matchesAt(line, pos, "false") -> {
                tokens.add(Token(pos, pos + 5, TokenType.BOOLEAN))
                pos += 5
            }

            // Number or date/time
            char.isDigit() || char == '+' || char == '-' -> {
                val (numTokens, endPos) = tokenizeNumberOrDateTime(line, pos)
                tokens.addAll(numTokens)
                pos = endPos
            }

            // Special float values
            matchesAt(line, pos, "inf") || matchesAt(line, pos, "+inf") || matchesAt(line, pos, "-inf") ||
            matchesAt(line, pos, "nan") || matchesAt(line, pos, "+nan") || matchesAt(line, pos, "-nan") -> {
                val wordEnd = readWord(line, pos)
                tokens.add(Token(pos, wordEnd, TokenType.NUMBER))
                pos = wordEnd
            }

            else -> {
                val wordEnd = readWord(line, pos)
                if (wordEnd > pos) {
                    tokens.add(Token(pos, wordEnd, TokenType.IDENTIFIER))
                    pos = wordEnd
                }
            }
        }

        return tokens to pos
    }

    private fun tokenizeInlineTable(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start

        tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
        pos++

        while (pos < line.length && line[pos] != '}') {
            when {
                line[pos].isWhitespace() -> pos = skipWhitespace(line, pos)
                line[pos] == ',' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                }
                line[pos] == '#' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    return tokens to line.length
                }
                isKeyStart(line[pos]) || line[pos] == '"' || line[pos] == '\'' -> {
                    val (keyTokens, keyEnd) = tokenizeKey(line, pos)
                    tokens.addAll(keyTokens)
                    pos = keyEnd

                    pos = skipWhitespace(line, pos)
                    if (pos < line.length && line[pos] == '=') {
                        tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                        pos++
                        pos = skipWhitespace(line, pos)

                        if (pos < line.length) {
                            val (valueTokens, valueEnd) = tokenizeValue(line, pos)
                            tokens.addAll(valueTokens)
                            pos = valueEnd
                        }
                    }
                }
                else -> pos++
            }
        }

        if (pos < line.length && line[pos] == '}') {
            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
            pos++
        }

        return tokens to pos
    }

    private fun tokenizeArray(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start

        tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
        pos++

        while (pos < line.length && line[pos] != ']') {
            when {
                line[pos].isWhitespace() -> pos = skipWhitespace(line, pos)
                line[pos] == ',' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                }
                line[pos] == '#' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    return tokens to line.length
                }
                else -> {
                    val (valueTokens, valueEnd) = tokenizeValue(line, pos)
                    tokens.addAll(valueTokens)
                    pos = valueEnd
                }
            }
        }

        if (pos < line.length && line[pos] == ']') {
            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
            pos++
        }

        return tokens to pos
    }

    private fun tokenizeNumberOrDateTime(line: String, start: Int): Pair<List<Token>, Int> {
        var pos = start

        // Check for sign
        if (pos < line.length && line[pos] in "+-") pos++

        // Read the full value
        val valueStart = start
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] in ".:_+-TZe")) pos++

        val value = line.substring(valueStart, pos)

        // Determine if it's a date/time or number
        val tokenType = when {
            value.contains('T') || value.contains(':') || value.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*")) -> TokenType.NUMBER // DateTime as number type
            else -> TokenType.NUMBER
        }

        return listOf(Token(valueStart, pos, tokenType)) to pos
    }

    private fun readBasicString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '"' -> return pos + 1
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }

    private fun readLiteralString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length && line[pos] != '\'') pos++
        if (pos < line.length) pos++
        return pos
    }

    private fun readMultilineString(line: String, start: Int, delimiter: String): Pair<Int, Boolean> {
        val endIdx = line.indexOf(delimiter, start)
        return if (endIdx >= 0) {
            (endIdx + delimiter.length) to true
        } else {
            line.length to false
        }
    }

    private fun continueMultilineString(line: String, start: Int, delimiter: String): Pair<Int, Boolean> {
        val endIdx = line.indexOf(delimiter, start)
        return if (endIdx >= 0) {
            (endIdx + delimiter.length) to true
        } else {
            line.length to false
        }
    }

    private fun isKeyStart(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_' || char == '-'
    }

    private fun readBareKey(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && isKeyStart(line[pos])) pos++
        return pos
    }

    private fun readWord(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && !line[pos].isWhitespace() && line[pos] !in ",]#}=") pos++
        return pos
    }
}
