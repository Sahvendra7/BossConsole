package ai.rever.bosseditor.highlight

/**
 * Interface for language-specific tokenizers (lexers).
 *
 * Implementations provide syntax highlighting tokens for their language.
 * The tokenization is line-based with state tracking for multi-line constructs.
 *
 * ## Implementation Guidelines
 * - Return tokens in order (by startOffset)
 * - Tokens should not overlap
 * - Handle multi-line constructs via startState/endState
 * - Return empty list for blank lines
 */
interface TokenProvider {

    /**
     * The language ID this provider handles.
     * Examples: "kotlin", "java", "python", "javascript"
     */
    val languageId: String

    /**
     * File extensions this provider handles.
     * Examples: listOf("kt", "kts") for Kotlin
     */
    val fileExtensions: List<String>

    /**
     * Tokenizes a single line of text.
     *
     * @param line The line text (without line terminator)
     * @param lineNumber The 0-indexed line number
     * @param startState The lexer state at start of line (from previous line's endState)
     * @return Tokens for this line plus the end state
     */
    fun tokenizeLine(
        line: String,
        lineNumber: Int,
        startState: LexerState = LexerState.NORMAL
    ): LineTokens

    /**
     * Returns the default start state for this language.
     */
    fun getDefaultState(): LexerState = LexerState.NORMAL

    /**
     * Checks if this provider can handle the given file.
     *
     * @param filePath File path or name
     * @return true if this provider should be used
     */
    fun canHandle(filePath: String): Boolean {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return extension in fileExtensions
    }
}

/**
 * Registry for token providers.
 *
 * Maintains a collection of language-specific lexers and provides
 * lookup by language ID or file extension.
 */
object TokenProviderRegistry {

    private val providers = mutableMapOf<String, TokenProvider>()
    private val extensionMap = mutableMapOf<String, TokenProvider>()

    /**
     * Registers a token provider.
     */
    fun register(provider: TokenProvider) {
        providers[provider.languageId] = provider
        provider.fileExtensions.forEach { ext ->
            extensionMap[ext.lowercase()] = provider
        }
    }

    /**
     * Gets a provider by language ID.
     */
    fun getByLanguage(languageId: String): TokenProvider? {
        return providers[languageId]
    }

    /**
     * Gets a provider for a file path.
     */
    fun getForFile(filePath: String): TokenProvider? {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return extensionMap[extension]
    }

    /**
     * Returns all registered language IDs.
     */
    fun getLanguages(): Set<String> = providers.keys.toSet()

    /**
     * Returns all registered providers.
     */
    fun getAllProviders(): Collection<TokenProvider> = providers.values

    /**
     * Clears all registered providers.
     */
    fun clear() {
        providers.clear()
        extensionMap.clear()
    }
}

/**
 * A simple token provider that returns no tokens (plain text).
 */
object PlainTextTokenProvider : TokenProvider {
    override val languageId: String = "plaintext"
    override val fileExtensions: List<String> = listOf("txt", "text")

    override fun tokenizeLine(
        line: String,
        lineNumber: Int,
        startState: LexerState
    ): LineTokens {
        return LineTokens(emptyList(), LexerState.NORMAL)
    }
}
