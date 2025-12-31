package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Java Properties file syntax highlighting lexer.
 * Also handles .env files and similar key=value formats.
 */
class PropertiesLexer : BaseLexer() {

    override val languageId: String = "properties"
    override val fileExtensions: List<String> = listOf("properties", "env", "cfg", "conf", "ini")

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState

        // Handle line continuation from previous line
        if (state == LexerState.IN_MULTILINE_STRING) {
            val (valueTokens, endPos, newState) = tokenizeValue(line, 0, true)
            tokens.addAll(valueTokens)
            return LineTokens(tokens, newState)
        }

        while (pos < line.length) {
            val char = line[pos]

            when {
                char.isWhitespace() && tokens.isEmpty() -> {
                    // Leading whitespace
                    pos = skipWhitespace(line, pos)
                }

                // Comment (# or ! at start, or after whitespace)
                (char == '#' || char == '!') && (pos == 0 || line.substring(0, pos).all { it.isWhitespace() }) -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    pos = line.length
                }

                // INI-style section header
                char == '[' && (pos == 0 || line.substring(0, pos).all { it.isWhitespace() }) -> {
                    val endBracket = line.indexOf(']', pos + 1)
                    if (endBracket >= 0) {
                        tokens.add(Token(pos, endBracket + 1, TokenType.TYPE))
                        pos = endBracket + 1
                        // Rest of line might be comment
                        pos = skipWhitespace(line, pos)
                        if (pos < line.length && (line[pos] == '#' || line[pos] == ';')) {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.TYPE))
                        pos = line.length
                    }
                }

                // INI-style comment with ;
                char == ';' && (pos == 0 || line.substring(0, pos).all { it.isWhitespace() }) -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    pos = line.length
                }

                // Key at start of line
                tokens.isEmpty() || tokens.all { it.type == TokenType.DEFAULT } -> {
                    val (keyEnd, separator) = findKeySeparator(line, pos)

                    if (keyEnd > pos) {
                        // Key
                        tokens.add(Token(pos, keyEnd, TokenType.PROPERTY))
                        pos = keyEnd

                        // Whitespace before separator
                        if (pos < line.length && line[pos].isWhitespace()) {
                            pos = skipWhitespace(line, pos)
                        }

                        // Separator (= or :)
                        if (pos < line.length && (line[pos] == '=' || line[pos] == ':')) {
                            tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                            pos++

                            // Whitespace after separator
                            if (pos < line.length && line[pos].isWhitespace()) {
                                pos = skipWhitespace(line, pos)
                            }

                            // Value
                            if (pos < line.length) {
                                val (valueTokens, valueEnd, newState) = tokenizeValue(line, pos, false)
                                tokens.addAll(valueTokens)
                                pos = valueEnd
                                if (newState != LexerState.NORMAL) {
                                    return LineTokens(tokens, newState)
                                }
                            }
                        }
                    } else {
                        // Just a key with no value
                        val lineEnd = line.length
                        tokens.add(Token(pos, lineEnd, TokenType.PROPERTY))
                        pos = lineEnd
                    }
                }

                else -> {
                    tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                    pos++
                }
            }
        }

        return LineTokens(tokens, LexerState.NORMAL)
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        return TokenType.PROPERTY
    }

    private fun findKeySeparator(line: String, start: Int): Pair<Int, Char?> {
        var pos = start
        var escaped = false

        while (pos < line.length) {
            val char = line[pos]

            if (escaped) {
                escaped = false
                pos++
                continue
            }

            when (char) {
                '\\' -> {
                    escaped = true
                    pos++
                }
                '=', ':' -> {
                    return pos to char
                }
                ' ', '\t' -> {
                    // Check if there's a separator after whitespace
                    var lookAhead = pos
                    while (lookAhead < line.length && line[lookAhead].isWhitespace()) lookAhead++
                    if (lookAhead < line.length && (line[lookAhead] == '=' || line[lookAhead] == ':')) {
                        return pos to line[lookAhead]
                    }
                    // Whitespace can be separator in properties files
                    return pos to ' '
                }
                else -> pos++
            }
        }

        return pos to null
    }

    private fun tokenizeValue(line: String, start: Int, isContinuation: Boolean): Triple<List<Token>, Int, LexerState> {
        val tokens = mutableListOf<Token>()
        var pos = start
        var tokenStart = start

        while (pos < line.length) {
            val char = line[pos]

            when {
                // Escape sequence
                char == '\\' -> {
                    if (pos + 1 < line.length) {
                        val nextChar = line[pos + 1]
                        when (nextChar) {
                            'n', 'r', 't', 'f', '\\', '=', ':', ' ', '#', '!' -> {
                                // Valid escape - continue as part of value
                                pos += 2
                            }
                            'u' -> {
                                // Unicode escape \uXXXX
                                if (tokenStart < pos) {
                                    tokens.add(Token(tokenStart, pos, TokenType.STRING))
                                }
                                val unicodeEnd = minOf(pos + 6, line.length)
                                tokens.add(Token(pos, unicodeEnd, TokenType.STRING_ESCAPE))
                                pos = unicodeEnd
                                tokenStart = pos
                            }
                            else -> pos += 2
                        }
                    } else {
                        // Line continuation
                        if (tokenStart < pos) {
                            tokens.add(Token(tokenStart, pos, TokenType.STRING))
                        }
                        tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                        return Triple(tokens, line.length, LexerState.IN_MULTILINE_STRING)
                    }
                }

                // Variable reference ${...} or $VAR
                char == '$' && pos + 1 < line.length -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }

                    val (varTokens, varEnd) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = varEnd
                    tokenStart = pos
                }

                else -> pos++
            }
        }

        // Remaining value
        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }

        return Triple(tokens, line.length, LexerState.NORMAL)
    }

    private fun tokenizeVariable(line: String, start: Int): Pair<List<Token>, Int> {
        var pos = start + 1

        if (pos >= line.length) {
            return listOf(Token(start, start + 1, TokenType.STRING)) to (start + 1)
        }

        // ${VAR} or ${VAR:-default} or ${VAR:+value}
        if (line[pos] == '{') {
            val endBrace = findMatchingBrace(line, pos + 1)
            return listOf(Token(start, endBrace, TokenType.VARIABLE)) to endBrace
        }

        // $VAR
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) {
            pos++
        }

        if (pos > start + 1) {
            return listOf(Token(start, pos, TokenType.VARIABLE)) to pos
        }

        return listOf(Token(start, start + 1, TokenType.STRING)) to (start + 1)
    }

    private fun findMatchingBrace(line: String, start: Int): Int {
        var depth = 1
        var pos = start
        while (pos < line.length && depth > 0) {
            when (line[pos]) {
                '{' -> depth++
                '}' -> depth--
            }
            pos++
        }
        return pos
    }
}
