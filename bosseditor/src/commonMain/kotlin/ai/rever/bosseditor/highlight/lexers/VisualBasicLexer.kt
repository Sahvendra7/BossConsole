package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Visual Basic / VBScript syntax highlighting lexer.
 */
class VisualBasicLexer : BaseLexer() {

    override val languageId: String = "vb"
    override val fileExtensions: List<String> = listOf("vb", "vbs", "bas", "frm", "cls", "vba")

    companion object {
        private val KEYWORDS = setOf(
            "addhandler", "addressof", "alias", "and", "andalso", "as", "boolean",
            "byref", "byte", "byval", "call", "case", "catch", "cbool", "cbyte",
            "cchar", "cdate", "cdbl", "cdec", "char", "cint", "class", "clng",
            "cobj", "const", "continue", "csbyte", "cshort", "csng", "cstr",
            "ctype", "cuint", "culng", "cushort", "date", "decimal", "declare",
            "default", "delegate", "dim", "directcast", "do", "double", "each",
            "else", "elseif", "end", "endif", "enum", "erase", "error", "event",
            "exit", "finally", "for", "friend", "function", "get", "gettype",
            "getxmlnamespace", "global", "gosub", "goto", "handles", "if",
            "implements", "imports", "in", "inherits", "integer", "interface",
            "is", "isnot", "let", "lib", "like", "long", "loop", "me", "mod",
            "module", "mustinherit", "mustoverride", "mybase", "myclass",
            "namespace", "narrowing", "new", "next", "not", "nothing",
            "notinheritable", "notoverridable", "object", "of", "on", "operator",
            "option", "optional", "or", "orelse", "overloads", "overridable",
            "overrides", "paramarray", "partial", "private", "property",
            "protected", "public", "raiseevent", "readonly", "redim", "rem",
            "removehandler", "resume", "return", "sbyte", "select", "set",
            "shadows", "shared", "short", "single", "static", "step", "stop",
            "string", "structure", "sub", "synclock", "then", "throw", "to",
            "true", "false", "try", "trycast", "typeof", "uinteger", "ulong",
            "ushort", "using", "variant", "wend", "when", "while", "widening",
            "with", "withevents", "writeonly", "xor"
        )

        private val BUILTINS = setOf(
            "abs", "array", "asc", "ascw", "atn", "chr", "chrw", "cos", "createobject",
            "cverr", "dateadd", "datediff", "datepart", "dateserial", "datevalue",
            "day", "ddb", "environ", "eof", "exp", "fileattr", "filedatetime",
            "filelen", "filter", "fix", "format", "formatcurrency", "formatdatetime",
            "formatnumber", "formatpercent", "freefile", "fv", "getattr", "getobject",
            "hex", "hour", "iif", "input", "inputbox", "instr", "instrrev", "int",
            "ipmt", "irr", "isarray", "isdate", "isempty", "iserror", "isnull",
            "isnumeric", "isobject", "join", "lbound", "lcase", "left", "len",
            "loc", "lof", "log", "ltrim", "mid", "minute", "mirr", "month",
            "monthname", "msgbox", "now", "nper", "npv", "oct", "pmt", "ppmt",
            "pv", "qbcolor", "rate", "replace", "rgb", "right", "rnd", "round",
            "rtrim", "second", "seek", "sgn", "shell", "sin", "sln", "space",
            "split", "sqr", "str", "strcomp", "strconv", "strreverse", "syd",
            "tan", "time", "timer", "timeserial", "timevalue", "trim", "typename",
            "ubound", "ucase", "val", "vartype", "weekday", "weekdayname", "year"
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState

        while (pos < line.length) {
            val char = line[pos]

            when (state) {
                LexerState.IN_MULTILINE_STRING -> {
                    // VB doesn't have true multiline strings, but line continuation
                    val endPos = readStringContent(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Single quote comment or REM
                        char == '\'' -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // String literal
                        char == '"' -> {
                            val endPos = readVBString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Date literal #...#
                        char == '#' -> {
                            val endPos = readDateLiteral(line, pos)
                            if (endPos > pos + 1) {
                                tokens.add(Token(pos, endPos, TokenType.CONSTANT))
                                pos = endPos
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                                pos++
                            }
                        }

                        // Number
                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readVBNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Identifier or keyword
                        isIdentifierStart(char) -> {
                            val endPos = readVBIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos)
                            val tokenType = classifyIdentifier(identifier)
                            tokens.add(Token(pos, endPos, tokenType))

                            // Check for REM keyword (rest of line is comment)
                            if (identifier.lowercase() == "rem") {
                                if (endPos < line.length) {
                                    tokens.add(Token(endPos, line.length, TokenType.COMMENT))
                                }
                                pos = line.length
                            } else {
                                pos = endPos
                            }
                        }

                        // Type character suffix &, %, !, #, @, $
                        char in "&%!@$" -> {
                            tokens.add(Token(pos, pos + 1, TokenType.TYPE))
                            pos++
                        }

                        // Operators
                        isOperator(char) -> {
                            val opLen = readOperator(line, pos)
                            tokens.add(Token(pos, pos + opLen, TokenType.OPERATOR))
                            pos += opLen
                        }

                        // Parentheses
                        char == '(' || char == ')' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PARENTHESIS))
                            pos++
                        }

                        // Brackets (array indexing in older VB)
                        char == '[' || char == ']' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            pos++
                        }

                        // Line continuation _
                        char == '_' && pos == line.length - 1 -> {
                            tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
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
        val lower = identifier.lowercase()
        return when {
            lower in KEYWORDS -> TokenType.KEYWORD
            lower in BUILTINS -> TokenType.FUNCTION_CALL
            lower == "true" || lower == "false" -> TokenType.BOOLEAN
            lower == "nothing" -> TokenType.NULL
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readVBString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '"' -> {
                    // Check for escaped quote ""
                    if (pos + 1 < line.length && line[pos + 1] == '"') {
                        pos += 2
                    } else {
                        return pos + 1
                    }
                }
                else -> pos++
            }
        }
        return line.length
    }

    private fun readStringContent(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length) {
            if (line[pos] == '"') {
                if (pos + 1 < line.length && line[pos + 1] == '"') {
                    pos += 2
                } else {
                    return pos + 1
                }
            } else {
                pos++
            }
        }
        return line.length
    }

