package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * C/C++ syntax highlighting lexer.
 */
class CLexer : BaseLexer() {

    override val languageId: String = "c"
    override val fileExtensions: List<String> = listOf("c", "h", "cpp", "hpp", "cc", "hh", "cxx", "hxx", "c++", "h++", "ino")

    companion object {
        private val C_KEYWORDS = setOf(
            "auto", "break", "case", "char", "const", "continue", "default",
            "do", "double", "else", "enum", "extern", "float", "for", "goto",
            "if", "inline", "int", "long", "register", "restrict", "return",
            "short", "signed", "sizeof", "static", "struct", "switch", "typedef",
            "union", "unsigned", "void", "volatile", "while",
            "_Alignas", "_Alignof", "_Atomic", "_Bool", "_Complex", "_Generic",
            "_Imaginary", "_Noreturn", "_Static_assert", "_Thread_local"
        )

        private val CPP_KEYWORDS = C_KEYWORDS + setOf(
            "alignas", "alignof", "and", "and_eq", "asm", "bitand", "bitor",
            "bool", "catch", "class", "compl", "concept", "consteval", "constexpr",
            "constinit", "const_cast", "co_await", "co_return", "co_yield",
            "decltype", "delete", "dynamic_cast", "explicit", "export", "false",
            "friend", "mutable", "namespace", "new", "noexcept", "not", "not_eq",
            "nullptr", "operator", "or", "or_eq", "private", "protected", "public",
            "reinterpret_cast", "requires", "static_assert", "static_cast",
            "template", "this", "thread_local", "throw", "true", "try", "typeid",
            "typename", "using", "virtual", "wchar_t", "xor", "xor_eq",
            "override", "final", "import", "module", "char8_t", "char16_t", "char32_t"
        )

        private val TYPES = setOf(
            "int8_t", "int16_t", "int32_t", "int64_t",
            "uint8_t", "uint16_t", "uint32_t", "uint64_t",
            "size_t", "ssize_t", "ptrdiff_t", "intptr_t", "uintptr_t",
            "FILE", "fpos_t", "time_t", "clock_t",
            "string", "vector", "map", "set", "list", "deque", "array",
            "unordered_map", "unordered_set", "pair", "tuple",
            "shared_ptr", "unique_ptr", "weak_ptr", "optional", "variant",
            "any", "string_view", "span", "function", "thread", "mutex",
            "atomic", "future", "promise", "condition_variable"
        )

        private val PREPROCESSOR = setOf(
            "include", "define", "undef", "ifdef", "ifndef", "if", "else",
            "elif", "endif", "error", "warning", "pragma", "line"
        )

        private val OPERATORS = setOf(
            '+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '?', ':'
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

                LexerState.IN_RAW_STRING -> {
                    val (endPos, complete) = continueRawStringLiteral(line, pos)
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

                        // Raw string literal R"delim(...)delim"
                        matchesAt(line, pos, "R\"") -> {
                            val (endPos, complete) = readRawStringLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                            if (!complete) state = LexerState.IN_RAW_STRING
                        }

                        // String literal (including prefixed strings)
                        (char == '"') || (char in "LuU" && pos + 1 < line.length && line[pos + 1] == '"') ||
                        (matchesAt(line, pos, "u8\"")) -> {
                            val startPos = pos
                            if (char != '"') {
                                pos = if (matchesAt(line, pos, "u8")) pos + 2 else pos + 1
                            }
                            val endPos = readStringLiteral(line, pos)
                            tokens.add(Token(startPos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Character literal
                        (char == '\'') || (char in "LuU" && pos + 1 < line.length && line[pos + 1] == '\'') -> {
                            val startPos = pos
                            if (char != '\'') pos++
                            val endPos = readCharLiteral(line, pos)
                            tokens.add(Token(startPos, endPos, TokenType.CHAR))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Number
                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Identifier or keyword
                        isIdentifierStart(char) -> {
                            val endPos = readIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos)
                            tokens.add(Token(pos, endPos, classifyIdentifier(identifier)))
                            pos = endPos
                        }

                        // Operators
                        char in OPERATORS -> {
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
            identifier in CPP_KEYWORDS -> TokenType.KEYWORD
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "nullptr" || identifier == "NULL" -> TokenType.NULL
            identifier in TYPES -> TokenType.TYPE
            identifier.startsWith("std::") -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizePreprocessor(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1

        // Skip whitespace after #
        while (pos < line.length && line[pos].isWhitespace()) pos++

        // Read directive name
        val directiveStart = pos
        while (pos < line.length && line[pos].isLetter()) pos++

        if (pos > directiveStart) {
            val directive = line.substring(directiveStart, pos)
            tokens.add(Token(start, pos, TokenType.KEYWORD))

            // Handle include specially
            if (directive == "include") {
                while (pos < line.length && line[pos].isWhitespace()) pos++
                if (pos < line.length && (line[pos] == '<' || line[pos] == '"')) {
                    val quote = line[pos]
                    val closeQuote = if (quote == '<') '>' else '"'
                    val strStart = pos
                    pos++
                    while (pos < line.length && line[pos] != closeQuote) pos++
                    if (pos < line.length) pos++
                    tokens.add(Token(strStart, pos, TokenType.STRING))
                }
            }
        } else {
            tokens.add(Token(start, start + 1, TokenType.KEYWORD))
        }

        // Rest of line is part of preprocessor
        if (pos < line.length) {
            // Check for comments
            val commentIdx = line.indexOf("//", pos)
            val blockIdx = line.indexOf("/*", pos)

            val endPos = when {
                commentIdx >= 0 && (blockIdx < 0 || commentIdx < blockIdx) -> commentIdx
                blockIdx >= 0 -> blockIdx
                else -> line.length
            }

            if (pos < endPos) {
                tokens.add(Token(pos, endPos, TokenType.DEFAULT))
            }
            pos = endPos
        }

        return tokens to pos
    }

    private fun readRawStringLiteral(line: String, start: Int): Pair<Int, Boolean> {
        // R"delim(...)delim"
        var pos = start + 2 // Skip R"
        val delimStart = pos
        while (pos < line.length && line[pos] != '(') pos++
        val delim = line.substring(delimStart, pos) + "\""
        pos++ // Skip (

        val endMarker = ")$delim"
        val endIdx = line.indexOf(endMarker, pos)
        return if (endIdx >= 0) {
            (endIdx + endMarker.length) to true
        } else {
            line.length to false
        }
    }

    private fun continueRawStringLiteral(line: String, start: Int): Pair<Int, Boolean> {
        // Simplified: look for )"
        val endIdx = line.indexOf(")\"", start)
        return if (endIdx >= 0) {
            (endIdx + 2) to true
        } else {
            line.length to false
        }
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("<<=", ">>=", "...", "->*", "<=>")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", "::", "->", ".*")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
