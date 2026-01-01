package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * JavaScript/ECMAScript syntax highlighting lexer.
 * Also serves as base for TypeScript lexer.
 */
open class JavaScriptLexer : BaseLexer() {

    override val languageId: String = "javascript"
    override val fileExtensions: List<String> = listOf("js", "mjs", "cjs", "jsx")

    companion object {
        val JS_KEYWORDS = setOf(
            "await", "break", "case", "catch", "class", "const", "continue",
            "debugger", "default", "delete", "do", "else", "export", "extends",
            "finally", "for", "function", "if", "import", "in", "instanceof",
            "let", "new", "return", "static", "super", "switch", "this",
            "throw", "try", "typeof", "var", "void", "while", "with", "yield",
            "async", "of", "get", "set", "from", "as"
        )

        val JS_TYPES = setOf(
            "Array", "Boolean", "Date", "Error", "Function", "JSON", "Map",
            "Math", "Number", "Object", "Promise", "Proxy", "RegExp", "Set",
            "String", "Symbol", "WeakMap", "WeakSet", "ArrayBuffer", "BigInt",
            "DataView", "Float32Array", "Float64Array", "Int8Array", "Int16Array",
            "Int32Array", "Uint8Array", "Uint16Array", "Uint32Array",
            "console", "window", "document", "navigator", "localStorage",
            "sessionStorage", "fetch", "XMLHttpRequest", "WebSocket",
            "Node", "Element", "HTMLElement", "Event", "EventTarget"
        )

        val JS_CONSTANTS = setOf(
            "undefined", "NaN", "Infinity", "globalThis", "arguments"
        )

        val JS_FUNCTIONS = setOf(
            "parseInt", "parseFloat", "isNaN", "isFinite", "encodeURI",
            "decodeURI", "encodeURIComponent", "decodeURIComponent",
            "eval", "setTimeout", "setInterval", "clearTimeout", "clearInterval",
            "requestAnimationFrame", "cancelAnimationFrame", "alert", "confirm",
            "prompt", "require", "module", "exports"
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
                    val (endPos, complete) = continueTemplateString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
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

                        // Template string
                        char == '`' -> {
                            val (endPos, stringTokens, complete) = tokenizeTemplateString(line, pos)
                            tokens.addAll(stringTokens)
                            pos = endPos
                            if (!complete) state = LexerState.IN_MULTILINE_STRING
                        }

                        char == '"' || char == '\'' -> {
                            val endPos = readJsString(line, pos, char)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Regex literal (simplified detection)
                        char == '/' && canStartRegex(line, pos) -> {
                            val endPos = readRegex(line, pos)
                            if (endPos > pos + 1) {
                                tokens.add(Token(pos, endPos, TokenType.REGEX))
                                pos = endPos
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

                        isIdentifierStart(char) || char == '$' -> {
                            val endPos = readJsIdentifier(line, pos)
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
            identifier in JS_KEYWORDS -> TokenType.KEYWORD
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "null" -> TokenType.NULL
            identifier in JS_CONSTANTS -> TokenType.CONSTANT
            identifier in JS_TYPES -> TokenType.TYPE
            identifier in JS_FUNCTIONS -> TokenType.FUNCTION_CALL
            else -> TokenType.IDENTIFIER
        }
    }

    override fun isIdentifierStart(char: Char): Boolean {
        return char.isLetter() || char == '_' || char == '$'
    }

    protected fun readJsIdentifier(text: String, start: Int): Int {
        if (start >= text.length) return start
        val char = text[start]
        if (!char.isLetter() && char != '_' && char != '$') return start

        var pos = start + 1
        while (pos < text.length) {
            val c = text[pos]
            if (!c.isLetterOrDigit() && c != '_' && c != '$') break
            pos++
        }
        return pos
    }

    private fun readJsString(line: String, start: Int, quote: Char): Int {
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

    private fun tokenizeTemplateString(line: String, start: Int): Triple<Int, List<Token>, Boolean> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1
        var tokenStart = start

        while (pos < line.length) {
            when {
                line[pos] == '`' -> {
                    tokens.add(Token(tokenStart, pos + 1, TokenType.STRING))
                    return Triple(pos + 1, tokens, true)
                }
                line[pos] == '$' && pos + 1 < line.length && line[pos + 1] == '{' -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    val exprEnd = findMatchingBrace(line, pos + 2)
                    tokens.add(Token(pos, exprEnd, TokenType.STRING_TEMPLATE))
                    pos = exprEnd
                    tokenStart = pos
                }
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }

        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }
        return Triple(line.length, tokens, false)
    }

    private fun continueTemplateString(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start
        while (pos < line.length) {
            when {
                line[pos] == '`' -> return (pos + 1) to true
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return line.length to false
    }

    private fun findMatchingBrace(line: String, start: Int): Int {
        var depth = 1
        var pos = start
        while (pos < line.length && depth > 0) {
            when (line[pos]) {
                '{' -> depth++
                '}' -> depth--
            }
            pos++
        }
        return pos
    }

    private fun canStartRegex(line: String, pos: Int): Boolean {
        // Simplified: regex can start after certain characters or at line start
        if (pos == 0) return true
        val prevNonSpace = (pos - 1 downTo 0).firstOrNull { !line[it].isWhitespace() } ?: return true
        val prev = line[prevNonSpace]
        return prev in setOf('(', '[', '{', ',', ';', ':', '=', '!', '&', '|', '?', '+', '-', '*', '/', '%', '<', '>', '^', '~')
    }

    private fun readRegex(line: String, start: Int): Int {
        if (start >= line.length || line[start] != '/') return start
        var pos = start + 1
        var inClass = false

        while (pos < line.length) {
            when (val c = line[pos]) {
                '\\' -> pos += 2
                '[' -> { inClass = true; pos++ }
                ']' -> { inClass = false; pos++ }
                '/' -> if (!inClass) {
                    pos++
                    // Read flags
                    while (pos < line.length && line[pos] in "gimsuy") pos++
                    return pos
                } else pos++
                '\n' -> return start
                else -> pos++
            }
        }
        return start
    }

    private fun isOperator(char: Char): Boolean {
        return char in setOf('+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '?', ':')
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("===", "!==", ">>>", "**=", "&&=", "||=", "??=")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "**", "=>", "??", "<<", ">>", "&=", "|=", "^=", "?.")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
