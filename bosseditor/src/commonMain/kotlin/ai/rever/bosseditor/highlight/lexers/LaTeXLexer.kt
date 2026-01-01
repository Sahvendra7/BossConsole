package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * LaTeX syntax highlighting lexer.
 */
class LaTeXLexer : BaseLexer() {

    override val languageId: String = "latex"
    override val fileExtensions: List<String> = listOf("tex", "sty", "cls", "bib", "bst", "ltx")

    companion object {
        private val SECTIONING_COMMANDS = setOf(
            "part", "chapter", "section", "subsection", "subsubsection",
            "paragraph", "subparagraph"
        )

        private val DOCUMENT_COMMANDS = setOf(
            "documentclass", "usepackage", "begin", "end", "newcommand",
            "renewcommand", "newenvironment", "renewenvironment",
            "input", "include", "includeonly", "bibliography", "bibliographystyle"
        )

        private val TEXT_COMMANDS = setOf(
            "textbf", "textit", "texttt", "textrm", "textsf", "textsc",
            "emph", "underline", "uppercase", "lowercase", "mbox", "fbox",
            "makebox", "framebox", "parbox", "minipage", "footnote", "marginpar"
        )

        private val MATH_ENVIRONMENTS = setOf(
            "equation", "align", "gather", "multline", "eqnarray",
            "equation*", "align*", "gather*", "multline*",
            "matrix", "pmatrix", "bmatrix", "vmatrix", "Vmatrix", "cases"
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
                    // Verbatim environment - look for \end{verbatim}
                    val endVerbatim = line.indexOf("\\end{verbatim}", pos)
                    if (endVerbatim >= 0) {
                        tokens.add(Token(pos, endVerbatim, TokenType.STRING))
                        tokens.add(Token(endVerbatim, endVerbatim + 14, TokenType.KEYWORD))
                        pos = endVerbatim + 14
                        state = LexerState.NORMAL
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.STRING))
                        pos = line.length
                    }
                }

                LexerState.NORMAL -> {
                    when {
                        // Comment
                        char == '%' -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Command
                        char == '\\' -> {
                            val (cmdTokens, endPos, newState) = tokenizeCommand(line, pos)
                            tokens.addAll(cmdTokens)
                            pos = endPos
                            if (newState != LexerState.NORMAL) {
                                state = newState
                            }
                        }

                        // Math mode $...$
                        char == '$' -> {
                            val isDisplay = pos + 1 < line.length && line[pos + 1] == '$'
                            val (mathTokens, endPos) = tokenizeMath(line, pos, isDisplay)
                            tokens.addAll(mathTokens)
                            pos = endPos
                        }

                        // Group
                        char == '{' || char == '}' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            pos++
                        }

                        // Optional argument
                        char == '[' || char == ']' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            pos++
                        }

                        // Special characters
                        char == '&' || char == '~' || char == '^' || char == '_' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                            pos++
                        }

                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        else -> {
                            // Regular text
                            val textEnd = findTextEnd(line, pos)
                            if (textEnd > pos) {
                                tokens.add(Token(pos, textEnd, TokenType.DEFAULT))
                                pos = textEnd
                            } else {
                                pos++
                            }
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
            identifier in SECTIONING_COMMANDS -> TokenType.KEYWORD
            identifier in DOCUMENT_COMMANDS -> TokenType.KEYWORD
            identifier in TEXT_COMMANDS -> TokenType.FUNCTION_CALL
            else -> TokenType.FUNCTION
        }
    }

    private fun tokenizeCommand(line: String, start: Int): Triple<List<Token>, Int, LexerState> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1

        if (pos >= line.length) {
            tokens.add(Token(start, start + 1, TokenType.PUNCTUATION))
            return Triple(tokens, pos, LexerState.NORMAL)
        }

        val char = line[pos]

        // Special single-character commands
        if (!char.isLetter()) {
            tokens.add(Token(start, pos + 1, TokenType.KEYWORD))
            return Triple(tokens, pos + 1, LexerState.NORMAL)
        }

        // Read command name
        while (pos < line.length && line[pos].isLetter()) pos++
        val cmdName = line.substring(start + 1, pos)

        // Check for verbatim environment
        if (cmdName == "begin") {
            val afterCmd = pos
            // Look for {verbatim}
            while (pos < line.length && line[pos].isWhitespace()) pos++
            if (pos < line.length && line[pos] == '{') {
                val envStart = pos + 1
                val envEnd = line.indexOf('}', envStart)
                if (envEnd > envStart) {
                    val envName = line.substring(envStart, envEnd)
                    if (envName == "verbatim" || envName == "lstlisting") {
                        tokens.add(Token(start, envEnd + 1, TokenType.KEYWORD))
                        return Triple(tokens, envEnd + 1, LexerState.IN_BLOCK_COMMENT)
                    }
                }
            }
            pos = afterCmd
        }

        // Classify command
        val tokenType = when {
            cmdName in SECTIONING_COMMANDS -> TokenType.TYPE
            cmdName in DOCUMENT_COMMANDS -> TokenType.KEYWORD
            cmdName in TEXT_COMMANDS -> TokenType.FUNCTION_CALL
            cmdName == "begin" || cmdName == "end" -> TokenType.KEYWORD
            cmdName.startsWith("text") || cmdName.startsWith("math") -> TokenType.FUNCTION_CALL
            else -> TokenType.FUNCTION
        }

        tokens.add(Token(start, pos, tokenType))

        // Handle arguments
        while (pos < line.length) {
            when (line[pos]) {
                ' ', '\t' -> pos++
                '*' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                }
                '[' -> {
                    // Optional argument
                    tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                    pos++
                    val argEnd = findClosingBracket(line, pos, '[', ']')
                    if (argEnd > pos) {
                        tokens.add(Token(pos, argEnd - 1, TokenType.STRING))
                        tokens.add(Token(argEnd - 1, argEnd, TokenType.BRACKET))
                        pos = argEnd
                    }
                }
                '{' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                    pos++
                    val argEnd = findClosingBracket(line, pos, '{', '}')
                    if (argEnd > pos) {
                        // Check if this is an environment name
                        if (cmdName == "begin" || cmdName == "end") {
                            tokens.add(Token(pos, argEnd - 1, TokenType.TYPE))
                        }
                        // Otherwise, content is processed normally
                        pos = argEnd - 1
                        if (pos < line.length && line[pos] == '}') {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            pos++
                        }
                    }
                    break
                }
                else -> break
            }
        }

        return Triple(tokens, pos, LexerState.NORMAL)
    }

    private fun tokenizeMath(line: String, start: Int, isDisplay: Boolean): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start
        val delimiter = if (isDisplay) "$$" else "$"
        val delimLen = delimiter.length

        tokens.add(Token(pos, pos + delimLen, TokenType.OPERATOR))
        pos += delimLen

        val mathStart = pos
        while (pos < line.length) {
            if (isDisplay && matchesAt(line, pos, "$$")) {
                if (mathStart < pos) {
                    tokens.add(Token(mathStart, pos, TokenType.NUMBER))
                }
                tokens.add(Token(pos, pos + 2, TokenType.OPERATOR))
                return tokens to (pos + 2)
            } else if (!isDisplay && line[pos] == '$') {
                if (mathStart < pos) {
                    tokens.add(Token(mathStart, pos, TokenType.NUMBER))
                }
                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                return tokens to (pos + 1)
            } else if (line[pos] == '\\' && pos + 1 < line.length) {
                pos += 2
            } else {
                pos++
            }
        }

        // Unclosed math mode
        if (mathStart < line.length) {
            tokens.add(Token(mathStart, line.length, TokenType.NUMBER))
        }
        return tokens to line.length
    }

    private fun findClosingBracket(line: String, start: Int, open: Char, close: Char): Int {
        var pos = start
        var depth = 1
        while (pos < line.length && depth > 0) {
            when {
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                line[pos] == open -> { depth++; pos++ }
                line[pos] == close -> { depth--; pos++ }
                else -> pos++
            }
        }
        return pos
    }

    private fun findTextEnd(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length) {
            val char = line[pos]
            if (char in "\\$%{}[]&~^_" || char.isWhitespace()) break
            pos++
        }
        return pos
    }
}
