package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Shell/Bash syntax highlighting lexer.
 */
class ShellLexer : BaseLexer() {

    override val languageId: String = "shell"
    override val fileExtensions: List<String> = listOf("sh", "bash", "zsh", "fish", "ksh", "csh", "tcsh")

    companion object {
        private val KEYWORDS = setOf(
            "if", "then", "else", "elif", "fi", "case", "esac", "for", "while",
            "until", "do", "done", "in", "function", "select", "time", "coproc",
            "break", "continue", "return", "exit"
        )

        private val BUILTINS = setOf(
            "echo", "printf", "read", "cd", "pwd", "pushd", "popd", "dirs",
            "export", "unset", "set", "shopt", "source", "alias", "unalias",
            "type", "which", "hash", "help", "history", "fc", "jobs", "fg", "bg",
            "kill", "wait", "disown", "suspend", "logout", "exec", "eval",
            "trap", "test", "true", "false", "let", "declare", "local", "readonly",
            "typeset", "getopts", "shift", "umask", "ulimit", "enable", "builtin",
            "command", "compgen", "complete", "compopt", "mapfile", "readarray"
        )

        private val COMMON_COMMANDS = setOf(
            "ls", "cat", "grep", "awk", "sed", "find", "xargs", "sort", "uniq",
            "wc", "head", "tail", "cut", "tr", "tee", "diff", "patch", "tar",
            "gzip", "gunzip", "zip", "unzip", "curl", "wget", "ssh", "scp",
            "rsync", "git", "docker", "kubectl", "make", "npm", "yarn", "pip",
            "python", "python3", "node", "java", "javac", "go", "cargo", "rustc",
            "chmod", "chown", "chgrp", "mkdir", "rmdir", "rm", "cp", "mv", "ln",
            "touch", "file", "stat", "df", "du", "mount", "umount", "ps", "top",
            "htop", "kill", "killall", "pkill", "pgrep", "nohup", "screen", "tmux",
            "vim", "vi", "nano", "emacs", "less", "more", "man", "info", "apropos",
            "sudo", "su", "whoami", "id", "groups", "passwd", "useradd", "usermod",
            "apt", "apt-get", "yum", "dnf", "pacman", "brew", "snap", "flatpak"
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
                    // Heredoc content
                    val (endPos, complete) = continueHeredoc(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Comment
                        char == '#' -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Heredoc
                        matchesAt(line, pos, "<<") -> {
                            val endPos = readHeredocStart(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.OPERATOR))
                            pos = endPos
                            state = LexerState.IN_MULTILINE_STRING
                        }

                        // Variable
                        char == '$' -> {
                            val (varTokens, endPos) = tokenizeVariable(line, pos)
                            tokens.addAll(varTokens)
                            pos = endPos
                        }

                        // Single-quoted string (no expansion)
                        char == '\'' -> {
                            val endPos = readSingleQuotedString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Double-quoted string (with expansion)
                        char == '"' -> {
                            val (strTokens, endPos) = tokenizeDoubleQuotedString(line, pos)
                            tokens.addAll(strTokens)
                            pos = endPos
                        }

                        // Backtick command substitution
                        char == '`' -> {
                            val endPos = readBacktickCommand(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING_TEMPLATE))
                            pos = endPos
                        }

                        // Number
                        char.isDigit() -> {
                            val endPos = readNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Identifier/command
                        isIdentifierStart(char) -> {
                            val endPos = readShellIdentifier(line, pos)
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
                            tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
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
            identifier in COMMON_COMMANDS -> TokenType.FUNCTION_CALL
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizeVariable(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1

        if (pos >= line.length) {
            tokens.add(Token(start, start + 1, TokenType.OPERATOR))
            return tokens to pos
        }

        when {
            // ${var} or ${var:-default}
            line[pos] == '{' -> {
                val endPos = findMatchingBrace(line, pos + 1)
                tokens.add(Token(start, endPos, TokenType.VARIABLE))
                pos = endPos
            }
            // $(command) or $((arithmetic))
            line[pos] == '(' -> {
                val isArithmetic = pos + 1 < line.length && line[pos + 1] == '('
                val endPos = if (isArithmetic) {
                    findDoubleParenEnd(line, pos + 2)
                } else {
                    findMatchingParen(line, pos + 1)
                }
                tokens.add(Token(start, endPos, TokenType.STRING_TEMPLATE))
                pos = endPos
            }
            // Special variables: $?, $!, $$, $#, $@, $*, $0-9
            line[pos] in "?!$#@*0123456789-" -> {
                tokens.add(Token(start, pos + 1, TokenType.VARIABLE))
                pos++
            }
            // Regular variable
            line[pos].isLetter() || line[pos] == '_' -> {
                while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
                tokens.add(Token(start, pos, TokenType.VARIABLE))
            }
            else -> {
                tokens.add(Token(start, start + 1, TokenType.OPERATOR))
            }
        }

        return tokens to pos
    }

    private fun tokenizeDoubleQuotedString(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1
        var tokenStart = start

        while (pos < line.length) {
            when {
                line[pos] == '"' -> {
                    tokens.add(Token(tokenStart, pos + 1, TokenType.STRING))
                    return tokens to (pos + 1)
                }
                line[pos] == '\\' && pos + 1 < line.length -> {
                    pos += 2
                }
                line[pos] == '$' -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = endPos
                    tokenStart = pos
                }
                line[pos] == '`' -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    val endPos = readBacktickCommand(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING_TEMPLATE))
                    pos = endPos
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
            if (line[pos] == '\'') return pos + 1
            pos++
        }
        return line.length
    }

    private fun readBacktickCommand(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '`' -> return pos + 1
                line[pos] == '\\' -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }

    private fun readHeredocStart(line: String, start: Int): Int {
        var pos = start + 2
        if (pos < line.length && line[pos] == '-') pos++
        while (pos < line.length && line[pos].isWhitespace()) pos++
        // Read delimiter
        val delimStart = pos
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
        return pos
    }

    private fun continueHeredoc(line: String, start: Int): Pair<Int, Boolean> {
        // Simplified: heredoc ends at EOF marker
        return line.length to false
    }

    private fun readShellIdentifier(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_' || line[pos] == '-')) {
            pos++
        }
        return pos
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

    private fun findMatchingParen(line: String, start: Int): Int {
        var depth = 1
        var pos = start
        while (pos < line.length && depth > 0) {
            when (line[pos]) {
                '(' -> depth++
                ')' -> depth--
            }
            pos++
        }
        return pos
    }

    private fun findDoubleParenEnd(line: String, start: Int): Int {
        var depth = 1
        var pos = start
        while (pos + 1 < line.length && depth > 0) {
            if (line[pos] == '(' && line[pos + 1] == '(') {
                depth++
                pos += 2
            } else if (line[pos] == ')' && line[pos + 1] == ')') {
                depth--
                pos += 2
            } else {
                pos++
            }
        }
        return pos
    }

    private fun isOperator(char: Char): Boolean {
        return char in setOf('|', '&', '<', '>', ';', '=', '!', '+', '-', '*', '/')
    }

    private fun readOperator(line: String, pos: Int): Int {
        val twoChar = listOf("||", "&&", ">>", "<<", ";;", ">&", "<&", "|&", ">=", "<=", "==", "!=", "+=", "-=")
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
