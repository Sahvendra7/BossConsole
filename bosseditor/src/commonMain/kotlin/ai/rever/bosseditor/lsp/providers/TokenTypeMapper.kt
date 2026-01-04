package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.highlight.TokenModifier
import ai.rever.bosseditor.highlight.TokenType
import ai.rever.bosseditor.lsp.protocol.SemanticTokenModifiers
import ai.rever.bosseditor.lsp.protocol.SemanticTokenTypes

/**
 * Maps LSP semantic token types and modifiers to BossEditor's TokenType and TokenModifier.
 *
 * This mapper provides bidirectional conversion between the LSP specification's
 * semantic token types and the BossEditor's internal highlighting system.
 */
object TokenTypeMapper {

    /**
     * Map from LSP semantic token type string to BossEditor TokenType.
     */
    private val lspToEditorType: Map<String, TokenType> = mapOf(
        // Namespaces/Modules
        SemanticTokenTypes.NAMESPACE to TokenType.IDENTIFIER,

        // Types
        SemanticTokenTypes.TYPE to TokenType.TYPE,
        SemanticTokenTypes.CLASS to TokenType.TYPE,
        SemanticTokenTypes.ENUM to TokenType.ENUM,
        SemanticTokenTypes.INTERFACE to TokenType.INTERFACE,
        SemanticTokenTypes.STRUCT to TokenType.TYPE,
        SemanticTokenTypes.TYPE_PARAMETER to TokenType.TYPE_PARAMETER,

        // Variables and parameters
        SemanticTokenTypes.PARAMETER to TokenType.PARAMETER,
        SemanticTokenTypes.VARIABLE to TokenType.VARIABLE,
        SemanticTokenTypes.PROPERTY to TokenType.PROPERTY,
        SemanticTokenTypes.ENUM_MEMBER to TokenType.ENUM_MEMBER,

        // Functions and methods
        SemanticTokenTypes.EVENT to TokenType.FUNCTION,
        SemanticTokenTypes.FUNCTION to TokenType.FUNCTION,
        SemanticTokenTypes.METHOD to TokenType.FUNCTION,
        SemanticTokenTypes.MACRO to TokenType.PREPROCESSOR,

        // Keywords and modifiers
        SemanticTokenTypes.KEYWORD to TokenType.KEYWORD,
        SemanticTokenTypes.MODIFIER to TokenType.KEYWORD_MODIFIER,

        // Literals
        SemanticTokenTypes.COMMENT to TokenType.COMMENT,
        SemanticTokenTypes.STRING to TokenType.STRING,
        SemanticTokenTypes.NUMBER to TokenType.NUMBER,
        SemanticTokenTypes.REGEXP to TokenType.REGEX,

        // Operators and decorators
        SemanticTokenTypes.OPERATOR to TokenType.OPERATOR,
        SemanticTokenTypes.DECORATOR to TokenType.ANNOTATION
    )

    /**
     * Extended mappings for common language server custom token types.
     * Language servers often define additional token types beyond the LSP spec.
     */
    private val extendedMappings: Map<String, TokenType> = mapOf(
        // Common extensions from various language servers
        "label" to TokenType.LABEL,
        "boolean" to TokenType.BOOLEAN,
        "character" to TokenType.CHAR,
        "escapeSequence" to TokenType.STRING_ESCAPE,
        "formatSpecifier" to TokenType.STRING_TEMPLATE,
        "selfKeyword" to TokenType.KEYWORD,
        "selfParameter" to TokenType.PARAMETER,
        "builtinType" to TokenType.TYPE,
        "builtinConstant" to TokenType.CONSTANT,
        "lifetime" to TokenType.TYPE_PARAMETER, // Rust lifetimes
        "attribute" to TokenType.ANNOTATION,
        "attributeBracket" to TokenType.ANNOTATION,
        "derive" to TokenType.ANNOTATION,
        "deriveHelper" to TokenType.ANNOTATION,
        "generic" to TokenType.TYPE_PARAMETER,
        "angle" to TokenType.PUNCTUATION,
        "arithmetic" to TokenType.OPERATOR,
        "bitwise" to TokenType.OPERATOR,
        "comparison" to TokenType.OPERATOR_COMPARISON,
        "logical" to TokenType.OPERATOR_LOGICAL,
        "brace" to TokenType.BRACKET,
        "bracket" to TokenType.BRACKET,
        "parenthesis" to TokenType.PARENTHESIS,
        "colon" to TokenType.PUNCTUATION,
        "comma" to TokenType.PUNCTUATION,
        "dot" to TokenType.PUNCTUATION,
        "semicolon" to TokenType.PUNCTUATION,
        "unresolvedReference" to TokenType.ERROR,
        "typeAlias" to TokenType.TYPE,
        "union" to TokenType.TYPE,
        "constParameter" to TokenType.CONSTANT
    )

    /**
     * Combined mappings (LSP standard + extensions).
     */
    private val allMappings: Map<String, TokenType> = lspToEditorType + extendedMappings

    /**
     * Map an LSP semantic token type to BossEditor TokenType.
     *
     * @param lspType The LSP semantic token type string (e.g., "function", "variable")
     * @return The corresponding BossEditor TokenType, or TokenType.DEFAULT if unknown
     */
    fun mapLspType(lspType: String): TokenType {
        return allMappings[lspType] ?: TokenType.DEFAULT
    }

