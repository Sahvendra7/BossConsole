package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Diff/Patch syntax highlighting lexer.
 * Supports unified diff, context diff, and git diff formats.
 */
class DiffLexer : BaseLexer() {

    override val languageId: String = "diff"
    override val fileExtensions: List<String> = listOf("diff", "patch", "rej")

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()

        if (line.isEmpty()) {
            return LineTokens(tokens, LexerState.NORMAL)
        }

        when {
            // Git diff header lines
            line.startsWith("diff ") -> {
                tokens.add(Token(0, line.length, TokenType.KEYWORD))
            }

            // Index line
            line.startsWith("index ") -> {
                tokenizeIndexLine(line, tokens)
            }

            // File headers (unified diff)
            line.startsWith("--- ") -> {
                tokens.add(Token(0, 4, TokenType.KEYWORD))
                tokens.add(Token(4, line.length, TokenType.TYPE))
            }

            line.startsWith("+++ ") -> {
                tokens.add(Token(0, 4, TokenType.KEYWORD))
                tokens.add(Token(4, line.length, TokenType.TYPE))
            }

            // File headers (context diff)
            line.startsWith("*** ") -> {
                tokens.add(Token(0, 4, TokenType.KEYWORD))
                tokens.add(Token(4, line.length, TokenType.TYPE))
            }

            // Hunk header @@ -l,s +l,s @@ or @@ -l +l @@
            line.startsWith("@@") -> {
                tokenizeHunkHeader(line, tokens)
            }

            // Context diff hunk header
            line.startsWith("***************") -> {
                tokens.add(Token(0, line.length, TokenType.ANNOTATION))
            }

            // Git extended headers
            line.startsWith("new file mode ") ||
            line.startsWith("deleted file mode ") ||
            line.startsWith("old mode ") ||
            line.startsWith("new mode ") ||
            line.startsWith("similarity index ") ||
            line.startsWith("rename from ") ||
            line.startsWith("rename to ") ||
            line.startsWith("copy from ") ||
            line.startsWith("copy to ") ||
            line.startsWith("Binary files ") -> {
                tokenizeGitExtendedHeader(line, tokens)
            }

            // Added line
            line.startsWith("+") -> {
                tokens.add(Token(0, 1, TokenType.OPERATOR))
                if (line.length > 1) {
                    tokens.add(Token(1, line.length, TokenType.INSERTION))
                }
            }

            // Removed line
            line.startsWith("-") -> {
                tokens.add(Token(0, 1, TokenType.OPERATOR))
                if (line.length > 1) {
                    tokens.add(Token(1, line.length, TokenType.DELETION))
                }
            }

            // Context line (starts with space)
            line.startsWith(" ") -> {
                tokens.add(Token(0, line.length, TokenType.DEFAULT))
            }

            // Context diff change marker
            line.startsWith("! ") -> {
                tokens.add(Token(0, 2, TokenType.OPERATOR))
                tokens.add(Token(2, line.length, TokenType.MODIFICATION))
            }

            // No newline at end of file
            line.startsWith("\\ No newline at end of file") ||
            line.startsWith("\\ ") -> {
                tokens.add(Token(0, line.length, TokenType.COMMENT))
            }

            // Git commit info
            line.startsWith("commit ") -> {
                tokens.add(Token(0, 7, TokenType.KEYWORD))
                tokens.add(Token(7, line.length, TokenType.CONSTANT))
            }

            line.startsWith("Author: ") -> {
                tokens.add(Token(0, 8, TokenType.KEYWORD))
                tokens.add(Token(8, line.length, TokenType.STRING))
            }

            line.startsWith("Date: ") -> {
                tokens.add(Token(0, 6, TokenType.KEYWORD))
                tokens.add(Token(6, line.length, TokenType.STRING))
            }

            line.startsWith("Merge: ") -> {
                tokens.add(Token(0, 7, TokenType.KEYWORD))
                tokens.add(Token(7, line.length, TokenType.CONSTANT))
            }

            // Only in (from diff -r)
            line.startsWith("Only in ") -> {
                tokens.add(Token(0, 8, TokenType.KEYWORD))
                tokens.add(Token(8, line.length, TokenType.TYPE))
            }

            // Common in (from diff -r)
            line.startsWith("Common subdirectories: ") -> {
                tokens.add(Token(0, 23, TokenType.KEYWORD))
                tokens.add(Token(23, line.length, TokenType.TYPE))
            }

            // Default - treat as context/text
            else -> {
                tokens.add(Token(0, line.length, TokenType.DEFAULT))
            }
        }

