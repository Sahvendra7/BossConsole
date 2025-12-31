package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * CSS syntax highlighting lexer.
 * Also handles SCSS/SASS/LESS basics.
 */
class CssLexer : BaseLexer() {

    override val languageId: String = "css"
    override val fileExtensions: List<String> = listOf("css", "scss", "sass", "less")

    companion object {
        private val KEYWORDS = setOf(
            "!important", "inherit", "initial", "unset", "revert",
            "none", "auto", "normal", "hidden", "visible", "solid",
            "dashed", "dotted", "double", "groove", "ridge", "inset", "outset",
            "block", "inline", "inline-block", "flex", "grid", "table",
            "absolute", "relative", "fixed", "sticky", "static",
            "left", "right", "top", "bottom", "center",
            "bold", "italic", "underline", "uppercase", "lowercase",
            "transparent", "currentColor"
        )

        private val AT_RULES = setOf(
            "@media", "@import", "@font-face", "@keyframes", "@supports",
            "@page", "@charset", "@namespace", "@viewport", "@counter-style",
            "@font-feature-values", "@property", "@layer", "@container",
            "@mixin", "@include", "@extend", "@if", "@else", "@for", "@each", "@while", "@function", "@return" // SCSS
        )

        private val PSEUDO_CLASSES = setOf(
            "hover", "active", "focus", "visited", "link", "first-child",
            "last-child", "nth-child", "nth-of-type", "first-of-type",
            "last-of-type", "only-child", "only-of-type", "empty", "checked",
            "disabled", "enabled", "required", "optional", "valid", "invalid",
            "target", "root", "not", "has", "where", "is", "focus-visible",
            "focus-within", "placeholder-shown", "default", "indeterminate"
        )

        private val PSEUDO_ELEMENTS = setOf(
            "before", "after", "first-line", "first-letter", "selection",
            "placeholder", "marker", "backdrop", "cue", "grammar-error",
            "spelling-error", "slotted", "part"
        )

        private val FUNCTIONS = setOf(
            "rgb", "rgba", "hsl", "hsla", "hwb", "lab", "lch", "oklch", "oklab",
            "color", "color-mix", "url", "var", "calc", "min", "max", "clamp",
            "attr", "counter", "counters", "linear-gradient", "radial-gradient",
            "conic-gradient", "repeating-linear-gradient", "repeating-radial-gradient",
            "translate", "translateX", "translateY", "translateZ", "translate3d",
            "rotate", "rotateX", "rotateY", "rotateZ", "rotate3d",
            "scale", "scaleX", "scaleY", "scaleZ", "scale3d",
            "skew", "skewX", "skewY", "matrix", "matrix3d", "perspective",
            "cubic-bezier", "steps", "env", "fit-content", "minmax", "repeat"
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
                    val (endPos, complete) = readBlockComment(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Single-line comment (SCSS/LESS)
                        matchesAt(line, pos, "//") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Block comment
                        matchesAt(line, pos, "/*") -> {
                            val (endPos, complete) = readBlockComment(line, pos + 2)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                            pos = endPos
                            if (!complete) state = LexerState.IN_BLOCK_COMMENT
                        }

                        // At-rule
                        char == '@' -> {
                            val endPos = readCssIdentifier(line, pos)
                            val keyword = line.substring(pos, endPos)
                            tokens.add(Token(pos, endPos, TokenType.KEYWORD))
                            pos = endPos
                        }

                        // Class selector
                        char == '.' && pos + 1 < line.length && (line[pos + 1].isLetter() || line[pos + 1] == '-' || line[pos + 1] == '_') -> {
                            val endPos = readCssIdentifier(line, pos + 1)
                            tokens.add(Token(pos, endPos, TokenType.TYPE))
                            pos = endPos
                        }

                        // ID selector
                        char == '#' && pos + 1 < line.length && (line[pos + 1].isLetterOrDigit() || line[pos + 1] == '-' || line[pos + 1] == '_') -> {
                            val endPos = readCssIdentifier(line, pos + 1)
                            tokens.add(Token(pos, endPos, TokenType.CONSTANT))
                            pos = endPos
                        }

                        // Hex color
                        char == '#' && pos + 1 < line.length && line[pos + 1].isHexDigit() -> {
                            val endPos = readHexColor(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos
                        }

                        // SCSS variable
                        char == '$' -> {
                            val endPos = readCssIdentifier(line, pos + 1)
                            tokens.add(Token(pos, endPos, TokenType.VARIABLE))
                            pos = endPos
                        }

                        // Pseudo-class/element
                        char == ':' -> {
                            val isDouble = pos + 1 < line.length && line[pos + 1] == ':'
                            val nameStart = pos + (if (isDouble) 2 else 1)
                            val nameEnd = readCssIdentifier(line, nameStart)
                            if (nameEnd > nameStart) {
                                val name = line.substring(nameStart, nameEnd)
                                val tokenType = when {
                                    isDouble || name in PSEUDO_ELEMENTS -> TokenType.ANNOTATION
                                    name in PSEUDO_CLASSES -> TokenType.ANNOTATION
                                    else -> TokenType.PUNCTUATION
                                }
                                tokens.add(Token(pos, nameEnd, tokenType))
                                pos = nameEnd
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                                pos++
                            }
                        }

                        // String
                        char == '"' || char == '\'' -> {
                            val endPos = readCssString(line, pos, char)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Number with unit
                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) ||
                        (char == '-' && pos + 1 < line.length && (line[pos + 1].isDigit() || line[pos + 1] == '.')) -> {
                            val endPos = readCssNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Function or identifier
                        char.isLetter() || char == '-' || char == '_' -> {
                            val endPos = readCssIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos)

                            // Check if followed by (
                            val isFunction = endPos < line.length && line[endPos] == '('
                            val tokenType = when {
                                isFunction && identifier in FUNCTIONS -> TokenType.FUNCTION_CALL
                                isFunction -> TokenType.FUNCTION_CALL
                                identifier in KEYWORDS -> TokenType.KEYWORD
                                else -> TokenType.PROPERTY
                            }
                            tokens.add(Token(pos, endPos, tokenType))
                            pos = endPos
                        }

                        char == '{' || char == '}' || char == '[' || char == ']' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            pos++
                        }

                        char == '(' || char == ')' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PARENTHESIS))
                            pos++
                        }

