package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * C# syntax highlighting lexer.
 */
class CSharpLexer : BaseLexer() {

    override val languageId: String = "csharp"
    override val fileExtensions: List<String> = listOf("cs", "csx")

    companion object {
        private val KEYWORDS = setOf(
            "abstract", "as", "base", "bool", "break", "byte", "case", "catch",
            "char", "checked", "class", "const", "continue", "decimal", "default",
            "delegate", "do", "double", "else", "enum", "event", "explicit",
            "extern", "false", "finally", "fixed", "float", "for", "foreach",
            "goto", "if", "implicit", "in", "int", "interface", "internal",
            "is", "lock", "long", "namespace", "new", "null", "object",
            "operator", "out", "override", "params", "private", "protected",
            "public", "readonly", "ref", "return", "sbyte", "sealed", "short",
            "sizeof", "stackalloc", "static", "string", "struct", "switch",
            "this", "throw", "true", "try", "typeof", "uint", "ulong",
            "unchecked", "unsafe", "ushort", "using", "virtual", "void",
            "volatile", "while",
            // Contextual keywords
            "add", "alias", "ascending", "async", "await", "by", "descending",
            "dynamic", "equals", "from", "get", "global", "group", "init",
            "into", "join", "let", "managed", "nameof", "nint", "not",
            "notnull", "nuint", "on", "or", "orderby", "partial", "record",
            "remove", "required", "scoped", "select", "set", "unmanaged",
            "value", "var", "when", "where", "with", "yield", "and", "file"
        )

        private val TYPES = setOf(
            "Boolean", "Byte", "Char", "DateTime", "Decimal", "Double", "Guid",
            "Int16", "Int32", "Int64", "Object", "SByte", "Single", "String",
            "TimeSpan", "UInt16", "UInt32", "UInt64", "Void",
            "List", "Dictionary", "HashSet", "Queue", "Stack", "Array",
            "IEnumerable", "ICollection", "IList", "IDictionary", "ISet",
            "Task", "ValueTask", "Span", "Memory", "ReadOnlySpan", "ReadOnlyMemory",
            "Action", "Func", "Predicate", "Comparison", "EventHandler",
            "Exception", "ArgumentException", "InvalidOperationException",
            "NullReferenceException", "NotImplementedException"
        )

        private val PREPROCESSOR = setOf(
            "if", "else", "elif", "endif", "define", "undef", "warning",
            "error", "line", "region", "endregion", "pragma", "nullable"
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

                LexerState.IN_DOC_COMMENT -> {
                    val (endPos, complete) = readBlockComment(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.COMMENT_DOC))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.IN_MULTILINE_STRING -> {
                    val (endPos, complete) = continueVerbatimString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Preprocessor directive
                        char == '#' -> {
                            val (ppTokens, endPos) = tokenizePreprocessor(line, pos)
                            tokens.addAll(ppTokens)
                            pos = endPos
                        }

                        // XML doc comment ///
                        matchesAt(line, pos, "///") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT_DOC))
                            pos = line.length
                        }

