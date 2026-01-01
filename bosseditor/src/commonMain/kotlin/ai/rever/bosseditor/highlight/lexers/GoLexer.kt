package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Go syntax highlighting lexer.
 */
class GoLexer : BaseLexer() {

    override val languageId: String = "go"
    override val fileExtensions: List<String> = listOf("go")

    companion object {
        private val KEYWORDS = setOf(
            "break", "case", "chan", "const", "continue", "default", "defer",
            "else", "fallthrough", "for", "func", "go", "goto", "if", "import",
            "interface", "map", "package", "range", "return", "select", "struct",
            "switch", "type", "var"
        )

        private val TYPES = setOf(
            "bool", "byte", "complex64", "complex128", "error", "float32", "float64",
            "int", "int8", "int16", "int32", "int64", "rune", "string",
            "uint", "uint8", "uint16", "uint32", "uint64", "uintptr", "any", "comparable"
        )

        private val BUILTINS = setOf(
            "append", "cap", "clear", "close", "complex", "copy", "delete",
            "imag", "len", "make", "max", "min", "new", "panic", "print",
            "println", "real", "recover"
        )

        private val CONSTANTS = setOf(
            "true", "false", "nil", "iota"
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState

        while (pos < line.length) {
            val char = line[pos]

            when (state) {
                LexerState.IN_BLOCK_COMMENT -> {
                    val (endPos, complete) = readBlockComment(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.IN_RAW_STRING -> {
                    val endIdx = line.indexOf('`', pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 1, TokenType.STRING))
                        pos = endIdx + 1
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.STRING))
                        pos = line.length
                    }
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

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

                        // Raw string literal
                        char == '`' -> {
                            val endIdx = line.indexOf('`', pos + 1)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 1, TokenType.STRING))
                                pos = endIdx + 1
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.STRING))
                                pos = line.length
                                state = LexerState.IN_RAW_STRING
                            }
                        }

                        char == '"' -> {
                            val endPos = readStringLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        char == '\'' -> {
                            val endPos = readCharLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.CHAR))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readGoNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        isIdentifierStart(char) -> {
                            val endPos = readIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos)
                            tokens.add(Token(pos, endPos, classifyIdentifier(identifier)))
                            pos = endPos
                        }

                        isOperator(char) -> {
                            val opLen = readOperator(line, pos)
                            tokens.add(Token(pos, pos + opLen, TokenType.OPERATOR))
                            pos += opLen
                        }

                        char == '{' || char == '}' || char == '[' || char == ']' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            pos++
                        }

                        char == '(' || char == ')' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PARENTHESIS))
                            pos++
                        }

                        else -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
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
        return when {
            identifier in KEYWORDS -> TokenType.KEYWORD
            identifier in CONSTANTS -> if (identifier == "nil") TokenType.NULL else TokenType.BOOLEAN
            identifier in TYPES -> TokenType.TYPE
            identifier in BUILTINS -> TokenType.FUNCTION_CALL
            identifier.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readGoNumber(line: String, start: Int): Int {
        var pos = start

        // Check for prefix
        if (pos + 1 < line.length && line[pos] == '0') {
            when (line[pos + 1]) {
                'x', 'X' -> {
                    pos += 2
                    while (pos < line.length && (line[pos].isHexDigit() || line[pos] == '_')) pos++
                    return pos
                }
                'o', 'O' -> {
                    pos += 2
                    while (pos < line.length && (line[pos] in '0'..'7' || line[pos] == '_')) pos++
                    return pos
                }
                'b', 'B' -> {
                    pos += 2
                    while (pos < line.length && (line[pos] in "01_")) pos++
                    return pos
                }
            }
        }

        // Decimal
        while (pos < line.length && (line[pos].isDigit() || line[pos] == '_')) pos++

        // Float
        if (pos < line.length && line[pos] == '.') {
            if (pos + 1 < line.length && line[pos + 1].isDigit()) {
                pos++
                while (pos < line.length && (line[pos].isDigit() || line[pos] == '_')) pos++
            }
        }

        // Exponent
        if (pos < line.length && line[pos] in "eE") {
            pos++
            if (pos < line.length && line[pos] in "+-") pos++
            while (pos < line.length && (line[pos].isDigit() || line[pos] == '_')) pos++
        }

        // Imaginary suffix
        if (pos < line.length && line[pos] == 'i') pos++

        return pos
    }

    private fun Char.isHexDigit() = isDigit() || this in 'a'..'f' || this in 'A'..'F'

    private fun isOperator(char: Char): Boolean {
        return char in setOf('+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', ':', '.')
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("<<=", ">>=", "&^=", "...")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", "&^", ":=", "<-")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
