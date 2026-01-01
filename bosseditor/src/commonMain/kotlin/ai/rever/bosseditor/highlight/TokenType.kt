package ai.rever.bosseditor.highlight

/**
 * Comprehensive token types for syntax highlighting.
 *
 * These types are language-agnostic and map to colors in EditorColors.
 * Inspired by LSP SemanticTokenTypes and IntelliJ's token system.
 */
enum class TokenType {
    // Basic
    DEFAULT,
    WHITESPACE,

    // Keywords
    KEYWORD,              // if, for, while, return, etc.
    KEYWORD_MODIFIER,     // public, private, abstract, etc.
    KEYWORD_CONTROL,      // break, continue, throw

    // Identifiers
    IDENTIFIER,           // Generic identifier
    FUNCTION,             // Function/method name
    FUNCTION_CALL,        // Function call (may be different color)
    TYPE,                 // Type/class name
    TYPE_PARAMETER,       // Generic type parameter <T>
    INTERFACE,            // Interface name
    ENUM,                 // Enum name
    ENUM_MEMBER,          // Enum value

    // Variables
    VARIABLE,             // Generic variable
    PARAMETER,            // Function parameter
    PROPERTY,             // Class property/field
    LOCAL_VARIABLE,       // Local variable
    CONSTANT,             // Constant (val, const)

    // Literals
    STRING,               // "string"
    STRING_ESCAPE,        // \n, \t, etc.
    STRING_TEMPLATE,      // ${expression} in strings
    CHAR,                 // 'c'
    NUMBER,               // 123, 1.5, 0xFF
    BOOLEAN,              // true, false
    NULL,                 // null

    // Comments
    COMMENT,              // // single line
    COMMENT_BLOCK,        // /* multi-line */
    COMMENT_DOC,          // /** doc comment */
    COMMENT_DOC_TAG,      // @param, @return, etc.

    // Operators and punctuation
    OPERATOR,             // +, -, *, /, etc.
    OPERATOR_LOGICAL,     // &&, ||, !
    OPERATOR_COMPARISON,  // ==, !=, <, >
    PUNCTUATION,          // { } ( ) [ ] ; ,
    BRACKET,              // { } [ ]
    PARENTHESIS,          // ( )

    // Annotations
    ANNOTATION,           // @Annotation

    // Special (language-specific)
    PREPROCESSOR,         // #define, #include (C/C++)
    REGEX,                // /pattern/
    LABEL,                // label@

    // Markup/HTML
    MARKUP_TAG,           // <tag>
    MARKUP_ATTRIBUTE,     // attribute="value"
    MARKUP_ENTITY,        // &nbsp;

    // Semantic (from PSI/LSP)
    SEMANTIC_VARIABLE,    // PSI-identified variable
    SEMANTIC_PARAMETER,   // PSI-identified parameter
    SEMANTIC_PROPERTY,    // PSI-identified property
    SEMANTIC_FUNCTION,    // PSI-identified function

    // Errors
    ERROR,                // Syntax error
    ERROR_DEPRECATED,     // Deprecated usage

    // Special rendering
    TODO,                 // TODO comments
    FIXME,                // FIXME comments
    HYPERLINK,            // URLs in comments/strings

    // Diff/Patch
    INSERTION,            // Added lines (+)
    DELETION,             // Removed lines (-)
    MODIFICATION,         // Changed lines (context diff !)
    ESCAPE                // Escape sequences
}

/**
 * Token style modifiers (can be combined).
 */
enum class TokenModifier {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH
}

/**
 * A token with position and type information.
 *
 * @property startOffset Start offset within the line (0-indexed)
 * @property endOffset End offset within the line (exclusive)
 * @property type The token type for coloring
 * @property modifiers Optional style modifiers
 */
data class Token(
    val startOffset: Int,
    val endOffset: Int,
    val type: TokenType,
    val modifiers: Set<TokenModifier> = emptySet()
) {
    val length: Int get() = endOffset - startOffset

    /**
     * Checks if this token overlaps with a range.
     */
    fun overlaps(start: Int, end: Int): Boolean {
        return startOffset < end && endOffset > start
    }

    /**
     * Checks if this token contains an offset.
     */
    fun contains(offset: Int): Boolean {
        return offset in startOffset until endOffset
    }
}

/**
 * Result of tokenizing a line.
 *
 * @property tokens List of tokens on this line
 * @property endState State at end of line (for multi-line tokens)
 */
data class LineTokens(
    val tokens: List<Token>,
    val endState: LexerState = LexerState.NORMAL
)

/**
 * Lexer state for tracking multi-line constructs.
 */
enum class LexerState {
    NORMAL,
    IN_BLOCK_COMMENT,
    IN_DOC_COMMENT,
    IN_MULTILINE_STRING,
    IN_RAW_STRING        // Kotlin raw string """..."""
}
