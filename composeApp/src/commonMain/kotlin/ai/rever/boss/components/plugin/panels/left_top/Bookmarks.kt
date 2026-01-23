package ai.rever.boss.components.plugin.panels.left_top

import BossDarkAccent
import BossDarkBackground
import BossDarkTextSecondary
import ai.rever.boss.cache.loadFaviconFromCache
import ai.rever.boss.utils.extractFileName
import ai.rever.boss.components.bookmarks.Bookmark
import ai.rever.boss.components.bookmarks.BookmarkCollection
import ai.rever.boss.components.bookmarks.WorkspacePanelTarget
import ai.rever.boss.components.bookmarks.bookmarkManager
import ai.rever.boss.components.dialogs.BookmarkDialog
import ai.rever.boss.components.dialogs.CollectionSelectionDialog
import ai.rever.boss.components.dialogs.CollectionSelectionMode
import ai.rever.boss.components.dialogs.ConfirmationDialog
import ai.rever.boss.components.dialogs.NewCollectionDialog
import ai.rever.boss.components.dialogs.NewWorkspaceDialog
import ai.rever.boss.components.dialogs.RemoveBookmarkConfirmationDialog
import ai.rever.boss.components.dialogs.RenameDialog
import ai.rever.boss.components.dialogs.WorkspaceSelectionDialog
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.overlays.contextMenu
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.LocalSplitViewState
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.LocalWorkspaceManager
import ai.rever.boss.components.plugin.tab_types.TerminalTabInfo
import ai.rever.boss.components.plugin.tab_types.TerminalTab
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.components.registery.TabIcon
import ai.rever.boss.components.window_panel.SplitViewState
import ai.rever.boss.components.workspaces.applyWorkspace
import ai.rever.boss.components.workspaces.PanelConfig
import ai.rever.boss.components.workspaces.SplitConfig
import ai.rever.boss.components.workspaces.TabConfig
import ai.rever.boss.components.workspaces.workspaceManager
import ai.rever.boss.window.LocalWindowProjectState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Represents the hierarchical tab structure within a workspace
 */
sealed class WorkspaceTabStructure {
    data class TabItem(
        val tabConfig: TabConfig
    ) : WorkspaceTabStructure()

    data class SplitSection(
        val sectionName: String,  // "Left", "Right", "Top", "Bottom"
        val children: List<WorkspaceTabStructure>,
        val level: Int = 0
    ) : WorkspaceTabStructure()
}

/**
 * Bookmarks panel component
 */
