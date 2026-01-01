package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Makefile syntax highlighting lexer.
 */
class MakefileLexer : BaseLexer() {

    override val languageId: String = "makefile"
    override val fileExtensions: List<String> = listOf("makefile", "mk", "mak")

    companion object {
        private val DIRECTIVES = setOf(
            "define", "endef", "undefine", "ifdef", "ifndef", "ifeq", "ifneq",
            "else", "endif", "include", "-include", "sinclude", "override",
            "export", "unexport", "private", "vpath"
        )

        private val FUNCTIONS = setOf(
            "subst", "patsubst", "strip", "findstring", "filter", "filter-out",
            "sort", "word", "wordlist", "words", "firstword", "lastword",
            "dir", "notdir", "suffix", "basename", "addsuffix", "addprefix",
            "join", "wildcard", "realpath", "abspath", "error", "warning",
            "info", "shell", "origin", "flavor", "foreach", "if", "or", "and",
            "call", "eval", "file", "value"
        )

        private val AUTOMATIC_VARIABLES = setOf(
            "@", "%", "<", "?", "^", "+", "|", "*",
            "@D", "@F", "*D", "*F", "%D", "%F", "<D", "<F",
            "^D", "^F", "+D", "+F", "?D", "?F"
        )

        private val SPECIAL_TARGETS = setOf(
            ".PHONY", ".SUFFIXES", ".DEFAULT", ".PRECIOUS", ".INTERMEDIATE",
            ".SECONDARY", ".SECONDEXPANSION", ".DELETE_ON_ERROR", ".IGNORE",
            ".LOW_RESOLUTION_TIME", ".SILENT", ".EXPORT_ALL_VARIABLES",
            ".NOTPARALLEL", ".ONESHELL", ".POSIX"
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        val state = startState

        // Check if this is a recipe line (starts with tab)
        val isRecipe = line.isNotEmpty() && line[0] == '\t'

        if (isRecipe) {
            return tokenizeRecipeLine(line)
        }

        while (pos < line.length) {
            val char = line[pos]

            when {
                char.isWhitespace() -> pos = skipWhitespace(line, pos)

                // Comment
                char == '#' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    pos = line.length
                }

                // Variable reference $(VAR) or ${VAR}
                char == '$' -> {
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = endPos
                }

                // Check for target/variable definition at start of line
                pos == 0 || tokens.isEmpty() -> {
                    val (lineTokens, endPos) = tokenizeLineStart(line, pos)
                    tokens.addAll(lineTokens)
                    pos = endPos
                }

                // Assignment operators
                matchesAt(line, pos, "::=") || matchesAt(line, pos, "?=") ||
                matchesAt(line, pos, "+=") || matchesAt(line, pos, ":=") ||
                matchesAt(line, pos, "!=") -> {
                    val opLen = if (matchesAt(line, pos, "::=")) 3 else 2
                    tokens.add(Token(pos, pos + opLen, TokenType.OPERATOR))
                    pos += opLen
                    // Rest is value
                    pos = tokenizeValue(line, pos, tokens)
                }

                char == '=' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                    pos = tokenizeValue(line, pos, tokens)
                }

                // Rule separator
                char == ':' -> {
                    // Check for :: double colon rule
                    if (pos + 1 < line.length && line[pos + 1] == ':') {
                        tokens.add(Token(pos, pos + 2, TokenType.OPERATOR))
                        pos += 2
                    } else {
                        tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                        pos++
                    }
                    // Prerequisites
                    pos = tokenizePrerequisites(line, pos, tokens)
                }

                // Special characters
                char == ';' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                    // Inline recipe after ;
                    if (pos < line.length) {
                        pos = tokenizeRecipeContent(line, pos, tokens)
                    }
                }

                char == '\\' && pos == line.length - 1 -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                }

