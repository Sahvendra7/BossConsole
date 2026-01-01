package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Lua syntax highlighting lexer.
 */
class LuaLexer : BaseLexer() {

    override val languageId: String = "lua"
    override val fileExtensions: List<String> = listOf("lua")

    companion object {
        private val KEYWORDS = setOf(
            "and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "goto", "if", "in", "local", "nil", "not", "or",
            "repeat", "return", "then", "true", "until", "while"
        )

        private val BUILTINS = setOf(
            "assert", "collectgarbage", "dofile", "error", "getmetatable",
            "ipairs", "load", "loadfile", "next", "pairs", "pcall", "print",
            "rawequal", "rawget", "rawlen", "rawset", "require", "select",
            "setmetatable", "tonumber", "tostring", "type", "xpcall",
            "_G", "_VERSION"
        )

        private val STANDARD_LIBS = setOf(
            "coroutine", "debug", "io", "math", "os", "package", "string", "table", "utf8"
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
                    val (endPos, complete) = continueLongBracket(line, pos, true)
                    tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.IN_MULTILINE_STRING -> {
                    val (endPos, complete) = continueLongBracket(line, pos, false)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Long bracket comment --[[ ... ]]
                        matchesAt(line, pos, "--[[") || matchesAt(line, pos, "--[=") -> {
                            val (endPos, complete) = readLongBracket(line, pos + 2, true)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                            pos = endPos
                            if (!complete) state = LexerState.IN_BLOCK_COMMENT
                        }

                        // Single line comment
                        matchesAt(line, pos, "--") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Long bracket string [[ ... ]] or [=[ ... ]=]
                        char == '[' && pos + 1 < line.length && (line[pos + 1] == '[' || line[pos + 1] == '=') -> {
                            val (endPos, complete) = readLongBracket(line, pos, false)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                            if (!complete) state = LexerState.IN_MULTILINE_STRING
                        }

                        // Double-quoted string
                        char == '"' -> {
                            val endPos = readLuaString(line, pos, '"')
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Single-quoted string
                        char == '\'' -> {
                            val endPos = readLuaString(line, pos, '\'')
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Number
                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readLuaNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Label ::name::
                        matchesAt(line, pos, "::") -> {
                            val labelEnd = line.indexOf("::", pos + 2)
                            if (labelEnd > pos + 2) {
                                tokens.add(Token(pos, labelEnd + 2, TokenType.LABEL))
                                pos = labelEnd + 2
                            } else {
                                tokens.add(Token(pos, pos + 2, TokenType.OPERATOR))
                                pos += 2
                            }
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
            identifier == "nil" -> TokenType.NULL
            identifier in BUILTINS -> TokenType.FUNCTION_CALL
            identifier in STANDARD_LIBS -> TokenType.TYPE
            identifier == "self" -> TokenType.KEYWORD
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readLuaString(line: String, start: Int, quote: Char): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == quote -> return pos + 1
                line[pos] == '\\' && pos + 1 < line.length -> {
                    // Handle Lua escape sequences including \z (skip whitespace)
                    if (line[pos + 1] == 'z') {
                        pos += 2
                        while (pos < line.length && line[pos].isWhitespace()) pos++
                    } else {
                        pos += 2
                    }
                }
                else -> pos++
            }
        }
        return line.length
    }

    private fun readLongBracket(line: String, start: Int, isComment: Boolean): Pair<Int, Boolean> {
        var pos = start
        if (pos >= line.length || line[pos] != '[') return pos to true

        // Count equals signs
        pos++
        var level = 0
        while (pos < line.length && line[pos] == '=') {
            level++
            pos++
        }
        if (pos >= line.length || line[pos] != '[') return start to true
        pos++

        // Find closing bracket with same level
        val closePattern = "]" + "=".repeat(level) + "]"
        val closeIdx = line.indexOf(closePattern, pos)
        return if (closeIdx >= 0) {
            (closeIdx + closePattern.length) to true
        } else {
            line.length to false
        }
    }

    private fun continueLongBracket(line: String, start: Int, isComment: Boolean): Pair<Int, Boolean> {
        // Simplified: look for closing ]] or ]=]+ pattern
        var pos = start
        while (pos < line.length) {
            if (line[pos] == ']') {
                var level = 0
                var checkPos = pos + 1
                while (checkPos < line.length && line[checkPos] == '=') {
                    level++
                    checkPos++
                }
                if (checkPos < line.length && line[checkPos] == ']') {
                    return (checkPos + 1) to true
                }
            }
            pos++
        }
        return line.length to false
    }

    private fun readLuaNumber(line: String, start: Int): Int {
        var pos = start

        // Hexadecimal
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1] in "xX") {
            pos += 2
            while (pos < line.length && (line[pos].isHexDigit() || line[pos] == '.')) pos++
            // Hex exponent p/P
            if (pos < line.length && line[pos] in "pP") {
                pos++
                if (pos < line.length && line[pos] in "+-") pos++
                while (pos < line.length && line[pos].isDigit()) pos++
            }
            return pos
        }

        // Decimal
        while (pos < line.length && line[pos].isDigit()) pos++

        // Float part
        if (pos < line.length && line[pos] == '.') {
            pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        // Exponent
        if (pos < line.length && line[pos] in "eE") {
            pos++
            if (pos < line.length && line[pos] in "+-") pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        return pos
    }

    private fun Char.isHexDigit() = isDigit() || this in 'a'..'f' || this in 'A'..'F'

    private fun isOperator(char: Char): Boolean {
        return char in "+-*/%^#=<>~.&|"
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("...", "//=")
        val twoChar = listOf("==", "~=", "<=", ">=", "..", "<<", ">>", "//", "::", "->")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