class BookmarksPanel(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        val collections by bookmarkManager.collections.collectAsState()
        val favoriteWorkspaces by bookmarkManager.favoriteWorkspaces.collectAsState()
        val workspaces by workspaceManager.workspaces.collectAsState()

        // Dialog states
        var showNewCollectionDialog by remember { mutableStateOf(false) }
        var showNewWorkspaceDialog by remember { mutableStateOf(false) }
        var showClearFavoritesDialog by remember { mutableStateOf(false) }
        var showUnfavoriteAllWorkspacesDialog by remember { mutableStateOf(false) }

        // Access composition locals for tab/workspace operations
        val splitViewState = LocalSplitViewState.current
        val workspaceManagerLocal = LocalWorkspaceManager.current
        val coroutineScope = rememberCoroutineScope()
        val windowProjectState = LocalWindowProjectState.current
        // Per-window project state (required for multi-window support)
        val currentProjectPath = windowProjectState?.selectedProject?.value?.path ?: ""

        // Search state
        var searchQuery by remember { mutableStateOf("") }

        // Filtered data based on search query
        val filteredCollections = remember(collections, searchQuery) {
            filterCollections(collections, searchQuery)
        }
        val filteredFavoriteWorkspaces = remember(favoriteWorkspaces, workspaces, searchQuery) {
            // Map favorite workspace IDs to actual workspace objects, then filter
            val favoriteWorkspacesList = favoriteWorkspaces.mapNotNull { fav ->
                workspaces.find { it.id == fav.workspaceId }
            }
            filterWorkspaces(favoriteWorkspacesList, searchQuery, ::buildTabStructure)
        }
        val filteredAllWorkspaces = remember(workspaces, searchQuery) {
            filterWorkspaces(workspaces, searchQuery, ::buildTabStructure)
        }

        // Filter favorites collection bookmarks
        val favoritesCollection = collections.find { it.isFavorite }
        val filteredFavorites = remember(favoritesCollection?.bookmarks, searchQuery) {
            favoritesCollection?.let { filterBookmarks(it.bookmarks, searchQuery) } ?: emptyList()
        }

        // Section expansion states (only Favorites expanded by default)
        var favoritesExpanded by remember { mutableStateOf(true) }
        var collectionsExpanded by remember { mutableStateOf(false) }
        var allWorkspacesExpanded by remember { mutableStateOf(false) }
        var favoriteWorkspacesExpanded by remember { mutableStateOf(true) }

        // Track expansion state for each collection and workspace
        var expandedCollections by remember { mutableStateOf<Set<String>>(emptySet()) }
        var expandedWorkspaces by remember { mutableStateOf<Set<String>>(emptySet()) }

        fun toggleCollectionExpansion(collectionId: String) {
            expandedCollections = if (expandedCollections.contains(collectionId)) {
                expandedCollections - collectionId
            } else {
                expandedCollections + collectionId
            }
        }

        fun toggleWorkspaceExpansion(workspaceId: String) {
            expandedWorkspaces = if (expandedWorkspaces.contains(workspaceId)) {
                expandedWorkspaces - workspaceId
            } else {
                expandedWorkspaces + workspaceId
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BossDarkBackground)
        ) {
            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookmarkSearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it }
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Favorites section
                if (favoritesCollection != null) {
                    item {
                        CollapsibleSection(
                            title = favoritesCollection.name,
                            isExpanded = favoritesExpanded,
                            onToggle = { favoritesExpanded = !favoritesExpanded },
                            icon = Icons.Outlined.Star,
                            contextMenuItems = buildList {
                                if (favoritesCollection.bookmarks.isNotEmpty()) {
                                    add(ContextMenuItem("Clear All Favorites", Icons.Outlined.DeleteSweep, onClick = {
                                        showClearFavoritesDialog = true
                                    }))
                                }
                            }
                        )
                    }

                    if (favoritesExpanded) {
                        if (filteredFavorites.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Outlined.Star,
                                    message = if (searchQuery.isBlank()) "No favorites yet" else "No matching favorites"
                                )
                            }
                        } else {
                            items(filteredFavorites) { bookmark ->
                                BookmarkItem(
                                    bookmark = bookmark,
                                    collectionId = favoritesCollection.id,
                                    onClick = { onBookmarkClick(bookmark, splitViewState, workspaceManagerLocal, coroutineScope, workspaces, currentProjectPath) }
                                )
                            }
                        }
                    }
                }

                // Collections section (all non-favorite collections)
                val otherCollections = filteredCollections.filter { !it.isFavorite }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    CollapsibleSection(
                        title = "Collections",
                        isExpanded = collectionsExpanded,
                        onToggle = { collectionsExpanded = !collectionsExpanded },
                        icon = Icons.Outlined.FolderOpen,
                        trailingAction = {
                            IconButton(
                                onClick = { showNewCollectionDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "New Collection",
                                    tint = BossDarkAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        contextMenuItems = buildList {
                            add(ContextMenuItem("New Collection", Icons.Outlined.CreateNewFolder, onClick = {
                                showNewCollectionDialog = true
                            }))
                            add(ContextMenuItem("Import Collection", Icons.Outlined.FileUpload, onClick = {
                                // TODO: Implement import functionality
                                println("Import collection requested")
                            }))
                        }
                    )
                }

                if (collectionsExpanded) {
                    if (otherCollections.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.FolderOpen,
                                message = if (searchQuery.isBlank()) "No collections yet" else "No matching collections"
                            )
                        }
                    } else {
                        items(otherCollections) { collection ->
                            CollectionItem(
                                collection = collection,
                                isExpanded = expandedCollections.contains(collection.id),
                                onToggleExpand = { toggleCollectionExpansion(collection.id) },
                                onBookmarkClick = { bookmark ->
                                    onBookmarkClick(bookmark, splitViewState, workspaceManagerLocal, coroutineScope, workspaces, currentProjectPath)
                                },
                                collectionId = collection.id,
                                searchQuery = searchQuery
                            )
                        }
                    }
                }

                // Favorite Workspaces section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    CollapsibleSection(
                        title = "Favorite Workspaces",
                        isExpanded = favoriteWorkspacesExpanded,
                        onToggle = { favoriteWorkspacesExpanded = !favoriteWorkspacesExpanded },
                        icon = Icons.Outlined.Star,
                        contextMenuItems = buildList {
                            if (favoriteWorkspaces.isNotEmpty()) {
                                add(ContextMenuItem("Unfavorite All", Icons.Outlined.DeleteSweep, onClick = {
                                    showUnfavoriteAllWorkspacesDialog = true
                                }))
                            }
                        }
                    )
                }

                if (favoriteWorkspacesExpanded) {
                    if (filteredFavoriteWorkspaces.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.Favorite,
                                message = if (searchQuery.isBlank()) "No favorite workspaces" else "No matching favorite workspaces"
                            )
                        }
                    } else {
                        items(filteredFavoriteWorkspaces) { workspace ->
                            WorkspaceItem(
                                workspace = workspace,
                                isExpanded = expandedWorkspaces.contains(workspace.id),
                                onToggleExpand = { toggleWorkspaceExpansion(workspace.id) },
                                onWorkspaceClick = { onWorkspaceClick(workspace, splitViewState, workspaceManagerLocal, coroutineScope) },
                                onTabClick = { tabConfig -> onWorkspaceTabClick(tabConfig, splitViewState, currentProjectPath) },
                                buildStructure = ::buildTabStructure,
                                isFavorite = bookmarkManager.isFavorite(workspace.id)
                            )
                        }
                    }
                }

                // All Workspaces section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    CollapsibleSection(
                        title = "All Workspaces",
                        isExpanded = allWorkspacesExpanded,
                        onToggle = { allWorkspacesExpanded = !allWorkspacesExpanded },
                        icon = Icons.Outlined.WorkOutline,
                        trailingAction = {
                            IconButton(
                                onClick = { showNewWorkspaceDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "New Workspace",
                                    tint = BossDarkAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        contextMenuItems = buildList {
                            add(ContextMenuItem("New Workspace", Icons.Outlined.CreateNewFolder, onClick = {
                                showNewWorkspaceDialog = true
                            }))
                            // TODO: Import Workspace (needs file picker to select JSON file)
                            // add(ContextMenuItem("Import Workspace", Icons.Outlined.FileUpload, onClick = {
                            //     // Would need: val json = selectFile(); workspaceManager.importWorkspace(json)
                            //     println("Import workspace requested")
                            // }))
                        }
                    )
                }

                if (allWorkspacesExpanded) {
                    if (filteredAllWorkspaces.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.FolderOpen,
                                message = if (searchQuery.isBlank()) "No workspaces" else "No matching workspaces"
                            )
                        }
                    } else {
                        items(filteredAllWorkspaces) { workspace ->
                            WorkspaceItem(
                                workspace = workspace,
                                isExpanded = expandedWorkspaces.contains(workspace.id),
                                onToggleExpand = { toggleWorkspaceExpansion(workspace.id) },
                                onWorkspaceClick = { onWorkspaceClick(workspace, splitViewState, workspaceManagerLocal, coroutineScope) },
                                onTabClick = { tabConfig -> onWorkspaceTabClick(tabConfig, splitViewState, currentProjectPath) },
                                buildStructure = ::buildTabStructure,
                                isFavorite = bookmarkManager.isFavorite(workspace.id)
                            )
                        }
                    }
                }

                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Dialogs
            if (showNewCollectionDialog) {
                NewCollectionDialog(
                    onDismiss = { showNewCollectionDialog = false },
                    onCreate = { name ->
                        if (name.isNotEmpty()) {
                            bookmarkManager.createCollection(name)
                        }
                        showNewCollectionDialog = false
                    }
                )
            }

            if (showNewWorkspaceDialog) {
                NewWorkspaceDialog(
                    onDismiss = { showNewWorkspaceDialog = false },
                    onCreate = { name ->
                        if (name.isNotEmpty()) {
                            // Create a new empty workspace with a single panel
                            val newWorkspace = ai.rever.boss.components.workspaces.LayoutWorkspace(
                                name = name,
                                description = "",
                                layout = SplitConfig.SinglePanel(
                                    panel = PanelConfig(
                                        id = "panel-1",
                                        tabs = emptyList()
                                    )
                                )
                            )
                            // Set as current and save
                            workspaceManagerLocal?.updateCurrentWorkspace(newWorkspace)
                            workspaceManagerLocal?.saveCurrentWorkspace(name)
                        }
                        showNewWorkspaceDialog = false
                    }
                )
            }

            // Clear all favorites confirmation dialog
            if (showClearFavoritesDialog) {
                ConfirmationDialog(
                    title = "Clear All Favorites?",
                    message = "All bookmarks will be removed from your Favorites collection. The bookmarks will remain in their other collections.",
                    icon = Icons.Outlined.DeleteSweep,
                    iconTint = Color(0xFFEF4444),
                    confirmText = "Clear All",
                    onDismiss = { showClearFavoritesDialog = false },
                    onConfirm = {
                        val favoritesCollection = collections.find { it.isFavorite }
                        if (favoritesCollection != null) {
                            // Remove all bookmarks from favorites
                            favoritesCollection.bookmarks.forEach { bookmark ->
                                bookmarkManager.removeBookmark(favoritesCollection.id, bookmark.id)
                            }
                        }
                    }
                )
            }

            // Unfavorite all workspaces confirmation dialog
            if (showUnfavoriteAllWorkspacesDialog) {
                ConfirmationDialog(
                    title = "Unfavorite All Workspaces?",
                    message = "All ${favoriteWorkspaces.size} workspaces will be removed from your Favorite Workspaces. The workspaces themselves will not be deleted.",
                    icon = Icons.Outlined.DeleteSweep,
                    iconTint = Color(0xFFEF4444),
                    confirmText = "Unfavorite All",
                    onDismiss = { showUnfavoriteAllWorkspacesDialog = false },
                    onConfirm = {
                        favoriteWorkspaces.forEach { fav ->
                            bookmarkManager.removeFavoriteWorkspace(fav.workspaceId)
                        }
                    }
                )
            }
        }
    }

    /**
     * Handle bookmark click - opens tab in target workspace/panel or current workspace
     */
    private fun onBookmarkClick(
        bookmark: Bookmark,
        splitViewState: SplitViewState?,
        workspaceManagerLocal: ai.rever.boss.components.workspaces.WorkspaceManager?,
        coroutineScope: kotlinx.coroutines.CoroutineScope,
        workspaces: List<ai.rever.boss.components.workspaces.LayoutWorkspace>,
        projectPath: String
    ) {
        // Mark bookmark as accessed
        val collection = bookmarkManager.collections.value.find { coll ->
            coll.bookmarks.any { it.id == bookmark.id }
        }
        if (collection != null) {
            bookmarkManager.markBookmarkAsAccessed(collection.id, bookmark.id)
        }

        // Get target workspaces
        val targets = bookmark.targetWorkspaces

        // Handle multiple target workspaces
        if (targets.isNotEmpty() && splitViewState != null && workspaceManagerLocal != null) {
            coroutineScope.launch {
                // Preserve current state before switching
                val currentWorkspace = workspaceManagerLocal.currentWorkspace.value
                if (currentWorkspace != null && currentWorkspace.id.isNotEmpty()) {
                    splitViewState.preserveCurrentState(currentWorkspace.id, currentWorkspace.name)
                }

                // Open tab in each target workspace/panel
                targets.forEach { target ->
                    val targetWorkspace = workspaces.find { it.name == target.workspaceName }

                    if (targetWorkspace != null) {
                        // Load and apply the target workspace
                        workspaceManagerLocal.loadWorkspace(targetWorkspace)
                        applyWorkspace(targetWorkspace, splitViewState)

                        // Set target panel as active if specified
                        if (target.panelId != null) {
                            splitViewState.setActivePanel(target.panelId)
                        }

                        // Open the tab in the (now active) panel
                        openTabInActivePanel(bookmark, splitViewState, projectPath)
                    }
                }
            }
            return
        }

        // No target workspaces - use current workspace
        if (splitViewState != null) {
            // Open the tab in the active panel
            openTabInActivePanel(bookmark, splitViewState, projectPath)
        }
    }

    /**
     * Helper function to open a tab in the active panel based on tab type
     */
    private fun openTabInActivePanel(bookmark: Bookmark, splitViewState: SplitViewState, projectPath: String) {
        when (bookmark.tabConfig.type) {
            "browser" -> {
                val url = bookmark.tabConfig.url ?: "about:blank"
                splitViewState.openUrlInActivePanel(url, bookmark.tabConfig.title, forceNewTab = true)
            }
            "editor" -> {
                val filePath = bookmark.tabConfig.filePath ?: ""
                if (filePath.isNotEmpty()) {
                    val fileName = filePath.extractFileName()
                    splitViewState.openFileInActivePanel(filePath, fileName)
                }
            }
            "terminal" -> {
                // Get active tabs component and add terminal tab
                val activeComponent = splitViewState.getActiveTabsComponent()
                if (activeComponent != null) {
                    val terminalTab = TerminalTabInfo(
                        id = "terminal-${Random.nextLong()}",
                        typeId = TerminalTab.typeId,
                        title = bookmark.tabConfig.title,
                        workingDirectory = projectPath.ifEmpty { null }
                    )
                    activeComponent.addTab(terminalTab)
                }
            }
        }
    }

    /**
     * Handle workspace click - loads entire workspace
     */
    private fun onWorkspaceClick(
        workspace: ai.rever.boss.components.workspaces.LayoutWorkspace,
        splitViewState: SplitViewState?,
        workspaceManagerLocal: ai.rever.boss.components.workspaces.WorkspaceManager?,
        coroutineScope: kotlinx.coroutines.CoroutineScope
    ) {
        if (splitViewState != null && workspaceManagerLocal != null) {
            coroutineScope.launch {
                // Preserve current state before switching
                val currentWorkspace = workspaceManagerLocal.currentWorkspace.value
                if (currentWorkspace != null && currentWorkspace.id.isNotEmpty()) {
                    splitViewState.preserveCurrentState(currentWorkspace.id, currentWorkspace.name)
                }

                // Load and apply the workspace
                workspaceManagerLocal.loadWorkspace(workspace)
                applyWorkspace(workspace, splitViewState)
            }
        }
    }

    /**
     * Build hierarchical tab structure from workspace layout
     */
    private fun buildTabStructure(layout: SplitConfig, level: Int = 0): List<WorkspaceTabStructure> {
        return when (layout) {
            is SplitConfig.SinglePanel -> {
                // No sections for single panel, just tabs
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

    /**
     * Handle workspace tab click - opens individual tab from workspace
     */
    private fun onWorkspaceTabClick(tabConfig: TabConfig, splitViewState: SplitViewState?, projectPath: String) {
        // Open the tab (reuse bookmark opening logic)
        if (splitViewState != null) {
            when (tabConfig.type) {
                "browser" -> {
                    val url = tabConfig.url ?: "about:blank"
                    splitViewState.openUrlInActivePanel(url, tabConfig.title, forceNewTab = true)
                }
                "editor" -> {
                    val filePath = tabConfig.filePath ?: ""
                    if (filePath.isNotEmpty()) {
                        val fileName = filePath.substringAfterLast('/')
                        splitViewState.openFileInActivePanel(filePath, fileName)
                    }
                }
                "terminal" -> {
                    // Get active tabs component and add terminal tab
                    val activeComponent = splitViewState.getActiveTabsComponent()
                    if (activeComponent != null) {
                        val terminalTab = TerminalTabInfo(
                            id = "terminal-${Random.nextLong()}",
                            typeId = TerminalTab.typeId,
                            title = tabConfig.title,
                            workingDirectory = projectPath.ifEmpty { null }
                        )
                        activeComponent.addTab(terminalTab)
                    }
                }
            }
        }
    }
}

/**
 * Collapsible section header
 */
@Composable
private fun CollapsibleSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    contextMenuItems: List<ContextMenuItem> = emptyList()
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side: chevron + icon + title (clickable)
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggle)
                .then(
                    if (contextMenuItems.isNotEmpty()) {
                        Modifier.contextMenu(items = contextMenuItems)
                    } else {
                        Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(16.dp),
                tint = BossDarkTextSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))

            // Section icon
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = BossDarkAccent  // Standard blue accent
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BossDarkTextSecondary
            )
        }

        // Right side: trailing action (if provided)
        if (trailingAction != null) {
            trailingAction()
        }
    }
}

