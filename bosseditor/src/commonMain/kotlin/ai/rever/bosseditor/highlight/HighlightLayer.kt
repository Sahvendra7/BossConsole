package ai.rever.bosseditor.highlight

import ai.rever.bosseditor.core.EditorDocument

/**
 * Manages multiple layers of syntax highlighting that are merged for rendering.
 *
 * Layers (in priority order, highest first):
 * 1. Semantic tokens - From PSI/LSP analysis (most accurate)
 * 2. Lexer tokens - From language lexer (fast, always available)
 * 3. Default tokens - Fallback for unrecognized text
 *
 * The HighlightLayer coordinates between:
 * - TokenProvider (lexers) for basic syntax highlighting
 * - TokenCache for efficient per-line caching
 * - SemanticTokenProvider (optional) for PSI-based semantic highlighting
 *
 * ## Usage
 * ```kotlin
 * val highlighter = HighlightLayer(document)
 * highlighter.setLanguage("kotlin") // Or detect from file extension
 *
 * // Get merged tokens for a line
 * val tokens = highlighter.getLineTokens(lineNumber)
 * ```
 */
class HighlightLayer(
    private val document: EditorDocument
) {
    private var tokenProvider: TokenProvider = PlainTextTokenProvider
    private var tokenCache: TokenCache? = null
    private var semanticProvider: SemanticTokenProvider? = null

    /**
     * Sets the language for syntax highlighting.
     */
    fun setLanguage(languageId: String) {
        val provider = TokenProviderRegistry.getByLanguage(languageId) ?: PlainTextTokenProvider
        setTokenProvider(provider)
    }

    /**
     * Sets the language based on file extension.
     */
    fun setLanguageFromFile(filePath: String) {
        val provider = TokenProviderRegistry.getForFile(filePath) ?: PlainTextTokenProvider
        setTokenProvider(provider)
    }

    /**
     * Sets the token provider directly.
     */
    fun setTokenProvider(provider: TokenProvider) {
        tokenCache?.dispose()
        tokenProvider = provider
        tokenCache = TokenCache(document, provider)
    }

    /**
     * Sets an optional semantic token provider for PSI-based highlighting.
     */
    fun setSemanticProvider(provider: SemanticTokenProvider?) {
        semanticProvider = provider
    }

    /**
     * Gets merged tokens for a specific line.
     *
     * Merges lexer tokens with semantic tokens, where semantic tokens
     * take precedence for overlapping regions.
     */
    fun getLineTokens(lineNumber: Int): List<Token> {
        if (lineNumber < 0 || lineNumber >= document.lineCount) {
            return emptyList()
        }

        // Get lexer tokens (cached)
        val lexerTokens = tokenCache?.getLineTokens(lineNumber) ?: emptyList()

        // Get semantic tokens if available
        val semanticTokens = semanticProvider?.getLineTokens(lineNumber)

        // If no semantic tokens, return lexer tokens directly
        if (semanticTokens.isNullOrEmpty()) {
            return lexerTokens
        }

        // Merge tokens (semantic takes precedence)
        return mergeTokens(lexerTokens, semanticTokens)
    }

    /**
     * Merges two token lists, where overlay tokens take precedence.
     *
     * The algorithm:
     * 1. Start with base tokens
     * 2. For each overlay token, split/replace any overlapping base tokens
     * 3. Return sorted merged list
     */
    private fun mergeTokens(base: List<Token>, overlay: List<Token>): List<Token> {
        if (base.isEmpty()) return overlay
        if (overlay.isEmpty()) return base

        val result = mutableListOf<Token>()
        var baseIndex = 0
        var overlayIndex = 0

        while (baseIndex < base.size || overlayIndex < overlay.size) {
            // If no more overlay tokens, add remaining base tokens
            if (overlayIndex >= overlay.size) {
                result.addAll(base.subList(baseIndex, base.size))
                break
            }

            // If no more base tokens, add remaining overlay tokens
            if (baseIndex >= base.size) {
                result.addAll(overlay.subList(overlayIndex, overlay.size))
                break
            }

            val baseToken = base[baseIndex]
            val overlayToken = overlay[overlayIndex]

            when {
                // Base token comes completely before overlay - keep it
                baseToken.endOffset <= overlayToken.startOffset -> {
                    result.add(baseToken)
                    baseIndex++
                }

                // Overlay token comes completely before base - add it
                overlayToken.endOffset <= baseToken.startOffset -> {
                    result.add(overlayToken)
                    overlayIndex++
                }

                // Tokens overlap - overlay takes precedence
                else -> {
                    // Add part of base before overlay (if any)
                    if (baseToken.startOffset < overlayToken.startOffset) {
                        result.add(
                            Token(
                                baseToken.startOffset,
                                overlayToken.startOffset,
                                baseToken.type,
                                baseToken.modifiers
                            )
                        )
                    }

                    // Add overlay token
                    result.add(overlayToken)

                    // Handle remaining part of base token
                    if (baseToken.endOffset > overlayToken.endOffset) {
                        // Split: add remaining part after overlay
                        val remaining = Token(
                            overlayToken.endOffset,
                            baseToken.endOffset,
                            baseToken.type,
                            baseToken.modifiers
                        )
                        // Process remaining against next overlay tokens
                        // For simplicity, we'll just increment overlay and continue
                        overlayIndex++
                        // Check if remaining part overlaps with next overlay
                        if (overlayIndex < overlay.size &&
                            remaining.startOffset < overlay[overlayIndex].startOffset
                        ) {
                            // Add non-overlapping part
                            val nextOverlay = overlay[overlayIndex]
                            if (remaining.endOffset <= nextOverlay.startOffset) {
                                result.add(remaining)
                            } else {
                                result.add(
                                    Token(
                                        remaining.startOffset,
                                        nextOverlay.startOffset,
                                        remaining.type,
                                        remaining.modifiers
                                    )
                                )
                            }
                        } else if (overlayIndex >= overlay.size) {
                            result.add(remaining)
                        }
                        baseIndex++
                    } else {
                        // Base token completely covered by overlay
                        baseIndex++
                        // Check if we need to move overlay index
                        if (overlayToken.endOffset >= base.getOrNull(baseIndex)?.startOffset ?: Int.MAX_VALUE) {
                            // Overlay covers next base token too, keep processing
                        } else {
                            overlayIndex++
                        }
                    }
                }
            }
        }

        return result.sortedBy { it.startOffset }
    }

    /**
     * Invalidates highlighting for a range of lines.
     */
    fun invalidateLines(startLine: Int, endLine: Int) {
        tokenCache?.invalidateLines(startLine, endLine)
    }

    /**
     * Invalidates all highlighting.
     */
    fun invalidateAll() {
        tokenCache?.invalidateAll()
    }

    /**
     * Pre-tokenizes a range of lines (for background processing).
     */
    fun pretokenize(startLine: Int, endLine: Int) {
        tokenCache?.pretokenize(startLine, endLine)
    }

    /**
     * Gets the current language ID.
     */
    val languageId: String
        get() = tokenProvider.languageId

    /**
     * Gets cache statistics for debugging.
     */
    fun getCacheStats(): CacheStats? {
        return tokenCache?.getStats()
    }

    /**
     * Disposes resources.
     */
    fun dispose() {
        tokenCache?.dispose()
        tokenCache = null
    }
}