    /**
     * Map an LSP semantic token type to BossEditor TokenType with modifier awareness.
     *
     * Some token types should be mapped differently based on modifiers.
     * For example, a variable with the "readonly" modifier might be a CONSTANT.
     *
     * @param lspType The LSP semantic token type string
     * @param modifiers Set of LSP modifier strings for this token
     * @return The corresponding BossEditor TokenType
     */
    fun mapLspTypeWithModifiers(lspType: String, modifiers: Set<String>): TokenType {
        // Special handling based on modifiers
        return when {
            // Variables with readonly modifier become constants
            lspType == SemanticTokenTypes.VARIABLE &&
                SemanticTokenModifiers.READONLY in modifiers -> TokenType.CONSTANT

            // Properties with readonly modifier become constants
            lspType == SemanticTokenTypes.PROPERTY &&
                SemanticTokenModifiers.READONLY in modifiers -> TokenType.CONSTANT

            // Deprecated symbols get special styling
            SemanticTokenModifiers.DEPRECATED in modifiers -> {
                // Return base type but caller should add strikethrough
                allMappings[lspType] ?: TokenType.DEFAULT
            }

            // Static members might be styled differently in some themes
            // but we'll use the base type for now
            else -> allMappings[lspType] ?: TokenType.DEFAULT
        }
    }

    /**
     * Map LSP semantic token modifiers to BossEditor TokenModifiers.
     *
     * LSP modifiers describe semantic properties (declaration, readonly, deprecated),
     * while BossEditor modifiers describe visual styling (bold, italic, underline).
     *
     * @param lspModifiers Set of LSP modifier strings
     * @return Set of BossEditor TokenModifiers for visual styling
     */
    fun mapLspModifiers(lspModifiers: Set<String>): Set<TokenModifier> {
        val result = mutableSetOf<TokenModifier>()

        // Map semantic modifiers to visual styling
        if (SemanticTokenModifiers.DEPRECATED in lspModifiers) {
            result.add(TokenModifier.STRIKETHROUGH)
        }

        if (SemanticTokenModifiers.READONLY in lspModifiers ||
            SemanticTokenModifiers.STATIC in lspModifiers) {
            result.add(TokenModifier.ITALIC)
        }

        if (SemanticTokenModifiers.DECLARATION in lspModifiers ||
            SemanticTokenModifiers.DEFINITION in lspModifiers) {
            result.add(TokenModifier.BOLD)
        }

        return result
    }

    /**
     * Check if a token type should be considered semantic (from PSI/LSP)
     * rather than syntactic (from lexer).
     *
     * Semantic tokens provide richer information than lexer tokens
     * and should take precedence in highlighting.
     *
     * @param tokenType The BossEditor TokenType to check
     * @return true if this is a semantic token type
     */
    fun isSemanticType(tokenType: TokenType): Boolean {
        return tokenType in setOf(
            TokenType.SEMANTIC_VARIABLE,
            TokenType.SEMANTIC_PARAMETER,
            TokenType.SEMANTIC_PROPERTY,
            TokenType.SEMANTIC_FUNCTION,
            TokenType.FUNCTION,
            TokenType.FUNCTION_CALL,
            TokenType.TYPE,
            TokenType.TYPE_PARAMETER,
            TokenType.INTERFACE,
            TokenType.ENUM,
            TokenType.ENUM_MEMBER,
            TokenType.PARAMETER,
            TokenType.VARIABLE,
            TokenType.PROPERTY,
            TokenType.LOCAL_VARIABLE,
            TokenType.CONSTANT
        )
    }

    /**
     * Convert a BossEditor TokenType back to LSP semantic token type.
     * Useful for testing or for sending custom tokens to language servers.
     *
     * @param editorType The BossEditor TokenType
     * @return The corresponding LSP semantic token type string, or null if no mapping
     */
    fun toXLspType(editorType: TokenType): String? {
        return when (editorType) {
            TokenType.IDENTIFIER -> SemanticTokenTypes.NAMESPACE
            TokenType.TYPE -> SemanticTokenTypes.TYPE
            TokenType.ENUM -> SemanticTokenTypes.ENUM
            TokenType.INTERFACE -> SemanticTokenTypes.INTERFACE
            TokenType.TYPE_PARAMETER -> SemanticTokenTypes.TYPE_PARAMETER
            TokenType.PARAMETER -> SemanticTokenTypes.PARAMETER
            TokenType.VARIABLE, TokenType.LOCAL_VARIABLE, TokenType.SEMANTIC_VARIABLE -> SemanticTokenTypes.VARIABLE
            TokenType.PROPERTY, TokenType.SEMANTIC_PROPERTY -> SemanticTokenTypes.PROPERTY
            TokenType.ENUM_MEMBER -> SemanticTokenTypes.ENUM_MEMBER
            TokenType.FUNCTION, TokenType.FUNCTION_CALL, TokenType.SEMANTIC_FUNCTION -> SemanticTokenTypes.FUNCTION
            TokenType.PREPROCESSOR -> SemanticTokenTypes.MACRO
            TokenType.KEYWORD -> SemanticTokenTypes.KEYWORD
            TokenType.KEYWORD_MODIFIER -> SemanticTokenTypes.MODIFIER
            TokenType.COMMENT, TokenType.COMMENT_BLOCK, TokenType.COMMENT_DOC -> SemanticTokenTypes.COMMENT
            TokenType.STRING -> SemanticTokenTypes.STRING
            TokenType.NUMBER -> SemanticTokenTypes.NUMBER
            TokenType.REGEX -> SemanticTokenTypes.REGEXP
            TokenType.OPERATOR, TokenType.OPERATOR_LOGICAL, TokenType.OPERATOR_COMPARISON -> SemanticTokenTypes.OPERATOR
            TokenType.ANNOTATION -> SemanticTokenTypes.DECORATOR
            else -> null
        }
    }
}
