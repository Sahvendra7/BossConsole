package ai.rever.bosseditor.largefile

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Search result in a large file.
 *
 * @property range The range in the document where the match was found
 * @property lineText The full text of the line containing the match
 * @property matchText The actual matched text
 */
data class LargeFileSearchResult(
    val range: EditorRange,
    val lineText: String,
    val matchText: String
)

/**
 * Progressive searcher for large files.
 *
 * Searches page by page, emitting results as they're found.
 * This allows the UI to display partial results while the search
 * is still in progress.
 *
 * Features:
 * - Progressive search with intermediate results
 * - Progress reporting (0.0 to 1.0)
 * - Cancellation support
 * - Case-insensitive and case-sensitive search
 * - Regex escaping for literal pattern matching
 *
 * Usage:
 * ```kotlin
 * val searcher = PagedSearcher(document)
 * searcher.search("pattern", ignoreCase = true, scope = coroutineScope)
 *
 * // Observe results as they're found
 * searcher.results.collect { results ->
 *     updateUI(results)
 * }
 *
 * // Observe progress
 * searcher.progress.collect { progress ->
 *     updateProgressBar(progress)
 * }
 * ```
 */
class PagedSearcher(
    private val document: LargeFileDocument,
    private val maxResults: Int = MAX_SEARCH_RESULTS
) {
    companion object {
        /**
         * Maximum number of search results to store.
         * Prevents memory issues with extremely large files containing many matches.
         */
        const val MAX_SEARCH_RESULTS = 10_000
    }

    private val _results = MutableStateFlow<List<LargeFileSearchResult>>(emptyList())
    val results: StateFlow<List<LargeFileSearchResult>> = _results.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _currentResultIndex = MutableStateFlow(-1)
    val currentResultIndex: StateFlow<Int> = _currentResultIndex.asStateFlow()

    private val _hitMaxResults = MutableStateFlow(false)
    val hitMaxResults: StateFlow<Boolean> = _hitMaxResults.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Start a search operation.
     *
     * Cancels any in-progress search before starting a new one.
     * Results are emitted progressively as they are found.
     *
     * @param pattern The search pattern (will be regex-escaped for literal matching)
     * @param ignoreCase Whether to perform case-insensitive search
     * @param scope The coroutine scope to run the search in
     * @return A Job that can be used to track or cancel the search
     */
    fun search(
        pattern: String,
        ignoreCase: Boolean = true,
        scope: CoroutineScope
    ): Job {
        // Cancel previous search
        searchJob?.cancel()
        _results.value = emptyList()
        _progress.value = 0f
        _currentResultIndex.value = -1
        _hitMaxResults.value = false

        if (pattern.isEmpty()) {
            return Job().also { it.complete() }
        }

        searchJob = scope.launch(Dispatchers.Default) {
            _isSearching.value = true
            try {
                val totalLines = document.lineCount
                val foundResults = mutableListOf<LargeFileSearchResult>()
                val regex = if (ignoreCase) {
                    Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
                } else {
                    Regex(Regex.escape(pattern))
                }

                for (line in 0 until totalLines) {
                    if (!isActive) break

                    // Check if we've hit the max results limit
                    if (foundResults.size >= maxResults) {
                        _hitMaxResults.value = true
                        break
                    }

                    val lineText = try {
                        document.getLineText(line)
                    } catch (e: Exception) {
                        // Skip lines that fail to read
                        continue
                    }

                    regex.findAll(lineText).forEach { match ->
                        if (foundResults.size < maxResults) {
                            val result = LargeFileSearchResult(
                                range = EditorRange(
                                    start = EditorPosition(line, match.range.first),
                                    end = EditorPosition(line, match.range.last + 1)
                                ),
                                lineText = lineText,
                                matchText = match.value
                            )
                            foundResults.add(result)
                        }
                    }

                    // Update progress every 100 lines to reduce UI updates
                    if (line % 100 == 0) {
                        _progress.value = line.toFloat() / totalLines
                        _results.value = foundResults.toList()
                    }
                }

                _progress.value = 1f
                _results.value = foundResults.toList()

                // Auto-select first result if any found
                if (foundResults.isNotEmpty()) {
                    _currentResultIndex.value = 0
                }
            } finally {
                _isSearching.value = false
            }
        }

        return searchJob!!
    }

    /**
     * Navigate to the next search result.
     * Wraps around to the first result when at the end.
     *
     * @return The next result, or null if no results
     */
    fun nextResult(): LargeFileSearchResult? {
        val currentResults = results.value
        if (currentResults.isEmpty()) return null

        val nextIndex = (_currentResultIndex.value + 1) % currentResults.size
        _currentResultIndex.value = nextIndex
        return currentResults.getOrNull(nextIndex)
    }

    /**
     * Navigate to the previous search result.
     * Wraps around to the last result when at the beginning.
     *
     * @return The previous result, or null if no results
     */
    fun previousResult(): LargeFileSearchResult? {
        val currentResults = results.value
        if (currentResults.isEmpty()) return null

        val prevIndex = if (_currentResultIndex.value <= 0) {
            currentResults.size - 1
        } else {
            _currentResultIndex.value - 1
        }
        _currentResultIndex.value = prevIndex
        return currentResults.getOrNull(prevIndex)
    }

    /**
     * Get the current search result.
     *
     * @return The current result, or null if no results
     */
    fun currentResult(): LargeFileSearchResult? {
        val currentResults = results.value
        val index = _currentResultIndex.value
        return if (index >= 0 && index < currentResults.size) {
            currentResults[index]
        } else {
            null
        }
    }

    /**
     * Cancel the current search.
     */
    fun cancel() {
        searchJob?.cancel()
        _isSearching.value = false
    }

    /**
     * Clear search results and reset state.
     */
    fun clear() {
        cancel()
        _results.value = emptyList()
        _progress.value = 0f
        _currentResultIndex.value = -1
        _hitMaxResults.value = false
    }

    /**
     * Check if the given line contains any search results.
     *
     * @param line The line number to check
     * @return true if the line contains at least one result
     */
    fun lineHasResults(line: Int): Boolean {
        return results.value.any { it.range.start.line == line }
    }

    /**
     * Get all results on a specific line.
     *
     * @param line The line number
     * @return List of results on that line
     */
    fun getResultsForLine(line: Int): List<LargeFileSearchResult> {
        return results.value.filter { it.range.start.line == line }
    }
}
