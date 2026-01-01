package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Windows Batch/CMD syntax highlighting lexer.
 */
class BatchLexer : BaseLexer() {

    override val languageId: String = "batch"
    override val fileExtensions: List<String> = listOf("bat", "cmd")

    companion object {
        private val COMMANDS = setOf(
            "call", "cd", "chdir", "cls", "cmd", "color", "copy", "date", "del",
            "dir", "echo", "endlocal", "erase", "exit", "for", "ftype", "goto",
            "if", "md", "mkdir", "mklink", "move", "path", "pause", "popd",
            "prompt", "pushd", "rd", "rem", "ren", "rename", "rmdir", "set",
            "setlocal", "shift", "start", "time", "title", "type", "ver",
            "verify", "vol", "xcopy", "robocopy", "attrib", "find", "findstr",
            "more", "sort", "tree", "where", "whoami", "hostname", "ipconfig",
            "ping", "netstat", "tasklist", "taskkill", "shutdown", "reg",
            "schtasks", "net", "sc", "wmic", "powershell", "assoc", "break",
            "cacls", "chcp", "chkdsk", "choice", "cipher", "clip", "comp",
            "compact", "convert", "debug", "diskpart", "doskey", "driverquery",
            "expand", "fc", "format", "fsutil", "gpresult", "graftabl", "help",
            "icacls", "label", "mode", "openfiles", "print", "recover", "replace",
            "subst", "systeminfo", "timeout", "wevtutil", "winsat"
        )

        private val KEYWORDS = setOf(
            "do", "else", "errorlevel", "exist", "in", "not", "nul", "con",
            "prn", "aux", "com1", "com2", "com3", "com4", "lpt1", "lpt2", "lpt3",
            "defined", "equ", "neq", "lss", "leq", "gtr", "geq", "cmdextversion",
            "enabledelayedexpansion", "disabledelayedexpansion", "enableextensions",
            "disableextensions"
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        val lineLower = line.lowercase()

        // Check for REM comment at start of line (case insensitive)
        val trimmedStart = line.indexOfFirst { !it.isWhitespace() }
        if (trimmedStart >= 0 && lineLower.substring(trimmedStart).startsWith("rem") &&
            (trimmedStart + 3 >= line.length || !line[trimmedStart + 3].isLetterOrDigit())) {
            if (trimmedStart > 0) {
                // Skip leading whitespace
                pos = trimmedStart
            }
            tokens.add(Token(pos, line.length, TokenType.COMMENT))
            return LineTokens(tokens, LexerState.NORMAL)
        }

        while (pos < line.length) {
            val char = line[pos]

            when {
                char.isWhitespace() -> pos = skipWhitespace(line, pos)

                // :: comment (must be at start or after whitespace/special char)
                char == ':' && pos + 1 < line.length && line[pos + 1] == ':' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    pos = line.length
                }

                // Label :label
                char == ':' && pos + 1 < line.length && line[pos + 1] != ':' -> {
                    val labelEnd = findLabelEnd(line, pos + 1)
                    tokens.add(Token(pos, labelEnd, TokenType.LABEL))
                    pos = labelEnd
                }

                // Variable %var% or %~dp0 style
                char == '%' -> {
                    val (endPos, tokenType) = readVariable(line, pos)
                    tokens.add(Token(pos, endPos, tokenType))
                    pos = endPos
                }

                // Delayed expansion !var!
                char == '!' -> {
                    val endPos = readDelayedVariable(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.VARIABLE))
                    pos = endPos
                }

                // String
                char == '"' -> {
                    val endPos = readString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                }

                // Operators and redirections
                char == '>' || char == '<' || char == '|' || char == '&' || char == '^' -> {
                    val opLen = readOperator(line, pos)
                    tokens.add(Token(pos, pos + opLen, TokenType.OPERATOR))
                    pos += opLen
                }

                // Parentheses for grouping
                char == '(' || char == ')' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PARENTHESIS))
                    pos++
                }

                // @ symbol (echo off prefix)
                char == '@' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.ANNOTATION))
                    pos++
                }

                // Number
                char.isDigit() -> {
                    val endPos = readBatchNumber(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.NUMBER))
                    pos = endPos
                }

                // Identifier/Command
                isIdentifierStart(char) -> {
                    val endPos = readIdentifier(line, pos)
                    val identifier = line.substring(pos, endPos)
                    tokens.add(Token(pos, endPos, classifyIdentifier(identifier)))
                    pos = endPos
                }

                // Comparison operators
                char == '=' -> {
                    if (pos + 1 < line.length && line[pos + 1] == '=') {
                        tokens.add(Token(pos, pos + 2, TokenType.OPERATOR))
                        pos += 2
                    } else {
                        tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                        pos++
                    }
                }

                // Path separator
                char == '\\' || char == '/' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
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

    override fun classifyIdentifier(identifier: String): TokenType {
        val lower = identifier.lowercase()
        return when {
            lower in COMMANDS -> TokenType.KEYWORD
            lower in KEYWORDS -> TokenType.KEYWORD
            lower == "on" || lower == "off" -> TokenType.CONSTANT
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readVariable(line: String, start: Int): Pair<Int, TokenType> {
        var pos = start + 1

        // Single digit parameter %0-%9
        if (pos < line.length && line[pos].isDigit()) {
            return (pos + 1) to TokenType.VARIABLE
        }

        // Special variables %*
        if (pos < line.length && line[pos] == '*') {
            return (pos + 1) to TokenType.VARIABLE
        }

        // Modifiers %~dp0 etc.
        if (pos < line.length && line[pos] == '~') {
            pos++
            while (pos < line.length && line[pos] in "fdpnxsatz$") pos++
            if (pos < line.length && (line[pos].isDigit() || line[pos].isLetter())) pos++
            return pos to TokenType.VARIABLE
        }

        // Environment variable %VAR%
        while (pos < line.length && line[pos] != '%' && !line[pos].isWhitespace()) {
            pos++
        }
        if (pos < line.length && line[pos] == '%') {
            pos++
        }
        return pos to TokenType.VARIABLE
    }

    private fun readDelayedVariable(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length && line[pos] != '!' && !line[pos].isWhitespace()) {
            pos++
        }
        if (pos < line.length && line[pos] == '!') pos++
        return pos
    }

    private fun readString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == '"' -> return pos + 1
                line[pos] == '^' && pos + 1 < line.length -> pos += 2 // Escape
                else -> pos++
            }
        }
        return line.length
    }

    private fun findLabelEnd(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && !line[pos].isWhitespace()) {
            pos++
        }
        return pos
    }

    private fun readBatchNumber(line: String, start: Int): Int {
        var pos = start
        // Hex 0x...
        if (pos + 1 < line.length && line[pos] == '0' && line[pos + 1] in "xX") {
            pos += 2
            while (pos < line.length && (line[pos].isDigit() || line[pos] in "abcdefABCDEF")) pos++
            return pos
        }
        while (pos < line.length && line[pos].isDigit()) pos++
        return pos
    }

    private fun readOperator(line: String, pos: Int): Int {
        val twoChar = listOf(">>", "<<", "&&", "||", "2>", "1>", "2>&1", ">|")
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return op.length
        }
        return 1
    }
}
