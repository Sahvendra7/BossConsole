package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Delphi/Pascal syntax highlighting lexer.
 */
class DelphiLexer : BaseLexer() {

    override val languageId: String = "pascal"
    override val fileExtensions: List<String> = listOf("pas", "dpr", "dpk", "pp", "inc", "lpr", "lfm", "dfm")

    companion object {
        private val KEYWORDS = setOf(
            "absolute", "abstract", "and", "array", "as", "asm", "assembler",
            "automated", "begin", "case", "cdecl", "class", "const", "constructor",
            "contains", "default", "deprecated", "destructor", "dispid", "dispinterface",
            "div", "do", "downto", "dynamic", "else", "end", "except", "export",
            "exports", "external", "far", "file", "final", "finalization", "finally",
            "for", "forward", "function", "goto", "if", "implementation", "implements",
            "in", "index", "inherited", "initialization", "inline", "interface", "is",
            "label", "library", "local", "message", "mod", "name", "near", "nil",
            "nodefault", "not", "object", "of", "on", "operator", "or", "out",
            "overload", "override", "package", "packed", "pascal", "platform",
            "private", "procedure", "program", "property", "protected", "public",
            "published", "raise", "read", "readonly", "record", "register", "reintroduce",
            "repeat", "requires", "resourcestring", "safecall", "sealed", "set",
            "shl", "shr", "static", "stdcall", "stored", "strict", "string",
            "then", "threadvar", "to", "try", "type", "unit", "unsafe", "until",
            "uses", "var", "varargs", "virtual", "while", "with", "write", "writeonly",
            "xor"
        )

        private val TYPES = setOf(
            "boolean", "byte", "cardinal", "char", "comp", "currency", "double",
            "extended", "int64", "integer", "longbool", "longint", "longword",
            "nativeint", "nativeuint", "olevariant", "pointer", "pchar", "pansichar",
            "pwidechar", "real", "real48", "shortint", "shortstring", "single",
            "smallint", "string", "text", "uint64", "variant", "widechar", "widestring",
            "word", "wordbool", "ansichar", "ansistring", "unicodestring", "rawbytestring"
        )

        private val BUILTINS = setOf(
            "abs", "addr", "append", "arctan", "assert", "assign", "assigned",
            "blockread", "blockwrite", "break", "chdir", "chr", "close", "concat",
            "continue", "copy", "cos", "dec", "delete", "dispose", "eof", "eoln",
            "erase", "exclude", "exit", "exp", "filepos", "filesize", "fillchar",
            "flush", "frac", "freemem", "getdir", "getmem", "halt", "hi", "high",
            "inc", "include", "insert", "int", "ioresult", "length", "ln", "lo",
            "low", "mkdir", "move", "new", "odd", "ord", "paramcount", "paramstr",
            "pi", "pos", "pred", "ptr", "random", "randomize", "read", "readln",
            "reallocmem", "rename", "reset", "rewrite", "rmdir", "round", "runerror",
            "seek", "seekeof", "seekeoln", "setlength", "setstring", "settextbuf",
            "sin", "sizeof", "sqr", "sqrt", "str", "succ", "swap", "trunc",
            "truncate", "typeof", "upcase", "val", "write", "writeln"
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
                    // { } style comment
                    val endIdx = line.indexOf('}', pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 1, TokenType.COMMENT_BLOCK))
                        pos = endIdx + 1
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                        pos = line.length
                    }
                }

                LexerState.IN_DOC_COMMENT -> {
                    // (* *) style comment
                    val endIdx = line.indexOf("*)", pos)
                    if (endIdx >= 0) {
                        tokens.add(Token(pos, endIdx + 2, TokenType.COMMENT_BLOCK))
                        pos = endIdx + 2
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                        pos = line.length
                    }
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Single line comment //
                        matchesAt(line, pos, "//") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Block comment { } or compiler directive {$...}
                        char == '{' -> {
                            if (pos + 1 < line.length && line[pos + 1] == '$') {
                                // Compiler directive
                                val endIdx = line.indexOf('}', pos + 2)
                                if (endIdx >= 0) {
                                    tokens.add(Token(pos, endIdx + 1, TokenType.ANNOTATION))
                                    pos = endIdx + 1
                                } else {
                                    tokens.add(Token(pos, line.length, TokenType.ANNOTATION))
                                    pos = line.length
                                }
                            } else {
                                val endIdx = line.indexOf('}', pos + 1)
                                if (endIdx >= 0) {
                                    tokens.add(Token(pos, endIdx + 1, TokenType.COMMENT_BLOCK))
                                    pos = endIdx + 1
                                } else {
                                    tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                                    pos = line.length
                                    state = LexerState.IN_BLOCK_COMMENT
                                }
                            }
                        }

                        // Block comment (* *) or directive (*$...*)
                        matchesAt(line, pos, "(*") -> {
                            if (pos + 2 < line.length && line[pos + 2] == '$') {
                                // Compiler directive
                                val endIdx = line.indexOf("*)", pos + 3)
                                if (endIdx >= 0) {
                                    tokens.add(Token(pos, endIdx + 2, TokenType.ANNOTATION))
                                    pos = endIdx + 2
                                } else {
                                    tokens.add(Token(pos, line.length, TokenType.ANNOTATION))
                                    pos = line.length
                                }
                            } else {
                                val endIdx = line.indexOf("*)", pos + 2)
                                if (endIdx >= 0) {
                                    tokens.add(Token(pos, endIdx + 2, TokenType.COMMENT_BLOCK))
                                    pos = endIdx + 2
                                } else {
                                    tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                                    pos = line.length
                                    state = LexerState.IN_DOC_COMMENT
                                }
                            }
                        }

                        // String literal 'string'
                        char == '\'' -> {
                            val endPos = readPascalString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Control string #nn
                        char == '#' -> {
                            val endPos = readControlString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.CHAR))
                            pos = endPos
                        }

                        // Hex number $FFFF
                        char == '$' -> {
                            val endPos = readHexNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos
                        }

                        // Number
                        char.isDigit() -> {
                            val endPos = readPascalNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos
                        }

                        // Identifier or keyword
                        isIdentifierStart(char) || char == '&' -> {
                            val endPos = readPascalIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos)
                            // Remove & prefix (escape for reserved words)
                            val cleanId = if (identifier.startsWith("&")) identifier.substring(1) else identifier
                            tokens.add(Token(pos, endPos, classifyIdentifier(cleanId)))
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

                        char == '[' || char == ']' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            pos++
                        }

                        // Semicolon, comma, etc.
                        char == ';' || char == ',' || char == '.' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
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
            lower in TYPES -> TokenType.TYPE
            lower in BUILTINS -> TokenType.FUNCTION_CALL
            lower == "true" || lower == "false" -> TokenType.BOOLEAN
            lower == "nil" -> TokenType.NULL
            lower == "self" || lower == "result" -> TokenType.KEYWORD
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readPascalString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '\'' -> {
                    // Check for escaped quote ''
                    if (pos + 1 < line.length && line[pos + 1] == '\'') {
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

    private fun readControlString(line: String, start: Int): Int {
        var pos = start + 1
        // Can be #nn (decimal) or #$nn (hex)
        if (pos < line.length && line[pos] == '$') {
            pos++
            while (pos < line.length && (line[pos].isDigit() || line[pos] in "abcdefABCDEF")) pos++
        } else {
            while (pos < line.length && line[pos].isDigit()) pos++
        }
        return pos.coerceAtLeast(start + 1)
    }

    private fun readHexNumber(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length && (line[pos].isDigit() || line[pos] in "abcdefABCDEF")) pos++
        return pos.coerceAtLeast(start + 1)
    }

    private fun readPascalNumber(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && line[pos].isDigit()) pos++

        // Decimal point
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

        return pos
    }

    private fun readPascalIdentifier(line: String, start: Int): Int {
        var pos = start
        // & prefix for escaped keywords
        if (pos < line.length && line[pos] == '&') pos++
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
        return pos.coerceAtLeast(start + 1)
    }

    private fun isOperator(char: Char): Boolean {
        return char in "+-*/<>=:@^"
    }

    private fun readOperator(line: String, pos: Int): Int {
        val twoChar = listOf(":=", "<=", ">=", "<>", "..", "**", "><", "+=", "-=", "*=", "/=")
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
