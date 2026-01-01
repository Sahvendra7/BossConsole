package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Java syntax highlighting lexer.
 */
class JavaLexer : BaseLexer() {

    override val languageId: String = "java"
    override val fileExtensions: List<String> = listOf("java")

    companion object {
        private val KEYWORDS = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            // Java 8+
            "var", "yield", "record", "sealed", "permits", "non-sealed"
        )

        private val TYPES = setOf(
            "String", "Object", "Integer", "Long", "Double", "Float", "Boolean",
            "Byte", "Short", "Character", "Void", "Class", "Number",
            "List", "Map", "Set", "Collection", "ArrayList", "HashMap", "HashSet",
            "LinkedList", "TreeMap", "TreeSet", "Queue", "Deque", "Stack",
            "Optional", "Stream", "Comparable", "Iterable", "Iterator",
            "Exception", "RuntimeException", "Throwable", "Error",
            "Thread", "Runnable", "Callable", "Future", "CompletableFuture"
        )

        private val ANNOTATIONS = setOf(
            "Override", "Deprecated", "SuppressWarnings", "SafeVarargs",
            "FunctionalInterface", "Nullable", "NonNull", "NotNull",
            "Autowired", "Component", "Service", "Repository", "Controller",
            "RestController", "RequestMapping", "GetMapping", "PostMapping",
            "Entity", "Table", "Column", "Id", "GeneratedValue", "Test"
        )

        private val OPERATORS = setOf(
            '+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '?', ':'
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState

        while (pos < line.length) {
            val char = line[pos]

            when (state) {
                LexerState.IN_BLOCK_COMMENT, LexerState.IN_DOC_COMMENT -> {
                    val (endPos, complete) = readBlockComment(line, pos)
                    val tokenType = if (state == LexerState.IN_DOC_COMMENT) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK
                    tokens.add(Token(pos, endPos, tokenType))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        matchesAt(line, pos, "//") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        matchesAt(line, pos, "/**") -> {
                            val (endPos, complete) = readBlockComment(line, pos + 3)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_DOC))
                            pos = endPos
                            if (!complete) state = LexerState.IN_DOC_COMMENT
                        }

                        matchesAt(line, pos, "/*") -> {
                            val (endPos, complete) = readBlockComment(line, pos + 2)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                            pos = endPos
                            if (!complete) state = LexerState.IN_BLOCK_COMMENT
                        }

                        char == '"' -> {
                            // Check for text block (Java 15+)
                            if (matchesAt(line, pos, "\"\"\"")) {
                                val (endPos, complete) = readTextBlock(line, pos)
                                tokens.add(Token(pos, endPos, TokenType.STRING))
                                pos = endPos
                                if (!complete) state = LexerState.IN_MULTILINE_STRING
                            } else {
                                val endPos = readStringLiteral(line, pos)
                                tokens.add(Token(pos, endPos, TokenType.STRING))
                                pos = endPos
                            }
                        }

                        char == '\'' -> {
                            val endPos = readCharLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.CHAR))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        char == '@' -> {
                            val nameEnd = readIdentifier(line, pos + 1)
                            if (nameEnd > pos + 1) {
                                tokens.add(Token(pos, nameEnd, TokenType.ANNOTATION))
                                pos = nameEnd
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
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

                        char in OPERATORS -> {
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

                LexerState.IN_MULTILINE_STRING -> {
                    val (endPos, complete) = continueTextBlock(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                else -> pos++
            }
        }

        return LineTokens(tokens, state)
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        return when {
            identifier in KEYWORDS -> TokenType.KEYWORD
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "null" -> TokenType.NULL
            identifier in TYPES -> TokenType.TYPE
            identifier in ANNOTATIONS -> TokenType.ANNOTATION
            identifier.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readOperator(line: String, pos: Int): Int {
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", "->")
        val threeChar = listOf(">>>", "<<=", ">>=")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }

    private fun readTextBlock(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start + 3
        while (pos + 2 < line.length) {
            if (line[pos] == '"' && line[pos + 1] == '"' && line[pos + 2] == '"') {
                return (pos + 3) to true
            }
            pos++
        }
        return line.length to false
    }

    private fun continueTextBlock(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start
        while (pos + 2 < line.length) {
            if (line[pos] == '"' && line[pos + 1] == '"' && line[pos + 2] == '"') {
                return (pos + 3) to true
            }
            pos++
        }
        return line.length to false
    }
}
