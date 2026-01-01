package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * PHP syntax highlighting lexer.
 */
class PHPLexer : BaseLexer() {

    override val languageId: String = "php"
    override val fileExtensions: List<String> = listOf("php", "phtml", "php3", "php4", "php5", "php7", "phps", "php-s")

    companion object {
        private val KEYWORDS = setOf(
            "abstract", "and", "array", "as", "break", "callable", "case", "catch",
            "class", "clone", "const", "continue", "declare", "default", "die", "do",
            "echo", "else", "elseif", "empty", "enddeclare", "endfor", "endforeach",
            "endif", "endswitch", "endwhile", "eval", "exit", "extends", "final",
            "finally", "fn", "for", "foreach", "function", "global", "goto", "if",
            "implements", "include", "include_once", "instanceof", "insteadof",
            "interface", "isset", "list", "match", "namespace", "new", "or", "print",
            "private", "protected", "public", "readonly", "require", "require_once",
            "return", "static", "switch", "throw", "trait", "try", "unset", "use",
            "var", "while", "xor", "yield", "yield from", "enum"
        )

        private val TYPES = setOf(
            "int", "integer", "float", "double", "bool", "boolean", "string", "array",
            "object", "callable", "iterable", "void", "null", "mixed", "never",
            "resource", "self", "parent", "static", "false", "true"
        )

        private val MAGIC_CONSTANTS = setOf(
            "__CLASS__", "__DIR__", "__FILE__", "__FUNCTION__", "__LINE__",
            "__METHOD__", "__NAMESPACE__", "__TRAIT__"
        )

        private val BUILTINS = setOf(
            "echo", "print", "isset", "unset", "empty", "die", "exit", "eval",
            "include", "include_once", "require", "require_once", "list", "array"
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
                    val (endPos, complete) = continueHeredoc(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Single-line comment
                        matchesAt(line, pos, "//") || char == '#' -> {
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

                        // Heredoc/Nowdoc
                        matchesAt(line, pos, "<<<") -> {
                            val heredocEnd = readHeredocStart(line, pos)
                            tokens.add(Token(pos, heredocEnd, TokenType.STRING))
                            pos = heredocEnd
                            state = LexerState.IN_MULTILINE_STRING
                        }

                        // Double-quoted string with interpolation
                        char == '"' -> {
                            val (stringTokens, endPos) = tokenizePHPString(line, pos)
                            tokens.addAll(stringTokens)
                            pos = endPos
                        }

                        // Single-quoted string
                        char == '\'' -> {
                            val endPos = readStringLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Backtick execution string
                        char == '`' -> {
                            val endPos = readBacktickString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Variable
                        char == '$' -> {
                            val endPos = readVariable(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.PROPERTY))
                            pos = endPos
                        }

                        // Attribute (PHP 8+)
                        char == '#' && pos + 1 < line.length && line[pos + 1] == '[' -> {
                            val endPos = findMatchingBracket(line, pos + 2)
                            tokens.add(Token(pos, endPos, TokenType.ANNOTATION))
                            pos = endPos
                        }

                        // Number
                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readPHPNumber(line, pos)
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
            identifier == "null" || identifier == "NULL" -> TokenType.NULL
            identifier in MAGIC_CONSTANTS -> TokenType.CONSTANT
            identifier in TYPES -> TokenType.TYPE
            identifier in BUILTINS -> TokenType.FUNCTION_CALL
            identifier.startsWith("__") && identifier.endsWith("__") -> TokenType.CONSTANT
            identifier.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizePHPString(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1
        var tokenStart = start

        while (pos < line.length) {
            when {
                line[pos] == '"' -> {
                    tokens.add(Token(tokenStart, pos + 1, TokenType.STRING))
                    return tokens to (pos + 1)
                }
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                line[pos] == '$' -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    val varEnd = readVariable(line, pos)
                    tokens.add(Token(pos, varEnd, TokenType.STRING_TEMPLATE))
                    pos = varEnd
                    tokenStart = pos
                }
                line[pos] == '{' && pos + 1 < line.length && line[pos + 1] == '$' -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    val exprEnd = findMatchingBrace(line, pos + 1)
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

    private fun readVariable(line: String, start: Int): Int {
        var pos = start + 1
        // ${...} complex syntax
        if (pos < line.length && line[pos] == '{') {
            return findMatchingBrace(line, pos + 1)
        }
        // Variable variable $$var
        if (pos < line.length && line[pos] == '$') {
            pos++
        }
        // Regular variable
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
        // Array access or property
        while (pos < line.length) {
            if (line[pos] == '[') {
                pos = findMatchingBracket(line, pos + 1)
            } else if (matchesAt(line, pos, "->") && pos + 2 < line.length) {
                pos += 2
                while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
            } else {
                break
            }
        }
        return pos
    }

    private fun readHeredocStart(line: String, start: Int): Int {
        var pos = start + 3
        // Skip optional quote for nowdoc
        if (pos < line.length && line[pos] in "'\"") pos++
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
        if (pos < line.length && line[pos] in "'\"") pos++
        return pos
    }

    private fun continueHeredoc(line: String, start: Int): Pair<Int, Boolean> {
        val trimmed = line.trim()
        // Check if line starts with heredoc identifier
        if (trimmed.all { it.isLetterOrDigit() || it == '_' || it == ';' } && trimmed.isNotEmpty()) {
            val identifier = trimmed.trimEnd(';')
            if (identifier.isNotEmpty() && identifier.all { it.isLetterOrDigit() || it == '_' }) {
                return line.length to true
            }
        }
        return line.length to false
    }

    private fun readBacktickString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '`' -> return pos + 1
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }

    private fun readPHPNumber(line: String, start: Int): Int {
        var pos = start

        // Check for prefix
        if (pos + 1 < line.length && line[pos] == '0') {
            when (line[pos + 1]) {
                'x', 'X' -> {
                    pos += 2
                    while (pos < line.length && (line[pos].isHexDigit() || line[pos] == '_')) pos++
                    return pos
                }
                'b', 'B' -> {
                    pos += 2
                    while (pos < line.length && (line[pos] in "01_")) pos++
                    return pos
                }
                'o', 'O' -> {
                    pos += 2
                    while (pos < line.length && (line[pos] in '0'..'7' || line[pos] == '_')) pos++
                    return pos
                }
            }
        }

        // Decimal
        while (pos < line.length && (line[pos].isDigit() || line[pos] == '_')) pos++

        // Float
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

        return pos
    }

    private fun Char.isHexDigit() = isDigit() || this in 'a'..'f' || this in 'A'..'F'

    private fun isOperator(char: Char): Boolean {
        return char in setOf('+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '?', ':', '.', '@')
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("===", "!==", "<=>", "**=", "??=", "...", ">>=", "<<=", "&&=", "||=")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "**", "??", "->", "=>", "::", ".=", "<<", ">>")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
