package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Scala syntax highlighting lexer.
 */
class ScalaLexer : BaseLexer() {

    override val languageId: String = "scala"
    override val fileExtensions: List<String> = listOf("scala", "sc", "sbt")

    companion object {
        private val KEYWORDS = setOf(
            "abstract", "case", "catch", "class", "def", "do", "else", "extends",
            "false", "final", "finally", "for", "forSome", "if", "implicit",
            "import", "lazy", "match", "new", "null", "object", "override",
            "package", "private", "protected", "return", "sealed", "super",
            "this", "throw", "trait", "try", "true", "type", "val", "var",
            "while", "with", "yield",
            // Scala 3
            "enum", "export", "given", "then", "end", "extension", "inline",
            "opaque", "open", "transparent", "using", "derives"
        )

        private val TYPES = setOf(
            "Any", "AnyRef", "AnyVal", "Boolean", "Byte", "Char", "Double",
            "Float", "Int", "Long", "Nothing", "Null", "Short", "String",
            "Unit", "Array", "List", "Map", "Set", "Option", "Some", "None",
            "Either", "Left", "Right", "Try", "Success", "Failure", "Future",
            "Vector", "Seq", "IndexedSeq", "Iterator", "Iterable", "Traversable",
            "Range", "BigInt", "BigDecimal", "Tuple1", "Tuple2", "Tuple3",
            "Function0", "Function1", "Function2", "PartialFunction"
        )

        private val BUILTINS = setOf(
            "println", "print", "printf", "readLine", "require", "assert",
            "assume", "ensuring", "identity", "implicitly", "locally",
            "manifest", "optManifest", "classOf", "typeOf"
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
                    val (endPos, complete) = readNestedBlockComment(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.IN_MULTILINE_STRING -> {
                    val (endPos, stringTokens, complete) = continueMultilineString(line, pos)
                    tokens.addAll(stringTokens)
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

                        matchesAt(line, pos, "/*") -> {
                            val isDoc = matchesAt(line, pos, "/**")
                            val (endPos, complete) = readNestedBlockComment(line, pos + 2)
                            val tokenType = if (isDoc) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK
                            tokens.add(Token(pos, endPos, tokenType))
                            pos = endPos
                            if (!complete) state = LexerState.IN_BLOCK_COMMENT
                        }

                        // Multi-line string """..."""
                        matchesAt(line, pos, "\"\"\"") -> {
                            val (endPos, stringTokens, complete) = tokenizeMultilineString(line, pos)
                            tokens.addAll(stringTokens)
                            pos = endPos
                            if (!complete) state = LexerState.IN_MULTILINE_STRING
                        }

                        // String interpolation s"...", f"...", raw"..."
                        (char == 's' || char == 'f' || char == 'r') &&
                        pos + 1 < line.length && line[pos + 1] == '"' -> {
                            val prefix = char
                            if (prefix == 'r' && matchesAt(line, pos, "raw\"")) {
                                // raw string
                                val (stringTokens, endPos) = tokenizeInterpolatedString(line, pos + 3, false)
                                tokens.add(Token(pos, pos + 3, TokenType.KEYWORD))
                                tokens.addAll(stringTokens)
                                pos = endPos
                            } else {
                                val (stringTokens, endPos) = tokenizeInterpolatedString(line, pos + 1, prefix != 'r')
                                tokens.add(Token(pos, pos + 1, TokenType.KEYWORD))
                                tokens.addAll(stringTokens)
                                pos = endPos
                            }
                        }

                        // Regular string
                        char == '"' -> {
                            val endPos = readStringLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Character literal
                        char == '\'' -> {
                            // Could be char or symbol
                            if (pos + 1 < line.length && isIdentifierStart(line[pos + 1])) {
                                // Check if it's a symbol 'symbolName
                                val symEnd = readIdentifier(line, pos + 1)
                                if (symEnd < line.length && line[symEnd] == '\'') {
                                    // It's a char literal
                                    tokens.add(Token(pos, symEnd + 1, TokenType.CHAR))
                                    pos = symEnd + 1
                                } else {
                                    // It's a symbol
                                    tokens.add(Token(pos, symEnd, TokenType.STRING))
                                    pos = symEnd
                                }
                            } else {
                                val endPos = readCharLiteral(line, pos)
                                tokens.add(Token(pos, endPos, TokenType.CHAR))
                                pos = endPos.coerceAtLeast(pos + 1)
                            }
                        }

                        // Annotation
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

                        // Number
                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readScalaNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Identifier or keyword
                        isIdentifierStart(char) -> {
                            val endPos = readScalaIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos)
                            tokens.add(Token(pos, endPos, classifyIdentifier(identifier)))
                            pos = endPos
                        }

                        // Operator identifier
                        isOperatorChar(char) -> {
                            val opEnd = readOperatorIdentifier(line, pos)
                            tokens.add(Token(pos, opEnd, TokenType.OPERATOR))
                            pos = opEnd
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
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "null" -> TokenType.NULL
            identifier in TYPES -> TokenType.TYPE
            identifier in BUILTINS -> TokenType.FUNCTION_CALL
            identifier.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizeInterpolatedString(line: String, start: Int, allowInterpolation: Boolean): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1
        var tokenStart = start

        while (pos < line.length) {
            when {
                line[pos] == '"' -> {
                    tokens.add(Token(tokenStart, pos + 1, TokenType.STRING))
                    return tokens to (pos + 1)
                }
                line[pos] == '\\' && pos + 1 < line.length -> {
                    pos += 2
                }
                allowInterpolation && line[pos] == '$' -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    val (exprEnd, exprType) = readInterpolationExpr(line, pos)
                    tokens.add(Token(pos, exprEnd, TokenType.STRING_TEMPLATE))
                    pos = exprEnd
                    tokenStart = pos
                }
                else -> pos++
            }
        }

        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }
        return tokens to line.length
    }

    private fun readInterpolationExpr(line: String, start: Int): Pair<Int, TokenType> {
        var pos = start + 1
        if (pos >= line.length) return pos to TokenType.STRING_TEMPLATE

        return if (line[pos] == '{') {
            var depth = 1
            pos++
            while (pos < line.length && depth > 0) {
                when (line[pos]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                pos++
            }
            pos to TokenType.STRING_TEMPLATE
        } else {
            // Simple $identifier
            while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
            pos to TokenType.STRING_TEMPLATE
        }
    }

    private fun tokenizeMultilineString(line: String, start: Int): Triple<Int, List<Token>, Boolean> {
        val tokens = mutableListOf<Token>()
        var pos = start + 3
        var tokenStart = start

        while (pos + 2 < line.length) {
            if (line[pos] == '"' && line[pos + 1] == '"' && line[pos + 2] == '"') {
                tokens.add(Token(tokenStart, pos + 3, TokenType.STRING))
                return Triple(pos + 3, tokens, true)
            }
            if (line[pos] == '$' && pos + 1 < line.length) {
                if (tokenStart < pos) {
                    tokens.add(Token(tokenStart, pos, TokenType.STRING))
                }
                val (exprEnd, _) = readInterpolationExpr(line, pos)
                tokens.add(Token(pos, exprEnd, TokenType.STRING_TEMPLATE))
                pos = exprEnd
                tokenStart = pos
            } else {
                pos++
            }
        }

        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }
        return Triple(line.length, tokens, false)
    }

    private fun continueMultilineString(line: String, start: Int): Triple<Int, List<Token>, Boolean> {
        return tokenizeMultilineString(line, start - 3)
    }

    private fun readScalaIdentifier(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
        // Allow trailing _ followed by operators
        if (pos < line.length && line[pos - 1] == '_' && isOperatorChar(line[pos])) {
            while (pos < line.length && isOperatorChar(line[pos])) pos++
        }
        return pos
    }

    private fun readOperatorIdentifier(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && isOperatorChar(line[pos])) pos++
        return pos.coerceAtLeast(start + 1)
    }

    private fun isOperatorChar(char: Char): Boolean {
        return char in "!#%&*+-/:<=>?@\\^|~"
    }

    private fun readScalaNumber(line: String, start: Int): Int {
        var pos = start

        // Check for prefix
        if (pos + 1 < line.length && line[pos] == '0') {
            when (line[pos + 1]) {
                'x', 'X' -> {
                    pos += 2
                    while (pos < line.length && (line[pos].isHexDigit() || line[pos] == '_')) pos++
                    return readNumberSuffix(line, pos)
                }
                'b', 'B' -> {
                    pos += 2
                    while (pos < line.length && line[pos] in "01_") pos++
                    return readNumberSuffix(line, pos)
                }
            }
        }

        // Decimal
        while (pos < line.length && (line[pos].isDigit() || line[pos] == '_')) pos++

        // Float part
        if (pos < line.length && line[pos] == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) {
            pos++
            while (pos < line.length && (line[pos].isDigit() || line[pos] == '_')) pos++
        }

        // Exponent
        if (pos < line.length && line[pos] in "eE") {
            pos++
            if (pos < line.length && line[pos] in "+-") pos++
            while (pos < line.length && (line[pos].isDigit() || line[pos] == '_')) pos++
        }

        return readNumberSuffix(line, pos)
    }

    private fun readNumberSuffix(line: String, pos: Int): Int {
        var p = pos
        if (p < line.length && line[p] in "lLfFdD") p++
        return p
    }

    private fun Char.isHexDigit() = isDigit() || this in 'a'..'f' || this in 'A'..'F'

    private fun readNestedBlockComment(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start
        var depth = 1
        while (pos + 1 < line.length && depth > 0) {
            if (line[pos] == '/' && line[pos + 1] == '*') {
                depth++
                pos += 2
            } else if (line[pos] == '*' && line[pos + 1] == '/') {
                depth--
                pos += 2
            } else {
                pos++
            }
        }
        return if (depth == 0) pos to true else line.length to false
    }
}
