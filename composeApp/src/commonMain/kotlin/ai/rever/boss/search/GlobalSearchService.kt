package ai.rever.boss.search

import ai.rever.boss.dashboard.RecentBrowserPagesManager
import ai.rever.boss.keymap.KeymapSettingsManager
import ai.rever.boss.keymap.model.KeymapActions
import ai.rever.boss.keymap.model.formatShortcutLabel
import ai.rever.boss.mcp.McpToolRegistryImpl
import ai.rever.boss.plugin.api.PluginSearchResult
import ai.rever.boss.plugin.api.SearchResultAction
import ai.rever.boss.run.RunConfigurationManager
import ai.rever.boss.topofmind.TopOfMindStateHolder
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private val logger = BossLogger.forComponent("GlobalSearchService")

/**
 * Central coordination service for BOSS Spotlight global search functionality.
 *
 * Searches across multiple data sources:
 * - Files in the project directory
 * - Open tabs across all windows
 * - Bookmarks (via registered SearchProviders from plugins)
 * - Run configurations
 * - Plugin-contributed search results
 */
object GlobalSearchService {
    private val fileIndexer = FileIndexer()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _activeCategory = MutableStateFlow(SearchCategory.ALL)
    val activeCategory: StateFlow<SearchCategory> = _activeCategory.asStateFlow()

    /**
     * The file indexer's isIndexing state.
     */
    val isIndexing: StateFlow<Boolean> = fileIndexer.isIndexing

    /**
     * The currently indexed project path.
     */
    val indexedPath: StateFlow<String?> = fileIndexer.indexedPath

    /**
     * Maximum number of results per category.
     *
     * Set to 15 to balance between showing enough results for discovery
     * while keeping the UI responsive and not overwhelming. With 6 categories,
     * this allows up to 90 results maximum which fits well in the dialog.
     */
    private const val MAX_RESULTS_PER_CATEGORY = 15

    /**
     * Minimum fuzzy match score threshold for results.
     *
     * Set to 1 (very permissive) to show partial matches. Higher values
     * would filter out results where only 1-2 characters match, but users
     * expect to see something even with minimal input. The results are
     * sorted by score anyway, so low-scoring matches appear at the bottom.
     */
    private const val MIN_SCORE = 1

    /**
     * How much a keyword hit gives up against a label hit, for settings.
     *
     * A keyword exists so that "passkey" reaches "Platform Authenticator". It is not a second name
     * for the row, so a setting actually called what you typed has to win - the same ordering
     * `SettingsSearchMatcher` applies inside the settings window.
     */
    private const val KEYWORD_PENALTY = 10

    /**
     * Index a project directory for file searching.
     *
     * If switching to a different project, the old index is automatically cleared
     * to prevent memory buildup from multiple indexed projects.
     *
     * @param projectPath The root directory to index
     * @param forceReindex If true, re-index even if already indexed
     */
    suspend fun indexProject(
        projectPath: String,
        forceReindex: Boolean = false,
    ) {
        // Clear old index when switching projects to prevent memory leak
        val currentPath = fileIndexer.indexedPath.value
        if (currentPath != null && currentPath != projectPath) {
            logger.debug(
                LogCategory.FILE,
                "Clearing old index before switching projects",
                mapOf("oldPath" to currentPath, "newPath" to projectPath),
            )
            fileIndexer.clearIndex()
        }
        fileIndexer.indexProject(projectPath, forceReindex)
    }

    /**
     * Set the active search category for filtering.
     */
    fun setActiveCategory(category: SearchCategory) {
        _activeCategory.value = category
    }

