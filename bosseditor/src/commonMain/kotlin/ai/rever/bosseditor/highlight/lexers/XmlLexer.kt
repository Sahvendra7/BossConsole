package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * XML syntax highlighting lexer.
 * Also serves as base for HTML lexer.
 */
open class XmlLexer : BaseLexer() {

    override val languageId: String = "xml"
    override val fileExtensions: List<String> = listOf("xml", "xsd", "xsl", "xslt", "svg", "plist", "xaml")

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState

        while (pos < line.length) {
            when (state) {
                LexerState.IN_BLOCK_COMMENT -> {
                    val endIdx = line.indexOf("-->", pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 3, TokenType.COMMENT_BLOCK))
                        pos = endIdx + 3
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                        pos = line.length
                    }
                }

                LexerState.IN_RAW_STRING -> { // CDATA
                    val endIdx = line.indexOf("]]>", pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 3, TokenType.STRING))
                        pos = endIdx + 3
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.STRING))
                        pos = line.length
                    }
                }

                LexerState.NORMAL -> {
                    val char = line[pos]
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // XML Comment
                        matchesAt(line, pos, "<!--") -> {
                            val endIdx = line.indexOf("-->", pos + 4)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 3, TokenType.COMMENT_BLOCK))
                                pos = endIdx + 3
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                                pos = line.length
                                state = LexerState.IN_BLOCK_COMMENT
                            }
                        }

                        // CDATA
                        matchesAt(line, pos, "<![CDATA[") -> {
                            val endIdx = line.indexOf("]]>", pos + 9)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 3, TokenType.STRING))
                                pos = endIdx + 3
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.STRING))
                                pos = line.length
                                state = LexerState.IN_RAW_STRING
                            }
                        }

                        // Processing instruction
                        matchesAt(line, pos, "<?") -> {
                            val endIdx = line.indexOf("?>", pos + 2)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 2, TokenType.KEYWORD))
                                pos = endIdx + 2
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.KEYWORD))
                                pos = line.length
                            }
                        }

                        // DOCTYPE
                        matchesAt(line, pos, "<!DOCTYPE") || matchesAt(line, pos, "<!ENTITY") -> {
                            val endIdx = findTagEnd(line, pos)
                            tokens.add(Token(pos, endIdx, TokenType.KEYWORD))
                            pos = endIdx
                        }

                        // Closing tag
                        matchesAt(line, pos, "</") -> {
                            val (tagTokens, endPos) = tokenizeTag(line, pos, isClosing = true)
                            tokens.addAll(tagTokens)
                            pos = endPos
                        }

                        // Self-closing or opening tag
                        char == '<' -> {
                            val (tagTokens, endPos) = tokenizeTag(line, pos, isClosing = false)
                            tokens.addAll(tagTokens)
                            pos = endPos
                        }

                        // Entity reference
                        char == '&' -> {
                            val endPos = readEntityReference(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.CONSTANT))
                            pos = endPos
                        }

                        else -> {
                            // Text content
                            val endPos = readTextContent(line, pos)
                            if (endPos > pos) {
                                tokens.add(Token(pos, endPos, TokenType.DEFAULT))
                                pos = endPos
                            } else {
                                pos++
                            }
                        }
                    }
                }

                else -> pos++
            }
        }

        return LineTokens(tokens, state)
    }

    protected open fun tokenizeTag(line: String, start: Int, isClosing: Boolean): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start

        // Tag start (< or </)
        val tagStartLen = if (isClosing) 2 else 1
        tokens.add(Token(pos, pos + tagStartLen, TokenType.PUNCTUATION))
        pos += tagStartLen

        // Skip whitespace
        while (pos < line.length && line[pos].isWhitespace()) pos++

        // Tag name
        val nameStart = pos
        while (pos < line.length && isTagNameChar(line[pos])) pos++
        if (pos > nameStart) {
            tokens.add(Token(nameStart, pos, TokenType.MARKUP_TAG))
        }

        // Attributes (only for non-closing tags)
        if (!isClosing) {
            while (pos < line.length) {
                // Skip whitespace
                while (pos < line.length && line[pos].isWhitespace()) pos++
                if (pos >= line.length) break

                // End of tag?
                if (line[pos] == '>' || matchesAt(line, pos, "/>")) {
                    break
                }

                // Attribute name
                val attrStart = pos
                while (pos < line.length && isAttributeNameChar(line[pos])) pos++
                if (pos > attrStart) {
                    tokens.add(Token(attrStart, pos, TokenType.PROPERTY))
                }

                // Skip whitespace around =
                while (pos < line.length && line[pos].isWhitespace()) pos++
                if (pos < line.length && line[pos] == '=') {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                    while (pos < line.length && line[pos].isWhitespace()) pos++

                    // Attribute value
                    if (pos < line.length && (line[pos] == '"' || line[pos] == '\'')) {
                        val quote = line[pos]
                        val valueStart = pos
                        pos++
                        while (pos < line.length && line[pos] != quote) pos++
                        if (pos < line.length) pos++ // Include closing quote
                        tokens.add(Token(valueStart, pos, TokenType.STRING))
                    }
                }
            }
        }

        // Tag end (> or />)
        if (pos < line.length) {
            if (matchesAt(line, pos, "/>")) {
                tokens.add(Token(pos, pos + 2, TokenType.PUNCTUATION))
                pos += 2
            } else if (line[pos] == '>') {
                tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                pos++
            }
        }

        return tokens to pos
    }

    protected fun isTagNameChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == ':' || char == '-' || char == '_' || char == '.'
    }

    protected fun isAttributeNameChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == ':' || char == '-' || char == '_'
    }

    private fun readEntityReference(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '#')) {
            pos++
        }
        if (pos < line.length && line[pos] == ';') pos++
        return pos
    }

    private fun readTextContent(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && line[pos] != '<' && line[pos] != '&') {
            pos++
        }
        return pos
    }

    private fun findTagEnd(line: String, start: Int): Int {
        var pos = start
        var depth = 0
        while (pos < line.length) {
            when (line[pos]) {
                '<' -> depth++
                '>' -> {
                    if (depth <= 1) return pos + 1
                    depth--
                }
            }
            pos++
        }
        return line.length
    }

    override fun classifyIdentifier(identifier: String): TokenType = TokenType.IDENTIFIER
}