/**
 * Split section header (e.g., "---- Left ----")
 */
@Composable
private fun SplitSectionHeader(
    sectionName: String,
    level: Int
) {
    val indentation = (44 + (level * 16)).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentation, end = 24.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left divider
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(1.dp)
                .background(Color(0xFF4B5563))
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Section name
        Text(
            text = sectionName,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF9CA3AF),
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Right divider
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFF4B5563))
        )
    }
}

/**
 * Render hierarchical tab structure recursively
 */
@Composable
private fun RenderTabStructure(
    structure: List<WorkspaceTabStructure>,
    workspaceName: String,
    onTabClick: (TabConfig) -> Unit,
    baseIndentation: Int = 44
) {
    structure.forEach { item ->
        when (item) {
            is WorkspaceTabStructure.TabItem -> {
                WorkspaceTabItem(
                    tabConfig = item.tabConfig,
                    workspaceName = workspaceName,
                    onClick = { onTabClick(item.tabConfig) },
                    indentation = baseIndentation.dp
                )
            }

            is WorkspaceTabStructure.SplitSection -> {
                SplitSectionHeader(
                    sectionName = item.sectionName,
                    level = item.level
                )

                // Render children with increased indentation
                RenderTabStructure(
                    structure = item.children,
                    workspaceName = workspaceName,
                    onTabClick = onTabClick,
                    baseIndentation = baseIndentation + (item.level * 16)
                )
            }
        }
    }
}

