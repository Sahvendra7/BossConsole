package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.highlight.SemanticTokenProvider
import ai.rever.bosseditor.highlight.Token
import ai.rever.bosseditor.lsp.protocol.DecodedSemanticToken
import ai.rever.bosseditor.lsp.protocol.SemanticTokenDecoder
import ai.rever.bosseditor.lsp.protocol.SemanticTokensLegend

/**
 * Semantic token provider that uses LSP semantic tokens.
 *
 * This provider bridges the LSP semantic tokens to BossEditor's
 * SemanticTokenProvider interface, allowing LSP-based highlighting
 * to integrate seamlessly with the existing highlighting system.
 *
 * The provider maintains a cache of decoded tokens organized by line
 * for efficient line-by-line access required by the editor.
 *
 * **Thread Safety**:
 * - Token updates should be called from a single thread (typically IO dispatcher)
 * - Token reads (getLineTokens) can be called from any thread
 * - Internal synchronization ensures thread-safe access
 *
 * **Note**: This class uses JVM-specific `synchronized()` for thread safety.
 * While placed in commonMain for code organization, this is desktop-only
 * as BOSS targets only desktop platforms (macOS, Windows, Linux).
 */
class LspSemanticTokenProvider : SemanticTokenProvider {

    /**
     * Decoded semantic tokens from the LSP server, organized by line number.
     * Key: line number (0-based)
     * Value: List of tokens on that line, sorted by start character
     */
    private val tokensByLine = mutableMapOf<Int, List<Token>>()

    /**
     * Lock for thread-safe access to tokens.
     */
    private val lock = Any()

    /**
     * Whether semantic tokens are currently available.
     */
    @Volatile
    private var available = false

    /**
     * The current semantic tokens legend from the server.
     */
    private var legend: SemanticTokensLegend? = null

    /**
     * Version of the last processed tokens (for delta updates).
     */
    private var lastResultId: String? = null

    /**
     * Total line count in the document (for bounds checking).
     */
    private var lineCount = 0

    override fun getLineTokens(lineNumber: Int): List<Token>? {
        if (!available) return null

        synchronized(lock) {
            return tokensByLine[lineNumber]
        }
    }

    override fun isAvailable(): Boolean = available

    /**
     * Set the semantic tokens legend from the server capabilities.
     * Must be called before processing any tokens.
     *
     * @param legend The semantic tokens legend defining token types and modifiers
     */
    fun setLegend(legend: SemanticTokensLegend) {
        synchronized(lock) {
            this.legend = legend
        }
    }

    /**
     * Update the total line count of the document.
     * Used for bounds checking.
     *
     * @param count The total number of lines in the document
     */
    fun setLineCount(count: Int) {
        this.lineCount = count
    }

    /**
     * Update semantic tokens from raw LSP data.
     *
     * @param data The encoded token data from the LSP server
     * @param resultId Optional result ID for delta updates
     */
    fun updateTokens(data: List<Int>, resultId: String? = null) {
        val currentLegend = legend ?: return

        val decodedTokens = SemanticTokenDecoder.decode(data, currentLegend)
        processDecodedTokens(decodedTokens)

        lastResultId = resultId
        available = true
    }

    /**
     * Apply delta updates to existing tokens.
     *
     * @param edits List of edits to apply (line ranges to replace)
     * @param resultId New result ID after applying deltas
     */
    fun applyDelta(edits: List<SemanticTokenEdit>, resultId: String?) {
        // Delta updates are complex and require the full edit list
        // For simplicity, we'll re-request full tokens on edit
        // This can be optimized later if needed
        lastResultId = resultId
    }

    /**
     * Process decoded semantic tokens and organize by line.
     */
    private fun processDecodedTokens(decodedTokens: List<DecodedSemanticToken>) {
        val newTokensByLine = mutableMapOf<Int, MutableList<Token>>()

        for (decoded in decodedTokens) {
            val editorToken = convertToEditorToken(decoded)

            newTokensByLine
                .getOrPut(decoded.line) { mutableListOf() }
                .add(editorToken)
        }

        // Sort tokens within each line by start position
        for ((_, tokens) in newTokensByLine) {
            tokens.sortBy { it.startOffset }
        }

        synchronized(lock) {
            tokensByLine.clear()
            tokensByLine.putAll(newTokensByLine)
        }
    }

    /**
     * Convert an LSP DecodedSemanticToken to a BossEditor Token.
     */
    private fun convertToEditorToken(decoded: DecodedSemanticToken): Token {
        val type = TokenTypeMapper.mapLspTypeWithModifiers(decoded.tokenType, decoded.modifiers)
        val modifiers = TokenTypeMapper.mapLspModifiers(decoded.modifiers)

        return Token(
            startOffset = decoded.startChar,
            endOffset = decoded.startChar + decoded.length,
            type = type,
            modifiers = modifiers
        )
    }

    /**
     * Clear all cached tokens.
     * Call this when a document is closed or the connection is lost.
     */
    fun clear() {
        synchronized(lock) {
            tokensByLine.clear()
            available = false
            lastResultId = null
        }
    }

    /**
     * Handle document change by invalidating affected lines.
     *
     * For incremental changes, we can invalidate just the affected range.
     * For full document changes, we clear everything.
     *
     * @param startLine First line affected by the change (0-based)
     * @param endLine Last line affected by the change (0-based, inclusive)
     * @param lineDelta Number of lines added (positive) or removed (negative)
     */
    fun invalidateLines(startLine: Int, endLine: Int, lineDelta: Int = 0) {
        synchronized(lock) {
            if (lineDelta == 0) {
                // Simple invalidation - just remove affected lines
                for (line in startLine..endLine) {
                    tokensByLine.remove(line)
                }
            } else {
                // Lines shifted - need to rebuild the map
                val newTokensByLine = mutableMapOf<Int, List<Token>>()

                for ((line, tokens) in tokensByLine) {
                    when {
                        line < startLine -> {
                            // Before change - keep as is
                            newTokensByLine[line] = tokens
                        }
                        line in startLine..endLine -> {
                            // In change range - discard (will be updated by LSP)
                        }
                        else -> {
                            // After change - shift by delta
                            val newLine = line + lineDelta
                            if (newLine >= 0) {
                                newTokensByLine[newLine] = tokens
                            }
                        }
                    }
                }

                tokensByLine.clear()
                tokensByLine.putAll(newTokensByLine)
            }
        }
    }

    /**
     * Get the last result ID for delta requests.
     */
    fun getLastResultId(): String? = lastResultId

    /**
     * Check if delta updates are supported (have a previous result ID).
     */
    fun supportsDelta(): Boolean = lastResultId != null

    /**
     * Get all tokens for a range of lines.
     * Useful for rendering a viewport.
     *
     * @param startLine First line (0-based, inclusive)
     * @param endLine Last line (0-based, inclusive)
     * @return Map of line number to tokens
     */
    fun getTokensInRange(startLine: Int, endLine: Int): Map<Int, List<Token>> {
        if (!available) return emptyMap()

        synchronized(lock) {
            return (startLine..endLine).mapNotNull { line ->
                tokensByLine[line]?.let { line to it }
            }.toMap()
        }
    }
}

/**
 * Represents a semantic token edit for delta updates.
 */
data class SemanticTokenEdit(
    val start: Int,
    val deleteCount: Int,
    val data: List<Int>? = null
)
