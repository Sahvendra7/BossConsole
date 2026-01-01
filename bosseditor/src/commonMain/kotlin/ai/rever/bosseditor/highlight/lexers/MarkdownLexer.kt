package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Markdown syntax highlighting lexer.
 */
class MarkdownLexer : BaseLexer() {

    override val languageId: String = "markdown"
    override val fileExtensions: List<String> = listOf("md", "markdown", "mdown", "mkd", "mdx")

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState

        when (state) {
            LexerState.IN_BLOCK_COMMENT -> {
                // Code block
                if (line.trim().startsWith("```")) {
                    tokens.add(Token(0, line.length, TokenType.STRING))
                    return LineTokens(tokens, LexerState.NORMAL)
                }
                tokens.add(Token(0, line.length, TokenType.STRING))
                return LineTokens(tokens, LexerState.IN_BLOCK_COMMENT)
            }

            LexerState.NORMAL -> {
                // Code block start
                if (line.trim().startsWith("```")) {
                    tokens.add(Token(0, line.length, TokenType.STRING))
                    return LineTokens(tokens, LexerState.IN_BLOCK_COMMENT)
                }

                // ATX Headers
                val headerMatch = Regex("^(#{1,6})\\s+(.*)").find(line)
                if (headerMatch != null) {
                    tokens.add(Token(0, headerMatch.groupValues[1].length, TokenType.KEYWORD))
                    val contentStart = headerMatch.groupValues[1].length
                    tokens.addAll(tokenizeInlineMarkdown(line, contentStart, line.length))
                    return LineTokens(tokens, LexerState.NORMAL)
                }

                // Horizontal rule
                if (line.trim().matches(Regex("^[-*_]{3,}$"))) {
                    tokens.add(Token(0, line.length, TokenType.PUNCTUATION))
                    return LineTokens(tokens, LexerState.NORMAL)
                }

                // Blockquote
                if (line.trimStart().startsWith(">")) {
                    val indent = line.indexOfFirst { it == '>' }
                    tokens.add(Token(indent, indent + 1, TokenType.KEYWORD))
                    tokens.addAll(tokenizeInlineMarkdown(line, indent + 1, line.length))
                    return LineTokens(tokens, LexerState.NORMAL)
                }

                // Unordered list
                val ulMatch = Regex("^(\\s*)([-*+])\\s+(.*)").find(line)
                if (ulMatch != null) {
                    val bulletStart = ulMatch.groups[2]!!.range.first
                    val bulletEnd = ulMatch.groups[2]!!.range.last + 1
                    tokens.add(Token(bulletStart, bulletEnd, TokenType.KEYWORD))
                    tokens.addAll(tokenizeInlineMarkdown(line, bulletEnd, line.length))
                    return LineTokens(tokens, LexerState.NORMAL)
                }

                // Ordered list
                val olMatch = Regex("^(\\s*)(\\d+\\.)\\s+(.*)").find(line)
                if (olMatch != null) {
                    val numStart = olMatch.groups[2]!!.range.first
                    val numEnd = olMatch.groups[2]!!.range.last + 1
                    tokens.add(Token(numStart, numEnd, TokenType.NUMBER))
                    tokens.addAll(tokenizeInlineMarkdown(line, numEnd, line.length))
                    return LineTokens(tokens, LexerState.NORMAL)
                }

                // Indented code block
                if (line.startsWith("    ") || line.startsWith("\t")) {
                    tokens.add(Token(0, line.length, TokenType.STRING))
                    return LineTokens(tokens, LexerState.NORMAL)
                }

                // Regular text with inline formatting
                tokens.addAll(tokenizeInlineMarkdown(line, 0, line.length))
            }

            else -> {
                tokens.add(Token(0, line.length, TokenType.DEFAULT))
            }
        }