/**
 * Bookmark item with comprehensive context menu
 */
@Composable
private fun BookmarkItem(
    bookmark: Bookmark,
    collectionId: String,
    onClick: () -> Unit
) {
    // Dialog states
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showWorkspaceDialog by remember { mutableStateOf(false) }
    var showEditTargetsDialog by remember { mutableStateOf(false) }

    val collections by bookmarkManager.collections.collectAsState()
    val workspaces by workspaceManager.workspaces.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .contextMenu(
                items = buildList {
                    // Remove from collection
                    add(ContextMenuItem("Remove from Collection", Icons.Outlined.Delete, onClick = {
                        showRemoveDialog = true
                    }))

                    add(ContextMenuItem(isDivider = true))

                    // Copy to collection
                    add(ContextMenuItem("Copy to Collection", Icons.Outlined.ContentCopy, onClick = {
                        showCopyDialog = true
                    }))

                    // Move to collection
                    add(ContextMenuItem("Move to Collection", Icons.AutoMirrored.Outlined.DriveFileMove, onClick = {
                        showMoveDialog = true
                    }))

                    add(ContextMenuItem(isDivider = true))

                    // Add to workspace
                    add(ContextMenuItem("Add to Workspace", Icons.Outlined.AddCircleOutline, onClick = {
                        showWorkspaceDialog = true
                    }))

                    // Edit target workspaces
                    add(ContextMenuItem("Edit Target Workspaces", Icons.Outlined.Edit, onClick = {
                        showEditTargetsDialog = true
                    }))
                }
            )
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FaviconIcon(
            faviconCacheKey = bookmark.tabConfig.faviconCacheKey,
            fallbackIcon = when (bookmark.tabConfig.type) {
                "browser" -> Icons.Outlined.Language
                "editor" -> Icons.Outlined.Code
                "terminal" -> Icons.Outlined.Terminal
                else -> Icons.AutoMirrored.Outlined.InsertDriveFile
            },
            modifier = Modifier.size(16.dp),
            tint = Color(0xFF9CA3AF)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = bookmark.tabConfig.title,
            fontSize = 12.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "Remove bookmark",
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = { showRemoveDialog = true }),
            tint = Color(0xFFFBBF24)
        )
    }

    // Remove confirmation dialog
    if (showRemoveDialog) {
        RemoveBookmarkConfirmationDialog(
            bookmarkTitle = bookmark.tabConfig.title,
            onDismiss = { showRemoveDialog = false },
            onConfirm = {
                bookmarkManager.removeBookmark(collectionId, bookmark.id)
            }
        )
    }

    // Copy to collection dialog
    if (showCopyDialog) {
        CollectionSelectionDialog(
            title = "Copy Bookmark to Collections",
            collections = collections,
            excludeCollectionId = collectionId,
            mode = CollectionSelectionMode.COPY,
            onDismiss = { showCopyDialog = false },
            onConfirm = { selectedCollections ->
                selectedCollections.forEach { targetCollectionId ->
                    bookmarkManager.addBookmark(
                        collections.find { it.id == targetCollectionId }?.name ?: "",
                        bookmark
                    )
                }
            }
        )
    }

    // Move to collection dialog
    if (showMoveDialog) {
        CollectionSelectionDialog(
            title = "Move Bookmark to Collection",
            collections = collections,
            excludeCollectionId = collectionId,
            mode = CollectionSelectionMode.MOVE,
            onDismiss = { showMoveDialog = false },
            onConfirm = { selectedCollections ->
                selectedCollections.firstOrNull()?.let { targetCollectionId ->
                    bookmarkManager.moveBookmark(bookmark.id, collectionId, targetCollectionId)
                }
            }
        )
    }

    // Add to workspace dialog
    if (showWorkspaceDialog) {
        WorkspaceSelectionDialog(
            title = "Add Bookmark to Workspaces",
            workspaces = workspaces,
            preselectedWorkspaces = bookmark.targetWorkspaces.associate {  it.workspaceName to it.panelId },
            onDismiss = { showWorkspaceDialog = false },
            onConfirm = { workspacePanelMap ->
                val updatedTargets = workspacePanelMap.map { (wsName, panelId) ->
                    WorkspacePanelTarget(wsName, panelId)
                }
                val updatedBookmark = bookmark.copy(targetWorkspaces = updatedTargets)
                bookmarkManager.updateBookmark(collectionId, updatedBookmark)
            }
        )
    }

    // Edit target workspaces dialog (reuse BookmarkDialog)
    if (showEditTargetsDialog) {
        WorkspaceSelectionDialog(
            title = "Edit Target Workspaces",
            workspaces = workspaces,
            preselectedWorkspaces = bookmark.targetWorkspaces.associate { it.workspaceName to it.panelId },
            onDismiss = { showEditTargetsDialog = false },
            onConfirm = { workspacePanelMap ->
                val updatedTargets = workspacePanelMap.map { (wsName, panelId) ->
                    WorkspacePanelTarget(wsName, panelId)
                }
                val updatedBookmark = bookmark.copy(targetWorkspaces = updatedTargets)
                bookmarkManager.updateBookmark(collectionId, updatedBookmark)
            }
        )
    }
}