                        // Single line comment
                        matchesAt(line, pos, "//") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Doc comment
                        matchesAt(line, pos, "/**") -> {
                            val (endPos, complete) = readBlockComment(line, pos + 3)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_DOC))
                            pos = endPos
                            if (!complete) state = LexerState.IN_DOC_COMMENT
                        }

                        // Block comment
                        matchesAt(line, pos, "/*") -> {
                            val (endPos, complete) = readBlockComment(line, pos + 2)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                            pos = endPos
                            if (!complete) state = LexerState.IN_BLOCK_COMMENT
                        }

                        // Raw string literal """...""" (C# 11)
                        matchesAt(line, pos, "\"\"\"") -> {
                            val endIdx = line.indexOf("\"\"\"", pos + 3)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 3, TokenType.STRING))
                                pos = endIdx + 3
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.STRING))
                                pos = line.length
                                state = LexerState.IN_MULTILINE_STRING
                            }
                        }

                        // Verbatim string @"..."
                        char == '@' && pos + 1 < line.length && line[pos + 1] == '"' -> {
                            val (endPos, complete) = readVerbatimString(line, pos + 2)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                            if (!complete) state = LexerState.IN_MULTILINE_STRING
                        }

                        // Interpolated verbatim string $@"..." or @$"..."
                        (char == '$' && matchesAt(line, pos, "$@\"")) ||
                        (char == '@' && matchesAt(line, pos, "@$\"")) -> {
                            val (stringTokens, endPos, complete) = tokenizeInterpolatedVerbatim(line, pos + 3)
                            tokens.add(Token(pos, pos + 3, TokenType.STRING))
                            tokens.addAll(stringTokens)
                            pos = endPos
                            if (!complete) state = LexerState.IN_MULTILINE_STRING
                        }

                        // Interpolated string $"..."
                        char == '$' && pos + 1 < line.length && line[pos + 1] == '"' -> {
                            val (stringTokens, endPos) = tokenizeInterpolatedString(line, pos + 2)
                            tokens.add(Token(pos, pos + 2, TokenType.STRING))
                            tokens.addAll(stringTokens)
                            pos = endPos
                        }

                        // Regular string
                        char == '"' -> {
                            val endPos = readStringLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Character literal
                        char == '\'' -> {
                            val endPos = readCharLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.CHAR))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Attribute
                        char == '[' && isAttributeContext(line, pos) -> {
                            val endPos = findMatchingBracket(line, pos + 1)
                            tokens.add(Token(pos, endPos, TokenType.ANNOTATION))
                            pos = endPos
                        }

                        // Number
                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readCSharpNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Identifier or keyword
                        isIdentifierStart(char) || char == '@' -> {
                            val endPos = if (char == '@') readIdentifier(line, pos + 1) else readIdentifier(line, pos)
                            val identifier = line.substring(if (char == '@') pos + 1 else pos, endPos)
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
            identifier == "null" -> TokenType.NULL
            identifier in TYPES -> TokenType.TYPE
            identifier.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizePreprocessor(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1

        while (pos < line.length && line[pos].isWhitespace()) pos++

        val directiveStart = pos
        while (pos < line.length && line[pos].isLetter()) pos++

        if (pos > directiveStart) {
            tokens.add(Token(start, pos, TokenType.KEYWORD))
            // Rest of line is part of directive
            if (pos < line.length) {
                tokens.add(Token(pos, line.length, TokenType.DEFAULT))
            }
        } else {
            tokens.add(Token(start, start + 1, TokenType.PUNCTUATION))
        }

        return tokens to line.length
    }

    private fun tokenizeInterpolatedString(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start
        var tokenStart = start

        while (pos < line.length) {
            when {
                line[pos] == '"' -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    tokens.add(Token(pos, pos + 1, TokenType.STRING))
                    return tokens to (pos + 1)
                }
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                line[pos] == '{' -> {
                    if (pos + 1 < line.length && line[pos + 1] == '{') {
                        pos += 2 // Escaped brace
                    } else {
                        if (tokenStart < pos) {
                            tokens.add(Token(tokenStart, pos, TokenType.STRING))
                        }
                        val exprEnd = findMatchingBrace(line, pos + 1)
                        tokens.add(Token(pos, exprEnd, TokenType.STRING_TEMPLATE))
                        pos = exprEnd
                        tokenStart = pos
                    }
                }
                else -> pos++
            }
        }

        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }
        return tokens to line.length
    }

    private fun tokenizeInterpolatedVerbatim(line: String, start: Int): Triple<List<Token>, Int, Boolean> {
        val tokens = mutableListOf<Token>()
        var pos = start
        var tokenStart = start

        while (pos < line.length) {
            when {
                line[pos] == '"' -> {
                    if (pos + 1 < line.length && line[pos + 1] == '"') {
                        pos += 2 // Escaped quote
                    } else {
                        if (tokenStart < pos) {
                            tokens.add(Token(tokenStart, pos, TokenType.STRING))
                        }
                        tokens.add(Token(pos, pos + 1, TokenType.STRING))
                        return Triple(tokens, pos + 1, true)
                    }
                }
                line[pos] == '{' -> {
                    if (pos + 1 < line.length && line[pos + 1] == '{') {
                        pos += 2
                    } else {
                        if (tokenStart < pos) {
                            tokens.add(Token(tokenStart, pos, TokenType.STRING))
                        }
                        val exprEnd = findMatchingBrace(line, pos + 1)
                        tokens.add(Token(pos, exprEnd, TokenType.STRING_TEMPLATE))
                        pos = exprEnd
                        tokenStart = pos
                    }
                }
                else -> pos++
            }
        }

        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }
        return Triple(tokens, line.length, false)
    }

    private fun readVerbatimString(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start
        while (pos < line.length) {
            if (line[pos] == '"') {
                if (pos + 1 < line.length && line[pos + 1] == '"') {
                    pos += 2 // Escaped quote
                } else {
                    return (pos + 1) to true
                }
            } else {
                pos++
            }
        }
        return line.length to false
    }

    private fun continueVerbatimString(line: String, start: Int): Pair<Int, Boolean> {
        return readVerbatimString(line, start)
    }

    private fun readCSharpNumber(line: String, start: Int): Int {
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
        // C# numeric suffixes: U, L, UL, LU, F, D, M
        if (p < line.length && line[p] in "uUlLfFdDmM") {
            p++
            if (p < line.length && line[p] in "lLuU") p++
        }
        return p
    }

    private fun Char.isHexDigit() = isDigit() || this in 'a'..'f' || this in 'A'..'F'

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

    private fun findMatchingBracket(line: String, start: Int): Int {
        var depth = 1
        var pos = start
        while (pos < line.length && depth > 0) {
            when (line[pos]) {
                '[' -> depth++
                ']' -> depth--
            }
            pos++
        }
        return pos
    }

    private fun isAttributeContext(line: String, pos: Int): Boolean {
        // Simple heuristic: [ at start of line or after whitespace/punctuation
        if (pos == 0) return true
        val prev = line.getOrNull(pos - 1)
        return prev?.isWhitespace() == true || prev != null && prev in ",;{}"
    }

    private fun isOperator(char: Char): Boolean {
        return char in "+-*/%=<>!&|^~?:."
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("??=", ">>=", "<<=", ">>>")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", "=>", "??", "?.", "?[", "::")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
