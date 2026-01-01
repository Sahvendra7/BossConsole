package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Clojure syntax highlighting lexer.
 */
class ClojureLexer : BaseLexer() {

    override val languageId: String = "clojure"
    override val fileExtensions: List<String> = listOf("clj", "cljs", "cljc", "edn")

    companion object {
        private val SPECIAL_FORMS = setOf(
            "def", "if", "do", "let", "quote", "var", "fn", "loop", "recur",
            "throw", "try", "catch", "finally", "monitor-enter", "monitor-exit",
            "new", "set!", ".", "deftype", "defrecord", "reify", "defprotocol",
            "extend", "extend-protocol", "extend-type", "letfn", "case"
        )

        private val MACROS = setOf(
            "defn", "defn-", "defmacro", "defmulti", "defmethod", "defonce",
            "defstruct", "ns", "in-ns", "require", "use", "import", "refer",
            "cond", "condp", "when", "when-not", "when-let", "when-first",
            "when-some", "if-let", "if-not", "if-some", "and", "or", "not",
            "doto", "dotimes", "doseq", "dorun", "doall", "for", "while",
            "->", "->>", "as->", "some->", "some->>", "cond->", "cond->>",
            "binding", "with-open", "with-local-vars", "with-redefs",
            "future", "delay", "lazy-seq", "lazy-cat", "declare", "assert",
            "comment", "doc", "time", "gen-class", "gen-interface"
        )

        private val CORE_FUNCTIONS = setOf(
            "str", "print", "println", "pr", "prn", "read", "read-string",
            "slurp", "spit", "format", "apply", "partial", "comp", "juxt",
            "constantly", "identity", "complement", "fnil", "memoize",
            "map", "filter", "remove", "reduce", "reductions", "take",
            "drop", "take-while", "drop-while", "partition", "partition-by",
            "group-by", "sort", "sort-by", "reverse", "distinct", "dedupe",
            "interleave", "interpose", "flatten", "mapcat", "concat",
            "first", "second", "last", "rest", "next", "nth", "get",
            "get-in", "assoc", "assoc-in", "dissoc", "update", "update-in",
            "select-keys", "keys", "vals", "merge", "merge-with", "zipmap",
            "contains?", "empty?", "seq?", "coll?", "list?", "vector?",
            "map?", "set?", "string?", "keyword?", "symbol?", "fn?",
            "nil?", "true?", "false?", "some?", "any?", "every?", "not-any?",
            "count", "empty", "conj", "cons", "into", "vec", "set", "hash-map",
            "hash-set", "sorted-map", "sorted-set", "list", "vector",
            "+", "-", "*", "/", "mod", "rem", "quot", "inc", "dec",
            "max", "min", "abs", "rand", "rand-int", "rand-nth",
            "=", "==", "not=", "<", ">", "<=", ">=", "compare",
            "atom", "ref", "agent", "deref", "reset!", "swap!", "alter"
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        val state = startState

        while (pos < line.length) {
            val char = line[pos]

            when {
                char.isWhitespace() -> pos = skipWhitespace(line, pos)

                // Comment
                char == ';' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    pos = line.length
                }

                // Discard #_
                char == '#' && pos + 1 < line.length && line[pos + 1] == '_' -> {
                    tokens.add(Token(pos, pos + 2, TokenType.COMMENT))
                    pos += 2
                }

                // Reader macro
                char == '#' -> {
                    val (tokenType, endPos) = readReaderMacro(line, pos)
                    tokens.add(Token(pos, endPos, tokenType))
                    pos = endPos
                }

                // String
                char == '"' -> {
                    val endPos = readClojureString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                }

                // Character literal
                char == '\\' -> {
                    val endPos = readCharacterLiteral(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.CHAR))
                    pos = endPos
                }

                // Keyword
                char == ':' -> {
                    val endPos = readKeyword(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.CONSTANT))
                    pos = endPos
                }

                // Number
                char.isDigit() || (char == '-' && pos + 1 < line.length && line[pos + 1].isDigit()) ||
                (char == '+' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                    val endPos = readClojureNumber(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.NUMBER))
                    pos = endPos.coerceAtLeast(pos + 1)
                }

                // Symbol/Identifier
                isSymbolStart(char) -> {
                    val endPos = readSymbol(line, pos)
                    val symbol = line.substring(pos, endPos)
                    tokens.add(Token(pos, endPos, classifySymbol(symbol)))
                    pos = endPos
                }