        return LineTokens(tokens, state)
    }

    private fun tokenizeInlineMarkdown(line: String, start: Int, end: Int): List<Token> {
        val tokens = mutableListOf<Token>()
        var pos = start

        while (pos < end) {
            val char = line[pos]
            when {
                // Inline code
                char == '`' -> {
                    val codeEnd = findInlineCodeEnd(line, pos, end)
                    tokens.add(Token(pos, codeEnd, TokenType.STRING))
                    pos = codeEnd
                }

                // Bold/italic
                char == '*' || char == '_' -> {
                    val (tokenType, tokenEnd) = parseEmphasis(line, pos, end, char)
                    if (tokenEnd > pos) {
                        tokens.add(Token(pos, tokenEnd, tokenType))
                        pos = tokenEnd
                    } else {
                        tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                        pos++
                    }
                }

                // Strikethrough
                matchesAt(line, pos, "~~") -> {
                    val strikeEnd = line.indexOf("~~", pos + 2)
                    if (strikeEnd > pos) {
                        tokens.add(Token(pos, strikeEnd + 2, TokenType.COMMENT))
                        pos = strikeEnd + 2
                    } else {
                        tokens.add(Token(pos, pos + 2, TokenType.DEFAULT))
                        pos += 2
                    }
                }

                // Link [text](url) or [text][ref]
                char == '[' -> {
                    val (linkTokens, linkEnd) = parseLink(line, pos, end)
                    if (linkEnd > pos) {
                        tokens.addAll(linkTokens)
                        pos = linkEnd
                    } else {
                        tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                        pos++
                    }
                }

                // Image ![alt](url)
                char == '!' && pos + 1 < end && line[pos + 1] == '[' -> {
                    val (linkTokens, linkEnd) = parseLink(line, pos + 1, end)
                    if (linkEnd > pos + 1) {
                        tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                        tokens.addAll(linkTokens)
                        pos = linkEnd
                    } else {
                        tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                        pos++
                    }
                }

                // Auto-link <url> or <email>
                char == '<' -> {
                    val gtPos = line.indexOf('>', pos + 1)
                    if (gtPos > pos && gtPos < end) {
                        val content = line.substring(pos + 1, gtPos)
                        if (content.contains("://") || content.contains("@")) {
                            tokens.add(Token(pos, gtPos + 1, TokenType.STRING))
                            pos = gtPos + 1
                        } else {
                            tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                            pos++
                        }
                    } else {
                        tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                        pos++
                    }
                }

                // Escape
                char == '\\' && pos + 1 < end -> {
                    tokens.add(Token(pos, pos + 2, TokenType.STRING_ESCAPE))
                    pos += 2
                }

                else -> {
                    // Find next special char
                    val nextSpecial = findNextSpecial(line, pos + 1, end)
                    if (nextSpecial > pos) {
                        tokens.add(Token(pos, nextSpecial, TokenType.DEFAULT))
                        pos = nextSpecial
                    } else {
                        pos++
                    }
                }
            }
        }

        return tokens
    }

    private fun findInlineCodeEnd(line: String, start: Int, end: Int): Int {
        val isDouble = start + 1 < end && line[start + 1] == '`'
        val searchStart = if (isDouble) start + 2 else start + 1
        val pattern = if (isDouble) "``" else "`"
        val closePos = line.indexOf(pattern, searchStart)
        return if (closePos in searchStart until end) {
            closePos + pattern.length
        } else {
            end
        }
    }

    private fun parseEmphasis(line: String, start: Int, end: Int, marker: Char): Pair<TokenType, Int> {
        val isBold = start + 1 < end && line[start + 1] == marker
        val markerLen = if (isBold) 2 else 1
        val pattern = marker.toString().repeat(markerLen)
        val closePos = line.indexOf(pattern, start + markerLen)

        return if (closePos in (start + markerLen) until end) {
            val tokenType = if (isBold) TokenType.KEYWORD else TokenType.ANNOTATION
            tokenType to (closePos + markerLen)
        } else {
            TokenType.DEFAULT to start
        }
    }

    private fun parseLink(line: String, start: Int, end: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        val closeBracket = line.indexOf(']', start + 1)
        if (closeBracket < 0 || closeBracket >= end) return tokens to start

        // [text]
        tokens.add(Token(start, closeBracket + 1, TokenType.STRING))

        if (closeBracket + 1 < end) {
            when (line[closeBracket + 1]) {
                '(' -> {
                    // [text](url)
                    val closeParen = line.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket && closeParen < end) {
                        tokens.add(Token(closeBracket + 1, closeParen + 1, TokenType.STRING))
                        return tokens to (closeParen + 1)
                    }
                }
                '[' -> {
                    // [text][ref]
                    val closeRef = line.indexOf(']', closeBracket + 2)
                    if (closeRef > closeBracket && closeRef < end) {
                        tokens.add(Token(closeBracket + 1, closeRef + 1, TokenType.VARIABLE))
                        return tokens to (closeRef + 1)
                    }
                }
            }
        }

        return tokens to (closeBracket + 1)
    }

    private fun findNextSpecial(line: String, start: Int, end: Int): Int {
        val specials = setOf('`', '*', '_', '[', '!', '<', '\\', '~')
        for (i in start until end) {
            if (line[i] in specials) return i
        }
        return end
    }

    override fun classifyIdentifier(identifier: String): TokenType = TokenType.DEFAULT
}
