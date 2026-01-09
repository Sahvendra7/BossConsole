package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * D programming language syntax highlighting lexer.
 */
class DLexer : BaseLexer() {

    override val languageId: String = "d"
    override val fileExtensions: List<String> = listOf("d", "di")

    companion object {
        private val KEYWORDS = setOf(
            "abstract", "alias", "align", "asm", "assert", "auto", "body", "bool",
            "break", "byte", "case", "cast", "catch", "cdouble", "cent", "cfloat",
            "char", "class", "const", "continue", "creal", "dchar", "debug",
            "default", "delegate", "delete", "deprecated", "do", "double", "else",
            "enum", "export", "extern", "false", "final", "finally", "float", "for",
            "foreach", "foreach_reverse", "function", "goto", "idouble", "if",
            "ifloat", "immutable", "import", "in", "inout", "int", "interface",
            "invariant", "ireal", "is", "lazy", "long", "macro", "mixin", "module",
            "new", "nothrow", "null", "out", "override", "package", "pragma",
            "private", "protected", "public", "pure", "real", "ref", "return",
            "scope", "shared", "short", "static", "struct", "super", "switch",
            "synchronized", "template", "this", "throw", "true", "try", "typeid",
            "typeof", "ubyte", "ucent", "uint", "ulong", "union", "unittest",
            "ushort", "version", "void", "wchar", "while", "with", "__FILE__",
            "__LINE__", "__MODULE__", "__FUNCTION__", "__PRETTY_FUNCTION__",
            "__gshared", "__traits", "__vector", "__parameters"
        )

        private val SPECIAL_TOKENS = setOf(
            "__DATE__", "__TIME__", "__TIMESTAMP__", "__VENDOR__", "__VERSION__"
        )

        private val BUILTINS = setOf(
            "string", "wstring", "dstring", "size_t", "ptrdiff_t", "hash_t"
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
                    // Nested /+ +/ comment
                    val (endPos, complete) = continueNestedComment(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.COMMENT_DOC))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.IN_MULTILINE_STRING -> {
                    // Heredoc string
                    val endIdx = line.indexOf("\"", pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 1, TokenType.STRING))
                        pos = endIdx + 1
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.STRING))
                        pos = line.length
                    }
                }

                LexerState.IN_RAW_STRING -> {
                    // Backtick string
                    val endIdx = line.indexOf('`', pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 1, TokenType.STRING))
                        pos = endIdx + 1
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.STRING))
                        pos = line.length
                    }
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Line comment //
                        matchesAt(line, pos, "//") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Nested comment /+ +/
                        matchesAt(line, pos, "/+") -> {
                            val (endPos, complete) = readNestedComment(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_DOC))
                            pos = endPos
                            if (!complete) state = LexerState.IN_DOC_COMMENT
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

                        // Wysiwyg string r"..." or `...`
                        char == 'r' && pos + 1 < line.length && line[pos + 1] == '"' -> {
                            val endPos = readWysiwygString(line, pos + 1)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Backtick string
                        char == '`' -> {
                            val endIdx = line.indexOf('`', pos + 1)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 1, TokenType.STRING))
                                pos = endIdx + 1
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.STRING))
                                pos = line.length
                                state = LexerState.IN_RAW_STRING
                            }
                        }

                        // Hex string x"..."
                        char == 'x' && pos + 1 < line.length && line[pos + 1] == '"' -> {
                            val endPos = readDString(line, pos + 1)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Delimited string q"..."
                        char == 'q' && pos + 1 < line.length && line[pos + 1] == '"' -> {
                            val endPos = readDelimitedString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Token string q{...}
                        char == 'q' && pos + 1 < line.length && line[pos + 1] == '{' -> {
                            val endPos = readTokenString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Regular string
                        char == '"' -> {
                            val endPos = readDString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Character literal
                        char == '\'' -> {
                            val endPos = readDCharLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.CHAR))
                            pos = endPos
                        }

                        // Number
                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readDNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // @ attribute
                        char == '@' -> {
                            val endPos = readAttribute(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.ANNOTATION))
                            pos = endPos
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
            }
        }

        return LineTokens(tokens, state)
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        return when {
            identifier in KEYWORDS -> TokenType.KEYWORD
            identifier in SPECIAL_TOKENS -> TokenType.CONSTANT
            identifier in BUILTINS -> TokenType.TYPE
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "null" -> TokenType.NULL
            identifier.first().isUpperCase() -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readDString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '"' -> return readStringSuffix(line, pos + 1)
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }

    private fun readWysiwygString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length && line[pos] != '"') pos++
        if (pos < line.length) pos++
        return readStringSuffix(line, pos)
    }

    private fun readDelimitedString(line: String, start: Int): Int {
        // q"EOS...EOS" or q"[...]" etc.
        var pos = start + 2 // Skip q"
        if (pos >= line.length) return line.length

        val delimiter = line[pos]
        val closeDelim = when (delimiter) {
            '[' -> ']'
            '(' -> ')'
            '<' -> '>'
            '{' -> '}'
            else -> delimiter
        }

        pos++
        while (pos < line.length) {
            if (line[pos] == closeDelim && pos + 1 < line.length && line[pos + 1] == '"') {
                return readStringSuffix(line, pos + 2)
            }
            pos++
        }
        return line.length
    }

    private fun readTokenString(line: String, start: Int): Int {
        var pos = start + 2 // Skip q{
        var depth = 1
        while (pos < line.length && depth > 0) {
            when (line[pos]) {
                '{' -> depth++
                '}' -> depth--
            }
            pos++
        }
        return readStringSuffix(line, pos)
    }

    private fun readStringSuffix(line: String, pos: Int): Int {
        var p = pos
        // String suffix c, w, d
        if (p < line.length && line[p] in "cwd") p++
        return p
    }

    private fun readDCharLiteral(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '\'' -> return pos + 1
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }

    private fun readNestedComment(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start + 2
        var depth = 1
        while (pos < line.length && depth > 0) {
            when {
                matchesAt(line, pos, "/+") -> { depth++; pos += 2 }
                matchesAt(line, pos, "+/") -> { depth--; pos += 2 }
                else -> pos++
            }
        }
        return pos to (depth == 0)
    }

    private fun continueNestedComment(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start
        var depth = 1
        while (pos < line.length && depth > 0) {
            when {
                matchesAt(line, pos, "/+") -> { depth++; pos += 2 }
                matchesAt(line, pos, "+/") -> { depth--; pos += 2 }
                else -> pos++
            }
        }
        return pos to (depth == 0)
    }

    private fun readDNumber(line: String, start: Int): Int {
        var pos = start

        // Binary 0b
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1] in "bB") {
            pos += 2
            while (pos < line.length && (line[pos] in "01" || line[pos] == '_')) pos++
            return readNumberSuffix(line, pos)
        }

        // Hex 0x
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1] in "xX") {
            pos += 2
            while (pos < line.length && (line[pos].isHexDigit() || line[pos] == '_' || line[pos] == '.')) pos++
            // Hex exponent p
            if (pos < line.length && line[pos] in "pP") {
                pos++
                if (pos < line.length && line[pos] in "+-") pos++
                while (pos < line.length && (line[pos].isDigit() || line[pos] == '_')) pos++
            }
            return readNumberSuffix(line, pos)
        }

        // Decimal/float
        while (pos < line.length && (line[pos].isDigit() || line[pos] == '_')) pos++

        // Float part
        if (pos < line.length && line[pos] == '.') {
            if (pos + 1 < line.length && line[pos + 1] == '.') {
                // Range operator ..
                return readNumberSuffix(line, pos)
            }
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
        // Integer suffixes: u, U, L, uL, UL, Lu, LU
        if (p < line.length && line[p] in "uU") p++
        if (p < line.length && line[p] == 'L') p++
        // Float suffixes: f, F, L, i
        if (p < line.length && line[p] in "fFLi") p++
        return p
    }

    private fun Char.isHexDigit() = isDigit() || this in 'a'..'f' || this in 'A'..'F'

    private fun readAttribute(line: String, start: Int): Int {
        var pos = start + 1
        // @identifier or @(...)
        if (pos < line.length && line[pos] == '(') {
            var depth = 1
            pos++
            while (pos < line.length && depth > 0) {
                when (line[pos]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                pos++
            }
            return pos
        }
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
        return pos.coerceAtLeast(start + 1)
    }

    private fun isOperator(char: Char): Boolean {
        return char in "+-*/%<>=!&|^~.?:"
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf(">>>", "<<=", ">>=", "^^=", "...", "!<>", "!<", "!>", "!<=", "!>=")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "<<", ">>", "++", "--",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "~=", "..", "=>", "^^")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return op.length
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
