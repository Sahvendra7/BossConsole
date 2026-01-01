package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Swift syntax highlighting lexer.
 */
class SwiftLexer : BaseLexer() {

    override val languageId: String = "swift"
    override val fileExtensions: List<String> = listOf("swift")

    companion object {
        private val KEYWORDS = setOf(
            // Declarations
            "associatedtype", "class", "deinit", "enum", "extension", "fileprivate",
            "func", "import", "init", "inout", "internal", "let", "open", "operator",
            "private", "precedencegroup", "protocol", "public", "rethrows", "static",
            "struct", "subscript", "typealias", "var", "actor", "macro", "nonisolated",
            // Statements
            "break", "case", "catch", "continue", "default", "defer", "do", "else",
            "fallthrough", "for", "guard", "if", "in", "repeat", "return", "switch",
            "throw", "throws", "try", "where", "while",
            // Expressions
            "as", "Any", "await", "catch", "false", "is", "nil", "self", "Self",
            "super", "throw", "throws", "true", "try", "async",
            // Attributes
            "willSet", "didSet", "get", "set", "mutating", "nonmutating",
            "convenience", "dynamic", "final", "indirect", "lazy", "optional",
            "override", "required", "unowned", "weak", "some", "any"
        )

        private val TYPES = setOf(
            "Bool", "String", "Int", "Int8", "Int16", "Int32", "Int64",
            "UInt", "UInt8", "UInt16", "UInt32", "UInt64",
            "Float", "Double", "Character", "Void", "Never",
            "Array", "Dictionary", "Set", "Optional", "Result",
            "Error", "Codable", "Encodable", "Decodable", "Hashable",
            "Equatable", "Comparable", "Identifiable", "CustomStringConvertible",
            "AnyObject", "AnyClass", "AnyHashable"
        )

        private val ATTRIBUTES = setOf(
            "available", "discardableResult", "dynamicCallable", "dynamicMemberLookup",
            "escaping", "frozen", "GKInspectable", "IBAction", "IBDesignable",
            "IBInspectable", "IBOutlet", "IBSegueAction", "inlinable", "main",
            "nonobjc", "NSApplicationMain", "NSCopying", "NSManaged", "objc",
            "objcMembers", "propertyWrapper", "requires_stored_property_inits",
            "resultBuilder", "testable", "UIApplicationMain", "unchecked",
            "unknown", "usableFromInline", "warn_unqualified_access", "Sendable"
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
                    val (endPos, complete) = readNestedBlockComment(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.IN_MULTILINE_STRING -> {
                    val (endPos, stringTokens, complete) = continueMultilineString(line, pos)
                    tokens.addAll(stringTokens)
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        matchesAt(line, pos, "//") -> {
                            val tokenType = if (matchesAt(line, pos, "///")) TokenType.COMMENT_DOC else TokenType.COMMENT
                            tokens.add(Token(pos, line.length, tokenType))
                            pos = line.length
                        }

                        matchesAt(line, pos, "/*") -> {
                            val (endPos, complete) = readNestedBlockComment(line, pos + 2)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                            pos = endPos
                            if (!complete) state = LexerState.IN_BLOCK_COMMENT
                        }

                        // Multi-line string
                        matchesAt(line, pos, "\"\"\"") -> {
                            val (endPos, stringTokens, complete) = tokenizeMultilineString(line, pos)
                            tokens.addAll(stringTokens)
                            pos = endPos
                            if (!complete) state = LexerState.IN_MULTILINE_STRING
                        }

                        char == '"' -> {
                            val (stringTokens, endPos) = tokenizeSwiftString(line, pos)
                            tokens.addAll(stringTokens)
                            pos = endPos
                        }

                        // Attribute
                        char == '@' -> {
                            val nameEnd = readIdentifier(line, pos + 1)
                            if (nameEnd > pos + 1) {
                                tokens.add(Token(pos, nameEnd, TokenType.ANNOTATION))
                                pos = nameEnd
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                                pos++
                            }
                        }

                        // Directive
                        char == '#' -> {
                            val endPos = readIdentifier(line, pos + 1)
                            if (endPos > pos + 1) {
                                tokens.add(Token(pos, endPos, TokenType.KEYWORD))
                                pos = endPos
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                                pos++
                            }
                        }

                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        isIdentifierStart(char) || char == '`' -> {
                            val endPos = if (char == '`') readBacktickIdentifier(line, pos) else readIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos).trim('`')
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
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "nil" -> TokenType.NULL
            identifier in TYPES -> TokenType.TYPE
            identifier in ATTRIBUTES -> TokenType.ANNOTATION
            identifier.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    private fun tokenizeSwiftString(line: String, start: Int): Pair<List<Token>, Int> {
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
                    if (line[pos + 1] == '(') {
                        // String interpolation
                        if (tokenStart < pos) {
                            tokens.add(Token(tokenStart, pos, TokenType.STRING))
                        }
                        val exprEnd = findMatchingParen(line, pos + 2)
                        tokens.add(Token(pos, exprEnd, TokenType.STRING_TEMPLATE))
                        pos = exprEnd
                        tokenStart = pos
                    } else {
                        pos += 2
                    }
                }
                else -> pos++
            }
        }

        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }
        return tokens to line.length
    }

    private fun tokenizeMultilineString(line: String, start: Int): Triple<Int, List<Token>, Boolean> {
        val tokens = mutableListOf<Token>()
        var pos = start + 3
        var tokenStart = start

        while (pos + 2 < line.length) {
            if (line[pos] == '"' && line[pos + 1] == '"' && line[pos + 2] == '"') {
                tokens.add(Token(tokenStart, pos + 3, TokenType.STRING))
                return Triple(pos + 3, tokens, true)
            }
            if (line[pos] == '\\' && pos + 1 < line.length && line[pos + 1] == '(') {
                if (tokenStart < pos) {
                    tokens.add(Token(tokenStart, pos, TokenType.STRING))
                }
                val exprEnd = findMatchingParen(line, pos + 2)
                tokens.add(Token(pos, exprEnd, TokenType.STRING_TEMPLATE))
                pos = exprEnd
                tokenStart = pos
            } else {
                pos++
            }
        }

        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }
        return Triple(line.length, tokens, false)
    }

    private fun continueMultilineString(line: String, start: Int): Triple<Int, List<Token>, Boolean> {
        return tokenizeMultilineString(line, start - 3)
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

    private fun readBacktickIdentifier(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length && line[pos] != '`') pos++
        if (pos < line.length) pos++
        return pos
    }

    private fun readNestedBlockComment(line: String, start: Int): Pair<Int, Boolean> {
        var pos = start
        var depth = 1
        while (pos + 1 < line.length && depth > 0) {
            if (line[pos] == '/' && line[pos + 1] == '*') {
                depth++
                pos += 2
            } else if (line[pos] == '*' && line[pos + 1] == '/') {
                depth--
                pos += 2
            } else {
                pos++
            }
        }
        return if (depth == 0) pos to true else line.length to false
    }

    private fun isOperator(char: Char): Boolean {
        return char in setOf('+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '?', ':', '.')
    }

    private fun readOperator(line: String, pos: Int): Int {
        val threeChar = listOf("...", "..<", "===", "!==")
        val twoChar = listOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", "??", "->", "..")

        for (op in threeChar) {
            if (matchesAt(line, pos, op)) return 3
        }
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
