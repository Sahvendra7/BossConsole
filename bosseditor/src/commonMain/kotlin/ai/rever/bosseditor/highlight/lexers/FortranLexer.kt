package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Fortran syntax highlighting lexer (supports F77, F90, F95, F2003, F2008).
 */
class FortranLexer : BaseLexer() {

    override val languageId: String = "fortran"
    override val fileExtensions: List<String> = listOf("f", "for", "f77", "f90", "f95", "f03", "f08", "f18")

    companion object {
        private val KEYWORDS = setOf(
            "abstract", "allocatable", "allocate", "assign", "associate", "asynchronous",
            "backspace", "bind", "block", "blockdata", "call", "case", "class", "close",
            "codimension", "common", "concurrent", "contains", "contiguous", "continue",
            "critical", "cycle", "data", "deallocate", "default", "deferred", "dimension",
            "do", "elemental", "else", "elseif", "elsewhere", "end", "endassociate",
            "endblock", "endblockdata", "endcritical", "enddo", "endenum", "endfile",
            "endforall", "endfunction", "endif", "endinterface", "endmodule", "endprogram",
            "endselect", "endsubmodule", "endsubroutine", "endtype", "endwhere", "entry",
            "enum", "enumerator", "equivalence", "error", "exit", "extends", "external",
            "final", "flush", "forall", "format", "function", "generic", "go", "goto",
            "if", "images", "implicit", "import", "impure", "in", "include", "inout",
            "inquire", "intent", "interface", "intrinsic", "lock", "module", "namelist",
            "non_overridable", "nopass", "nullify", "only", "open", "operator", "optional",
            "out", "parameter", "pass", "pause", "pointer", "print", "private", "procedure",
            "program", "protected", "public", "pure", "read", "recursive", "result",
            "return", "rewind", "save", "select", "selectcase", "selecttype", "sequence",
            "stop", "submodule", "subroutine", "sync", "target", "then", "to", "type",
            "unlock", "use", "value", "volatile", "wait", "where", "while", "write"
        )

        private val TYPES = setOf(
            "character", "complex", "double", "doubleprecision", "integer", "logical",
            "real", "precision"
        )

        private val BUILTINS = setOf(
            "abs", "achar", "acos", "acosh", "adjustl", "adjustr", "aimag", "aint",
            "all", "allocated", "anint", "any", "asin", "asinh", "associated", "atan",
            "atan2", "atanh", "bessel_j0", "bessel_j1", "bessel_jn", "bessel_y0",
            "bessel_y1", "bessel_yn", "bge", "bgt", "bit_size", "ble", "blt", "btest",
            "ceiling", "char", "cmplx", "command_argument_count", "conjg", "cos", "cosh",
            "count", "cpu_time", "cshift", "date_and_time", "dble", "digits", "dim",
            "dot_product", "dprod", "dshiftl", "dshiftr", "eoshift", "epsilon", "erf",
            "erfc", "erfc_scaled", "execute_command_line", "exp", "exponent", "extends_type_of",
            "findloc", "floor", "fraction", "gamma", "get_command", "get_command_argument",
            "get_environment_variable", "huge", "hypot", "iachar", "iall", "iand", "iany",
            "ibclr", "ibits", "ibset", "ichar", "ieee_class", "ieee_copy_sign", "ieor",
            "image_index", "index", "int", "ior", "iparity", "is_contiguous", "is_iostat_end",
            "is_iostat_eor", "ishft", "ishftc", "kind", "lbound", "lcobound", "leadz",
            "len", "len_trim", "lge", "lgt", "lle", "llt", "log", "log10", "log_gamma",
            "logical", "maskl", "maskr", "matmul", "max", "maxexponent", "maxloc", "maxval",
            "merge", "merge_bits", "min", "minexponent", "minloc", "minval", "mod", "modulo",
            "move_alloc", "mvbits", "nearest", "new_line", "nint", "norm2", "not", "null",
            "num_images", "pack", "parity", "popcnt", "poppar", "precision", "present",
            "product", "radix", "random_number", "random_seed", "range", "rank", "real",
            "repeat", "reshape", "rrspacing", "same_type_as", "scale", "scan", "selected_char_kind",
            "selected_int_kind", "selected_real_kind", "set_exponent", "shape", "shifta",
            "shiftl", "shiftr", "sign", "sin", "sinh", "size", "sngl", "spacing", "spread",
            "sqrt", "storage_size", "sum", "system_clock", "tan", "tanh", "this_image",
            "tiny", "trailz", "transfer", "transpose", "trim", "ubound", "ucobound",
            "unpack", "verify"
        )

        private val OPERATORS = setOf(
            ".and.", ".eq.", ".eqv.", ".false.", ".ge.", ".gt.", ".le.", ".lt.",
            ".ne.", ".neqv.", ".not.", ".or.", ".true."
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0

        // Check for fixed-form comment (C, c, *, or ! in column 1)
        if (line.isNotEmpty() && line[0] in "Cc*!") {
            tokens.add(Token(0, line.length, TokenType.COMMENT))
            return LineTokens(tokens, LexerState.NORMAL)
        }

        while (pos < line.length) {
            val char = line[pos]

            when {
                char.isWhitespace() -> pos = skipWhitespace(line, pos)

                // Comment ! (free-form)
                char == '!' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    pos = line.length
                }

                // Preprocessor directive #
                char == '#' && isPreprocessorLine(line, pos) -> {
                    tokens.add(Token(pos, line.length, TokenType.ANNOTATION))
                    pos = line.length
                }

                // String (single or double quotes)
                char == '"' || char == '\'' -> {
                    val endPos = readFortranString(line, pos, char)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                }

                // Dot operator .and., .or., .true., etc.
                char == '.' && pos + 1 < line.length && line[pos + 1].isLetter() -> {
                    val endPos = readDotOperator(line, pos)
                    val op = line.substring(pos, endPos).lowercase()
                    val tokenType = when {
                        op in OPERATORS -> TokenType.OPERATOR
                        op == ".true." || op == ".false." -> TokenType.BOOLEAN
                        else -> TokenType.OPERATOR
                    }
                    tokens.add(Token(pos, endPos, tokenType))
                    pos = endPos
                }

                // Number (including kind specifier like 1.0_dp)
                char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                    val endPos = readFortranNumber(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.NUMBER))
                    pos = endPos.coerceAtLeast(pos + 1)
                }

                // Binary, octal, hex literals B'101', O'777', Z'FF'
                char in "BOZboz" && pos + 1 < line.length && line[pos + 1] in "\"'" -> {
                    val endPos = readBasedLiteral(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.NUMBER))
                    pos = endPos
                }

