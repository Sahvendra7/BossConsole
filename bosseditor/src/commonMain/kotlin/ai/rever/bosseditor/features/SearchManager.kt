package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.OffsetRange

/**
 * Manages search and replace functionality for the editor.
 *
 * Features:
 * - Case-sensitive and case-insensitive search
 * - Whole word matching
 * - Regular expression search
 * - Find next/previous
 * - Replace and replace all
 * - Search result highlighting
 *
 * ## Usage
 * ```kotlin
 * val searchManager = SearchManager(document)
 *
 * // Simple search
 * searchManager.search("findMe")
 *
 * // Navigate results
 * searchManager.findNext()
 * searchManager.findPrevious()
 *
 * // Replace
 * searchManager.replace("replacement")
 * searchManager.replaceAll("replacement")
 * ```
 */
class SearchManager(
    private val document: EditorDocument
) {
    // Search state
    private var searchQuery: String = ""
    private var searchOptions: SearchOptions = SearchOptions()
    private var matches: List<SearchMatch> = emptyList()
    private var currentMatchIndex: Int = -1

    // Listeners
    private val listeners = mutableListOf<SearchListener>()

    /**
     * Current search query.
     */
    val query: String
        get() = searchQuery

    /**
     * Current search options.
     */
    val options: SearchOptions
        get() = searchOptions

    /**
     * All search matches.
     */
    val allMatches: List<SearchMatch>
        get() = matches

    /**
     * Number of matches found.
     */
    val matchCount: Int
        get() = matches.size

    /**
     * Index of current match (0-based), or -1 if none.
     */
    val currentIndex: Int
        get() = currentMatchIndex

    /**
     * Current match, or null if none.
     */
    val currentMatch: SearchMatch?
        get() = if (currentMatchIndex >= 0 && currentMatchIndex < matches.size) {
            matches[currentMatchIndex]
        } else null

    /**
     * Whether there's an active search.
     */
    val isSearchActive: Boolean
        get() = searchQuery.isNotEmpty()

    /**
     * Performs a search with the given query.
     *
     * @param query The search string
     * @param options Search options (case sensitivity, regex, etc.)
     * @param startOffset Optional offset to start searching from
     * @return Number of matches found
     */
    fun search(
        query: String,
        options: SearchOptions = this.searchOptions,
        startOffset: Int = 0
    ): Int {
        searchQuery = query
        searchOptions = options

        if (query.isEmpty()) {
            clearSearch()
            return 0
        }

        // Find all matches
        matches = findAllMatches(query, options)

        // Set current match to first one at or after startOffset
        currentMatchIndex = if (matches.isNotEmpty()) {
            matches.indexOfFirst { it.range.start >= startOffset }
                .takeIf { it >= 0 } ?: 0
        } else {
            -1
        }

        notifySearchChanged()
        return matches.size
    }

    /**
     * Updates search with new options, keeping the same query.
     */
    fun updateOptions(options: SearchOptions): Int {
        return search(searchQuery, options)
    }

    /**
     * Finds the next match from current position.
     *
     * @param wrap Whether to wrap around to the beginning
     * @return The next match, or null if none
     */
    fun findNext(wrap: Boolean = true): SearchMatch? {
        if (matches.isEmpty()) return null

        currentMatchIndex = if (currentMatchIndex < matches.size - 1) {
            currentMatchIndex + 1
        } else if (wrap) {
            0
        } else {
            currentMatchIndex
        }

        notifyCurrentMatchChanged()
        return currentMatch
    }

    /**
     * Finds the previous match from current position.
     *
     * @param wrap Whether to wrap around to the end
     * @return The previous match, or null if none
     */
    fun findPrevious(wrap: Boolean = true): SearchMatch? {
        if (matches.isEmpty()) return null

        currentMatchIndex = if (currentMatchIndex > 0) {
            currentMatchIndex - 1
        } else if (wrap) {
            matches.size - 1
        } else {
            currentMatchIndex
        }

        notifyCurrentMatchChanged()
        return currentMatch
    }

    /**
     * Finds the match nearest to the given offset.
     */
    fun findNearestMatch(offset: Int): SearchMatch? {
        if (matches.isEmpty()) return null

        // Find the match containing or nearest to the offset
        var nearestIndex = 0
        var nearestDistance = Int.MAX_VALUE

        for ((index, match) in matches.withIndex()) {
            val distance = when {
                offset < match.range.start -> match.range.start - offset
                offset > match.range.end -> offset - match.range.end
                else -> 0 // offset is inside match
            }
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }

        currentMatchIndex = nearestIndex
        notifyCurrentMatchChanged()
        return currentMatch
    }

    /**
     * Replaces the current match with the replacement text.
     *
     * @param replacement The replacement text
     * @return true if replacement was made
     */
    fun replace(replacement: String): Boolean {
        val match = currentMatch ?: return false

        // Perform the replacement
        document.replace(match.range.start, match.range.end, replacement)

        // Re-search to update matches (document changed)
        val oldIndex = currentMatchIndex
        search(searchQuery, searchOptions)

        // Try to stay at similar position
        currentMatchIndex = oldIndex.coerceIn(0, (matches.size - 1).coerceAtLeast(0))
        if (matches.isEmpty()) currentMatchIndex = -1

        notifySearchChanged()
        return true
    }

    /**
     * Replaces all matches with the replacement text.
     *
     * @param replacement The replacement text
     * @return Number of replacements made
     */
    fun replaceAll(replacement: String): Int {
        if (matches.isEmpty()) return 0

        val count = matches.size

        // Replace from end to start to preserve offsets
        val sortedMatches = matches.sortedByDescending { it.range.start }
        for (match in sortedMatches) {
            document.replace(match.range.start, match.range.end, replacement)
        }

        // Clear search (all matches replaced)
        clearSearch()

        return count
    }

    /**
     * Clears the current search.
     */
    fun clearSearch() {
        searchQuery = ""
        matches = emptyList()
        currentMatchIndex = -1
        notifySearchChanged()
    }

    /**
     * Gets matches as OffsetRanges for highlighting.
     */
    fun getMatchRanges(): List<OffsetRange> {
        return matches.map { it.range }
    }

    /**
     * Adds a search listener.
     */
    fun addSearchListener(listener: SearchListener) {
        listeners.add(listener)
    }

    /**
     * Removes a search listener.
     */
    fun removeSearchListener(listener: SearchListener) {
        listeners.remove(listener)
    }

    // Private methods

    private fun findAllMatches(query: String, options: SearchOptions): List<SearchMatch> {
        val text = document.getText()
        val results = mutableListOf<SearchMatch>()

        if (options.useRegex) {
            findRegexMatches(text, query, options, results)
        } else {
            findTextMatches(text, query, options, results)
        }

        return results
    }

    private fun findTextMatches(
        text: String,
        query: String,
        options: SearchOptions,
        results: MutableList<SearchMatch>
    ) {
        val searchText = if (options.caseSensitive) text else text.lowercase()
        val searchQuery = if (options.caseSensitive) query else query.lowercase()

        var startIndex = 0
        while (startIndex < searchText.length) {
            val foundIndex = searchText.indexOf(searchQuery, startIndex)
            if (foundIndex < 0) break

            val endIndex = foundIndex + query.length

            // Check whole word if required
            if (options.wholeWord) {
                val isWordStart = foundIndex == 0 || !searchText[foundIndex - 1].isLetterOrDigit()
                val isWordEnd = endIndex >= searchText.length || !searchText[endIndex].isLetterOrDigit()
                if (!isWordStart || !isWordEnd) {
                    startIndex = foundIndex + 1
                    continue
                }
            }

            // Get actual matched text (preserving original case)
            val matchedText = text.substring(foundIndex, endIndex)

            results.add(
                SearchMatch(
                    range = OffsetRange(
                        start = foundIndex,
                        end = endIndex
                    ),
                    matchedText = matchedText
                )
            )

            startIndex = foundIndex + 1
        }
    }

    private fun findRegexMatches(
        text: String,
        pattern: String,
        options: SearchOptions,
        results: MutableList<SearchMatch>
    ) {
        try {
            val regexOptions = mutableSetOf<RegexOption>()
            if (!options.caseSensitive) {
                regexOptions.add(RegexOption.IGNORE_CASE)
            }
            if (options.multiline) {
                regexOptions.add(RegexOption.MULTILINE)
            }

            val regex = Regex(pattern, regexOptions)

            for (match in regex.findAll(text)) {
                results.add(
                    SearchMatch(
                        range = OffsetRange(
                            start = match.range.first,
                            end = match.range.last + 1
                        ),
                        matchedText = match.value,
                        groups = match.groupValues.drop(1) // Exclude full match
                    )
                )
            }
        } catch (e: Exception) {
            // Invalid regex - return no matches
        }
    }

    private fun notifySearchChanged() {
        listeners.forEach { it.searchChanged(this) }
    }

    private fun notifyCurrentMatchChanged() {
        listeners.forEach { it.currentMatchChanged(this) }
    }
}

/**
 * Search options.
 */
data class SearchOptions(
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val useRegex: Boolean = false,
    val multiline: Boolean = false
) {
    companion object {
        val DEFAULT = SearchOptions()
        val CASE_SENSITIVE = SearchOptions(caseSensitive = true)
        val WHOLE_WORD = SearchOptions(wholeWord = true)
        val REGEX = SearchOptions(useRegex = true)
    }
}

/**
 * A single search match.
 */
data class SearchMatch(
    val range: OffsetRange,
    val matchedText: String,
    val groups: List<String> = emptyList() // Regex capture groups
) {
    val startOffset: Int get() = range.start
    val endOffset: Int get() = range.end
    val length: Int get() = range.length
}

/**
 * Listener for search state changes.
 */
interface SearchListener {
    /**
     * Called when search results change (new search, clear, replace).
     */
    fun searchChanged(manager: SearchManager) {}

    /**
     * Called when current match changes (next/previous).
     */
    fun currentMatchChanged(manager: SearchManager) {}
}