        return LineTokens(tokens, LexerState.NORMAL)
    }

    override fun classifyIdentifier(identifier: String): TokenType = TokenType.DEFAULT

    private fun tokenizeIndexLine(line: String, tokens: MutableList<Token>) {
        // index abc123..def456 100644
        tokens.add(Token(0, 6, TokenType.KEYWORD)) // "index "
        var pos = 6

        // Find hash range
        val dotsIdx = line.indexOf("..", pos)
        if (dotsIdx > pos) {
            tokens.add(Token(pos, dotsIdx, TokenType.CONSTANT)) // First hash
            tokens.add(Token(dotsIdx, dotsIdx + 2, TokenType.OPERATOR)) // ".."

            // Find end of second hash
            var endHash = dotsIdx + 2
            while (endHash < line.length && !line[endHash].isWhitespace()) endHash++
            tokens.add(Token(dotsIdx + 2, endHash, TokenType.CONSTANT)) // Second hash

            // Mode if present
            if (endHash < line.length) {
                tokens.add(Token(endHash, line.length, TokenType.NUMBER))
            }
        } else {
            tokens.add(Token(pos, line.length, TokenType.CONSTANT))
        }
    }

    private fun tokenizeHunkHeader(line: String, tokens: MutableList<Token>) {
        // @@ -1,5 +1,6 @@ optional context
        var pos = 0

        // Opening @@
        tokens.add(Token(0, 2, TokenType.ANNOTATION))
        pos = 2

        // Skip whitespace
        while (pos < line.length && line[pos].isWhitespace()) pos++

        // Old file range -l,s
        if (pos < line.length && line[pos] == '-') {
            val rangeStart = pos
            pos++
            while (pos < line.length && (line[pos].isDigit() || line[pos] == ',')) pos++
            tokens.add(Token(rangeStart, pos, TokenType.DELETION))
        }

        // Skip whitespace
        while (pos < line.length && line[pos].isWhitespace()) pos++

        // New file range +l,s
        if (pos < line.length && line[pos] == '+') {
            val rangeStart = pos
            pos++
            while (pos < line.length && (line[pos].isDigit() || line[pos] == ',')) pos++
            tokens.add(Token(rangeStart, pos, TokenType.INSERTION))
        }

        // Skip whitespace
        while (pos < line.length && line[pos].isWhitespace()) pos++

        // Closing @@
        if (pos + 1 < line.length && line[pos] == '@' && line[pos + 1] == '@') {
            tokens.add(Token(pos, pos + 2, TokenType.ANNOTATION))
            pos += 2

            // Optional function/context name
            if (pos < line.length) {
                tokens.add(Token(pos, line.length, TokenType.COMMENT))
            }
        }
    }

    private fun tokenizeGitExtendedHeader(line: String, tokens: MutableList<Token>) {
        // Find the keyword part
        val spaceIdx = line.indexOf(' ')
        if (spaceIdx > 0) {
            // Check for multi-word keywords
            val keywords = listOf(
                "new file mode", "deleted file mode", "old mode", "new mode",
                "similarity index", "rename from", "rename to",
                "copy from", "copy to", "Binary files"
            )

            for (kw in keywords) {
                if (line.startsWith(kw)) {
                    tokens.add(Token(0, kw.length, TokenType.KEYWORD))
                    if (kw.length < line.length) {
                        val valueType = when {
                            kw.contains("mode") -> TokenType.NUMBER
                            kw.contains("index") -> TokenType.NUMBER
                            kw.contains("from") || kw.contains("to") -> TokenType.TYPE
                            kw == "Binary files" -> TokenType.TYPE
                            else -> TokenType.DEFAULT
                        }
                        tokens.add(Token(kw.length, line.length, valueType))
                    }
                    return
                }
            }
        }

        // Fallback
        tokens.add(Token(0, line.length, TokenType.DEFAULT))
    }
}
