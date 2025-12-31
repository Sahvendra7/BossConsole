package ai.rever.bosseditor.highlight

import ai.rever.bosseditor.core.DocumentChange
import ai.rever.bosseditor.core.DocumentListener
import ai.rever.bosseditor.core.EditorDocument

/**
 * Caches tokenization results per line for efficient re-rendering.
 *
 * Features:
 * - Per-line token caching
 * - Incremental invalidation on document changes
 * - Multi-line state tracking (for block comments, strings)
 * - Thread-safe access
 *
 * The cache listens to document changes and invalidates affected lines.
 * Lines following a change are also invalidated if their start state might change.
 */
class TokenCache(
    private val document: EditorDocument,
    private val tokenProvider: TokenProvider
) : DocumentListener {

    /**
     * Cached entry for a single line.
     */
    private data class CacheEntry(
        val lineVersion: Long,
        val lineTokens: LineTokens
    )

    private val cache = mutableMapOf<Int, CacheEntry>()
    private val lineStates = mutableMapOf<Int, LexerState>() // Start state for each line
    private val lock = Any()

    // Document version when cache was last fully valid
    private var cacheVersion: Long = -1

    init {
        document.addDocumentListener(this)
    }

    /**
     * Gets tokens for a specific line.
     * Returns cached tokens if valid, otherwise tokenizes and caches.
     */
    fun getLineTokens(lineNumber: Int): List<Token> {
        if (lineNumber < 0 || lineNumber >= document.lineCount) {
            return emptyList()
        }

        synchronized(lock) {
            val currentVersion = document.documentVersion
            val entry = cache[lineNumber]

            // Check if cache entry is valid
            if (entry != null && entry.lineVersion == currentVersion) {
                return entry.lineTokens.tokens
            }

            // Need to tokenize this line
            val lineText = document.getLineText(lineNumber)
            val startState = getStartState(lineNumber)

            val result = tokenProvider.tokenizeLine(lineText, lineNumber, startState)

            // Cache the result
            cache[lineNumber] = CacheEntry(currentVersion, result)
            lineStates[lineNumber + 1] = result.endState

            return result.tokens
        }
    }

    /**
     * Gets the start state for a line.
     * This requires ensuring all previous lines are tokenized.
     */
    private fun getStartState(lineNumber: Int): LexerState {
        if (lineNumber == 0) {
            return tokenProvider.getDefaultState()
        }

        // Check if we have a cached state
        val cachedState = lineStates[lineNumber]
        if (cachedState != null) {
            return cachedState
        }

        // Need to compute state by tokenizing previous lines
        var state = tokenProvider.getDefaultState()
        for (line in 0 until lineNumber) {
            val entry = cache[line]
            if (entry != null && entry.lineVersion == document.documentVersion) {
                state = entry.lineTokens.endState
            } else {
                // Tokenize this line to get its end state
                val lineText = document.getLineText(line)
                val result = tokenProvider.tokenizeLine(lineText, line, state)
                cache[line] = CacheEntry(document.documentVersion, result)
                state = result.endState
            }
            lineStates[line + 1] = state
        }

        return state
    }

    /**
     * Invalidates the cache for a range of lines.
     */
    fun invalidateLines(startLine: Int, endLine: Int) {
        synchronized(lock) {
            for (line in startLine..endLine.coerceAtMost(document.lineCount - 1)) {
                cache.remove(line)
            }
            // Also clear states for lines after the changed region
            lineStates.keys.filter { it > startLine }.forEach { lineStates.remove(it) }
        }
    }

    /**
     * Invalidates the entire cache.
     */
    fun invalidateAll() {
        synchronized(lock) {
            cache.clear()
            lineStates.clear()
            cacheVersion = -1
        }
    }

    /**
     * Called when the document changes.
     * Invalidates affected lines and potentially following lines.
     */
    override fun documentChanged(change: DocumentChange) {
        synchronized(lock) {
            // Find affected line range
            val startPos = document.offsetToPosition(change.offset)
            val startLine = startPos.line

            // Calculate how many lines were affected
            val oldLineCount = change.oldText.count { it == '\n' }
            val newLineCount = change.newText.count { it == '\n' }
            val lineDelta = newLineCount - oldLineCount

            // Invalidate from the changed line to the end
            // (conservative - could be smarter about multi-line state changes)
            val endLine = if (lineDelta != 0 || oldLineCount > 0 || newLineCount > 0) {
                // Line structure changed - invalidate to end
                document.lineCount - 1
            } else {
                // Single line change
                startLine
            }

            invalidateLines(startLine, endLine)

            // Shift cache entries if lines were added/removed
            if (lineDelta != 0) {
                shiftCacheEntries(startLine, lineDelta)
            }
        }
    }

    /**
     * Shifts cache entries when lines are added or removed.
     */
    private fun shiftCacheEntries(fromLine: Int, delta: Int) {
        if (delta == 0) return

        val newCache = mutableMapOf<Int, CacheEntry>()
        val newStates = mutableMapOf<Int, LexerState>()

        // Copy entries before the change
        for ((line, entry) in cache) {
            if (line < fromLine) {
                newCache[line] = entry
            } else if (line >= fromLine && delta > 0) {
                // Lines shifted down
                newCache[line + delta] = entry
            }
            // Lines at or after fromLine are invalidated when delta < 0
        }

        // Copy states before the change
        for ((line, state) in lineStates) {
            if (line <= fromLine) {
                newStates[line] = state
            } else if (delta > 0) {
                newStates[line + delta] = state
            }
        }

        cache.clear()
        cache.putAll(newCache)
        lineStates.clear()
        lineStates.putAll(newStates)
    }

    /**
     * Pre-tokenizes a range of lines (for background processing).
     */
    fun pretokenize(startLine: Int, endLine: Int) {
        val end = endLine.coerceAtMost(document.lineCount - 1)
        for (line in startLine..end) {
            getLineTokens(line)
        }
    }

    /**
     * Returns cache statistics for debugging.
     */
    fun getStats(): CacheStats {
        synchronized(lock) {
            return CacheStats(
                cachedLines = cache.size,
                totalLines = document.lineCount,
                cacheVersion = cacheVersion
            )
        }
    }

    /**
     * Disposes the cache and removes the document listener.
     */
    fun dispose() {
        document.removeDocumentListener(this)
        invalidateAll()
    }
}

/**
 * Statistics about the token cache.
 */
data class CacheStats(
    val cachedLines: Int,
    val totalLines: Int,
    val cacheVersion: Long
) {
    val hitRate: Float
        get() = if (totalLines > 0) cachedLines.toFloat() / totalLines else 0f
}
