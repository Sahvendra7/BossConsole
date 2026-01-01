package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Groovy syntax highlighting lexer.
 * Also handles Gradle build files.
 */
class GroovyLexer : BaseLexer() {

    override val languageId: String = "groovy"
    override val fileExtensions: List<String> = listOf("groovy", "gradle", "gvy", "gy", "gsh")

    companion object {
        private val KEYWORDS = setOf(
            "abstract", "as", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "def", "default",
            "do", "double", "else", "enum", "extends", "final", "finally",
            "float", "for", "goto", "if", "implements", "import", "in",
            "instanceof", "int", "interface", "long", "native", "new", "null",
            "package", "private", "protected", "public", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "trait", "transient", "try", "var", "void",
            "volatile", "while", "with"
        )

        private val GRADLE_KEYWORDS = setOf(
            "plugins", "apply", "dependencies", "repositories", "buildscript",
            "allprojects", "subprojects", "project", "task", "tasks",
            "configurations", "sourceSets", "android", "kotlin", "java",
            "implementation", "api", "compile", "compileOnly", "runtimeOnly",
            "testImplementation", "testCompile", "annotationProcessor", "kapt",
            "mavenCentral", "google", "jcenter", "maven", "gradlePluginPortal",
            "id", "version", "group", "description", "from", "into", "include",
            "exclude", "dependsOn", "doLast", "doFirst", "finalizedBy", "mustRunAfter",
            "shouldRunAfter", "enabled", "outputs", "inputs"
        )

        private val TYPES = setOf(
            "String", "Object", "Integer", "Long", "Double", "Float", "Boolean",
            "List", "Map", "Set", "Collection", "Closure", "GString", "Pattern",
            "File", "URL", "URI", "Date", "BigDecimal", "BigInteger"
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

                LexerState.IN_MULTILINE_STRING -> {
                    val (endPos, stringTokens, complete) = continueGString(line, pos)
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
                            val (endPos, complete) = readBlockComment(line, pos + 2)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                            pos = endPos
                            if (!complete) state = LexerState.IN_BLOCK_COMMENT
                        }

                        // Triple-quoted GString
                        matchesAt(line, pos, "\"\"\"") -> {
                            val (endPos, stringTokens, complete) = tokenizeTripleGString(line, pos)
                            tokens.addAll(stringTokens)
                            pos = endPos
                            if (!complete) state = LexerState.IN_MULTILINE_STRING
                        }

                        // Triple-quoted string
                        matchesAt(line, pos, "'''") -> {
                            val (endPos, complete) = readTripleQuotedString(line, pos, '\'')
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                            if (!complete) state = LexerState.IN_RAW_STRING
                        }

                        // GString (double-quoted with interpolation)
                        char == '"' -> {
                            val (stringTokens, endPos) = tokenizeGString(line, pos)
                            tokens.addAll(stringTokens)
                            pos = endPos
                        }

                        // Single-quoted string
                        char == '\'' -> {
                            val endPos = readStringLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Slashy string /regex/
                        char == '/' && canStartSlashyString(line, pos) -> {
                            val endPos = readSlashyString(line, pos)
                            if (endPos > pos + 1) {
                                tokens.add(Token(pos, endPos, TokenType.REGEX))
                                pos = endPos
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                                pos++
                            }
                        }

                        // Dollar slashy string
                        matchesAt(line, pos, "\$/") -> {
                            val endPos = readDollarSlashyString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.REGEX))
                            pos = endPos
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

                LexerState.IN_RAW_STRING -> {
                    val endIdx = line.indexOf("'''", pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 3, TokenType.STRING))
                        pos = endIdx + 3
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.STRING))
                        pos = line.length
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
            identifier in GRADLE_KEYWORDS -> TokenType.FUNCTION_CALL
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "null" -> TokenType.NULL
            identifier in TYPES -> TokenType.TYPE
            identifier.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizeGString(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1
        var tokenStart = start

        while (pos < line.length) {
            when {
                line[pos] == '"' -> {
                    tokens.add(Token(tokenStart, pos + 1, TokenType.STRING))
                    return tokens to (pos + 1)
                }
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                line[pos] == '$' -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    val (exprEnd, exprToken) = readGStringExpression(line, pos)
                    tokens.add(exprToken)
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

    private fun tokenizeTripleGString(line: String, start: Int): Triple<Int, List<Token>, Boolean> {
        val tokens = mutableListOf<Token>()
        var pos = start + 3
        var tokenStart = start

        while (pos + 2 < line.length) {
            when {
                line[pos] == '"' && line[pos + 1] == '"' && line[pos + 2] == '"' -> {
                    tokens.add(Token(tokenStart, pos + 3, TokenType.STRING))
                    return Triple(pos + 3, tokens, true)
                }
                line[pos] == '$' -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    val (exprEnd, exprToken) = readGStringExpression(line, pos)
                    tokens.add(exprToken)
                    pos = exprEnd
                    tokenStart = pos
                }
                else -> pos++
            }
        }

        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }
        return Triple(line.length, tokens, false)
    }

    private fun continueGString(line: String, start: Int): Triple<Int, List<Token>, Boolean> {
        return tokenizeTripleGString(line, start - 3).let { (endPos, tokens, complete) ->
            Triple(endPos, tokens, complete)
        }
    }

    private fun readGStringExpression(line: String, start: Int): Pair<Int, Token> {
        var pos = start + 1
        if (pos < line.length && line[pos] == '{') {
            var depth = 1
            pos++
            while (pos < line.length && depth > 0) {
                when (line[pos]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                pos++
            }
        } else {
            while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_' || line[pos] == '.')) {
                pos++
            }
        }
        return pos to Token(start, pos, TokenType.STRING_TEMPLATE)
    }

    private fun readTripleQuotedString(line: String, start: Int, quote: Char): Pair<Int, Boolean> {
        val marker = quote.toString().repeat(3)
        var pos = start + 3
        while (pos + 2 < line.length) {
            if (matchesAt(line, pos, marker)) {
                return (pos + 3) to true
            }
            pos++
        }
        return line.length to false
    }

    private fun canStartSlashyString(line: String, pos: Int): Boolean {
        if (pos == 0) return true
        val prev = (pos - 1 downTo 0).firstOrNull { !line[it].isWhitespace() } ?: return true
        return line[prev] in setOf('(', '[', '{', ',', ';', ':', '=', '~', '!', '&', '|', '?', '+', '-', '*', '%')
    }

    private fun readSlashyString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '/' -> return pos + 1
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return start
    }

    private fun readDollarSlashyString(line: String, start: Int): Int {
        var pos = start + 2
        while (pos + 1 < line.length) {
            if (line[pos] == '/' && line[pos + 1] == '$') {
                return pos + 2
            }
            pos++
        }
        return line.length
    }

    private fun isOperator(char: Char): Boolean {
        return char in setOf('+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '?', ':')
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("===", "!==", "<=>", "**=", ">>>")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "**", "?.", "?:", "..", "..<", "<<", ">>", "&=", "|=", "^=", "->", "::")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