    private fun readDateLiteral(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length && line[pos] != '#') {
            pos++
        }
        if (pos < line.length && line[pos] == '#') {
            return pos + 1
        }
        return start + 1
    }

    private fun readVBNumber(line: String, start: Int): Int {
        var pos = start

        // Hex &H...
        if (pos + 1 < line.length && line[pos] == '&' && line[pos + 1] in "hH") {
            pos += 2
            while (pos < line.length && (line[pos].isDigit() || line[pos] in "abcdefABCDEF")) pos++
            return readTypeSuffix(line, pos)
        }

        // Octal &O...
        if (pos + 1 < line.length && line[pos] == '&' && line[pos + 1] in "oO") {
            pos += 2
            while (pos < line.length && line[pos] in '0'..'7') pos++
            return readTypeSuffix(line, pos)
        }

        // Decimal
        while (pos < line.length && line[pos].isDigit()) pos++

        // Decimal point
        if (pos < line.length && line[pos] == '.') {
            pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        // Exponent
        if (pos < line.length && line[pos] in "eEdD") {
            pos++
            if (pos < line.length && line[pos] in "+-") pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        return readTypeSuffix(line, pos)
    }

    private fun readTypeSuffix(line: String, pos: Int): Int {
        // Type characters: %, &, !, #, @, S, I, L, D, F, R, US, UI, UL
        if (pos < line.length && line[pos] in "%&!#@") {
            return pos + 1
        }
        // Letter suffixes
        if (pos < line.length) {
            val suffixes = listOf("US", "UI", "UL", "S", "I", "L", "D", "F", "R")
            for (suffix in suffixes) {
                if (matchesAt(line, pos, suffix, ignoreCase = true)) {
                    val endPos = pos + suffix.length
                    // Make sure it's not part of identifier
                    if (endPos >= line.length || !line[endPos].isLetterOrDigit()) {
                        return endPos
                    }
                }
            }
        }
        return pos
    }

    private fun matchesAt(line: String, pos: Int, str: String, ignoreCase: Boolean): Boolean {
        if (pos + str.length > line.length) return false
        return line.substring(pos, pos + str.length).equals(str, ignoreCase)
    }

    private fun readVBIdentifier(line: String, start: Int): Int {
        var pos = start
        // VB identifiers can include underscores
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) {
            pos++
        }
        // Type character suffix
        if (pos < line.length && line[pos] in "%&!#@$") {
            pos++
        }
        return pos
    }

    private fun isOperator(char: Char): Boolean {
        return char in "+-*/\\^=<>&."
    }

    private fun readOperator(line: String, pos: Int): Int {
        val twoChar = listOf("<>", "<=", ">=", "<<", ">>", "+=", "-=", "*=", "/=", "\\=", "^=", "&=")
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
