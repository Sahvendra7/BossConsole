package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Tcl/Tk syntax highlighting lexer.
 */
class TclLexer : BaseLexer() {

    override val languageId: String = "tcl"
    override val fileExtensions: List<String> = listOf("tcl", "tk", "itcl", "itk")

    companion object {
        private val KEYWORDS = setOf(
            "after", "append", "apply", "array", "auto_execok", "auto_import",
            "auto_load", "auto_mkindex", "auto_qualify", "auto_reset", "bgerror",
            "binary", "break", "catch", "cd", "chan", "clock", "close", "concat",
            "continue", "coroutine", "dde", "dict", "encoding", "eof", "error",
            "eval", "exec", "exit", "expr", "fblocked", "fconfigure", "fcopy",
            "file", "fileevent", "flush", "for", "foreach", "format", "gets",
            "glob", "global", "history", "http", "if", "incr", "info", "interp",
            "join", "lappend", "lassign", "lindex", "linsert", "list", "llength",
            "load", "lrange", "lrepeat", "lreplace", "lreverse", "lsearch", "lset",
            "lsort", "mathfunc", "mathop", "memory", "msgcat", "namespace", "open",
            "package", "parray", "pid", "pkg_mkIndex", "platform", "proc", "puts",
            "pwd", "read", "refchan", "regexp", "registry", "regsub", "rename",
            "return", "safe", "scan", "seek", "set", "socket", "source", "split",
            "string", "subst", "switch", "tailcall", "tcl_endOfWord", "tcl_findLibrary",
            "tcl_startOfNextWord", "tcl_startOfPreviousWord", "tcl_wordBreakAfter",
            "tcl_wordBreakBefore", "tcltest", "tell", "throw", "time", "tm",
            "trace", "transchan", "try", "unknown", "unload", "unset", "update",
            "uplevel", "upvar", "variable", "vwait", "while", "yield", "yieldto",
            "zlib"
        )

        private val TK_COMMANDS = setOf(
            "bell", "bind", "bindtags", "bitmap", "button", "canvas", "checkbutton",
            "clipboard", "colors", "console", "cursors", "destroy", "entry", "event",
            "focus", "font", "frame", "grab", "grid", "image", "keysyms", "label",
            "labelframe", "listbox", "lower", "menu", "menubutton", "message",
            "option", "options", "pack", "panedwindow", "photo", "place", "radiobutton",
            "raise", "scale", "scrollbar", "selection", "send", "spinbox", "text",
            "tk", "tk_bisque", "tk_chooseColor", "tk_chooseDirectory", "tk_dialog",
            "tk_focusFollowsMouse", "tk_focusNext", "tk_focusPrev", "tk_getOpenFile",
            "tk_getSaveFile", "tk_menuSetFocus", "tk_messageBox", "tk_optionMenu",
            "tk_popup", "tk_setPalette", "tk_textCopy", "tk_textCut", "tk_textPaste",
            "tkerror", "tkwait", "toplevel", "ttk::button", "ttk::checkbutton",
            "ttk::combobox", "ttk::entry", "ttk::frame", "ttk::intro", "ttk::label",
            "ttk::labelframe", "ttk::menubutton", "ttk::notebook", "ttk::panedwindow",
            "ttk::progressbar", "ttk::radiobutton", "ttk::scale", "ttk::scrollbar",
            "ttk::separator", "ttk::sizegrip", "ttk::spinbox", "ttk::style",
            "ttk::treeview", "ttk::widget", "winfo", "wm"
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
                    // Multi-line comment continuation (rare in Tcl, using if 0 {...})
                    val closeIdx = line.indexOf("}", pos)
                    if (closeIdx >= 0) {
                        tokens.add(Token(pos, closeIdx + 1, TokenType.COMMENT_BLOCK))
                        pos = closeIdx + 1
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_BLOCK))
                        pos = line.length
                    }
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Comment
                        char == '#' -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Double-quoted string
                        char == '"' -> {
                            val endPos = readTclString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Braced string/block
                        char == '{' -> {
                            val (endPos, complete) = readBracedBlock(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Command substitution
                        char == '[' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            pos++
                        }

                        char == ']' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            pos++
                        }

                        // Variable substitution
                        char == '$' -> {
                            val endPos = readVariable(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.VARIABLE))
                            pos = endPos
                        }

