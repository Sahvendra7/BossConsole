package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * YAML syntax highlighting lexer.
 */
class YamlLexer : BaseLexer() {

    override val languageId: String = "yaml"
    override val fileExtensions: List<String> = listOf("yaml", "yml")

    companion object {
        private val KEYWORDS = setOf(
            "true", "false", "yes", "no", "on", "off",
            "null", "~", ".nan", ".inf", "-.inf"
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0

        // Handle multi-line strings
        if (startState == LexerState.IN_MULTILINE_STRING) {
            // Check if this is still a continuation line
            val indent = line.takeWhile { it == ' ' }.length
            if (indent > 0 || line.isEmpty()) {
                tokens.add(Token(0, line.length, TokenType.STRING))
                return LineTokens(tokens, LexerState.IN_MULTILINE_STRING)
            }
        }

        while (pos < line.length) {
            val char = line[pos]

            when {
                char.isWhitespace() -> pos = skipWhitespace(line, pos)

                // Comment
                char == '#' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    pos = line.length
                }

                // Document markers
                matchesAt(line, pos, "---") || matchesAt(line, pos, "...") -> {
                    tokens.add(Token(pos, pos + 3, TokenType.KEYWORD))
                    pos += 3
                }

                // Anchor &name and alias *name
                char == '&' || char == '*' -> {
                    val endPos = readYamlIdentifier(line, pos + 1)
                    tokens.add(Token(pos, endPos, TokenType.VARIABLE))
                    pos = endPos
                }

                // Tag !tag or !!type
                char == '!' -> {
                    val endPos = readYamlTag(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.ANNOTATION))
                    pos = endPos
                }

                // Key indicator
                char == '?' && (pos + 1 >= line.length || line[pos + 1].isWhitespace()) -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                }

                // String (quoted)
                char == '"' || char == '\'' -> {
                    val endPos = readYamlString(line, pos, char)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                }

                // Block scalar indicator | or >
                (char == '|' || char == '>') && (pos + 1 >= line.length || line[pos + 1].isWhitespace() || line[pos + 1] in "+-0123456789") -> {
                    val endPos = readBlockScalarIndicator(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.OPERATOR))
                    pos = endPos
                    // Rest of line after indicator
                    if (pos < line.length) {
                        tokens.add(Token(pos, line.length, TokenType.STRING))
                        pos = line.length
                    }
                    return LineTokens(tokens, LexerState.IN_MULTILINE_STRING)
                }

                // List item
                char == '-' && (pos + 1 >= line.length || line[pos + 1].isWhitespace()) -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                }

                // Key: value
                isIdentifierStart(char) || char == '-' || char == '.' -> {
                    val colonPos = findColon(line, pos)
                    if (colonPos > pos) {
                        // It's a key
                        tokens.add(Token(pos, colonPos, TokenType.PROPERTY))
                        tokens.add(Token(colonPos, colonPos + 1, TokenType.PUNCTUATION))
                        pos = colonPos + 1
                    } else {
                        // It's a value
                        val endPos = readYamlValue(line, pos)
                        val value = line.substring(pos, endPos).trim()
                        val tokenType = classifyYamlValue(value)
                        tokens.add(Token(pos, endPos, tokenType))
                        pos = endPos
                    }
                }

                // Colon (for inline mappings)
                char == ':' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                }

                // Flow indicators
                char == '[' || char == ']' || char == '{' || char == '}' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                    pos++
                }

                char == ',' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                }

                // Number
                char.isDigit() || (char == '-' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                    val endPos = readYamlNumber(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.NUMBER))
                    pos = endPos
                }

                else -> {
                    val endPos = readYamlValue(line, pos)
                    val value = line.substring(pos, endPos).trim()
                    val tokenType = classifyYamlValue(value)
                    tokens.add(Token(pos, endPos, tokenType))
                    pos = endPos.coerceAtLeast(pos + 1)
                }
            }
        }

        return LineTokens(tokens, LexerState.NORMAL)
    }

    private fun readYamlIdentifier(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] in "-_")) pos++
        return pos
    }

    private fun readYamlTag(line: String, start: Int): Int {
        var pos = start + 1
        // Handle !!type
        if (pos < line.length && line[pos] == '!') pos++
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] in "-_:/")) pos++
        return pos
    }

    private fun readYamlString(line: String, start: Int, quote: Char): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == quote -> return pos + 1
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }

    private fun readBlockScalarIndicator(line: String, start: Int): Int {
        var pos = start + 1
        // Optional chomping indicator
        if (pos < line.length && line[pos] in "+-") pos++
        // Optional indentation indicator
        while (pos < line.length && line[pos].isDigit()) pos++
        // Optional chomping indicator (if not already)
        if (pos < line.length && line[pos] in "+-") pos++
        return pos
    }

    private fun findColon(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length) {
            when {
                line[pos] == ':' && (pos + 1 >= line.length || line[pos + 1].isWhitespace() || line[pos + 1] in ",{}[]") -> return pos
                line[pos] == '#' -> return -1
                line[pos] in "\"'" -> {
                    val quote = line[pos]
                    pos++
                    while (pos < line.length && line[pos] != quote) {
                        if (line[pos] == '\\') pos++
                        pos++
                    }
                    if (pos < line.length) pos++
                }
                else -> pos++
            }
        }
        return -1
    }

    private fun readYamlValue(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && line[pos] != '#' && line[pos] !in ",{}[]") {
            pos++
        }
        // Trim trailing whitespace
        while (pos > start && line[pos - 1].isWhitespace()) pos--
        return pos
    }

    private fun readYamlNumber(line: String, start: Int): Int {
        var pos = start
        if (pos < line.length && line[pos] == '-') pos++

        // Check for special float values
        val remaining = line.substring(pos)
        if (remaining.startsWith(".inf") || remaining.startsWith(".nan")) {
            return pos + 4
        }

        // Hex/octal
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1] in "xXoO") {
            pos += 2
            while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
            return pos
        }

        // Regular number
        while (pos < line.length && (line[pos].isDigit() || line[pos] == '_')) pos++
        if (pos < line.length && line[pos] == '.') {
            pos++
            while (pos < line.length && (line[pos].isDigit() || line[pos] == '_')) pos++
        }
        if (pos < line.length && line[pos] in "eE") {
            pos++
            if (pos < line.length && line[pos] in "+-") pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }
        return pos
    }

    private fun classifyYamlValue(value: String): TokenType {
        val lower = value.lowercase()
        return when {
            lower in KEYWORDS -> if (lower == "null" || lower == "~") TokenType.NULL else TokenType.BOOLEAN
            value.matches(Regex("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) -> TokenType.NUMBER
            value.matches(Regex("0x[0-9a-fA-F]+")) -> TokenType.NUMBER
            else -> TokenType.STRING
        }
    }

    override fun classifyIdentifier(identifier: String): TokenType = TokenType.IDENTIFIER
}
