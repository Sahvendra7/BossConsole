package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Common Lisp / Emacs Lisp syntax highlighting lexer.
 */
class LispLexer : BaseLexer() {

    override val languageId: String = "lisp"
    override val fileExtensions: List<String> = listOf("lisp", "lsp", "cl", "el", "elc", "scm", "ss", "rkt")

    companion object {
        private val SPECIAL_FORMS = setOf(
            "and", "block", "catch", "cond", "declare", "defconstant", "defmacro",
            "defparameter", "defsetf", "defstruct", "deftype", "defun", "defvar",
            "do", "do*", "dolist", "dotimes", "eval-when", "flet", "function",
            "go", "if", "labels", "lambda", "let", "let*", "load-time-value",
            "locally", "macrolet", "multiple-value-bind", "multiple-value-call",
            "multiple-value-prog1", "or", "progn", "progv", "quote", "return",
            "return-from", "setf", "setq", "symbol-macrolet", "tagbody", "the",
            "throw", "unwind-protect", "when", "unless", "case", "ccase", "ecase",
            "typecase", "ctypecase", "etypecase", "loop", "prog", "prog*", "prog1",
            "prog2", "with-accessors", "with-compilation-unit", "with-condition-restarts",
            "with-hash-table-iterator", "with-input-from-string", "with-open-file",
            "with-open-stream", "with-output-to-string", "with-package-iterator",
            "with-simple-restart", "with-slots", "with-standard-io-syntax"
        )

        private val BUILTINS = setOf(
            "abs", "acons", "acos", "acosh", "adjoin", "append", "apply", "apropos",
            "aref", "array-dimension", "array-dimensions", "array-rank", "arrayp",
            "ash", "asin", "asinh", "assoc", "assoc-if", "assoc-if-not", "atan",
            "atanh", "atom", "boundp", "butlast", "car", "cdr", "ceiling", "char",
            "char-code", "characterp", "close", "coerce", "compile", "compile-file",
            "complex", "concatenate", "cons", "consp", "copy-list", "copy-seq",
            "copy-tree", "cos", "cosh", "count", "count-if", "count-if-not",
            "delete", "delete-duplicates", "delete-if", "delete-if-not", "describe",
            "disassemble", "documentation", "elt", "endp", "eq", "eql", "equal",
            "equalp", "error", "eval", "evenp", "every", "exp", "expt", "fboundp",
            "fill", "find", "find-if", "find-if-not", "first", "float", "floatp",
            "floor", "format", "funcall", "functionp", "gcd", "gensym", "get",
            "getf", "gethash", "identity", "imagpart", "incf", "decf", "input-stream-p",
            "inspect", "integer-length", "integerp", "intern", "intersection",
            "isqrt", "last", "lcm", "length", "list", "list*", "listp", "load",
            "log", "logand", "logandc1", "logandc2", "logbitp", "logcount", "logeqv",
            "logior", "lognand", "lognor", "lognot", "logorc1", "logorc2", "logtest",
            "logxor", "macroexpand", "make-array", "make-hash-table", "make-list",
            "make-package", "make-sequence", "make-string", "make-symbol", "makunbound",
            "map", "mapc", "mapcan", "mapcar", "mapcon", "maphash", "mapl", "maplist",
            "max", "member", "member-if", "member-if-not", "merge", "min", "minusp",
            "mismatch", "mod", "nbutlast", "nconc", "nintersection", "not", "notany",
            "notevery", "nreverse", "nset-difference", "nset-exclusive-or",
            "nstring-capitalize", "nstring-downcase", "nstring-upcase", "nsublis",
            "nsubst", "nsubst-if", "nsubst-if-not", "nth", "nthcdr", "null", "numberp",
            "numerator", "nunion", "oddp", "open", "output-stream-p", "pairlis",
            "parse-integer", "pathname", "peek-char", "plusp", "pop", "position",
            "position-if", "position-if-not", "pprint", "prin1", "princ", "print",
            "probe-file", "proclaim", "provide", "push", "pushnew", "random",
            "rassoc", "rassoc-if", "rassoc-if-not", "rational", "rationalize",
            "rationalp", "read", "read-byte", "read-char", "read-from-string",
            "read-line", "realp", "realpart", "reduce", "rem", "remf", "remhash",
            "remove", "remove-duplicates", "remove-if", "remove-if-not", "remprop",
            "rename-file", "replace", "require", "rest", "reverse", "round", "row-major-aref",
            "rplaca", "rplacd", "search", "second", "set", "set-difference",
            "set-exclusive-or", "sin", "sinh", "sleep", "some", "sort", "sqrt",
            "stable-sort", "string", "string-capitalize", "string-downcase",
            "string-equal", "string-greaterp", "string-left-trim", "string-lessp",
            "string-not-equal", "string-not-greaterp", "string-not-lessp",
            "string-right-trim", "string-trim", "string-upcase", "string<", "string<=",
            "string=", "string>", "string>=", "stringp", "sublis", "subseq", "subsetp",
            "subst", "subst-if", "subst-if-not", "substitute", "substitute-if",
            "substitute-if-not", "svref", "symbol-function", "symbol-name",
            "symbol-package", "symbol-plist", "symbol-value", "symbolp", "tan",
            "tanh", "terpri", "third", "time", "trace", "tree-equal", "truncate",
            "type-of", "typep", "unexport", "unintern", "union", "untrace", "values",
            "values-list", "vector", "vectorp", "warn", "write", "write-byte",
            "write-char", "write-line", "write-string", "write-to-string", "y-or-n-p",
            "yes-or-no-p", "zerop"
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
                    // #| ... |# multi-line comment
                    val endIdx = line.indexOf("|#", pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 2, TokenType.COMMENT_BLOCK))
                        pos = endIdx + 2
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                        pos = line.length
                    }
                }

                LexerState.IN_MULTILINE_STRING -> {
                    val endPos = continueString(line, pos)
                    tokens.add(Token(pos, endPos.first, TokenType.STRING))
                    pos = endPos.first
                    if (endPos.second) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Line comment ;
                        char == ';' -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Block comment #| ... |#
                        char == '#' && pos + 1 < line.length && line[pos + 1] == '|' -> {
                            val endIdx = line.indexOf("|#", pos + 2)
                            if (endIdx >= 0) {
                                tokens.add(Token(pos, endIdx + 2, TokenType.COMMENT_BLOCK))
                                pos = endIdx + 2
                            } else {
                                tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                                pos = line.length
                                state = LexerState.IN_BLOCK_COMMENT
                            }
                        }

                        // Character literal #\x
                        char == '#' && pos + 1 < line.length && line[pos + 1] == '\\' -> {
                            val endPos = readLispCharLiteral(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.CHAR))
                            pos = endPos
                        }

                        // Vector #(...)
                        char == '#' && pos + 1 < line.length && line[pos + 1] == '(' -> {
                            tokens.add(Token(pos, pos + 2, TokenType.BRACKET))
                            pos += 2
                        }

                        // Reader macros #'func, #:symbol, etc.
                        char == '#' -> {
                            val (tokenType, endPos) = readReaderMacro(line, pos)
                            tokens.add(Token(pos, endPos, tokenType))
                            pos = endPos
                        }

                        // String
                        char == '"' -> {
                            val (endPos, complete) = readString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                            if (!complete) state = LexerState.IN_MULTILINE_STRING
                        }

                        // Quote/backquote/unquote
                        char == '\'' || char == '`' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                            pos++
                        }

                        char == ',' -> {
                            if (pos + 1 < line.length && line[pos + 1] == '@') {
                                tokens.add(Token(pos, pos + 2, TokenType.OPERATOR))
                                pos += 2
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                                pos++
                            }
                        }

                        // Keyword :keyword
                        char == ':' -> {
                            val endPos = readSymbol(line, pos + 1)
                            tokens.add(Token(pos, endPos, TokenType.CONSTANT))
                            pos = endPos
                        }

                        // Parentheses
                        char == '(' || char == ')' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PARENTHESIS))
                            pos++
                        }

                        // Brackets (for Scheme/Racket)
                        char == '[' || char == ']' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            pos++
                        }

                        // Number
                        char.isDigit() || (char == '-' && pos + 1 < line.length && line[pos + 1].isDigit()) ||
                        (char == '+' && pos + 1 < line.length && line[pos + 1].isDigit()) ||
                        (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readLispNumber(line, pos)
                            if (endPos > pos) {
                                tokens.add(Token(pos, endPos, TokenType.NUMBER))
                                pos = endPos
                            } else {
                                val symEnd = readSymbol(line, pos)
                                tokens.add(Token(pos, symEnd, classifySymbol(line.substring(pos, symEnd))))
                                pos = symEnd
                            }
                        }

                        // Symbol/identifier
                        isSymbolStart(char) -> {
                            val endPos = readSymbol(line, pos)
                            val symbol = line.substring(pos, endPos)
                            tokens.add(Token(pos, endPos, classifySymbol(symbol)))
                            pos = endPos
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

    override fun classifyIdentifier(identifier: String): TokenType = classifySymbol(identifier)

    private fun classifySymbol(symbol: String): TokenType {
        val lower = symbol.lowercase()
        return when {
            lower in SPECIAL_FORMS -> TokenType.KEYWORD
            lower in BUILTINS -> TokenType.FUNCTION_CALL
            lower == "t" -> TokenType.BOOLEAN
            lower == "nil" -> TokenType.NULL
            symbol.startsWith("*") && symbol.endsWith("*") -> TokenType.VARIABLE // Special/dynamic var
            symbol.startsWith("+") && symbol.endsWith("+") -> TokenType.CONSTANT // Constant convention
            symbol.startsWith("&") -> TokenType.KEYWORD // Lambda list keywords
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readString(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '"' -> return (pos + 1) to true
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return line.length to false
    }

    private fun continueString(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start
        while (pos < line.length) {
            when {
                line[pos] == '"' -> return (pos + 1) to true
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return line.length to false
    }

    private fun readLispCharLiteral(line: String, start: Int): Int {
        var pos = start + 2 // Skip #\
        if (pos >= line.length) return pos

        // Named chars: #\newline, #\space, etc.
        if (line[pos].isLetter()) {
            while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '-')) pos++
            return pos
        }

        // Single char
        return pos + 1
    }

    private fun readReaderMacro(line: String, start: Int): Pair<TokenType, Int> {
        var pos = start + 1
        if (pos >= line.length) return TokenType.PUNCTUATION to pos

        return when (line[pos]) {
            '\'' -> TokenType.OPERATOR to (pos + 1) // Function quote #'
            ':' -> { // Uninterned symbol #:foo
                pos++
                val endPos = readSymbol(line, pos)
                TokenType.IDENTIFIER to endPos
            }
            '+', '-' -> { // Feature expression #+, #-
                pos++
                val endPos = readSymbol(line, pos)
                TokenType.ANNOTATION to endPos
            }
            '.' -> TokenType.OPERATOR to (pos + 1) // Read-time evaluation #.
            'b', 'B' -> { // Binary #b1010
                pos++
                while (pos < line.length && line[pos] in "01") pos++
                TokenType.NUMBER to pos
            }
            'o', 'O' -> { // Octal #o777
                pos++
                while (pos < line.length && line[pos] in '0'..'7') pos++
                TokenType.NUMBER to pos
            }
            'x', 'X' -> { // Hex #xFF
                pos++
                while (pos < line.length && (line[pos].isDigit() || line[pos] in "abcdefABCDEF")) pos++
                TokenType.NUMBER to pos
            }
            else -> {
                // Radix #nR or other
                while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '/')) pos++
                TokenType.NUMBER to pos
            }
        }
    }

    private fun readSymbol(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && isSymbolChar(line[pos])) {
            pos++
        }
        return pos.coerceAtLeast(start + 1)
    }

    private fun readLispNumber(line: String, start: Int): Int {
        var pos = start

        // Sign
        if (pos < line.length && line[pos] in "+-") pos++

        // Integer part
        val intStart = pos
        while (pos < line.length && line[pos].isDigit()) pos++

        // Ratio a/b
        if (pos < line.length && line[pos] == '/' && pos > intStart) {
            pos++
            while (pos < line.length && line[pos].isDigit()) pos++
            return pos
        }

        // Decimal part
        if (pos < line.length && line[pos] == '.') {
            pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        // Must have at least one digit
        if (pos == intStart || (pos == intStart + 1 && line[start] in "+-")) {
            return start
        }

        // Exponent
        if (pos < line.length && line[pos] in "eEdDfFlLsS") {
            pos++
            if (pos < line.length && line[pos] in "+-") pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        return pos
    }

    private fun isSymbolStart(char: Char): Boolean {
        return char.isLetter() || char in "*+!-_?<>=&%/$@~"
    }

    private fun isSymbolChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char in "*+!-_'?<>=&%/$@~."
    }
}