                        // Escape
                        char == '\\' -> {
                            val escLen = if (pos + 1 < line.length) 2 else 1
                            tokens.add(Token(pos, pos + escLen, TokenType.ESCAPE))
                            pos += escLen
                        }

                        // Number
                        char.isDigit() || (char == '-' && pos + 1 < line.length && line[pos + 1].isDigit()) ||
                        (char == '+' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readTclNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Namespace separator
                        matchesAt(line, pos, "::") -> {
                            tokens.add(Token(pos, pos + 2, TokenType.OPERATOR))
                            pos += 2
                        }

                        // Word/identifier
                        isWordStart(char) -> {
                            val endPos = readWord(line, pos)
                            val word = line.substring(pos, endPos)
                            tokens.add(Token(pos, endPos, classifyIdentifier(word)))
                            pos = endPos
                        }

                        // Operators
                        char in "+-*/%<>=!&|^~" -> {
                            val opLen = readOperator(line, pos)
                            tokens.add(Token(pos, pos + opLen, TokenType.OPERATOR))
                            pos += opLen
                        }

                        // Parentheses (used in expressions)
                        char == '(' || char == ')' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PARENTHESIS))
                            pos++
                        }

                        // Semicolon (command separator)
                        char == ';' -> {
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
        return when {
            identifier in KEYWORDS -> TokenType.KEYWORD
            identifier in TK_COMMANDS -> TokenType.FUNCTION_CALL
            identifier.startsWith("::") -> TokenType.TYPE // Namespace qualified
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readTclString(line: String, start: Int): Int {
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

    private fun readBracedBlock(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start + 1
        var depth = 1
        while (pos < line.length && depth > 0) {
            when {
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                line[pos] == '{' -> { depth++; pos++ }
                line[pos] == '}' -> { depth--; pos++ }
                else -> pos++
            }
        }
        return pos to (depth == 0)
    }

    private fun readVariable(line: String, start: Int): Int {
        var pos = start + 1
        if (pos >= line.length) return pos

        // ${varname} form
        if (line[pos] == '{') {
            pos++
            while (pos < line.length && line[pos] != '}') pos++
            if (pos < line.length) pos++
            return pos
        }

        // $varname or $::namespace::var
        while (pos < line.length) {
            val char = line[pos]
            if (char.isLetterOrDigit() || char == '_' || char == ':') {
                pos++
            } else if (char == '(' && pos > start + 1) {
                // Array access $arr(index)
                pos++
                var parenDepth = 1
                while (pos < line.length && parenDepth > 0) {
                    when (line[pos]) {
                        '(' -> parenDepth++
                        ')' -> parenDepth--
                    }
                    pos++
                }
                break
            } else {
                break
            }
        }
        return pos.coerceAtLeast(start + 1)
    }

    private fun readWord(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length) {
            val char = line[pos]
            if (char.isLetterOrDigit() || char == '_' || char == ':' || char == '-') {
                pos++
            } else {
                break
            }
        }
        return pos.coerceAtLeast(start + 1)
    }

    private fun isWordStart(char: Char): Boolean {
        return char.isLetter() || char == '_' || char == ':'
    }

    private fun readTclNumber(line: String, start: Int): Int {
        var pos = start
        if (line[pos] in "+-") pos++

        // Hex 0x
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1] in "xX") {
            pos += 2
            while (pos < line.length && (line[pos].isDigit() || line[pos] in "abcdefABCDEF")) pos++
            return pos
        }

        // Octal 0o
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1] in "oO") {
            pos += 2
            while (pos < line.length && line[pos] in '0'..'7') pos++
            return pos
        }

        // Binary 0b
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1] in "bB") {
            pos += 2
            while (pos < line.length && line[pos] in "01") pos++
            return pos
        }

        // Decimal
        while (pos < line.length && line[pos].isDigit()) pos++

        // Float
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

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("<<<", ">>>")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "<<", ">>", "**", "eq", "ne", "in", "ni")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
