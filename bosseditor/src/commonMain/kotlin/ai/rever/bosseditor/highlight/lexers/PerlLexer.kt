package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Perl syntax highlighting lexer.
 */
class PerlLexer : BaseLexer() {

    override val languageId: String = "perl"
    override val fileExtensions: List<String> = listOf("pl", "pm", "pod", "t", "psgi")

    companion object {
        private val KEYWORDS = setOf(
            "and", "cmp", "continue", "do", "else", "elsif", "eq", "for",
            "foreach", "ge", "gt", "if", "last", "le", "lt", "ne", "next",
            "not", "or", "package", "redo", "return", "sub", "unless", "until",
            "while", "xor", "BEGIN", "END", "CHECK", "INIT", "UNITCHECK",
            "__DATA__", "__END__", "__FILE__", "__LINE__", "__PACKAGE__",
            "use", "no", "require", "my", "our", "local", "state", "given",
            "when", "default", "break", "say", "try", "catch", "finally",
            "class", "method", "has", "with", "extends", "does", "around",
            "before", "after", "override", "augment"
        )

        private val BUILTINS = setOf(
            "abs", "accept", "alarm", "atan2", "bind", "binmode", "bless",
            "caller", "chdir", "chmod", "chomp", "chop", "chown", "chr",
            "chroot", "close", "closedir", "connect", "cos", "crypt",
            "dbmclose", "dbmopen", "defined", "delete", "die", "dump",
            "each", "endgrent", "endhostent", "endnetent", "endprotoent",
            "endpwent", "endservent", "eof", "eval", "exec", "exists",
            "exit", "exp", "fcntl", "fileno", "flock", "fork", "format",
            "formline", "getc", "getgrent", "getgrgid", "getgrnam",
            "gethostbyaddr", "gethostbyname", "gethostent", "getlogin",
            "getnetbyaddr", "getnetbyname", "getnetent", "getpeername",
            "getpgrp", "getppid", "getpriority", "getprotobyname",
            "getprotobynumber", "getprotoent", "getpwent", "getpwnam",
            "getpwuid", "getservbyname", "getservbyport", "getservent",
            "getsockname", "getsockopt", "glob", "gmtime", "goto", "grep",
            "hex", "import", "index", "int", "ioctl", "join", "keys", "kill",
            "lc", "lcfirst", "length", "link", "listen", "localtime", "log",
            "lstat", "map", "mkdir", "msgctl", "msgget", "msgrcv", "msgsnd",
            "oct", "open", "opendir", "ord", "pack", "pipe", "pop", "pos",
            "print", "printf", "prototype", "push", "quotemeta", "rand",
            "read", "readdir", "readline", "readlink", "readpipe", "recv",
            "ref", "rename", "reset", "reverse", "rewinddir", "rindex",
            "rmdir", "scalar", "seek", "seekdir", "select", "semctl",
            "semget", "semop", "send", "setgrent", "sethostent", "setnetent",
            "setpgrp", "setpriority", "setprotoent", "setpwent", "setservent",
            "setsockopt", "shift", "shmctl", "shmget", "shmread", "shmwrite",
            "shutdown", "sin", "sleep", "socket", "socketpair", "sort",
            "splice", "split", "sprintf", "sqrt", "srand", "stat", "study",
            "substr", "symlink", "syscall", "sysopen", "sysread", "sysseek",
            "system", "syswrite", "tell", "telldir", "tie", "tied", "time",
            "times", "truncate", "uc", "ucfirst", "umask", "undef", "unlink",
            "unpack", "unshift", "untie", "utime", "values", "vec", "wait",
            "waitpid", "wantarray", "warn", "write"
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
                    val (endPos, complete) = continueHeredoc(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.IN_DOC_COMMENT -> {
                    // POD documentation
                    if (matchesAt(line, pos, "=cut")) {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_DOC))
                        pos = line.length
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT_DOC))
                        pos = line.length
                    }
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // POD documentation
                        pos == 0 && char == '=' && pos + 1 < line.length && line[pos + 1].isLetter() -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT_DOC))
                            pos = line.length
                            state = LexerState.IN_DOC_COMMENT
                        }

                        // Comment
                        char == '#' -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Heredoc
                        matchesAt(line, pos, "<<") -> {
                            val heredocEnd = readHeredocStart(line, pos)
                            if (heredocEnd > pos) {
                                tokens.add(Token(pos, heredocEnd, TokenType.STRING))
                                pos = heredocEnd
                                state = LexerState.IN_MULTILINE_STRING
                            } else {
                                tokens.add(Token(pos, pos + 2, TokenType.OPERATOR))
                                pos += 2
                            }
                        }

                        // Regex match m/.../ or /..../
                        char == '/' && canStartRegex(line, pos) -> {
                            val endPos = readRegex(line, pos, '/')
                            tokens.add(Token(pos, endPos, TokenType.REGEX))
                            pos = endPos
                        }

                        // Regex match m{...} or s/.../.../
                        char == 'm' && pos + 1 < line.length && !line[pos + 1].isLetterOrDigit() -> {
                            val endPos = readPerlRegex(line, pos + 1)
                            tokens.add(Token(pos, endPos, TokenType.REGEX))
                            pos = endPos
                        }

                        // Substitution s/.../.../
                        char == 's' && pos + 1 < line.length && !line[pos + 1].isLetterOrDigit() -> {
                            val endPos = readPerlSubstitution(line, pos + 1)
                            tokens.add(Token(pos, endPos, TokenType.REGEX))
                            pos = endPos
                        }

                        // Transliteration tr/.../.../ or y/.../.../
                        (char == 't' && matchesAt(line, pos, "tr")) ||
                        (char == 'y' && pos + 1 < line.length && !line[pos + 1].isLetterOrDigit()) -> {
                            val startOff = if (char == 't') 2 else 1
                            val endPos = readPerlSubstitution(line, pos + startOff)
                            tokens.add(Token(pos, endPos, TokenType.REGEX))
                            pos = endPos
                        }

                        // qw/.../, q/.../, qq/.../, qx/.../, qr/.../
                        char == 'q' && pos + 1 < line.length -> {
                            val next = line[pos + 1]
                            if (next in "wqxr" || !next.isLetterOrDigit()) {
                                val (endPos, tokenType) = readQuoteLike(line, pos)
                                tokens.add(Token(pos, endPos, tokenType))
                                pos = endPos
                            } else {
                                val endPos = readIdentifier(line, pos)
                                tokens.add(Token(pos, endPos, classifyIdentifier(line.substring(pos, endPos))))
                                pos = endPos
                            }
                        }

                        // Double-quoted string
                        char == '"' -> {
                            val (stringTokens, endPos) = tokenizeInterpolatedString(line, pos)
                            tokens.addAll(stringTokens)
                            pos = endPos
                        }

                        // Single-quoted string
                        char == '\'' -> {
                            val endPos = readSingleQuotedString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Backtick command
                        char == '`' -> {
                            val endPos = readBacktickString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Scalar variable $var
                        char == '$' -> {
                            val endPos = readVariable(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.VARIABLE))
                            pos = endPos
                        }

                        // Array @var
                        char == '@' -> {
                            val endPos = readVariable(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.VARIABLE))
                            pos = endPos
                        }

                        // Hash %var
                        char == '%' -> {
                            val endPos = readVariable(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.VARIABLE))
                            pos = endPos
                        }

                        // Typeglob *var
                        char == '*' && pos + 1 < line.length && (line[pos + 1].isLetter() || line[pos + 1] == '_') -> {
                            val endPos = readIdentifier(line, pos + 1)
                            tokens.add(Token(pos, endPos, TokenType.VARIABLE))
                            pos = endPos
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
            identifier in BUILTINS -> TokenType.FUNCTION_CALL
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizeInterpolatedString(line: String, start: Int): Pair<List<Token>, Int> {
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
                line[pos] in "$@" -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    val varEnd = readVariable(line, pos)
                    tokens.add(Token(pos, varEnd, TokenType.STRING_TEMPLATE))
                    pos = varEnd
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

    private fun readSingleQuotedString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '\'' -> return pos + 1
                line[pos] == '\\' && pos + 1 < line.length && line[pos + 1] in "'\\" -> pos += 2
                else -> pos++
            }
        }
        return line.length
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

    private fun readVariable(line: String, start: Int): Int {
        var pos = start + 1
        if (pos >= line.length) return pos

        // Special variables like $!, $?, $$, etc.
        if (line[pos] in "!@#\$%^&*()_+-={}|[]\\:\";<>?,./~`") {
            return pos + 1
        }

        // ${...} or @{...}
        if (line[pos] == '{') {
            val end = line.indexOf('}', pos + 1)
            return if (end >= 0) end + 1 else line.length
        }

        // Named variable
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++

        return pos.coerceAtLeast(start + 1)
    }

    private fun readHeredocStart(line: String, start: Int): Int {
        var pos = start + 2
        // Skip ~ for indented heredoc (Perl 5.26+)
        if (pos < line.length && line[pos] == '~') pos++

        // Get delimiter
        val (delimiter, endPos) = when {
            pos < line.length && line[pos] == '\'' -> {
                val delimStart = pos + 1
                val delimEnd = line.indexOf('\'', delimStart)
                if (delimEnd > delimStart) line.substring(delimStart, delimEnd) to (delimEnd + 1) else "" to pos
            }
            pos < line.length && line[pos] == '"' -> {
                val delimStart = pos + 1
                val delimEnd = line.indexOf('"', delimStart)
                if (delimEnd > delimStart) line.substring(delimStart, delimEnd) to (delimEnd + 1) else "" to pos
            }
            pos < line.length && line[pos] == '`' -> {
                val delimStart = pos + 1
                val delimEnd = line.indexOf('`', delimStart)
                if (delimEnd > delimStart) line.substring(delimStart, delimEnd) to (delimEnd + 1) else "" to pos
            }
            else -> {
                val delimStart = pos
                while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
                if (pos > delimStart) line.substring(delimStart, pos) to pos else "" to start
            }
        }

        return if (delimiter.isNotEmpty()) endPos else start
    }

    private fun continueHeredoc(line: String, start: Int): Pair<Int, Boolean> {
        // Simplified heredoc handling
        return line.length to false
    }

    private fun canStartRegex(line: String, pos: Int): Boolean {
        if (pos == 0) return true
        val prev = (pos - 1 downTo 0).firstOrNull { !line[it].isWhitespace() } ?: return true
        return line[prev] in "=~!&|({[,;:?"
    }

    private fun readRegex(line: String, start: Int, delim: Char): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == delim -> {
                    pos++
                    // Read modifiers
                    while (pos < line.length && line[pos] in "msixpodualngc") pos++
                    return pos
                }
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }

    private fun readPerlRegex(line: String, start: Int): Int {
        if (start >= line.length) return start
        val delim = line[start]
        val closeDelim = getCloseDelim(delim)
        return readDelimitedContent(line, start + 1, closeDelim, true)
    }

    private fun readPerlSubstitution(line: String, start: Int): Int {
        if (start >= line.length) return start
        val delim = line[start]
        val closeDelim = getCloseDelim(delim)
        val firstEnd = readDelimitedContent(line, start + 1, closeDelim, false)
        if (firstEnd >= line.length) return firstEnd

        // Second part
        val secondDelim = if (delim == closeDelim) delim else line.getOrNull(firstEnd) ?: return firstEnd
        val secondClose = getCloseDelim(secondDelim)
        val secondStart = if (delim == closeDelim) firstEnd else firstEnd + 1
        return readDelimitedContent(line, secondStart, secondClose, true)
    }

    private fun getCloseDelim(open: Char): Char {
        return when (open) {
            '(' -> ')'
            '[' -> ']'
            '{' -> '}'
            '<' -> '>'
            else -> open
        }
    }

    private fun readDelimitedContent(line: String, start: Int, closeDelim: Char, withModifiers: Boolean): Int {
        var pos = start
        var depth = 1
        val openDelim = when (closeDelim) {
            ')' -> '('
            ']' -> '['
            '}' -> '{'
            '>' -> '<'
            else -> closeDelim
        }

        while (pos < line.length && depth > 0) {
            when {
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                line[pos] == closeDelim -> { depth--; pos++ }
                line[pos] == openDelim && openDelim != closeDelim -> { depth++; pos++ }
                else -> pos++
            }
        }

        if (withModifiers) {
            while (pos < line.length && line[pos] in "msixpodualngc") pos++
        }

        return pos
    }

    private fun readQuoteLike(line: String, start: Int): Pair<Int, TokenType> {
        var pos = start + 1
        val type = if (pos < line.length && line[pos] in "wqxr") {
            pos++
            line[pos - 1]
        } else {
            'q'
        }

        if (pos >= line.length) return pos to TokenType.STRING

        val delim = line[pos]
        val closeDelim = getCloseDelim(delim)
        val endPos = readDelimitedContent(line, pos + 1, closeDelim, type == 'r')

        val tokenType = if (type == 'r') TokenType.REGEX else TokenType.STRING
        return endPos to tokenType
    }

    private fun isOperator(char: Char): Boolean {
        return char in "+-*/%=<>!&|^~?:."
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("<=>", "...", "=~", "!~", "&&=", "||=", "//=", "**=")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "**", "=>", "->", "::", ".=", "x=", "&=", "|=", "^=", "<<", ">>", "~~", "//")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return op.length
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