/**
 * Collection item (expandable to show bookmarks)
 */
@Composable
private fun CollectionItem(
    collection: BookmarkCollection,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    collectionId: String,
    searchQuery: String = ""
) {
    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Filter bookmarks based on search query
    val filteredBookmarks = remember(collection.bookmarks, searchQuery) {
        filterBookmarks(collection.bookmarks, searchQuery)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Collection header (expandable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse icon
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onToggleExpand() },
                tint = Color(0xFF9CA3AF)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Collection name/icon
            Row(
                modifier = Modifier
                    .weight(1f)
                    .contextMenu(
                        items = buildList {
                            // Rename Collection (if not favorites)
                            if (!collection.isFavorite) {
                                add(ContextMenuItem("Rename Collection", Icons.Outlined.Edit, onClick = {
                                    showRenameDialog = true
                                }))
                            }

                            // Export Collection
                            add(ContextMenuItem("Export Collection", Icons.Outlined.FileDownload, onClick = {
                                // TODO: Implement export functionality
                                println("Export collection: ${collection.name}")
                            }))

                            // Delete Collection (if not favorites)
                            if (!collection.isFavorite) {
                                add(ContextMenuItem(isDivider = true))
                                add(ContextMenuItem("Delete Collection", Icons.Outlined.Delete, onClick = {
                                    showDeleteConfirmation = true
                                }))
                            }
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF9CA3AF)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = collection.name,
                    fontSize = 13.sp,
                    color = Color(0xFFF2F2F2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Bookmark count
                Text(
                    text = "(${filteredBookmarks.size})",
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        // Bookmarks list (shown when expanded)
        if (isExpanded) {
            if (filteredBookmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 44.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No bookmarks in this collection" else "No matching bookmarks",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            } else {
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    filteredBookmarks.forEach { bookmark ->
                        BookmarkItem(
                            bookmark = bookmark,
                            collectionId = collectionId,
                            onClick = { onBookmarkClick(bookmark) }
                        )
                    }
                }
            }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        RenameDialog(
            title = "Rename Collection",
            currentName = collection.name,
            label = "Collection Name",
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                bookmarkManager.renameCollection(collection.id, newName)
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        ConfirmationDialog(
            title = "Delete Collection?",
            message = "Collection '${collection.name}' and all its bookmarks will be permanently deleted. This action cannot be undone.",
            icon = Icons.Outlined.Delete,
            iconTint = Color(0xFFEF4444),
            confirmText = "Delete",
            onDismiss = { showDeleteConfirmation = false },
            onConfirm = {
                bookmarkManager.deleteCollection(collection.id)
            }
        )
    }
}

/**
 * Workspace item (expandable to show tabs)
 */
@Composable
private fun WorkspaceItem(
    workspace: ai.rever.boss.components.workspaces.LayoutWorkspace,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onWorkspaceClick: () -> Unit,
    onTabClick: (TabConfig) -> Unit,
    buildStructure: (SplitConfig) -> List<WorkspaceTabStructure>,
    isFavorite: Boolean = false
) {
    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Workspace header (expandable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse icon
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onToggleExpand() },
                tint = Color(0xFF9CA3AF)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Workspace name/icon (clickable to load workspace)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onWorkspaceClick() }
                    .contextMenu(
                        items = buildList {
                            // Load Workspace
                            add(ContextMenuItem("Load Workspace", Icons.Outlined.FolderOpen, onClick = {
                                onWorkspaceClick()
                            }))

                            add(ContextMenuItem(isDivider = true))

                            // Favorite/Unfavorite
                            val isFav = bookmarkManager.isFavorite(workspace.id)
                            add(ContextMenuItem(
                                if (isFav) "Unfavorite Workspace" else "Favorite Workspace",
                                if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                onClick = {
                                    if (isFav) {
                                        bookmarkManager.removeFavoriteWorkspace(workspace.id)
                                    } else {
                                        bookmarkManager.addFavoriteWorkspace(workspace.id, workspace.name)
                                    }
                                }
                            ))

                            add(ContextMenuItem(isDivider = true))

                            // Rename Workspace (if not "Last Session")
                            if (workspace.name != "Last Session") {
                                add(ContextMenuItem("Rename Workspace", Icons.Outlined.Edit, onClick = {
                                    showRenameDialog = true
                                }))
                            }

                            // Export Workspace
                            add(ContextMenuItem("Export Workspace", Icons.Outlined.FileDownload, onClick = {
                                val json = workspaceManager.exportWorkspace(workspace)
                                // TODO: Save json to file or show dialog
                                println("Exported workspace: $json")
                            }))

                            // TODO: Duplicate Workspace (API not yet available)
                            // add(ContextMenuItem("Duplicate Workspace", Icons.Outlined.ContentCopy, onClick = {
                            //     // Would need duplicateWorkspace(workspace: LayoutWorkspace): LayoutWorkspace
                            //     println("Duplicate workspace: ${workspace.name}")
                            // }))

                            add(ContextMenuItem(isDivider = true))

                            // Delete Workspace (if not current and not predefined)
                            if (workspace.id != workspaceManager.currentWorkspace.value?.id &&
                                workspace.name != "Last Session") {
                                add(ContextMenuItem("Delete Workspace", Icons.Outlined.Delete, onClick = {
                                    showDeleteConfirmation = true
                                }))
                            }
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isFavorite) Color(0xFFFBBF24) else Color(0xFF9CA3AF)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = workspace.name,
                    fontSize = 12.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Load workspace",
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF9CA3AF)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Favorite toggle button
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                modifier = Modifier
                    .size(16.dp)
                    .clickable {
                        if (isFavorite) {
                            bookmarkManager.removeFavoriteWorkspace(workspace.id)
                        } else {
                            bookmarkManager.addFavoriteWorkspace(workspace.id, workspace.name)
                        }
                    },
                tint = if (isFavorite) Color(0xFFFBBF24) else Color(0xFF9CA3AF)
            )
        }

        // Tab list (shown when expanded)
        if (isExpanded) {
            val tabStructure = buildStructure(workspace.layout)
            if (tabStructure.isEmpty()) {
                Text(
                    text = "No tabs",
                    fontSize = 11.sp,
                    color = BossDarkTextSecondary,
                    modifier = Modifier.padding(start = 44.dp, top = 4.dp, bottom = 4.dp)
                )
            } else {
                RenderTabStructure(
                    structure = tabStructure,
                    workspaceName = workspace.name,
                    onTabClick = onTabClick
                )
            }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        RenameDialog(
            title = "Rename Workspace",
            currentName = workspace.name,
            label = "Workspace Name",
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                workspaceManager.renameWorkspace(workspace.name, newName)
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        ConfirmationDialog(
            title = "Delete Workspace?",
            message = "Workspace '${workspace.name}' and all its tabs will be permanently deleted. This action cannot be undone.",
            icon = Icons.Outlined.Delete,
            iconTint = Color(0xFFEF4444),
            confirmText = "Delete",
            onDismiss = { showDeleteConfirmation = false },
            onConfirm = {
                workspaceManager.deleteWorkspace(workspace.name)
            }
        )
    }
}

/**
 * Workspace tab item (nested under workspace)
 */
@Composable
private fun WorkspaceTabItem(
    tabConfig: TabConfig,
    workspaceName: String,
    onClick: () -> Unit,
    indentation: Dp = 44.dp
) {
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    val isBookmarked = remember(tabConfig) { bookmarkManager.isTabBookmarked(tabConfig) }
    val collections by bookmarkManager.collections.collectAsState()
    val workspaces by workspaceManager.workspaces.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = indentation, end = 24.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FaviconIcon(
            faviconCacheKey = tabConfig.faviconCacheKey,
            fallbackIcon = when (tabConfig.type) {
                "browser" -> Icons.Outlined.Language
                "editor" -> Icons.Outlined.Code
                "terminal" -> Icons.Outlined.Terminal
                else -> Icons.AutoMirrored.Outlined.InsertDriveFile
            },
            modifier = Modifier.size(14.dp),
            tint = Color(0xFF9CA3AF)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = tabConfig.title,
            fontSize = 11.sp,
            color = Color(0xFFD1D5DB),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
            modifier = Modifier
                .size(14.dp)
                .clickable(onClick = {
                    if (isBookmarked) {
                        showRemoveDialog = true
                    } else {
                        showBookmarkDialog = true
                    }
                }),
            tint = if (isBookmarked) Color(0xFFFBBF24) else Color(0xFF9CA3AF)
        )
    }

    // Show bookmark dialog
    if (showBookmarkDialog) {
        BookmarkDialog(
            tabTitle = tabConfig.title,
            collections = collections,
            workspaces = workspaces,
            onDismiss = { showBookmarkDialog = false },
            onConfirm = { collectionIds, workspacePanelMap ->
                // Convert workspacePanelMap to list of WorkspacePanelTarget
                val targetWorkspaces = workspacePanelMap.map { (workspaceName, panelId) ->
                    WorkspacePanelTarget(workspaceName = workspaceName, panelId = panelId)
                }

                // Create bookmark for each selected collection
                collectionIds.forEach { collectionId ->
                    val bookmark = Bookmark(
                        tabConfig = tabConfig,
                        workspaceName = workspaceName,
                        targetWorkspaces = targetWorkspaces
                    )
                    val collection = collections.find { it.id == collectionId }
                    collection?.let {
                        bookmarkManager.addBookmark(it.name, bookmark)
                    }
                }

                showBookmarkDialog = false
            }
        )
    }

    // Show remove confirmation dialog
    if (showRemoveDialog) {
        RemoveBookmarkConfirmationDialog(
            bookmarkTitle = tabConfig.title,
            onDismiss = { showRemoveDialog = false },
            onConfirm = {
                bookmarkManager.findBookmarkForTab(tabConfig)?.let { (collectionId, bookmarkId) ->
                    bookmarkManager.removeBookmark(collectionId, bookmarkId)
                }
            }
        )
    }
}

/**
 * Favicon icon with fallback to Material icon
 * Displays cached favicon if available, otherwise shows fallback icon
 * Loads favicon asynchronously on IO thread to prevent UI blocking
 */
@Composable
private fun FaviconIcon(
    faviconCacheKey: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    // State to hold the loaded favicon
    var tabIcon by remember(faviconCacheKey) { mutableStateOf<TabIcon.Image?>(null) }

    // Load favicon asynchronously on IO thread
    LaunchedEffect(faviconCacheKey) {
        tabIcon = withContext(Dispatchers.IO) {
            loadFaviconFromCache(faviconCacheKey)
        }
    }

    when {
        tabIcon != null -> {
            // Display actual favicon
            Image(
                painter = tabIcon!!.asPainter(),
                contentDescription = null,
                modifier = modifier
            )
        }
        else -> {
            // Fallback to Material icon (shows while loading or if favicon unavailable)
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = modifier,
                tint = tint ?: Color(0xFF9CA3AF)
            )
        }
    }
}

/**
 * Empty state message
 */
@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = BossDarkTextSecondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            fontSize = 11.sp,
            color = BossDarkTextSecondary
        )
    }
}

