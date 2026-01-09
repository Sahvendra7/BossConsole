package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * JSP (Java Server Pages) syntax highlighting lexer.
 * Handles JSP tags, scriptlets, expressions, and embedded Java code.
 */
class JspLexer : BaseLexer() {

    override val languageId: String = "jsp"
    override val fileExtensions: List<String> = listOf("jsp", "jspf", "jspx", "tag", "tagx", "tld")

    companion object {
        private val JSP_DIRECTIVES = setOf(
            "page", "include", "taglib", "tag", "attribute", "variable"
        )

        private val JSP_ACTIONS = setOf(
            "jsp:include", "jsp:forward", "jsp:useBean", "jsp:setProperty",
            "jsp:getProperty", "jsp:plugin", "jsp:params", "jsp:param",
            "jsp:element", "jsp:attribute", "jsp:body", "jsp:text",
            "jsp:output", "jsp:root", "jsp:declaration", "jsp:scriptlet",
            "jsp:expression", "jsp:invoke", "jsp:doBody"
        )

        private val JAVA_KEYWORDS = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int", "interface",
            "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "try",
            "var", "void", "volatile", "while", "true", "false", "null"
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState

        while (pos < line.length) {
            when (state) {
                LexerState.IN_BLOCK_COMMENT -> {
                    // Java block comment in scriptlet
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
                    // JSP comment <%-- --%>
                    val endIdx = line.indexOf("--%>", pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 4, TokenType.COMMENT_BLOCK))
                        pos = endIdx + 4
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                        pos = line.length
                    }
                }

                LexerState.IN_MULTILINE_STRING -> {
                    // HTML content or continuing state
                    val (endPos, newState) = readHtmlUntilJsp(line, pos)
                    if (endPos > pos) {
                        tokens.add(Token(pos, endPos, TokenType.DEFAULT))
                    }
                    pos = endPos
                    state = newState
                }

                LexerState.IN_RAW_STRING -> {
                    // Inside scriptlet/expression (Java code)
                    val (javaTokens, endPos, newState) = tokenizeJavaBlock(line, pos)
                    tokens.addAll(javaTokens)
                    pos = endPos
                    state = newState
                }

                LexerState.NORMAL -> {
                    when {
                        // JSP comment <%-- --%>
                        matchesAt(line, pos, "<%--") -> {
                            val endIdx = line.indexOf("--%>", pos + 4)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 4, TokenType.COMMENT_BLOCK))
                                pos = endIdx + 4
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                                pos = line.length
                                state = LexerState.IN_DOC_COMMENT
                            }
                        }

