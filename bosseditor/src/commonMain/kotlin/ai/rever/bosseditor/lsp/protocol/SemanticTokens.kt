package ai.rever.bosseditor.lsp.protocol

import kotlinx.serialization.Serializable

/**
 * LSP Semantic Tokens types.
 *
 * Semantic tokens are used for semantic highlighting, providing richer
 * highlighting than lexical highlighting alone.
 *
 * Reference: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens
 */

/**
 * Parameters for a full semantic tokens request.
 */
@Serializable
data class SemanticTokensParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * An optional token that a server can use to report work done progress.
     */
    val workDoneToken: ProgressToken? = null,

    /**
     * An optional token that a server can use to report partial results.
     */
    val partialResultToken: ProgressToken? = null
)

/**
 * Parameters for a semantic tokens range request.
 */
@Serializable
data class SemanticTokensRangeParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The range the semantic tokens are requested for.
     */
    val range: Range,

    /**
     * An optional token that a server can use to report work done progress.
     */
    val workDoneToken: ProgressToken? = null,

    /**
     * An optional token that a server can use to report partial results.
     */
    val partialResultToken: ProgressToken? = null
)

/**
 * Parameters for a semantic tokens delta request.
 */
@Serializable
data class SemanticTokensDeltaParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The result id of a previous response. Used for incremental updates.
     */
    val previousResultId: String,

    /**
     * An optional token that a server can use to report work done progress.
     */
    val workDoneToken: ProgressToken? = null,

    /**
     * An optional token that a server can use to report partial results.
     */
    val partialResultToken: ProgressToken? = null
)

/**
 * Semantic tokens result.
 *
 * The data array contains semantic tokens encoded in a delta format:
 * [deltaLine, deltaStartChar, length, tokenType, tokenModifiers]
 *
 * For each token:
 * - deltaLine: Line delta from previous token (or 0 for first token)
 * - deltaStartChar: Start character delta (or absolute if deltaLine > 0)
 * - length: Length of the token
 * - tokenType: Index into the legend's tokenTypes array
 * - tokenModifiers: Bitmask of legend's tokenModifiers
 */
@Serializable
data class SemanticTokens(
    /**
     * An optional result id. If provided, clients can use it in subsequent
     * delta requests for incremental updates.
     */
    val resultId: String? = null,

    /**
     * The actual tokens. Each token is represented by 5 integers.
     */
    val data: List<Int>
)

/**
 * Semantic tokens delta result.
 */
@Serializable
data class SemanticTokensDelta(
    /**
     * An optional result id for subsequent delta requests.
     */
    val resultId: String? = null,

    /**
     * The semantic token edits to transform a previous result into a new result.
     */
    val edits: List<SemanticTokensEdit>
)

/**
 * A semantic tokens edit.
 */
@Serializable
data class SemanticTokensEdit(
    /**
     * The start offset of the edit.
     */
    val start: Int,

    /**
     * The count of elements to remove.
     */
    val deleteCount: Int,

    /**
     * The elements to insert.
     */
    val data: List<Int>? = null
)

/**
 * Standard LSP semantic token types.
 * These are the default token types defined in the LSP specification.
 */
object SemanticTokenTypes {
    const val NAMESPACE = "namespace"
    const val TYPE = "type"
    const val CLASS = "class"
    const val ENUM = "enum"
    const val INTERFACE = "interface"
    const val STRUCT = "struct"
    const val TYPE_PARAMETER = "typeParameter"
    const val PARAMETER = "parameter"
    const val VARIABLE = "variable"
    const val PROPERTY = "property"
    const val ENUM_MEMBER = "enumMember"
    const val EVENT = "event"
    const val FUNCTION = "function"
    const val METHOD = "method"
    const val MACRO = "macro"
    const val KEYWORD = "keyword"
    const val MODIFIER = "modifier"
    const val COMMENT = "comment"
    const val STRING = "string"
    const val NUMBER = "number"
    const val REGEXP = "regexp"
    const val OPERATOR = "operator"
    const val DECORATOR = "decorator"

