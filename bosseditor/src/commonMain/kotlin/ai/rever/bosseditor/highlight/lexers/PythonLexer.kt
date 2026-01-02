package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Python syntax highlighting lexer.
 */
class PythonLexer : BaseLexer() {

    override val languageId: String = "python"
    override val fileExtensions: List<String> = listOf("py", "pyw", "pyi")

    companion object {
        private val KEYWORDS = setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else",
            "except", "finally", "for", "from", "global", "if", "import",
            "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
            "return", "try", "while", "with", "yield", "match", "case"
        )

        private val BUILTINS = setOf(
            "abs", "all", "any", "ascii", "bin", "bool", "bytearray", "bytes",
            "callable", "chr", "classmethod", "compile", "complex", "delattr",
            "dict", "dir", "divmod", "enumerate", "eval", "exec", "filter",
            "float", "format", "frozenset", "getattr", "globals", "hasattr",
            "hash", "help", "hex", "id", "input", "int", "isinstance",
            "issubclass", "iter", "len", "list", "locals", "map", "max",
            "memoryview", "min", "next", "object", "oct", "open", "ord",
            "pow", "print", "property", "range", "repr", "reversed", "round",
            "set", "setattr", "slice", "sorted", "staticmethod", "str", "sum",
            "super", "tuple", "type", "vars", "zip", "__import__"
        )

        private val TYPES = setOf(
            "int", "float", "str", "bool", "list", "dict", "set", "tuple",
            "bytes", "bytearray", "complex", "frozenset", "range", "type",
            "object", "Exception", "BaseException", "List", "Dict", "Set",
            "Tuple", "Optional", "Union", "Any", "Callable", "Iterator",
            "Iterable", "Generator", "Sequence", "Mapping", "Type"
        )

        private val DECORATORS = setOf(
            "property", "staticmethod", "classmethod", "abstractmethod",
            "dataclass", "cached_property", "contextmanager", "wraps"
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState

        while (pos < line.length) {
            val char = line[pos]

            when (state) {
                LexerState.IN_MULTILINE_STRING, LexerState.IN_RAW_STRING -> {
                    val (endPos, complete) = continueMultilineString(line, pos, state == LexerState.IN_RAW_STRING)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        char == '#' -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Triple-quoted strings
                        matchesAt(line, pos, "\"\"\"") || matchesAt(line, pos, "'''") -> {
                            val quote = if (line[pos] == '"') "\"\"\"" else "'''"
                            val (endPos, complete) = readMultilineString(line, pos, quote)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                            if (!complete) {
                                state = if (quote == "\"\"\"") LexerState.IN_MULTILINE_STRING else LexerState.IN_RAW_STRING
                            }
                        }

                        // f-strings, r-strings, b-strings
                        (char == 'f' || char == 'r' || char == 'b' || char == 'F' || char == 'R' || char == 'B') &&
                        pos + 1 < line.length && (line[pos + 1] == '"' || line[pos + 1] == '\'') -> {
                            val endPos = readPrefixedString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        char == '"' || char == '\'' -> {
                            val endPos = readPythonString(line, pos, char)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        char == '@' -> {
                            val nameEnd = readIdentifier(line, pos + 1)
                            if (nameEnd > pos + 1) {
                                tokens.add(Token(pos, nameEnd, TokenType.ANNOTATION))
                                pos = nameEnd
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                                pos++
                            }
                        }

                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readNumber(line, pos)
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
            identifier == "True" || identifier == "False" -> TokenType.BOOLEAN
            identifier == "None" -> TokenType.NULL
            identifier in BUILTINS -> TokenType.FUNCTION_CALL
            identifier in TYPES -> TokenType.TYPE
            identifier in DECORATORS -> TokenType.ANNOTATION
            identifier.startsWith("__") && identifier.endsWith("__") -> TokenType.CONSTANT
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readPythonString(line: String, start: Int, quote: Char): Int {
        var pos = start + 1
        while (pos < line.length) {
            when (line[pos]) {
                quote -> return pos + 1
                '\\' -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }

    private fun readPrefixedString(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && line[pos].lowercaseChar() in "frbFRB") pos++
        if (pos >= line.length) return line.length
        val quote = line[pos]
        return readPythonString(line, pos, quote)
    }

    private fun readMultilineString(line: String, start: Int, quote: String): Pair<Int, Boolean> {
        var pos = start + 3
        while (pos + 2 < line.length) {
            if (matchesAt(line, pos, quote)) {
                return (pos + 3) to true
            }
            pos++
        }
        return line.length to false
    }

    private fun continueMultilineString(line: String, start: Int, isSingleQuote: Boolean): Pair<Int, Boolean> {
        val quote = if (isSingleQuote) "'''" else "\"\"\""
        var pos = start
        while (pos + 2 < line.length) {
            if (matchesAt(line, pos, quote)) {
                return (pos + 3) to true
            }
            pos++
        }
        return line.length to false
    }

    private fun isOperator(char: Char): Boolean {
        return char in setOf('+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '@', ':')
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("//=", "**=", ">>=", "<<=", "...")
        val twoChar = listOf("==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "%=", "**", "//", "<<", ">>", "&=", "|=", "^=", "->", ":=")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