                else -> {
                    // Read word
                    val wordEnd = readWord(line, pos)
                    if (wordEnd > pos) {
                        val word = line.substring(pos, wordEnd)
                        tokens.add(Token(pos, wordEnd, classifyWord(word)))
                        pos = wordEnd
                    } else {
                        tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                        pos++
                    }
                }
            }
        }

        return LineTokens(tokens, LexerState.NORMAL)
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        return classifyWord(identifier)
    }

    private fun classifyWord(word: String): TokenType {
        return when {
            word in SPECIAL_TARGETS -> TokenType.ANNOTATION
            word in DIRECTIVES -> TokenType.KEYWORD
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizeLineStart(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start

        // Check for directive
        val wordEnd = readWord(line, pos)
        if (wordEnd > pos) {
            val word = line.substring(pos, wordEnd)
            if (word in DIRECTIVES || word.startsWith("-") && word.drop(1) in DIRECTIVES) {
                tokens.add(Token(pos, wordEnd, TokenType.KEYWORD))
                pos = wordEnd
                // Directive arguments
                while (pos < line.length && line[pos] != '#') {
                    when {
                        line[pos].isWhitespace() -> pos = skipWhitespace(line, pos)
                        line[pos] == '$' -> {
                            val (varTokens, endPos) = tokenizeVariable(line, pos)
                            tokens.addAll(varTokens)
                            pos = endPos
                        }
                        else -> {
                            val argEnd = readUntilAny(line, pos, setOf(' ', '\t', '#', '$'))
                            if (argEnd > pos) {
                                tokens.add(Token(pos, argEnd, TokenType.STRING))
                                pos = argEnd
                            } else {
                                pos++
                            }
                        }
                    }
                }
                return tokens to pos
            }
        }

        // Target or variable name
        pos = start
        while (pos < line.length) {
            val char = line[pos]
            when {
                char == '$' -> {
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = endPos
                }
                char == ':' || char == '=' || char == '#' -> break
                matchesAt(line, pos, "::=") || matchesAt(line, pos, "?=") ||
                matchesAt(line, pos, "+=") || matchesAt(line, pos, ":=") ||
                matchesAt(line, pos, "!=") -> break
                char.isWhitespace() -> {
                    pos = skipWhitespace(line, pos)
                }
                else -> {
                    val wordEnd = readTargetWord(line, pos)
                    if (wordEnd > pos) {
                        val word = line.substring(pos, wordEnd)
                        val tokenType = if (word in SPECIAL_TARGETS) TokenType.ANNOTATION else TokenType.FUNCTION
                        tokens.add(Token(pos, wordEnd, tokenType))
                        pos = wordEnd
                    } else {
                        pos++
                    }
                }
            }
        }

        return tokens to pos
    }

    private fun tokenizeVariable(line: String, start: Int): Pair<List<Token>, Int> {
        var pos = start + 1

        if (pos >= line.length) {
            return listOf(Token(start, start + 1, TokenType.PUNCTUATION)) to (start + 1)
        }

        val char = line[pos]

        // Single character automatic variable
        if (char in AUTOMATIC_VARIABLES.filter { it.length == 1 }.map { it[0] }.toSet()) {
            return listOf(Token(start, pos + 1, TokenType.VARIABLE)) to (pos + 1)
        }

        // $(VAR) or ${VAR}
        if (char == '(' || char == '{') {
            val closeChar = if (char == '(') ')' else '}'
            pos++

            // Check for function call
            val funcEnd = readWord(line, pos)
            if (funcEnd > pos) {
                val funcName = line.substring(pos, funcEnd)
                if (funcName in FUNCTIONS) {
                    val tokens = mutableListOf<Token>()
                    tokens.add(Token(start, start + 2, TokenType.PUNCTUATION))
                    tokens.add(Token(pos, funcEnd, TokenType.FUNCTION_CALL))
                    pos = funcEnd

                    // Function arguments
                    var depth = 1
                    val argStart = pos
                    while (pos < line.length && depth > 0) {
                        when (line[pos]) {
                            '(', '{' -> depth++
                            ')', '}' -> depth--
                        }
                        pos++
                    }
                    if (argStart < pos - 1) {
                        tokens.add(Token(argStart, pos - 1, TokenType.STRING))
                    }
                    if (pos > 0) {
                        tokens.add(Token(pos - 1, pos, TokenType.PUNCTUATION))
                    }
                    return tokens to pos
                }
            }

            // Regular variable
            var depth = 1
            while (pos < line.length && depth > 0) {
                when (line[pos]) {
                    '(', '{' -> depth++
                    ')', '}' -> depth--
                }
                pos++
            }
            return listOf(Token(start, pos, TokenType.VARIABLE)) to pos
        }

        // $$
        if (char == '$') {
            return listOf(Token(start, pos + 1, TokenType.STRING)) to (pos + 1)
        }

        // $X single character variable
        if (char.isLetterOrDigit() || char == '_') {
            return listOf(Token(start, pos + 1, TokenType.VARIABLE)) to (pos + 1)
        }

        return listOf(Token(start, start + 1, TokenType.PUNCTUATION)) to (start + 1)
    }

    private fun tokenizeValue(line: String, start: Int, tokens: MutableList<Token>): Int {
        var pos = start

        while (pos < line.length) {
            val char = line[pos]
            when {
                char == '#' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    return line.length
                }
                char == '$' -> {
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = endPos
                }
                char == '\\' && pos == line.length - 1 -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                }
                char.isWhitespace() -> {
                    pos = skipWhitespace(line, pos)
                }
                else -> {
                    val valueEnd = readUntilAny(line, pos, setOf('#', '$', '\\'))
                    if (valueEnd > pos) {
                        tokens.add(Token(pos, valueEnd, TokenType.STRING))
                        pos = valueEnd
                    } else {
                        pos++
                    }
                }
            }
        }

        return pos
    }

    private fun tokenizePrerequisites(line: String, start: Int, tokens: MutableList<Token>): Int {
        var pos = start

        while (pos < line.length) {
            val char = line[pos]
            when {
                char == '#' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    return line.length
                }
                char == ';' -> return pos // Let main loop handle inline recipe
                char == '$' -> {
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = endPos
                }
                char == '|' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                }
                char == '\\' && pos == line.length - 1 -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                }
                char.isWhitespace() -> {
                    pos = skipWhitespace(line, pos)
                }
                else -> {
                    val prereqEnd = readTargetWord(line, pos)
                    if (prereqEnd > pos) {
                        tokens.add(Token(pos, prereqEnd, TokenType.IDENTIFIER))
                        pos = prereqEnd
                    } else {
                        pos++
                    }
                }
            }
        }

        return pos
    }

    private fun tokenizeRecipeLine(line: String): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0

        // Tab at start
        if (line.isNotEmpty() && line[0] == '\t') {
            tokens.add(Token(0, 1, TokenType.DEFAULT))
            pos = 1
        }

        pos = tokenizeRecipeContent(line, pos, tokens)
        return LineTokens(tokens, LexerState.NORMAL)
    }

    private fun tokenizeRecipeContent(line: String, start: Int, tokens: MutableList<Token>): Int {
        var pos = start

        // Check for @ (silent), - (ignore errors), + (force)
        while (pos < line.length && line[pos] in "@-+") {
            tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
            pos++
        }

        // Rest is shell command
        while (pos < line.length) {
            val char = line[pos]
            when {
                char == '#' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    return line.length
                }
                char == '$' -> {
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = endPos
                }
                char == '"' || char == '\'' -> {
                    val endPos = readQuotedString(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.STRING))
                    pos = endPos
                }
                char == '\\' && pos == line.length - 1 -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                }
                else -> {
                    tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                    pos++
                }
            }
        }

        return pos
    }

    private fun readWord(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && !line[pos].isWhitespace() && line[pos] !in ":=#$\\") pos++
        return pos
    }

    private fun readTargetWord(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && !line[pos].isWhitespace() && line[pos] !in ":=#$;|\\") pos++
        return pos
    }

    private fun readUntilAny(line: String, start: Int, chars: Set<Char>): Int {
        var pos = start
        while (pos < line.length && line[pos] !in chars) pos++
        return pos
    }

    private fun readQuotedString(line: String, start: Int): Int {
        val quote = line[start]
        var pos = start + 1
        while (pos < line.length) {
            when {
                line[pos] == quote -> return pos + 1
                line[pos] == '\\' && pos + 1 < line.length -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }
}