                        char == ';' || char == ',' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                            pos++
                        }

                        char == '>' || char == '+' || char == '~' || char == '*' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.OPERATOR))
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

    private fun readCssIdentifier(line: String, start: Int): Int {
        var pos = start
        // CSS identifiers can start with - or _ or letter
        if (pos < line.length && (line[pos].isLetter() || line[pos] == '-' || line[pos] == '_')) {
            pos++
            while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '-' || line[pos] == '_')) {
                pos++
            }
        }
        return pos
    }

    private fun readCssString(line: String, start: Int, quote: Char): Int {
        var pos = start + 1
        while (pos < line.length) {
            when (line[pos]) {
                quote -> return pos + 1
                '\\' -> pos += 2
                else -> pos++
            }
        }
        return line.length
    }

    private fun readCssNumber(line: String, start: Int): Int {
        var pos = start
        // Optional minus
        if (pos < line.length && line[pos] == '-') pos++
        // Integer part
        while (pos < line.length && line[pos].isDigit()) pos++
        // Decimal part
        if (pos < line.length && line[pos] == '.') {
            pos++
            while (pos < line.length && line[pos].isDigit()) pos++
        }
        // Unit
        while (pos < line.length && (line[pos].isLetter() || line[pos] == '%')) pos++
        return pos
    }

    private fun readHexColor(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length && line[pos].isHexDigit()) pos++
        return pos
    }

    private fun Char.isHexDigit(): Boolean {
        return isDigit() || this in 'a'..'f' || this in 'A'..'F'
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        return when {
            identifier in KEYWORDS -> TokenType.KEYWORD
            identifier in FUNCTIONS -> TokenType.FUNCTION_CALL
            else -> TokenType.PROPERTY
        }
    }
}