/**
 * Search bar for filtering bookmarks, collections, and workspaces
 * Matches console plugin design
 */
@Composable
private fun BookmarkSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.body2.copy(
            color = Color.White
        ),
        cursorBrush = SolidColor(Color(0xFFFBBF24)), // Gold cursor
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color(0xFF1E1F22), // Dark surface
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        Color(0xFF555555), // Gray border
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search icon
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF888888)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            "Search bookmarks, collections, workspaces...",
                            style = MaterialTheme.typography.body2,
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }
                    innerTextField()
                }

                // Clear button (only show when there's text)
                if (searchQuery.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF888888)
                        )
                    }
                }
            }
        }
    )
}

/**
 * Filter bookmarks by search query (title, URL, or tags)
 */
private fun filterBookmarks(bookmarks: List<Bookmark>, query: String): List<Bookmark> {
    if (query.isBlank()) return bookmarks
    val lowerQuery = query.lowercase()
    return bookmarks.filter { bookmark ->
        bookmark.tabConfig.title.lowercase().contains(lowerQuery) ||
        (bookmark.tabConfig.url?.lowercase()?.contains(lowerQuery) == true) ||
        bookmark.tags.any { it.lowercase().contains(lowerQuery) }
    }
}

/**
 * Filter collections by search query (name or bookmark count)
 */