    /**
     * All standard token types in order.
     * This order matches the LSP specification default legend.
     */
    val ALL = listOf(
        NAMESPACE, TYPE, CLASS, ENUM, INTERFACE, STRUCT,
        TYPE_PARAMETER, PARAMETER, VARIABLE, PROPERTY,
        ENUM_MEMBER, EVENT, FUNCTION, METHOD, MACRO,
        KEYWORD, MODIFIER, COMMENT, STRING, NUMBER,
        REGEXP, OPERATOR, DECORATOR
    )
}

/**
 * Standard LSP semantic token modifiers.
 * These modify the appearance of tokens (e.g., declaration vs reference).
 */
object SemanticTokenModifiers {
    const val DECLARATION = "declaration"
    const val DEFINITION = "definition"
    const val READONLY = "readonly"
    const val STATIC = "static"
    const val DEPRECATED = "deprecated"
    const val ABSTRACT = "abstract"
    const val ASYNC = "async"
    const val MODIFICATION = "modification"
    const val DOCUMENTATION = "documentation"
    const val DEFAULT_LIBRARY = "defaultLibrary"

    /**
     * All standard token modifiers in order.
     * The order determines the bit position in the modifier bitmask.
     */
    val ALL = listOf(
        DECLARATION, DEFINITION, READONLY, STATIC,
        DEPRECATED, ABSTRACT, ASYNC, MODIFICATION,
        DOCUMENTATION, DEFAULT_LIBRARY
    )
}

/**
 * A decoded semantic token with absolute positions.
 */
data class DecodedSemanticToken(
    /**
     * Line number (0-based).
     */
    val line: Int,

    /**
     * Start character (0-based).
     */
    val startChar: Int,

    /**
     * Token length.
     */
    val length: Int,

    /**
     * Token type name from the legend.
     */
    val tokenType: String,

    /**
     * Set of active token modifiers.
     */
    val modifiers: Set<String>
)

/**
 * Utility functions for decoding semantic tokens.
 */
object SemanticTokenDecoder {
    /**
     * Decode semantic tokens from the LSP delta-encoded format.
     *
     * @param data The encoded token data (5 integers per token)
     * @param legend The semantic tokens legend from the server
     * @return List of decoded tokens with absolute positions
     */
    fun decode(data: List<Int>, legend: SemanticTokensLegend): List<DecodedSemanticToken> {
        if (data.isEmpty()) return emptyList()

        val tokens = mutableListOf<DecodedSemanticToken>()
        var currentLine = 0
        var currentChar = 0

        var i = 0
        while (i + 4 < data.size) {
            val deltaLine = data[i]
            val deltaStartChar = data[i + 1]
            val length = data[i + 2]
            val tokenTypeIndex = data[i + 3]
            val tokenModifiersBits = data[i + 4]

            // Update position
            if (deltaLine > 0) {
                currentLine += deltaLine
                currentChar = deltaStartChar
            } else {
                currentChar += deltaStartChar
            }

            // Get token type
            val tokenType = if (tokenTypeIndex in legend.tokenTypes.indices) {
                legend.tokenTypes[tokenTypeIndex]
            } else {
                "unknown"
            }

            // Decode modifiers bitmask
            val modifiers = mutableSetOf<String>()
            var modBits = tokenModifiersBits
            var modIndex = 0
            while (modBits > 0 && modIndex < legend.tokenModifiers.size) {
                if (modBits and 1 == 1) {
                    modifiers.add(legend.tokenModifiers[modIndex])
                }
                modBits = modBits shr 1
                modIndex++
            }

            tokens.add(
                DecodedSemanticToken(
                    line = currentLine,
                    startChar = currentChar,
                    length = length,
                    tokenType = tokenType,
                    modifiers = modifiers
                )
            )

            i += 5
        }

        return tokens
    }

    /**
     * Get tokens for a specific line.
     */
    fun getTokensForLine(tokens: List<DecodedSemanticToken>, line: Int): List<DecodedSemanticToken> {
        return tokens.filter { it.line == line }
    }
}