                // Parentheses
                char == '(' || char == ')' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PARENTHESIS))
                    pos++
                }

                // Brackets
                char == '[' || char == ']' || char == '{' || char == '}' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                    pos++
                }

                // Quote/syntax quote
                char == '\'' || char == '`' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                }

                // Unquote
                char == '~' -> {
                    if (pos + 1 < line.length && line[pos + 1] == '@') {
                        tokens.add(Token(pos, pos + 2, TokenType.OPERATOR))
                        pos += 2
                    } else {
                        tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                        pos++
                    }
                }

                // Deref @
                char == '@' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                }

                // Metadata ^
                char == '^' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.ANNOTATION))
                    pos++
                }

                else -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                }
            }
        }

        return LineTokens(tokens, LexerState.NORMAL)
    }

    override fun classifyIdentifier(identifier: String): TokenType = classifySymbol(identifier)

    private fun classifySymbol(symbol: String): TokenType {
        return when {
            symbol in SPECIAL_FORMS -> TokenType.KEYWORD
            symbol in MACROS -> TokenType.KEYWORD
            symbol in CORE_FUNCTIONS -> TokenType.FUNCTION_CALL
            symbol == "true" || symbol == "false" -> TokenType.BOOLEAN
            symbol == "nil" -> TokenType.NULL
            symbol.startsWith("*") && symbol.endsWith("*") -> TokenType.VARIABLE // Dynamic vars
            symbol.first().isUpperCase() -> TokenType.TYPE // Java interop classes
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readClojureString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '"' -> return pos + 1
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }

    private fun readCharacterLiteral(line: String, start: Int): Int {
        var pos = start + 1
        if (pos >= line.length) return pos

        // Named character literals: \newline, \space, \tab, etc.
        if (line[pos].isLetter()) {
            while (pos < line.length && line[pos].isLetterOrDigit()) pos++
            return pos
        }

        // Unicode: \uXXXX
        if (line[pos] == 'u' && pos + 4 < line.length) {
            return pos + 5
        }

        // Single character
        return pos + 1
    }

    private fun readKeyword(line: String, start: Int): Int {
        var pos = start + 1
        // Namespaced keyword ::keyword or :ns/keyword
        if (pos < line.length && line[pos] == ':') pos++

        while (pos < line.length && isSymbolChar(line[pos])) pos++
        return pos.coerceAtLeast(start + 1)
    }

    private fun readSymbol(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && isSymbolChar(line[pos])) pos++
        return pos.coerceAtLeast(start + 1)
    }

    private fun readReaderMacro(line: String, start: Int): Pair<TokenType, Int> {
        var pos = start + 1
        if (pos >= line.length) return TokenType.PUNCTUATION to pos

        return when (line[pos]) {
            '\'' -> TokenType.OPERATOR to (pos + 1) // Var quote #'
            '(' -> TokenType.FUNCTION to (pos + 1) // Anonymous function #(
            '{' -> TokenType.BRACKET to (pos + 1) // Set #{
            '"' -> { // Regex #"..."
                pos++
                while (pos < line.length && line[pos] != '"') {
                    if (line[pos] == '\\' && pos + 1 < line.length) pos += 2
                    else pos++
                }
                if (pos < line.length) pos++
                TokenType.REGEX to pos
            }
            '?' -> { // Reader conditional #?
                pos++
                if (pos < line.length && line[pos] == '@') pos++ // Splicing #?@
                TokenType.ANNOTATION to pos
            }
            else -> {
                // Tagged literal #tag or #inst, etc.
                while (pos < line.length && isSymbolChar(line[pos])) pos++
                TokenType.ANNOTATION to pos
            }
        }
    }

    private fun readClojureNumber(line: String, start: Int): Int {
        var pos = start

        // Sign
        if (pos < line.length && line[pos] in "+-") pos++

        // Radix prefix: 2r, 8r, 16r, etc.
        if (pos + 1 < line.length && line[pos].isDigit()) {
            val radixEnd = pos
            while (pos < line.length && line[pos].isDigit()) pos++
            if (pos < line.length && line[pos] == 'r') {
                pos++
                while (pos < line.length && (line[pos].isLetterOrDigit())) pos++
                return pos
            }
            pos = radixEnd
        }

        // Hex
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1] in "xX") {
            pos += 2
            while (pos < line.length && (line[pos].isHexDigit())) pos++
            return readClojureNumberSuffix(line, pos)
        }

        // Octal
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1].isDigit()) {
            pos++
            while (pos < line.length && line[pos] in '0'..'7') pos++
            return pos
        }

        // Decimal
        while (pos < line.length && line[pos].isDigit()) pos++

        // Ratio: 1/2
        if (pos < line.length && line[pos] == '/') {
            pos++
            while (pos < line.length && line[pos].isDigit()) pos++
            return pos
        }

        // Float
        if (pos < line.length && line[pos] == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) {
            pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        // Exponent
        if (pos < line.length && line[pos] in "eE") {
            pos++
            if (pos < line.length && line[pos] in "+-") pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }

        return readClojureNumberSuffix(line, pos)
    }

    private fun readClojureNumberSuffix(line: String, pos: Int): Int {
        var p = pos
        // N for BigInt, M for BigDecimal
        if (p < line.length && line[p] in "NM") p++
        return p
    }

    private fun Char.isHexDigit() = isDigit() || this in 'a'..'f' || this in 'A'..'F'

    private fun isSymbolStart(char: Char): Boolean {
        return char.isLetter() || char in "*+!-_?<>=&.%/"
    }

    private fun isSymbolChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char in "*+!-_'?<>=&.%/#:"
    }
}
