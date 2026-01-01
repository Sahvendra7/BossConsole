package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Dockerfile syntax highlighting lexer.
 */
class DockerfileLexer : BaseLexer() {

    override val languageId: String = "dockerfile"
    override val fileExtensions: List<String> = listOf("dockerfile")

    companion object {
        private val INSTRUCTIONS = setOf(
            "FROM", "MAINTAINER", "RUN", "CMD", "LABEL", "EXPOSE", "ENV", "ADD",
            "COPY", "ENTRYPOINT", "VOLUME", "USER", "WORKDIR", "ARG", "ONBUILD",
            "STOPSIGNAL", "HEALTHCHECK", "SHELL", "CROSS_BUILD"
        )

        private val HEALTHCHECK_OPTIONS = setOf(
            "--interval", "--timeout", "--start-period", "--retries"
        )

        private val COPY_ADD_OPTIONS = setOf(
            "--from", "--chown", "--chmod", "--link"
        )

        private val RUN_OPTIONS = setOf(
            "--mount", "--network", "--security"
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        val state = startState

        // Handle line continuation from previous line
        val trimmedLine = line.trimStart()
        val leadingWhitespace = line.length - trimmedLine.length

        if (leadingWhitespace > 0) {
            pos = leadingWhitespace
        }

        while (pos < line.length) {
            val char = line[pos]

            when {
                char.isWhitespace() -> pos = skipWhitespace(line, pos)

                // Comment
                char == '#' -> {
                    // Check if it's a parser directive (first lines only)
                    if (isParserDirective(line, pos)) {
                        val (directiveTokens, endPos) = tokenizeParserDirective(line, pos)
                        tokens.addAll(directiveTokens)
                        pos = endPos
                    } else {
                        tokens.add(Token(pos, line.length, TokenType.COMMENT))
                        pos = line.length
                    }
                }

                // Instruction at line start
                pos <= leadingWhitespace && char.isLetter() -> {
                    val wordEnd = readWord(line, pos)
                    val word = line.substring(pos, wordEnd).uppercase()

                    if (word in INSTRUCTIONS) {
                        tokens.add(Token(pos, wordEnd, TokenType.KEYWORD))
                        pos = wordEnd

                        // Tokenize rest of line based on instruction
                        val (restTokens, endPos) = tokenizeInstructionArgs(line, pos, word)
                        tokens.addAll(restTokens)
                        pos = endPos
                    } else {
                        tokens.add(Token(pos, wordEnd, TokenType.IDENTIFIER))
                        pos = wordEnd
                    }
                }

                // Variable reference
                char == '$' -> {
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = endPos
                }

                // String
                char == '"' || char == '\'' -> {
                    val (stringTokens, endPos) = tokenizeString(line, pos, char)
                    tokens.addAll(stringTokens)
                    pos = endPos
                }

                // Line continuation
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

        // Check if line ends with continuation
        val endState = if (line.trimEnd().endsWith("\\")) {
            LexerState.IN_MULTILINE_STRING // Reuse for continuation
        } else {
            LexerState.NORMAL
        }

        return LineTokens(tokens, endState)
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        return when {
            identifier.uppercase() in INSTRUCTIONS -> TokenType.KEYWORD
            else -> TokenType.IDENTIFIER
        }
    }

    private fun isParserDirective(line: String, pos: Int): Boolean {
        // Parser directives are comments at the very start of file: # directive=value
        // We can't track file position, so check if it looks like a directive
        if (line[pos] != '#') return false
        val rest = line.substring(pos + 1).trim()
        return rest.contains("=") && !rest.contains(" ") && rest.split("=")[0].all { it.isLetter() }
    }

    private fun tokenizeParserDirective(line: String, start: Int): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1 // Skip #

        // Skip whitespace
        while (pos < line.length && line[pos].isWhitespace()) pos++
        val keyStart = pos

        // Read key
        while (pos < line.length && line[pos] != '=' && !line[pos].isWhitespace()) pos++
        if (keyStart < pos) {
            tokens.add(Token(start, pos, TokenType.ANNOTATION))
        }

        // Read =
        if (pos < line.length && line[pos] == '=') {
            pos++
        }

        // Rest is value
        if (pos < line.length) {
            tokens.add(Token(pos, line.length, TokenType.STRING))
        }

        return tokens to line.length
    }

    private fun tokenizeInstructionArgs(line: String, start: Int, instruction: String): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start

        when (instruction) {
            "FROM" -> {
                pos = skipWhitespace(line, pos)
                // Handle --platform flag
                if (matchesAt(line, pos, "--platform")) {
                    val flagEnd = readWord(line, pos)
                    tokens.add(Token(pos, flagEnd, TokenType.PARAMETER))
                    pos = flagEnd
                    if (pos < line.length && line[pos] == '=') {
                        tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                        pos++
                        val valueEnd = readUntilWhitespace(line, pos)
                        tokens.add(Token(pos, valueEnd, TokenType.STRING))
                        pos = valueEnd
                    }
                    pos = skipWhitespace(line, pos)
                }
                // Image name
                val imageEnd = readUntilWhitespace(line, pos)
                if (imageEnd > pos) {
                    tokens.add(Token(pos, imageEnd, TokenType.TYPE))
                    pos = imageEnd
                }
                // AS alias
                pos = skipWhitespace(line, pos)
                if (matchesAt(line, pos, "AS") || matchesAt(line, pos, "as")) {
                    val asEnd = pos + 2
                    tokens.add(Token(pos, asEnd, TokenType.KEYWORD))
                    pos = skipWhitespace(line, asEnd)
                    val aliasEnd = readUntilWhitespace(line, pos)
                    if (aliasEnd > pos) {
                        tokens.add(Token(pos, aliasEnd, TokenType.VARIABLE))
                        pos = aliasEnd
                    }
                }
            }

            "ENV", "ARG", "LABEL" -> {
                pos = tokenizeKeyValuePairs(line, pos, tokens)
            }

            "RUN", "CMD", "ENTRYPOINT", "SHELL" -> {
                pos = tokenizeShellCommand(line, pos, tokens)
            }

            "COPY", "ADD" -> {
                pos = tokenizeCopyAdd(line, pos, tokens)
            }

            "EXPOSE" -> {
                pos = tokenizePorts(line, pos, tokens)
            }

            "WORKDIR", "USER", "VOLUME" -> {
                pos = tokenizePath(line, pos, tokens)
            }

            "HEALTHCHECK" -> {
                pos = tokenizeHealthcheck(line, pos, tokens)
            }

            else -> {
                // Generic handling
                while (pos < line.length) {
                    val char = line[pos]
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)
                        char == '$' -> {
                            val (varTokens, endPos) = tokenizeVariable(line, pos)
                            tokens.addAll(varTokens)
                            pos = endPos
                        }
                        char == '"' || char == '\'' -> {
                            val (stringTokens, endPos) = tokenizeString(line, pos, char)
                            tokens.addAll(stringTokens)
                            pos = endPos
                        }
                        char == '\\' && pos == line.length - 1 -> {
                            tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                            pos++
                        }
                        char == '#' -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }
                        else -> {
                            tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                            pos++
                        }
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

        return if (line[pos] == '{') {
            val end = line.indexOf('}', pos)
            if (end >= 0) {
                listOf(Token(start, end + 1, TokenType.VARIABLE)) to (end + 1)
            } else {
                listOf(Token(start, line.length, TokenType.VARIABLE)) to line.length
            }
        } else {
            while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
            listOf(Token(start, pos, TokenType.VARIABLE)) to pos
        }
    }

    private fun tokenizeString(line: String, start: Int, quote: Char): Pair<List<Token>, Int> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1
        var tokenStart = start

        while (pos < line.length) {
            when {
                line[pos] == quote -> {
                    tokens.add(Token(tokenStart, pos + 1, TokenType.STRING))
                    return tokens to (pos + 1)
                }
                line[pos] == '\\' && pos + 1 < line.length -> {
                    pos += 2
                }
                line[pos] == '$' && quote == '"' -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens.map { Token(it.startOffset, it.endOffset, TokenType.STRING_TEMPLATE) })
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

    private fun tokenizeKeyValuePairs(line: String, start: Int, tokens: MutableList<Token>): Int {
        var pos = start

        while (pos < line.length) {
            pos = skipWhitespace(line, pos)
            if (pos >= line.length) break

            val char = line[pos]

            when {
                char == '#' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    return line.length
                }
                char == '\\' && pos == line.length - 1 -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    return pos + 1
                }
                char == '"' || char == '\'' -> {
                    val (stringTokens, endPos) = tokenizeString(line, pos, char)
                    tokens.addAll(stringTokens)
                    pos = endPos
                }
                char == '$' -> {
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = endPos
                }
                else -> {
                    // Key
                    val keyEnd = readUntilAny(line, pos, setOf('=', ' ', '\t'))
                    if (keyEnd > pos) {
                        tokens.add(Token(pos, keyEnd, TokenType.PROPERTY))
                        pos = keyEnd
                    }

                    // =
                    if (pos < line.length && line[pos] == '=') {
                        tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                        pos++

                        // Value
                        if (pos < line.length) {
                            if (line[pos] == '"' || line[pos] == '\'') {
                                val (stringTokens, endPos) = tokenizeString(line, pos, line[pos])
                                tokens.addAll(stringTokens)
                                pos = endPos
                            } else {
                                val valueEnd = readUntilWhitespace(line, pos)
                                if (valueEnd > pos) {
                                    tokens.add(Token(pos, valueEnd, TokenType.STRING))
                                    pos = valueEnd
                                }
                            }
                        }
                    }
                }
            }
        }

        return pos
    }

    private fun tokenizeShellCommand(line: String, start: Int, tokens: MutableList<Token>): Int {
        var pos = start

        // Check for JSON array format
        pos = skipWhitespace(line, pos)
        if (pos < line.length && line[pos] == '[') {
            return tokenizeJsonArray(line, pos, tokens)
        }

        // Check for RUN options
        while (pos < line.length && matchesAt(line, pos, "--")) {
            val optEnd = readWord(line, pos)
            val opt = line.substring(pos, optEnd)
            if (opt.substringBefore('=') in RUN_OPTIONS || opt.startsWith("--mount") || opt.startsWith("--network")) {
                tokens.add(Token(pos, optEnd, TokenType.PARAMETER))
                pos = optEnd
                if (pos < line.length && line[pos] == '=') {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                    val valueEnd = readUntilWhitespace(line, pos)
                    tokens.add(Token(pos, valueEnd, TokenType.STRING))
                    pos = valueEnd
                }
                pos = skipWhitespace(line, pos)
            } else {
                break
            }
        }

        // Rest is shell command - tokenize as default with variable interpolation
        while (pos < line.length) {
            val char = line[pos]
            when {
                char == '$' -> {
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = endPos
                }
                char == '"' || char == '\'' -> {
                    val (stringTokens, endPos) = tokenizeString(line, pos, char)
                    tokens.addAll(stringTokens)
                    pos = endPos
                }
                char == '\\' && pos == line.length - 1 -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    pos++
                }
                char == '#' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    pos = line.length
                }
                else -> {
                    tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                    pos++
                }
            }
        }

