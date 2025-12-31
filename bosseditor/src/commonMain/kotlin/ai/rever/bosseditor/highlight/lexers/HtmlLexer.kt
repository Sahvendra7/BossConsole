package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * HTML syntax highlighting lexer.
 * Extends XML with HTML-specific tags and attributes.
 */
class HtmlLexer : XmlLexer() {

    override val languageId: String = "html"
    override val fileExtensions: List<String> = listOf("html", "htm", "xhtml", "vue", "svelte")

    companion object {
        private val VOID_TAGS = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
        )

        private val BLOCK_TAGS = setOf(
            "address", "article", "aside", "blockquote", "canvas", "dd",
            "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer",
            "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup",
            "hr", "li", "main", "nav", "noscript", "ol", "p", "pre",
            "section", "table", "tbody", "td", "tfoot", "th", "thead",
            "tr", "ul", "video"
        )

        private val INLINE_TAGS = setOf(
            "a", "abbr", "acronym", "b", "bdo", "big", "br", "button",
            "cite", "code", "dfn", "em", "i", "img", "input", "kbd",
            "label", "map", "object", "output", "q", "samp", "script",
            "select", "small", "span", "strong", "sub", "sup", "textarea",
            "time", "tt", "var"
        )

        private val EVENT_ATTRIBUTES = setOf(
            "onabort", "onblur", "oncancel", "oncanplay", "oncanplaythrough",
            "onchange", "onclick", "onclose", "oncontextmenu", "oncuechange",
            "ondblclick", "ondrag", "ondragend", "ondragenter", "ondragleave",
            "ondragover", "ondragstart", "ondrop", "ondurationchange",
            "onemptied", "onended", "onerror", "onfocus", "oninput",
            "oninvalid", "onkeydown", "onkeypress", "onkeyup", "onload",
            "onloadeddata", "onloadedmetadata", "onloadstart", "onmousedown",
            "onmouseenter", "onmouseleave", "onmousemove", "onmouseout",
            "onmouseover", "onmouseup", "onmousewheel", "onpause", "onplay",
            "onplaying", "onprogress", "onratechange", "onreset", "onresize",
            "onscroll", "onseeked", "onseeking", "onselect", "onshow",
            "onstalled", "onsubmit", "onsuspend", "ontimeupdate", "ontoggle",
            "onvolumechange", "onwaiting"
        )

        private val GLOBAL_ATTRIBUTES = setOf(
            "accesskey", "class", "contenteditable", "contextmenu", "dir",
            "draggable", "dropzone", "hidden", "id", "lang", "spellcheck",
            "style", "tabindex", "title", "translate", "data-"
        )
    }

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

                // Script content
                LexerState.IN_MULTILINE_STRING -> {
                    val endIdx = line.lowercase().indexOf("</script>", pos)
                    if (endIdx >= 0) {
                        if (endIdx > pos) {
                            tokens.add(Token(pos, endIdx, TokenType.DEFAULT))
                        }
                        val (tagTokens, tagEnd) = tokenizeTag(line, endIdx, isClosing = true)
                        tokens.addAll(tagTokens)
                        pos = tagEnd
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.DEFAULT))
                        pos = line.length
                    }
                }

                // Style content
                LexerState.IN_RAW_STRING -> {
                    val endIdx = line.lowercase().indexOf("</style>", pos)
                    if (endIdx >= 0) {
                        if (endIdx > pos) {
                            tokens.add(Token(pos, endIdx, TokenType.DEFAULT))
                        }
                        val (tagTokens, tagEnd) = tokenizeTag(line, endIdx, isClosing = true)
                        tokens.addAll(tagTokens)
                        pos = tagEnd
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.DEFAULT))
                        pos = line.length
                    }
                }

                LexerState.NORMAL -> {
                    val char = line[pos]
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

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

                        matchesAt(line, pos, "<!DOCTYPE") || matchesAt(line, pos, "<!doctype") -> {
                            val endIdx = line.indexOf('>', pos)
                            val end = if (endIdx >= 0) endIdx + 1 else line.length
                            tokens.add(Token(pos, end, TokenType.KEYWORD))
                            pos = end
                        }

                        matchesAt(line, pos, "</") -> {
                            val (tagTokens, endPos) = tokenizeTag(line, pos, isClosing = true)
                            tokens.addAll(tagTokens)
                            pos = endPos
                        }

                        char == '<' -> {
                            val (tagTokens, endPos, tagName) = tokenizeHtmlTag(line, pos)
                            tokens.addAll(tagTokens)
                            pos = endPos

                            // Check for script/style tags
                            when (tagName?.lowercase()) {
                                "script" -> state = LexerState.IN_MULTILINE_STRING
                                "style" -> state = LexerState.IN_RAW_STRING
                            }
                        }

                        char == '&' -> {
                            val endPos = readEntityReference(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.CONSTANT))
                            pos = endPos
                        }

                        else -> {
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

    private fun tokenizeHtmlTag(line: String, start: Int): Triple<List<Token>, Int, String?> {
        val tokens = mutableListOf<Token>()
        var pos = start

        // Tag start (<)
        tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
        pos++

        // Skip whitespace
        while (pos < line.length && line[pos].isWhitespace()) pos++

        // Tag name
        val nameStart = pos
        while (pos < line.length && isTagNameChar(line[pos])) pos++
        val tagName = if (pos > nameStart) line.substring(nameStart, pos) else null
        if (tagName != null) {
            tokens.add(Token(nameStart, pos, TokenType.MARKUP_TAG))
        }

        // Attributes
        while (pos < line.length) {
            while (pos < line.length && line[pos].isWhitespace()) pos++
            if (pos >= line.length) break
            if (line[pos] == '>' || matchesAt(line, pos, "/>")) break

            // Attribute name
            val attrStart = pos
            while (pos < line.length && isAttributeNameChar(line[pos])) pos++
            if (pos > attrStart) {
                tokens.add(Token(attrStart, pos, TokenType.PROPERTY))
            }

            while (pos < line.length && line[pos].isWhitespace()) pos++
            if (pos < line.length && line[pos] == '=') {
                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                pos++
                while (pos < line.length && line[pos].isWhitespace()) pos++

                if (pos < line.length && (line[pos] == '"' || line[pos] == '\'')) {
                    val quote = line[pos]
                    val valueStart = pos
                    pos++
                    while (pos < line.length && line[pos] != quote) pos++
                    if (pos < line.length) pos++
                    tokens.add(Token(valueStart, pos, TokenType.STRING))
                } else if (pos < line.length && !line[pos].isWhitespace() && line[pos] != '>') {
                    // Unquoted attribute value
                    val valueStart = pos
                    while (pos < line.length && !line[pos].isWhitespace() && line[pos] != '>' && line[pos] != '/') pos++
                    tokens.add(Token(valueStart, pos, TokenType.STRING))
                }
            }
        }

        // Tag end
        if (pos < line.length) {
            if (matchesAt(line, pos, "/>")) {
                tokens.add(Token(pos, pos + 2, TokenType.PUNCTUATION))
                pos += 2
            } else if (line[pos] == '>') {
                tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                pos++
            }
        }

        return Triple(tokens, pos, tagName)
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
}