                // Identifier or keyword
                isIdentifierStart(char) -> {
                    val endPos = readFortranIdentifier(line, pos)
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

                // Parentheses (array subscripts, function calls)
                char == '(' || char == ')' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PARENTHESIS))
                    pos++
                }

                // Array constructor [/ /] or [ ]
                char == '[' || char == ']' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                    pos++
                }

                // Label (numeric at start of line in fixed-form)
                char.isDigit() && pos < 6 && isAtLineStart(line, pos) -> {
                    val endPos = readLabel(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.LABEL))
                    pos = endPos
                }

                else -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                }
            }
        }

        return LineTokens(tokens, LexerState.NORMAL)
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        val lower = identifier.lowercase()
        return when {
            lower in KEYWORDS -> TokenType.KEYWORD
            lower in TYPES -> TokenType.TYPE
            lower in BUILTINS -> TokenType.FUNCTION_CALL
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readFortranString(line: String, start: Int, quote: Char): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == quote -> {
                    // Check for escaped quote (doubled)
                    if (pos + 1 < line.length && line[pos + 1] == quote) {
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

    private fun readDotOperator(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length && line[pos].isLetter()) pos++
        if (pos < line.length && line[pos] == '.') pos++
        return pos
    }

    private fun readFortranNumber(line: String, start: Int): Int {
        var pos = start

        // Integer part
        while (pos < line.length && line[pos].isDigit()) pos++

        // Decimal part
        if (pos < line.length && line[pos] == '.') {
            pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        // Exponent (d, D, e, E for double/single precision)
        if (pos < line.length && line[pos] in "dDeE") {
            pos++
            if (pos < line.length && line[pos] in "+-") pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        // Kind specifier _kindname or _number
        if (pos < line.length && line[pos] == '_') {
            pos++
            if (pos < line.length && line[pos].isDigit()) {
                while (pos < line.length && line[pos].isDigit()) pos++
            } else {
                while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
            }
        }

        return pos
    }

    private fun readBasedLiteral(line: String, start: Int): Int {
        var pos = start + 1
        val quote = line[pos]
        pos++
        while (pos < line.length && line[pos] != quote) pos++
        if (pos < line.length) pos++
        return pos
    }

    private fun readFortranIdentifier(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
        return pos.coerceAtLeast(start + 1)
    }

    private fun readLabel(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && pos < 5 && line[pos].isDigit()) pos++
        return pos
    }

    private fun isPreprocessorLine(line: String, pos: Int): Boolean {
        // Check if # is at start (possibly after whitespace)
        for (i in 0 until pos) {
            if (!line[i].isWhitespace()) return false
        }
        return true
    }

    private fun isAtLineStart(line: String, pos: Int): Boolean {
        for (i in 0 until pos) {
            if (!line[i].isWhitespace()) return false
        }
        return true
    }

    private fun isOperator(char: Char): Boolean {
        return char in "+-*/<>=:,%"
    }

    private fun readOperator(line: String, pos: Int): Int {
        val twoChar = listOf("==", "/=", "<=", ">=", "**", "::", "=>", "//")
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
