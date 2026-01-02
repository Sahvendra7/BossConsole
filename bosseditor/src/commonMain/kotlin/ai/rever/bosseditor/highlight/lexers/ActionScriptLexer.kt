package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * ActionScript syntax highlighting lexer.
 */
class ActionScriptLexer : BaseLexer() {

    override val languageId: String = "actionscript"
    override val fileExtensions: List<String> = listOf("as", "mxml")

    companion object {
        private val KEYWORDS = setOf(
            "as", "break", "case", "catch", "class", "const", "continue", "default",
            "delete", "do", "dynamic", "each", "else", "extends", "false", "final",
            "finally", "for", "function", "get", "if", "implements", "import", "in",
            "include", "instanceof", "interface", "internal", "is", "namespace",
            "native", "new", "null", "override", "package", "private", "protected",
            "public", "return", "set", "static", "super", "switch", "this", "throw",
            "to", "true", "try", "typeof", "use", "var", "void", "while", "with"
        )

        private val TYPES = setOf(
            "Array", "Boolean", "Class", "Date", "Error", "Function", "int", "Number",
            "Object", "RegExp", "String", "uint", "Vector", "XML", "XMLList",
            "ArgumentError", "DefinitionError", "EvalError", "RangeError",
            "ReferenceError", "SecurityError", "SyntaxError", "TypeError", "URIError",
            "VerifyError"
        )

        private val BUILTINS = setOf(
            "decodeURI", "decodeURIComponent", "encodeURI", "encodeURIComponent",
            "escape", "eval", "isFinite", "isNaN", "isXMLName", "parseFloat",
            "parseInt", "trace", "unescape", "Infinity", "NaN", "undefined"
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
                    val endIdx = line.indexOf("*/", pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 2, TokenType.COMMENT_BLOCK))
                        pos = endIdx + 2
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                        pos = line.length
                    }
                }

                LexerState.IN_DOC_COMMENT -> {
                    val endIdx = line.indexOf("*/", pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 2, TokenType.COMMENT_DOC))
                        pos = endIdx + 2
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_DOC))
                        pos = line.length
                    }
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Line comment
                        matchesAt(line, pos, "//") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Doc comment /** */
                        matchesAt(line, pos, "/**") -> {
                            val endIdx = line.indexOf("*/", pos + 3)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 2, TokenType.COMMENT_DOC))
                                pos = endIdx + 2
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.COMMENT_DOC))
                                pos = line.length
                                state = LexerState.IN_DOC_COMMENT
                            }
                        }

                        // Block comment /* */
                        matchesAt(line, pos, "/*") -> {
                            val endIdx = line.indexOf("*/", pos + 2)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 2, TokenType.COMMENT_BLOCK))
                                pos = endIdx + 2
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                                pos = line.length
                                state = LexerState.IN_BLOCK_COMMENT
                            }
                        }

                        // Double-quoted string
                        char == '"' -> {
                            val endPos = readString(line, pos, '"')
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Single-quoted string
                        char == '\'' -> {
                            val endPos = readString(line, pos, '\'')
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Regex /pattern/flags
                        char == '/' && isRegexContext(tokens) -> {
                            val endPos = readRegex(line, pos)
                            if (endPos > pos + 1) {
                                tokens.add(Token(pos, endPos, TokenType.REGEX))
                                pos = endPos
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                                pos++
                            }
                        }

                        // XML literal (MXML style)
                        char == '<' && pos + 1 < line.length && (line[pos + 1].isLetter() || line[pos + 1] == '/') -> {
                            val endPos = readXMLLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Number
                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readActionScriptNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Metadata [Bindable], [Event], etc.
                        char == '[' && isMetadataContext(line, pos) -> {
                            val endIdx = line.indexOf(']', pos)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 1, TokenType.ANNOTATION))
                                pos = endIdx + 1
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                                pos++
                            }
                        }

                        // Identifier or keyword
                        isIdentifierStart(char) -> {
                            val endPos = readIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos)
                            tokens.add(Token(pos, endPos, classifyIdentifier(identifier)))
                            pos = endPos
                        }

                        // Operators
                        isOperator(char) -> {
                            val opLen = readOperator(line, pos)
                            tokens.add(Token(pos, pos + opLen, TokenType.OPERATOR))
                            pos += opLen
                        }

                        char == '(' || char == ')' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PARENTHESIS))
                            pos++
                        }

                        char == '{' || char == '}' || char == '[' || char == ']' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
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
            identifier in TYPES -> TokenType.TYPE
            identifier in BUILTINS -> TokenType.FUNCTION_CALL
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "null" -> TokenType.NULL
            identifier == "undefined" -> TokenType.NULL
            identifier.first().isUpperCase() -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readString(line: String, start: Int, quote: Char): Int {
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

    private fun readRegex(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '/' -> {
                    pos++
                    // Read flags
                    while (pos < line.length && line[pos] in "gimsxy") pos++
                    return pos
                }
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                line[pos] == '[' -> {
                    // Character class
                    pos++
                    while (pos < line.length && line[pos] != ']') {
                        if (line[pos] == '\\' && pos + 1 < line.length) pos += 2
                        else pos++
                    }
                    if (pos < line.length) pos++
                }
                else -> pos++
            }
        }
        return start + 1 // Not a valid regex
    }

    private fun readXMLLiteral(line: String, start: Int): Int {
        var pos = start
        var depth = 0
        while (pos < line.length) {
            when {
                matchesAt(line, pos, "/>") -> {
                    pos += 2
                    if (depth == 0) return pos
                }
                matchesAt(line, pos, "</") -> {
                    pos += 2
                    while (pos < line.length && line[pos] != '>') pos++
                    if (pos < line.length) pos++
                    depth--
                    if (depth <= 0) return pos
                }
                line[pos] == '<' -> {
                    pos++
                    if (pos < line.length && line[pos].isLetter()) depth++
                }
                line[pos] == '>' -> {
                    pos++
                    if (depth == 0) return pos
                }
                else -> pos++
            }
        }
        return line.length
    }

    private fun isRegexContext(tokens: List<Token>): Boolean {
        if (tokens.isEmpty()) return true
        val lastToken = tokens.lastOrNull { it.type != TokenType.DEFAULT } ?: return true
        return lastToken.type in listOf(
            TokenType.OPERATOR, TokenType.PARENTHESIS, TokenType.BRACKET,
            TokenType.KEYWORD, TokenType.PUNCTUATION
        )
    }

    private fun isMetadataContext(line: String, pos: Int): Boolean {
        // Simple check: metadata usually starts with [CapitalWord
        if (pos + 1 >= line.length) return false
        return line[pos + 1].isUpperCase()
    }

    private fun readActionScriptNumber(line: String, start: Int): Int {
        var pos = start

        // Hex 0x
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1] in "xX") {
            pos += 2
            while (pos < line.length && (line[pos].isDigit() || line[pos] in "abcdefABCDEF")) pos++
            return pos
        }

        // Decimal
        while (pos < line.length && line[pos].isDigit()) pos++

        // Float
        if (pos < line.length && line[pos] == '.') {
            pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        // Exponent
        if (pos < line.length && line[pos] in "eE") {
            pos++
            if (pos < line.length && line[pos] in "+-") pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        return pos
    }

    private fun isOperator(char: Char): Boolean {
        return char in "+-*/%<>=!&|^~?:"
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("===", "!==", ">>>", "<<=", ">>=", "&&=", "||=")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "<<", ">>", "++", "--",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "as", "is", "::", "??")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