        return pos
    }

    private fun tokenizeJsonArray(line: String, start: Int, tokens: MutableList<Token>): Int {
        var pos = start
        tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
        pos++

        while (pos < line.length) {
            pos = skipWhitespace(line, pos)
            if (pos >= line.length) break

            when (line[pos]) {
                ']' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                    return pos + 1
                }
                ',' -> {
                    tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                    pos++
                }
                '"' -> {
                    val (stringTokens, endPos) = tokenizeString(line, pos, '"')
                    tokens.addAll(stringTokens)
                    pos = endPos
                }
                else -> {
                    tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                    pos++
                }
            }
        }

        return pos
    }

    private fun tokenizeCopyAdd(line: String, start: Int, tokens: MutableList<Token>): Int {
        var pos = start

        // Handle options
        pos = skipWhitespace(line, pos)
        while (pos < line.length && matchesAt(line, pos, "--")) {
            val optEnd = readWord(line, pos)
            tokens.add(Token(pos, optEnd, TokenType.PARAMETER))
            pos = optEnd
            if (pos < line.length && line[pos] == '=') {
                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                pos++
                val valueEnd = readUntilWhitespace(line, pos)
                tokens.add(Token(pos, valueEnd, TokenType.STRING))
                pos = valueEnd
            }
            pos = skipWhitespace(line, pos)
        }

        // Check for JSON array format
        if (pos < line.length && line[pos] == '[') {
            return tokenizeJsonArray(line, pos, tokens)
        }

        // Paths
        return tokenizePath(line, pos, tokens)
    }

    private fun tokenizePorts(line: String, start: Int, tokens: MutableList<Token>): Int {
        var pos = start

        while (pos < line.length) {
            pos = skipWhitespace(line, pos)
            if (pos >= line.length) break

            val char = line[pos]
            when {
                char == '#' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    return line.length
                }
                char == '\\' && pos == line.length - 1 -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    return pos + 1
                }
                char == '$' -> {
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = endPos
                }
                char.isDigit() -> {
                    val numEnd = readPort(line, pos)
                    tokens.add(Token(pos, numEnd, TokenType.NUMBER))
                    pos = numEnd
                }
                else -> {
                    tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                    pos++
                }
            }
        }

        return pos
    }

    private fun readPort(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && (line[pos].isDigit() || line[pos] == '/' || line[pos] == '-' || line[pos] == ':')) {
            pos++
        }
        // Include protocol
        if (matchesAt(line, pos, "/tcp") || matchesAt(line, pos, "/udp")) {
            pos += 4
        }
        return pos
    }

    private fun tokenizePath(line: String, start: Int, tokens: MutableList<Token>): Int {
        var pos = start

        while (pos < line.length) {
            pos = skipWhitespace(line, pos)
            if (pos >= line.length) break

            val char = line[pos]
            when {
                char == '#' -> {
                    tokens.add(Token(pos, line.length, TokenType.COMMENT))
                    return line.length
                }
                char == '\\' && pos == line.length - 1 -> {
                    tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                    return pos + 1
                }
                char == '$' -> {
                    val (varTokens, endPos) = tokenizeVariable(line, pos)
                    tokens.addAll(varTokens)
                    pos = endPos
                }
                char == '"' || char == '\'' -> {
                    val (stringTokens, endPos) = tokenizeString(line, pos, char)
                    tokens.addAll(stringTokens)
                    pos = endPos
                }
                else -> {
                    val pathEnd = readUntilWhitespace(line, pos)
                    if (pathEnd > pos) {
                        tokens.add(Token(pos, pathEnd, TokenType.STRING))
                        pos = pathEnd
                    } else {
                        pos++
                    }
                }
            }
        }

        return pos
    }

    private fun tokenizeHealthcheck(line: String, start: Int, tokens: MutableList<Token>): Int {
        var pos = start
        pos = skipWhitespace(line, pos)

        // Check for NONE
        if (matchesAt(line, pos, "NONE")) {
            tokens.add(Token(pos, pos + 4, TokenType.KEYWORD))
            return pos + 4
        }

        // Handle options
        while (pos < line.length && matchesAt(line, pos, "--")) {
            val optEnd = readWord(line, pos)
            tokens.add(Token(pos, optEnd, TokenType.PARAMETER))
            pos = optEnd
            if (pos < line.length && line[pos] == '=') {
                tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
                pos++
                val valueEnd = readUntilWhitespace(line, pos)
                tokens.add(Token(pos, valueEnd, TokenType.NUMBER))
                pos = valueEnd
            }
            pos = skipWhitespace(line, pos)
        }

        // CMD
        if (matchesAt(line, pos, "CMD")) {
            tokens.add(Token(pos, pos + 3, TokenType.KEYWORD))
            pos += 3
            return tokenizeShellCommand(line, pos, tokens)
        }

        return pos
    }

    private fun readWord(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && !line[pos].isWhitespace() && line[pos] != '=') pos++
        return pos
    }

    private fun readUntilWhitespace(line: String, start: Int): Int {
        var pos = start
        while (pos < line.length && !line[pos].isWhitespace()) pos++
        return pos
    }

    private fun readUntilAny(line: String, start: Int, chars: Set<Char>): Int {
        var pos = start
        while (pos < line.length && line[pos] !in chars) pos++
        return pos
    }
}