private fun filterCollections(collections: List<BookmarkCollection>, query: String): List<BookmarkCollection> {
    if (query.isBlank()) return collections
    val lowerQuery = query.lowercase()
    return collections.filter { collection ->
        collection.name.lowercase().contains(lowerQuery)
    }
}

/**
 * Filter workspaces by search query (name, description, or tab titles)
 */
private fun filterWorkspaces(
    workspaces: List<ai.rever.boss.components.workspaces.LayoutWorkspace>,
    query: String,
    buildStructure: (SplitConfig) -> List<WorkspaceTabStructure>
): List<ai.rever.boss.components.workspaces.LayoutWorkspace> {
    if (query.isBlank()) return workspaces
    val lowerQuery = query.lowercase()
    return workspaces.filter { workspace ->
        workspace.name.lowercase().contains(lowerQuery) ||
        workspace.description.lowercase().contains(lowerQuery) ||
        extractTabTitles(buildStructure(workspace.layout)).any { tabTitle ->
            tabTitle.lowercase().contains(lowerQuery)
        }
    }
}

/**
 * Extract all tab titles from workspace tab structure (recursive)
 */
private fun extractTabTitles(structure: List<WorkspaceTabStructure>): List<String> {
    val titles = mutableListOf<String>()
    structure.forEach { item ->
        when (item) {
            is WorkspaceTabStructure.TabItem -> {
                titles.add(item.tabConfig.title)
            }
            is WorkspaceTabStructure.SplitSection -> {
                titles.addAll(extractTabTitles(item.children))
            }
        }
    }
    return titles
}