    /**
     * Search across all data sources matching the given query.
     *
     * Searches are run in parallel across all categories for better performance
     * with large datasets. Also queries registered search providers from plugins.
     *
     * @param query The search query
     * @return List of matching search results, sorted by relevance
     */
    suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return emptyList()
        }

        _isSearching.value = true

        try {
            val results =
                withContext(Dispatchers.Default) {
                    // Run all searches in parallel for better performance
                    coroutineScope {
                        val searchResults =
                            listOf(
                                async { searchFiles(query) },
                                async { searchTabs(query) },
                                async { searchPluginProviders(query) }, // Includes bookmarks from plugin
                                async { searchRunConfigs(query) },
                                async { searchCommands(query) },
                                async { searchTools(query) },
                                async { searchSettings(query) },
                                async { searchMcpTools(query) },
                                async { searchRecentPages(query) },
                            ).awaitAll().flatten()

                        // Sort by score
                        searchResults.sortedByDescending { it.score }
                    }
                }

            _searchResults.value = results
            return results
        } finally {
            _isSearching.value = false
        }
    }

    /**
     * Get results filtered by the active category.
     */
    fun getFilteredResults(): List<SearchResult> {
        val category = _activeCategory.value
        val results = _searchResults.value

        return if (category == SearchCategory.ALL) {
            results
        } else {
            results.filter { it.category == category }
        }
    }

    /**
     * Get result counts by category.
     */
    fun getResultCounts(): Map<SearchCategory, Int> {
        val results = _searchResults.value
        return SearchCategory.entries.associateWith { category ->
            if (category == SearchCategory.ALL) {
                results.size
            } else {
                results.count { it.category == category }
            }
        }
    }

    /**
     * Search files using fuzzy matching.
     */
    private fun searchFiles(query: String): List<SearchResult.FileResult> {
        val files = fileIndexer.indexedFiles.value
        if (files.isEmpty()) {
            return emptyList()
        }

        val queryLower = query.lowercase()
        val results = mutableListOf<SearchResult.FileResult>()

        for (file in files) {
            val nameMatch = FuzzyMatcher.match(queryLower, file.name, file.lowerName)

            if (nameMatch != null && nameMatch.score >= MIN_SCORE) {
                results.add(
                    SearchResult.FileResult(
                        name = file.name,
                        path = file.path,
                        relativePath = file.relativePath,
                        score = nameMatch.score + 50,
                        matchRanges = nameMatch.matchRanges,
                    ),
                )
                continue
            }

            val pathMatch = FuzzyMatcher.match(queryLower, file.relativePath, file.relativePath.lowercase())
            if (pathMatch != null && pathMatch.score >= MIN_SCORE) {
                val fileNameStart = file.relativePath.lastIndexOf('/') + 1
                val adjustedRanges =
                    pathMatch.matchRanges
                        .filter { it.start >= fileNameStart || it.end > fileNameStart }
                        .map { range ->
                            MatchRange(
                                start = maxOf(0, range.start - fileNameStart),
                                // Clamp end to file name length to prevent out-of-bounds
                                end = minOf(file.name.length, maxOf(0, range.end - fileNameStart)),
                            )
                        }.filter { it.start < file.name.length && it.end > it.start }

                results.add(
                    SearchResult.FileResult(
                        name = file.name,
                        path = file.path,
                        relativePath = file.relativePath,
                        score = pathMatch.score,
                        matchRanges = adjustedRanges,
                    ),
                )
            }
        }

        return results
            .sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_CATEGORY)
    }

    /**
     * Search open tabs.
     */
    private fun searchTabs(query: String): List<SearchResult.TabResult> {
        val tabs = TopOfMindStateHolder.activeTabs.value
        if (tabs.isEmpty()) {
            return emptyList()
        }

        val queryLower = query.lowercase()
        val results = mutableListOf<SearchResult.TabResult>()

        for (tab in tabs) {
            val title = tab.tabInfo.title
            val titleMatch = FuzzyMatcher.match(queryLower, title, title.lowercase())

            if (titleMatch != null && titleMatch.score >= MIN_SCORE) {
                results.add(
                    SearchResult.TabResult(
                        title = title,
                        tabId = tab.tabInfo.id,
                        workspaceName = tab.workspaceName,
                        windowId = tab.windowId,
                        panelId = tab.panelId,
                        tabType = tab.tabInfo.typeId.typeId,
                        url = null, // Would need FluckTabInfo check
                        filePath = null, // Would need EditorTabInfo check
                        score = titleMatch.score + 30, // Bonus for tabs (currently visible)
                        matchRanges = titleMatch.matchRanges,
                    ),
                )
            }
        }

        return results
            .sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_CATEGORY)
    }

    /**
     * Search registered search providers (includes bookmarks plugin and any other plugins).
     *
     * This queries all SearchProviders registered via PluginContext.registerSearchProvider().
     * The bookmarks plugin registers a BookmarkSearchProvider that contributes bookmark results.
     */
    private suspend fun searchPluginProviders(query: String): List<SearchResult> {
        val providers = SearchRegistryImpl.providers.value
        if (providers.isEmpty()) {
            return emptyList()
        }

        val results = mutableListOf<SearchResult>()

        for (provider in providers) {
            try {
                val providerResults = provider.search(query, MAX_RESULTS_PER_CATEGORY)

                // Convert PluginSearchResult to SearchResult
                for (result in providerResults) {
                    val searchResult = convertPluginSearchResult(result)
                    if (searchResult != null) {
                        results.add(searchResult)
                    }
                }
            } catch (e: Exception) {
                logger.warn(
                    LogCategory.SYSTEM,
                    "Search provider failed",
                    mapOf(
                        "providerId" to provider.providerId,
                    ),
                    error = e,
                )
            }
        }

        return results
            .sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_CATEGORY)
    }

    /**
     * Convert a PluginSearchResult to a SearchResult.
     */
    private fun convertPluginSearchResult(result: PluginSearchResult): SearchResult? {
        // Map category string to SearchCategory
        val category =
            when (result.category.lowercase()) {
                "bookmarks" -> SearchCategory.BOOKMARKS
                "files" -> SearchCategory.FILES
                "tabs" -> SearchCategory.TABS
                "run configs", "run_configs" -> SearchCategory.RUN_CONFIGS
                "commands" -> SearchCategory.COMMANDS
                else -> SearchCategory.BOOKMARKS // Default to bookmarks for plugin results
            }

        // Convert match ranges
        val matchRanges = result.matchRanges.map { MatchRange(it.start, it.end) }

        return when (category) {
            SearchCategory.BOOKMARKS -> {
                val url =
                    when (val action = result.action) {
                        is SearchResultAction.OpenUrl -> action.url
                        else -> null
                    }
                val filePath =
                    when (val action = result.action) {
                        is SearchResultAction.OpenFile -> action.path
                        else -> null
                    }

                SearchResult.BookmarkResult(
                    title = result.title,
                    bookmarkId = result.id,
                    collectionId = result.metadata["collectionId"] ?: "",
                    collectionName = result.metadata["collectionName"] ?: result.providerId,
                    tabType = result.metadata["tabType"] ?: "browser",
                    url = url,
                    filePath = filePath,
                    score = result.score,
                    matchRanges = matchRanges,
                )
            }

            else -> {
                null
            } // Other categories handled by dedicated search methods
        }
    }

    /**
     * Search run configurations.
     */
    private fun searchRunConfigs(query: String): List<SearchResult.RunConfigResult> {
        val settings = RunConfigurationManager.currentSettings.value
        val detectedConfigs = RunConfigurationManager.detectedConfigurations.value

        val allConfigs = settings.configurations + detectedConfigs
        if (allConfigs.isEmpty()) {
            return emptyList()
        }

        val queryLower = query.lowercase()
        val results = mutableListOf<SearchResult.RunConfigResult>()

        for (config in allConfigs) {
            val name = config.name
            val nameMatch = FuzzyMatcher.match(queryLower, name, name.lowercase())

            if (nameMatch != null && nameMatch.score >= MIN_SCORE) {
                results.add(
                    SearchResult.RunConfigResult(
                        name = name,
                        configId = config.id,
                        language = config.language.displayName,
                        filePath = config.filePath,
                        configType = config.type.name,
                        score = nameMatch.score,
                        matchRanges = nameMatch.matchRanges,
                    ),
                )
            }
        }

        return results
            .sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_CATEGORY)
    }

    /**
     * Search commands/actions from KeymapActions.
     */
    private fun searchCommands(query: String): List<SearchResult.CommandResult> {
        val allActionIds = KeymapActions.getAllActionIds()
        val settings = KeymapSettingsManager.currentSettings.value
        val queryLower = query.lowercase()
        val results = mutableListOf<SearchResult.CommandResult>()

        for (actionId in allActionIds) {
            val description = KeymapActions.getDescription(actionId)

            // Match against description
            val descriptionMatch = FuzzyMatcher.match(queryLower, description, description.lowercase())

            // Also match against action ID (e.g., "window.new")
            val actionIdMatch = FuzzyMatcher.match(queryLower, actionId, actionId.lowercase())

            val bestMatch = listOfNotNull(descriptionMatch, actionIdMatch).maxByOrNull { it.score }

            if (bestMatch != null && bestMatch.score >= MIN_SCORE) {
                // Get shortcut for this action
                val binding = settings.shortcuts[actionId]
                val shortcut =
                    if (binding != null && binding.enabled) {
                        formatShortcut(binding.modifiers, binding.key)
                    } else {
                        null
                    }

                results.add(
                    SearchResult.CommandResult(
                        actionId = actionId,
                        description = description,
                        shortcut = shortcut,
                        score = bestMatch.score,
                    ),
                )
            }
        }

        return results
            .sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_CATEGORY)
    }

    /**
     * Search the tools - plugin panels - in the active window's sidebar.
     *
     * **Hidden tools included**, deliberately, exactly as `ToolLauncherDialog` argues about its own
     * list: a tool someone hid from a strip is the one they will come here to find, and this is the
     * surface that has to be able to reach anything.
     *
     * Matched on the panel id as well as the label, because the id is what a plugin's own
     * documentation and its MCP tools call it.
     */
    private fun searchTools(query: String): List<SearchResult.ToolResult> {
        val queryLower = query.lowercase()

        return SearchSources
            .tools()
            .mapNotNull { tool ->
                val labelMatch = FuzzyMatcher.match(queryLower, tool.label, tool.label.lowercase())
                val idMatch = FuzzyMatcher.match(queryLower, tool.panelId, tool.panelId.lowercase())
                val best = listOfNotNull(labelMatch, idMatch).maxByOrNull { it.score }

                best?.takeIf { it.score >= MIN_SCORE }?.let {
                    SearchResult.ToolResult(panelId = tool.panelId, label = tool.label, score = it.score)
                }
            }.sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_CATEGORY)
    }

    /**
     * Search the rows of the Settings window.
     *
     * Keywords are matched but scored BELOW a label hit, which is the rule `SettingsSearchMatcher`
     * already applies inside the settings window: a keyword exists so that "passkey" finds
     * "Platform Authenticator", not so that it outranks a setting actually called that.
     */
    private fun searchSettings(query: String): List<SearchResult.SettingResult> {
        val queryLower = query.lowercase()

        return SearchSources
            .settings()
            .mapNotNull { entry ->
                val labelScore = FuzzyMatcher.match(queryLower, entry.label, entry.label.lowercase())?.score
                val crumbScore =
                    FuzzyMatcher.match(queryLower, entry.breadcrumb, entry.breadcrumb.lowercase())?.score
                val keywordScore =
                    entry.keywords
                        .mapNotNull { FuzzyMatcher.match(queryLower, it, it.lowercase())?.score }
                        .maxOrNull()
                        ?.minus(KEYWORD_PENALTY)

                listOfNotNull(labelScore, crumbScore, keywordScore)
                    .maxOrNull()
                    ?.takeIf { it >= MIN_SCORE }
                    ?.let { score ->
                        SearchResult.SettingResult(
                            section = entry.section,
                            pluginPageId = entry.pluginPageId,
                            group = entry.group,
                            label = entry.label,
                            breadcrumb = entry.breadcrumb,
                            highlightable = entry.highlightable,
                            score = score,
                        )
                    }
            }.sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_CATEGORY)
    }

    /**
     * Search the MCP tools plugins have contributed.
     *
     * **Permitted tools, disabled ones included.** Two filters, pulling opposite ways:
     *
     * - The DISABLED ones stay. A tool someone switched off is exactly what they will search for,
     *   and the row says `off` rather than hiding it.
     * - The ones this user has no permission for go. `allTools` deliberately keeps those for the
     *   management UI, which shows every tool with its state - but this is the everyday launcher,
     *   open to every user and to nobody signed in yet, where a name and a full description of an
     *   admin-only tool would be enumerable by typing. The settings source added alongside this one
     *   already filters by RBAC, and the two should not disagree about whether search respects it.
     *
     * `enabled` therefore means what the row claims: not switched off. Computing it from the
     * disabled set alone, over unfiltered tools, showed a permission-denied tool as live when no
     * client could see it - and answering "is this switched off" is the row's entire job.
     *
     * These results have no activation. See [SearchResult.McpToolResult].
     */
    private fun searchMcpTools(query: String): List<SearchResult.McpToolResult> {
        val queryLower = query.lowercase()
        val disabled = McpToolRegistryImpl.disabledToolNames.value

        return McpToolRegistryImpl
            .permittedTools()
            .mapNotNull { registered ->
                val def = registered.definition
                val nameMatch = FuzzyMatcher.match(queryLower, def.name, def.name.lowercase())
                val descMatch =
                    FuzzyMatcher.match(queryLower, def.description, def.description.lowercase())
                val best = listOfNotNull(nameMatch, descMatch).maxByOrNull { it.score }

                best?.takeIf { it.score >= MIN_SCORE }?.let {
                    SearchResult.McpToolResult(
                        name = def.name,
                        providerId = registered.providerId,
                        description = def.description,
                        enabled = def.name !in disabled,
                        score = it.score,
                    )
                }
            }.sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_CATEGORY)
    }

    /**
     * Search the browser's recent pages.
     *
     * Both the title and the URL, because half of what makes a page findable is its domain -
     * "github" should reach a page whose title never mentions it.
     */
    private fun searchRecentPages(query: String): List<SearchResult.PageResult> {
        val queryLower = query.lowercase()

        return RecentBrowserPagesManager.recentPages.value
            .mapNotNull { page ->
                val titleMatch = FuzzyMatcher.match(queryLower, page.title, page.title.lowercase())
                val urlMatch = FuzzyMatcher.match(queryLower, page.url, page.url.lowercase())
                val best = listOfNotNull(titleMatch, urlMatch).maxByOrNull { it.score }

                best?.takeIf { it.score >= MIN_SCORE }?.let {
                    SearchResult.PageResult(url = page.url, title = page.title, score = it.score)
                }
            }.sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_CATEGORY)
    }

    /**
     * Format a keyboard shortcut for display.
     */
    private fun formatShortcut(
        modifiers: List<String>,
        key: String,
    ): String = formatShortcutLabel(modifiers, key)

    /**
     * Clear search results.
     */
    fun clearResults() {
        _searchResults.value = emptyList()
    }

    /**
     * Clear the file index.
     */
    fun clearIndex() {
        fileIndexer.clearIndex()
        _searchResults.value = emptyList()
    }

    /**
     * Get the count of indexed files.
     */
    fun getIndexedFileCount(): Int = fileIndexer.getFileCount()
}
