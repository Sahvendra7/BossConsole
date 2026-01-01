package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Ruby syntax highlighting lexer.
 */
class RubyLexer : BaseLexer() {

    override val languageId: String = "ruby"
    override val fileExtensions: List<String> = listOf("rb", "rake", "gemspec", "erb", "ru", "podspec")

    companion object {
        private val KEYWORDS = setOf(
            "BEGIN", "END", "alias", "and", "begin", "break", "case", "class",
            "def", "defined?", "do", "else", "elsif", "end", "ensure", "false",
            "for", "if", "in", "module", "next", "nil", "not", "or", "redo",
            "rescue", "retry", "return", "self", "super", "then", "true",
            "undef", "unless", "until", "when", "while", "yield", "__FILE__",
            "__LINE__", "__ENCODING__", "lambda", "proc", "raise", "private",
            "protected", "public", "attr_reader", "attr_writer", "attr_accessor",
            "require", "require_relative", "include", "extend", "prepend"
        )

        private val BUILTINS = setOf(
            "puts", "print", "p", "gets", "chomp", "each", "map", "select",
            "reject", "reduce", "inject", "find", "detect", "any?", "all?",
            "none?", "one?", "count", "size", "length", "empty?", "nil?",
            "is_a?", "kind_of?", "instance_of?", "respond_to?", "send",
            "method", "methods", "class", "superclass", "ancestors",
            "new", "initialize", "to_s", "to_i", "to_f", "to_a", "to_h",
            "freeze", "frozen?", "dup", "clone", "tap", "then", "yield_self"
        )

        private val TYPES = setOf(
            "Array", "Hash", "String", "Integer", "Float", "Symbol", "Regexp",
            "Range", "Proc", "Lambda", "Method", "Object", "Class", "Module",
            "Struct", "OpenStruct", "File", "Dir", "IO", "Exception",
            "StandardError", "RuntimeError", "ArgumentError", "TypeError",
            "NameError", "NoMethodError", "Enumerable", "Comparable", "Kernel",
            "BasicObject", "NilClass", "TrueClass", "FalseClass", "Numeric",
            "Rational", "Complex", "Time", "Date", "DateTime", "Set", "Queue",
            "Thread", "Mutex", "Fiber"
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
                    val endIdx = line.indexOf("=end", pos)
                    if (endIdx == 0 || (endIdx > 0 && line[endIdx - 1].isWhitespace())) {
                        tokens.add(Token(pos, endIdx + 4, TokenType.COMMENT_BLOCK))
                        pos = endIdx + 4
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                        pos = line.length
                    }
                }

                LexerState.IN_MULTILINE_STRING -> {
                    val (endPos, complete) = continueHeredoc(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // =begin ... =end block comment
                        pos == 0 && matchesAt(line, pos, "=begin") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                            pos = line.length
                            state = LexerState.IN_BLOCK_COMMENT
                        }

                        char == '#' -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Heredoc
                        matchesAt(line, pos, "<<") -> {
                            val heredocEnd = readHeredocStart(line, pos)
                            if (heredocEnd > pos) {
                                tokens.add(Token(pos, heredocEnd, TokenType.STRING))
                                pos = heredocEnd
                                state = LexerState.IN_MULTILINE_STRING
                            } else {
                                tokens.add(Token(pos, pos + 2, TokenType.OPERATOR))
                                pos += 2
                            }
                        }

                        // Double-quoted string with interpolation
                        char == '"' -> {
                            val (stringTokens, endPos) = tokenizeRubyString(line, pos, '"')
                            tokens.addAll(stringTokens)
                            pos = endPos
                        }

                        // Single-quoted string
                        char == '\'' -> {
                            val endPos = readStringLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Percent strings %w, %i, %q, %Q, %r, %s, %x
                        char == '%' && pos + 1 < line.length -> {
                            val (endPos, tokenType) = readPercentLiteral(line, pos)
                            if (endPos > pos) {
                                tokens.add(Token(pos, endPos, tokenType))
                                pos = endPos
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                                pos++
                            }
                        }

                        // Regex literal
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

                        // Symbol
                        char == ':' -> {
                            if (pos + 1 < line.length) {
                                val next = line[pos + 1]
                                if (next == '"' || next == '\'') {
                                    val endPos = readStringLiteral(line, pos + 1)
                                    tokens.add(Token(pos, endPos, TokenType.STRING))
                                    pos = endPos
                                } else if (isIdentifierStart(next)) {
                                    val endPos = readIdentifier(line, pos + 1)
                                    tokens.add(Token(pos, endPos, TokenType.STRING))
                                    pos = endPos
                                } else {
                                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                                    pos++
                                }
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                                pos++
                            }
                        }

                        // Instance variable
                        char == '@' -> {
                            val endPos = if (pos + 1 < line.length && line[pos + 1] == '@') {
                                readIdentifier(line, pos + 2)
                            } else {
                                readIdentifier(line, pos + 1)
                            }
                            if (endPos > pos + 1) {
                                tokens.add(Token(pos, endPos, TokenType.PROPERTY))
                                pos = endPos
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                                pos++
                            }
                        }

                        // Global variable
                        char == '$' -> {
                            val endPos = readGlobalVariable(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.PROPERTY))
                            pos = endPos
                        }

                        char.isDigit() -> {
                            val endPos = readNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        isIdentifierStart(char) || char == '?' || char == '!' -> {
                            val endPos = readRubyIdentifier(line, pos)
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
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "nil" -> TokenType.NULL
            identifier in TYPES -> TokenType.TYPE
            identifier in BUILTINS -> TokenType.FUNCTION_CALL
            identifier.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizeRubyString(line: String, start: Int, quote: Char): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1
        var tokenStart = start

        while (pos < line.length) {
            when {
                line[pos] == quote -> {
                    tokens.add(Token(tokenStart, pos + 1, TokenType.STRING))
                    return tokens to (pos + 1)
                }
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                line[pos] == '#' && pos + 1 < line.length && line[pos + 1] == '{' -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    val exprEnd = findMatchingBrace(line, pos + 2)
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

    private fun readHeredocStart(line: String, start: Int): Int {
        var pos = start + 2
        // Skip - or ~ for indented heredoc
        if (pos < line.length && (line[pos] == '-' || line[pos] == '~')) pos++
        // Skip optional quote
        val quote = if (pos < line.length && line[pos] in "'\"") line[pos++] else null
        val idStart = pos
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
        if (pos > idStart) {
            if (quote != null && pos < line.length && line[pos] == quote) pos++
            return pos
        }
        return start
    }

    private fun continueHeredoc(line: String, start: Int): Pair<Int, Boolean> {
        // Simplified - look for identifier at start of line
        val trimmed = line.trim()
        if (trimmed.all { it.isLetterOrDigit() || it == '_' } && trimmed.isNotEmpty()) {
            return line.length to true
        }
        return line.length to false
    }

    private fun readPercentLiteral(line: String, start: Int): Pair<Int, TokenType> {
        var pos = start + 1
        val typeChar = if (pos < line.length && line[pos].isLetter()) line[pos++] else 'Q'
        if (pos >= line.length) return start to TokenType.OPERATOR

        val openChar = line[pos]
        val closeChar = when (openChar) {
            '(' -> ')'
            '[' -> ']'
            '{' -> '}'
            '<' -> '>'
            else -> openChar
        }
        pos++

        val tokenType = when (typeChar) {
            'r' -> TokenType.REGEX
            'w', 'W', 'i', 'I' -> TokenType.STRING
            's' -> TokenType.STRING
            else -> TokenType.STRING
        }

        var depth = 1
        while (pos < line.length && depth > 0) {
            when {
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                line[pos] == openChar && openChar != closeChar -> {
                    depth++
                    pos++
                }
                line[pos] == closeChar -> {
                    depth--
                    pos++
                }
                else -> pos++
            }
        }

        return pos to tokenType
    }

    private fun canStartRegex(line: String, pos: Int): Boolean {
        if (pos == 0) return true
        val prev = (pos - 1 downTo 0).firstOrNull { !line[it].isWhitespace() } ?: return true
        return line[prev] in setOf('(', '[', '{', ',', ';', ':', '=', '~', '!', '&', '|', '?', '+', '-', '*', '%')
    }

    private fun readRegex(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '/' -> {
                    pos++
                    // Read regex flags
                    while (pos < line.length && line[pos] in "imxo") pos++
                    return pos
                }
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return start
    }

    private fun readGlobalVariable(line: String, start: Int): Int {
        var pos = start + 1
        if (pos >= line.length) return pos
        // Special globals like $!, $?, $&, etc.
        if (line[pos] in "!@&`'+~=/\\,;.<>*$?:\"0-9") {
            return pos + 1
        }
        // Named globals
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
        return pos
    }

    private fun readRubyIdentifier(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
        // Ruby identifiers can end with ? or !
        if (pos < line.length && line[pos] in "?!") pos++
        return pos
    }

    private fun isOperator(char: Char): Boolean {
        return char in setOf('+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '?', '.')
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("**=", "&&=", "||=", "<<=", ">>=", "<=>", "===", "!==")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "**", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", "..", "=>", "=~", "!~", "::")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
