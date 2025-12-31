package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Rust syntax highlighting lexer.
 */
class RustLexer : BaseLexer() {

    override val languageId: String = "rust"
    override val fileExtensions: List<String> = listOf("rs")

    companion object {
        private val KEYWORDS = setOf(
            "as", "async", "await", "break", "const", "continue", "crate",
            "dyn", "else", "enum", "extern", "false", "fn", "for", "if",
            "impl", "in", "let", "loop", "match", "mod", "move", "mut",
            "pub", "ref", "return", "self", "Self", "static", "struct",
            "super", "trait", "true", "type", "unsafe", "use", "where",
            "while", "abstract", "become", "box", "do", "final", "macro",
            "override", "priv", "try", "typeof", "unsized", "virtual", "yield"
        )

        private val TYPES = setOf(
            "bool", "char", "str", "i8", "i16", "i32", "i64", "i128", "isize",
            "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64",
            "String", "Vec", "Option", "Result", "Box", "Rc", "Arc",
            "Cell", "RefCell", "Mutex", "RwLock", "HashMap", "HashSet",
            "BTreeMap", "BTreeSet", "VecDeque", "LinkedList", "BinaryHeap",
            "Cow", "Pin", "PhantomData", "NonNull", "MaybeUninit"
        )

        private val MACROS = setOf(
            "println", "print", "eprintln", "eprint", "format", "write", "writeln",
            "panic", "assert", "assert_eq", "assert_ne", "debug_assert",
            "vec", "format_args", "env", "option_env", "concat", "stringify",
            "include", "include_str", "include_bytes", "module_path", "file",
            "line", "column", "cfg", "todo", "unimplemented", "unreachable",
            "matches", "dbg"
        )

        private val ATTRIBUTES = setOf(
            "derive", "cfg", "cfg_attr", "test", "bench", "ignore", "should_panic",
            "allow", "warn", "deny", "forbid", "deprecated", "must_use",
            "non_exhaustive", "doc", "inline", "cold", "repr", "path",
            "macro_use", "macro_export", "no_mangle", "link", "link_name",
            "proc_macro", "proc_macro_derive", "proc_macro_attribute"
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
                    val (endPos, complete) = readNestedBlockComment(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.IN_RAW_STRING -> {
                    val (endPos, complete) = continueRawString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        matchesAt(line, pos, "//") -> {
                            val tokenType = if (matchesAt(line, pos, "///") || matchesAt(line, pos, "//!")) {
                                TokenType.COMMENT_DOC
                            } else {
                                TokenType.COMMENT
                            }
                            tokens.add(Token(pos, line.length, tokenType))
                            pos = line.length
                        }

                        matchesAt(line, pos, "/*") -> {
                            val isDoc = matchesAt(line, pos, "/**") || matchesAt(line, pos, "/*!")
                            val (endPos, complete) = readNestedBlockComment(line, pos + 2)
                            val tokenType = if (isDoc) TokenType.COMMENT_DOC else TokenType.COMMENT_BLOCK
                            tokens.add(Token(pos, endPos, tokenType))
                            pos = endPos
                            if (!complete) state = LexerState.IN_BLOCK_COMMENT
                        }

                        // Raw string r#"..."#
                        char == 'r' && pos + 1 < line.length && (line[pos + 1] == '"' || line[pos + 1] == '#') -> {
                            val (endPos, complete) = readRustRawString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                            if (!complete) state = LexerState.IN_RAW_STRING
                        }

                        // Byte string b"..."
                        char == 'b' && pos + 1 < line.length && line[pos + 1] == '"' -> {
                            val endPos = readStringLiteral(line, pos + 1)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // String
                        char == '"' -> {
                            val endPos = readStringLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Character
                        char == '\'' -> {
                            // Could be char or lifetime
                            val (tokenType, endPos) = readCharOrLifetime(line, pos)
                            tokens.add(Token(pos, endPos, tokenType))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Attribute
                        char == '#' -> {
                            if (pos + 1 < line.length && line[pos + 1] == '[') {
                                val endPos = findMatchingBracket(line, pos + 2)
                                tokens.add(Token(pos, endPos, TokenType.ANNOTATION))
                                pos = endPos
                            } else if (pos + 1 < line.length && line[pos + 1] == '!') {
                                // Inner attribute #![...]
                                if (pos + 2 < line.length && line[pos + 2] == '[') {
                                    val endPos = findMatchingBracket(line, pos + 3)
                                    tokens.add(Token(pos, endPos, TokenType.ANNOTATION))
                                    pos = endPos
                                } else {
                                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                                    pos++
                                }
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                                pos++
                            }
                        }

                        // Number
                        char.isDigit() -> {
                            val endPos = readRustNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Macro call (ends with !)
                        isIdentifierStart(char) -> {
                            val endPos = readIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos)

                            // Check if it's a macro call
                            if (endPos < line.length && line[endPos] == '!') {
                                val tokenType = if (identifier in MACROS) TokenType.FUNCTION_CALL else TokenType.FUNCTION_CALL
                                tokens.add(Token(pos, endPos + 1, tokenType))
                                pos = endPos + 1
                            } else {
                                tokens.add(Token(pos, endPos, classifyIdentifier(identifier)))
                                pos = endPos
                            }
                        }

                        // Operators
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
            identifier in TYPES -> TokenType.TYPE
            identifier.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
            identifier in MACROS -> TokenType.FUNCTION_CALL
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readCharOrLifetime(line: String, start: Int): Pair<TokenType, Int> {
        var pos = start + 1

        // Could be lifetime 'a or character 'x'
        if (pos < line.length && isIdentifierStart(line[pos])) {
            val idEnd = readIdentifier(line, pos)
            // If followed by ', it's a char
            if (idEnd < line.length && line[idEnd] == '\'') {
                return TokenType.CHAR to (idEnd + 1)
            }
            // Otherwise it's a lifetime
            return TokenType.ANNOTATION to idEnd
        }

        // Regular character literal
        while (pos < line.length) {
            when {
                line[pos] == '\'' -> return TokenType.CHAR to (pos + 1)
                line[pos] == '\\' -> pos += 2
                else -> pos++
            }
        }
        return TokenType.CHAR to pos
    }

    private fun readRustRawString(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start + 1
        var hashes = 0
        while (pos < line.length && line[pos] == '#') {
            hashes++
            pos++
        }
        if (pos >= line.length || line[pos] != '"') return pos to true

        pos++ // Skip opening quote
        val closePattern = "\"" + "#".repeat(hashes)

        while (pos + closePattern.length <= line.length) {
            if (matchesAt(line, pos, closePattern)) {
                return (pos + closePattern.length) to true
            }
            pos++
        }
        return line.length to false
    }

    private fun continueRawString(line: String, start: Int): Pair<Int, Boolean> {
        // Simplified: look for closing quote with potential hashes
        var pos = start
        while (pos < line.length) {
            if (line[pos] == '"') {
                var hashCount = 0
                var p = pos + 1
                while (p < line.length && line[p] == '#') {
                    hashCount++
                    p++
                }
                return p to true
            }
            pos++
        }
        return line.length to false
    }

    private fun readRustNumber(line: String, start: Int): Int {
        var pos = start

        // Check for prefix
        if (pos + 1 < line.length && line[pos] == '0') {
            when (line[pos + 1]) {
                'x', 'X' -> {
                    pos += 2
                    while (pos < line.length && (line[pos].isHexDigit() || line[pos] == '_')) pos++
                    return readNumberSuffix(line, pos)
                }
                'o', 'O' -> {
                    pos += 2
                    while (pos < line.length && (line[pos] in '0'..'7' || line[pos] == '_')) pos++
                    return readNumberSuffix(line, pos)
                }
                'b', 'B' -> {
                    pos += 2
                    while (pos < line.length && (line[pos] in "01_")) pos++
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
        val suffixes = listOf("i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64")
        for (suffix in suffixes) {
            if (matchesAt(line, p, suffix)) {
                return p + suffix.length
            }
        }
        return p
    }

    private fun Char.isHexDigit() = isDigit() || this in 'a'..'f' || this in 'A'..'F'

    private fun readNestedBlockComment(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start
        var depth = 1
        while (pos + 1 < line.length && depth > 0) {
            if (line[pos] == '/' && line[pos + 1] == '*') {
                depth++
                pos += 2
            } else if (line[pos] == '*' && line[pos + 1] == '/') {
                depth--
                pos += 2
            } else {
                pos++
            }
        }
        if (depth == 0 && pos <= line.length) {
            return pos to true
        }
        return line.length to false
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

    private fun isOperator(char: Char): Boolean {
        return char in setOf('+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '?', ':', '@')
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("..=", "<<=", ">>=")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", "::", "->", "=>", "..")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
