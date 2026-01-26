package ai.rever.boss.plugin.panel.bookmarks

import ai.rever.boss.plugin.api.BookmarkDataProvider
import ai.rever.boss.plugin.api.SplitViewOperations
import ai.rever.boss.plugin.api.WorkspaceDataProvider
import ai.rever.boss.plugin.bookmark.Bookmark
import ai.rever.boss.plugin.bookmark.BookmarkCollection
import ai.rever.boss.plugin.bookmark.WorkspacePanelTarget
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import ai.rever.boss.plugin.workspace.LayoutWorkspace
import ai.rever.boss.plugin.workspace.PanelConfig
import ai.rever.boss.plugin.workspace.SplitConfig
import ai.rever.boss.plugin.workspace.TabConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ViewModel for Bookmarks panel
 */
class BookmarksViewModel(
    private val bookmarkDataProvider: BookmarkDataProvider,
    private val workspaceDataProvider: WorkspaceDataProvider,
    private val splitViewOperations: SplitViewOperations?,
    private val projectPathProvider: () -> String
) {
    private val logger = BossLogger.forComponent("BookmarksViewModel")

    // Expose providers for UI
    val collections: StateFlow<List<BookmarkCollection>> = bookmarkDataProvider.collections
    val favoriteWorkspaces = bookmarkDataProvider.favoriteWorkspaces
    val workspaces: StateFlow<List<LayoutWorkspace>> = workspaceDataProvider.workspaces
    val currentWorkspace = workspaceDataProvider.currentWorkspace

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Handle bookmark click - opens tab in target workspace/panel or current workspace
     */
    fun onBookmarkClick(
        bookmark: Bookmark,
        coroutineScope: CoroutineScope
    ) {
        // Mark bookmark as accessed
        val collection = bookmarkDataProvider.collections.value.find { coll ->
            coll.bookmarks.any { it.id == bookmark.id }
        }
        if (collection != null) {
            bookmarkDataProvider.markBookmarkAsAccessed(collection.id, bookmark.id)
        }

        val splitView = splitViewOperations ?: return

        // Get target workspaces
        val targets = bookmark.targetWorkspaces

        // Handle multiple target workspaces
        if (targets.isNotEmpty()) {
            coroutineScope.launch {
                // Preserve current state before switching
                val currentWs = workspaceDataProvider.currentWorkspace.value
                if (currentWs != null && currentWs.id.isNotEmpty()) {
                    splitView.preserveCurrentState(currentWs.id, currentWs.name)
                }

                // Open tab in each target workspace/panel
                targets.forEach { target ->
                    val targetWorkspace = workspaceDataProvider.workspaces.value
                        .find { it.name == target.workspaceName }

                    if (targetWorkspace != null) {
                        // Load and apply the target workspace
                        workspaceDataProvider.loadWorkspace(targetWorkspace)
                        splitView.applyWorkspace(targetWorkspace)

                        // Set target panel as active if specified
                        target.panelId?.let { panelId ->
                            splitView.setActivePanel(panelId)
                        }

                        // Open the tab in the (now active) panel
                        openTabInActivePanel(bookmark, splitView)
                    }
                }
            }
            return
        }

        // No target workspaces - use current workspace
        openTabInActivePanel(bookmark, splitView)
    }

    /**
     * Helper function to open a tab in the active panel based on tab type
     */
    private fun openTabInActivePanel(bookmark: Bookmark, splitView: SplitViewOperations) {
        val projectPath = projectPathProvider()

        when (bookmark.tabConfig.type) {
            "browser" -> {
                val url = bookmark.tabConfig.url ?: "about:blank"
                splitView.openUrlInActivePanel(url, bookmark.tabConfig.title, forceNewTab = true)
            }
            "editor" -> {
                val filePath = bookmark.tabConfig.filePath ?: ""
                if (filePath.isNotEmpty()) {
                    val fileName = filePath.substringAfterLast('/')
                    splitView.openFileInActivePanel(filePath, fileName)
                }
            }
            "terminal" -> {
                splitView.getActiveTabsComponent()?.addTerminalTab(
                    id = "terminal-${Random.nextLong()}",
                    title = bookmark.tabConfig.title,
                    workingDirectory = projectPath.ifEmpty { null }
                )
            }
        }
    }

    /**
     * Handle workspace click - loads entire workspace
     */
    fun onWorkspaceClick(
        workspace: LayoutWorkspace,
        coroutineScope: CoroutineScope
    ) {
        val splitView = splitViewOperations ?: return

        coroutineScope.launch {
            // Preserve current state before switching
            val currentWs = workspaceDataProvider.currentWorkspace.value
            if (currentWs != null && currentWs.id.isNotEmpty()) {
                splitView.preserveCurrentState(currentWs.id, currentWs.name)
            }

            // Load and apply the workspace
            workspaceDataProvider.loadWorkspace(workspace)
            splitView.applyWorkspace(workspace)
        }
    }

    /**
     * Handle workspace tab click - opens individual tab from workspace
     */
    fun onWorkspaceTabClick(tabConfig: TabConfig) {
        val splitView = splitViewOperations ?: return
        val projectPath = projectPathProvider()

        when (tabConfig.type) {
            "browser" -> {
                val url = tabConfig.url ?: "about:blank"
                splitView.openUrlInActivePanel(url, tabConfig.title, forceNewTab = true)
            }
            "editor" -> {
                val filePath = tabConfig.filePath ?: ""
                if (filePath.isNotEmpty()) {
                    val fileName = filePath.substringAfterLast('/')
                    splitView.openFileInActivePanel(filePath, fileName)
                }
            }
            "terminal" -> {
                splitView.getActiveTabsComponent()?.addTerminalTab(
                    id = "terminal-${Random.nextLong()}",
                    title = tabConfig.title,
                    workingDirectory = projectPath.ifEmpty { null }
                )
            }
        }
    }

    // ==================== Bookmark Operations ====================

    fun addBookmark(collectionName: String, bookmark: Bookmark) {
        bookmarkDataProvider.addBookmark(collectionName, bookmark)
    }

    fun removeBookmark(collectionId: String, bookmarkId: String) {
        bookmarkDataProvider.removeBookmark(collectionId, bookmarkId)
    }

    fun updateBookmark(collectionId: String, bookmark: Bookmark) {
        bookmarkDataProvider.updateBookmark(collectionId, bookmark)
    }

    fun moveBookmark(bookmarkId: String, fromCollectionId: String, toCollectionId: String) {
        bookmarkDataProvider.moveBookmark(bookmarkId, fromCollectionId, toCollectionId)
    }

    fun isTabBookmarked(tabConfig: TabConfig): Boolean {
        return bookmarkDataProvider.isTabBookmarked(tabConfig)
    }

    fun findBookmarkForTab(tabConfig: TabConfig): Pair<String, String>? {
        return bookmarkDataProvider.findBookmarkForTab(tabConfig)
    }

    // ==================== Collection Operations ====================

    fun createCollection(name: String): BookmarkCollection {
        return bookmarkDataProvider.createCollection(name)
    }

    fun deleteCollection(collectionId: String) {
        bookmarkDataProvider.deleteCollection(collectionId)
    }

    fun renameCollection(collectionId: String, newName: String) {
        bookmarkDataProvider.renameCollection(collectionId, newName)
    }

    // ==================== Favorite Workspace Operations ====================

    fun addFavoriteWorkspace(workspaceId: String, workspaceName: String) {
        bookmarkDataProvider.addFavoriteWorkspace(workspaceId, workspaceName)
    }

    fun removeFavoriteWorkspace(workspaceId: String) {
        bookmarkDataProvider.removeFavoriteWorkspace(workspaceId)
    }

    fun isFavorite(workspaceId: String): Boolean {
        return bookmarkDataProvider.isFavorite(workspaceId)
    }

    // ==================== Workspace Operations ====================

    fun createNewWorkspace(name: String) {
        if (name.isNotEmpty()) {
            val newWorkspace = LayoutWorkspace(
                name = name,
                description = "",
                layout = SplitConfig.SinglePanel(
                    panel = PanelConfig(
                        id = "panel-1",
                        tabs = emptyList()
                    )
                )
            )
            workspaceDataProvider.updateCurrentWorkspace(newWorkspace)
            workspaceDataProvider.saveCurrentWorkspace(name)
        }
    }

    fun deleteWorkspace(name: String) {
        workspaceDataProvider.deleteWorkspace(name)
    }

    fun renameWorkspace(oldName: String, newName: String) {
        workspaceDataProvider.renameWorkspace(oldName, newName)
    }

    fun exportWorkspace(workspace: LayoutWorkspace): String {
        return workspaceDataProvider.exportWorkspace(workspace)
    }

    // ==================== Utility ====================

    /**
     * Build hierarchical tab structure from workspace layout
     */
    fun buildTabStructure(layout: SplitConfig, level: Int = 0): List<WorkspaceTabStructure> {
        return when (layout) {
            is SplitConfig.SinglePanel -> {
                layout.panel.tabs.map { WorkspaceTabStructure.TabItem(it) }
            }
            is SplitConfig.VerticalSplit -> {
                listOf(
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Left",
                        children = buildTabStructure(layout.left, level + 1),
                        level = level
                    ),
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Right",
                        children = buildTabStructure(layout.right, level + 1),
                        level = level
                    )
                )
            }
            is SplitConfig.HorizontalSplit -> {
                listOf(
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Top",
                        children = buildTabStructure(layout.top, level + 1),
                        level = level
                    ),
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Bottom",
                        children = buildTabStructure(layout.bottom, level + 1),
                        level = level
                    )
                )
            }
        }
    }
}

/**
 * Represents the hierarchical tab structure within a workspace
 */
sealed class WorkspaceTabStructure {
    data class TabItem(
        val tabConfig: TabConfig
    ) : WorkspaceTabStructure()

    data class SplitSection(
        val sectionName: String,
        val children: List<WorkspaceTabStructure>,
        val level: Int = 0
    ) : WorkspaceTabStructure()
}