/**
 * Interface for semantic token providers (PSI/LSP-based).
 *
 * Implementations provide semantically-analyzed tokens that are more
 * accurate than lexer-based tokens (e.g., distinguishing local variables
 * from parameters, or resolved type references).
 */
interface SemanticTokenProvider {
    /**
     * Gets semantic tokens for a line.
     * Returns null if semantic analysis is not available for this line.
     */
    fun getLineTokens(lineNumber: Int): List<Token>?

    /**
     * Checks if semantic analysis is available.
     */
    fun isAvailable(): Boolean
}

/**
 * Creates a HighlightLayer with the appropriate language provider.
 */
fun createHighlightLayer(
    document: EditorDocument,
    filePath: String? = null
): HighlightLayer {
    val highlighter = HighlightLayer(document)

    // Set language from file path if available
    if (filePath != null) {
        highlighter.setLanguageFromFile(filePath)
    }

    return highlighter
}

/**
 * Initializes the default token providers.
 * Call this at application startup.
 */
fun initializeDefaultTokenProviders() {
    // Register built-in providers
    TokenProviderRegistry.register(PlainTextTokenProvider)

    // Note: KotlinLexer and other language lexers should be registered
    // by the application layer, not automatically here, to avoid
    // pulling in all languages for applications that don't need them.
}