                        // JSP directive <%@ ... %>
                        matchesAt(line, pos, "<%@") -> {
                            val endIdx = line.indexOf("%>", pos + 3)
                            if (endIdx >= 0) {
                                tokenizeDirective(line, pos, endIdx + 2, tokens)
                                pos = endIdx + 2
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.ANNOTATION))
                                pos = line.length
                            }
                        }

                        // JSP declaration <%! ... %>
                        matchesAt(line, pos, "<%!") -> {
                            tokens.add(Token(pos, pos + 3, TokenType.MARKUP_TAG))
                            pos += 3
                            state = LexerState.IN_RAW_STRING
                        }

                        // JSP expression <%= ... %>
                        matchesAt(line, pos, "<%=") -> {
                            tokens.add(Token(pos, pos + 3, TokenType.MARKUP_TAG))
                            pos += 3
                            state = LexerState.IN_RAW_STRING
                        }

                        // JSP scriptlet <% ... %>
                        matchesAt(line, pos, "<%") -> {
                            tokens.add(Token(pos, pos + 2, TokenType.MARKUP_TAG))
                            pos += 2
                            state = LexerState.IN_RAW_STRING
                        }

                        // EL expression ${...} or #{...}
                        (line[pos] == '$' || line[pos] == '#') && pos + 1 < line.length && line[pos + 1] == '{' -> {
                            val (elTokens, endPos) = tokenizeEL(line, pos)
                            tokens.addAll(elTokens)
                            pos = endPos
                        }

                        // JSP action tag <jsp:...>
                        matchesAt(line, pos, "<jsp:") -> {
                            val endIdx = findTagEnd(line, pos)
                            tokenizeJspAction(line, pos, endIdx, tokens)
                            pos = endIdx
                        }

                        // HTML comment <!-- -->
                        matchesAt(line, pos, "<!--") -> {
                            val endIdx = line.indexOf("-->", pos + 4)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 3, TokenType.COMMENT))
                                pos = endIdx + 3
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.COMMENT))
                                pos = line.length
                            }
                        }

                        // HTML/XML tag
                        line[pos] == '<' -> {
                            val endIdx = findTagEnd(line, pos)
                            tokenizeHtmlTag(line, pos, endIdx, tokens)
                            pos = endIdx
                        }

                        else -> {
                            // Regular text content
                            val textEnd = findTextEnd(line, pos)
                            if (textEnd > pos) {
                                tokens.add(Token(pos, textEnd, TokenType.DEFAULT))
                                pos = textEnd
                            } else {
                                pos++
                            }
                        }
                    }
                }
            }
        }

        return LineTokens(tokens, state)
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        return when {
            identifier in JAVA_KEYWORDS -> TokenType.KEYWORD
            identifier in JSP_DIRECTIVES -> TokenType.ANNOTATION
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "null" -> TokenType.NULL
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizeDirective(line: String, start: Int, end: Int, tokens: MutableList<Token>) {
        tokens.add(Token(start, start + 3, TokenType.MARKUP_TAG)) // <%@
        var pos = start + 3

        // Skip whitespace
        while (pos < end - 2 && line[pos].isWhitespace()) pos++

        // Directive name
        val nameStart = pos
        while (pos < end - 2 && line[pos].isLetterOrDigit()) pos++
        if (pos > nameStart) {
            tokens.add(Token(nameStart, pos, TokenType.ANNOTATION))
        }

        // Attributes
        while (pos < end - 2) {
            while (pos < end - 2 && line[pos].isWhitespace()) pos++
            if (pos >= end - 2) break

            // Attribute name
            val attrStart = pos
            while (pos < end - 2 && (line[pos].isLetterOrDigit() || line[pos] == '_' || line[pos] == ':')) pos++
            if (pos > attrStart) {
                tokens.add(Token(attrStart, pos, TokenType.IDENTIFIER))
            }

            // =
            while (pos < end - 2 && line[pos].isWhitespace()) pos++
            if (pos < end - 2 && line[pos] == '=') {
                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                pos++
            }

            // Value
            while (pos < end - 2 && line[pos].isWhitespace()) pos++
            if (pos < end - 2 && line[pos] in "\"'") {
                val quote = line[pos]
                val strStart = pos
                pos++
                while (pos < end - 2 && line[pos] != quote) pos++
                if (pos < end - 2) pos++
                tokens.add(Token(strStart, pos, TokenType.STRING))
            }
        }

        tokens.add(Token(end - 2, end, TokenType.MARKUP_TAG)) // %>
    }

    private fun tokenizeJavaBlock(line: String, start: Int): Triple<List<Token>, Int, LexerState> {
        val tokens = mutableListOf<Token>()
        var pos = start

        while (pos < line.length) {
            val char = line[pos]

            when {
                // End of JSP block
                matchesAt(line, pos, "%>") -> {
                    tokens.add(Token(pos, pos + 2, TokenType.MARKUP_TAG))
                    return Triple(tokens, pos + 2, LexerState.NORMAL)
                }

                char.isWhitespace() -> pos = skipWhitespace(line, pos)

                // Java comment
                matchesAt(line, pos, "//") -> {
                    val endPos = line.indexOf("%>", pos)
                    if (endPos >= 0) {
                        tokens.add(Token(pos, endPos, TokenType.COMMENT))
                        pos = endPos
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT))
                        pos = line.length
                    }
                }

                matchesAt(line, pos, "/*") -> {
                    val endIdx = line.indexOf("*/", pos + 2)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 2, TokenType.COMMENT_BLOCK))
                        pos = endIdx + 2
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                        return Triple(tokens, line.length, LexerState.IN_BLOCK_COMMENT)
                    }
                }

                // String
                char == '"' -> {
                    val endPos = readJavaString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                }

                char == '\'' -> {
                    val endPos = readJspCharLiteral(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.CHAR))
                    pos = endPos
                }

                // Number
                char.isDigit() -> {
                    val endPos = readJavaNumber(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.NUMBER))
                    pos = endPos
                }

                // Identifier
                isIdentifierStart(char) -> {
                    val endPos = readIdentifier(line, pos)
                    val identifier = line.substring(pos, endPos)
                    tokens.add(Token(pos, endPos, classifyIdentifier(identifier)))
                    pos = endPos
                }

                else -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                }
            }
        }

        return Triple(tokens, line.length, LexerState.IN_RAW_STRING)
    }

    private fun tokenizeEL(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        tokens.add(Token(start, start + 2, TokenType.MARKUP_TAG)) // ${ or #{
        var pos = start + 2
        var depth = 1

        while (pos < line.length && depth > 0) {
            val char = line[pos]
            when {
                char == '{' -> {
                    depth++
                    tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                    pos++
                }
                char == '}' -> {
                    depth--
                    tokens.add(Token(pos, pos + 1, if (depth == 0) TokenType.MARKUP_TAG else TokenType.BRACKET))
                    pos++
                }
                char == '"' || char == '\'' -> {
                    val endPos = readJavaString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                }
                char.isDigit() -> {
                    val endPos = readJavaNumber(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.NUMBER))
                    pos = endPos
                }
                isIdentifierStart(char) -> {
                    val endPos = readIdentifier(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.IDENTIFIER))
                    pos = endPos
                }
                char.isWhitespace() -> pos++
                else -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                }
            }
        }

        return tokens to pos
    }

    private fun tokenizeJspAction(line: String, start: Int, end: Int, tokens: MutableList<Token>) {
        // Find tag name
        var pos = start
        while (pos < end && line[pos] != ' ' && line[pos] != '>' && line[pos] != '/') pos++
        tokens.add(Token(start, pos, TokenType.MARKUP_TAG))

        // Rest is attributes (simplified)
        if (pos < end) {
            tokens.add(Token(pos, end, TokenType.DEFAULT))
        }
    }

    private fun tokenizeHtmlTag(line: String, start: Int, end: Int, tokens: MutableList<Token>) {
        tokens.add(Token(start, end, TokenType.MARKUP_TAG))
    }

    private fun readHtmlUntilJsp(line: String, start: Int): Pair<Int, LexerState> {
        var pos = start
        while (pos < line.length) {
            if (matchesAt(line, pos, "<%") || matchesAt(line, pos, "\${") || matchesAt(line, pos, "#{")) {
                return pos to LexerState.NORMAL
            }
            pos++
        }
        return line.length to LexerState.NORMAL
    }

    private fun findTagEnd(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length && line[pos] != '>') {
            if (line[pos] == '"' || line[pos] == '\'') {
                val quote = line[pos]
                pos++
                while (pos < line.length && line[pos] != quote) pos++
            }
            pos++
        }
        if (pos < line.length) pos++
        return pos
    }

    private fun findTextEnd(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length) {
            if (line[pos] == '<' || matchesAt(line, pos, "\${") || matchesAt(line, pos, "#{")) break
            pos++
        }
        return pos
    }

    private fun readJavaString(line: String, start: Int): Int {
        val quote = line[start]
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

    private fun readJspCharLiteral(line: String, start: Int): Int {
        var pos = start + 1
        if (pos < line.length && line[pos] == '\\') pos += 2
        else if (pos < line.length) pos++
        if (pos < line.length && line[pos] == '\'') pos++
        return pos
    }

    private fun readJavaNumber(line: String, start: Int): Int {
        var pos = start
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1] in "xX") {
            pos += 2
            while (pos < line.length && (line[pos].isDigit() || line[pos] in "abcdefABCDEF")) pos++
        } else {
            while (pos < line.length && line[pos].isDigit()) pos++
            if (pos < line.length && line[pos] == '.') {
                pos++
                while (pos < line.length && line[pos].isDigit()) pos++
            }
        }
        if (pos < line.length && line[pos] in "lLfFdD") pos++
        return pos
    }
}
