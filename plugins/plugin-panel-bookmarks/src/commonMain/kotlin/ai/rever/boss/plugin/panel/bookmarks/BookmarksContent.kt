package ai.rever.boss.plugin.panel.bookmarks

import ai.rever.boss.plugin.bookmark.Bookmark
import ai.rever.boss.plugin.bookmark.BookmarkCollection
import ai.rever.boss.plugin.bookmark.WorkspacePanelTarget
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.lazyListScrollbar
import ai.rever.boss.plugin.ui.BossDarkAccent
import ai.rever.boss.plugin.ui.BossDarkBackground
import ai.rever.boss.plugin.ui.BossDarkTextSecondary
import ai.rever.boss.plugin.ui.ContextMenuItemData
import ai.rever.boss.plugin.workspace.LayoutWorkspace
import ai.rever.boss.plugin.workspace.SplitConfig
import ai.rever.boss.plugin.workspace.TabConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope

private val bookmarksLogger = BossLogger.forComponent("BookmarksContent")

/**
 * Main content composable for Bookmarks panel
 */
@Composable
fun BookmarksContent(
    viewModel: BookmarksViewModel,
    faviconLoader: @Composable (String?, ImageVector, Modifier, Color?) -> Unit,
    contextMenuModifier: @Composable (Modifier, List<ContextMenuItemData>) -> Modifier,
    onShowNewCollectionDialog: () -> Unit,
    onShowNewWorkspaceDialog: () -> Unit,
    onShowClearFavoritesDialog: () -> Unit,
    onShowUnfavoriteAllWorkspacesDialog: () -> Unit,
    onShowBookmarkRemoveDialog: (Bookmark, String) -> Unit,
    onShowBookmarkCopyDialog: (Bookmark, String) -> Unit,
    onShowBookmarkMoveDialog: (Bookmark, String) -> Unit,
    onShowBookmarkWorkspaceDialog: (Bookmark, String) -> Unit,
    onShowCollectionRenameDialog: (BookmarkCollection) -> Unit,
    onShowCollectionDeleteDialog: (BookmarkCollection) -> Unit,
    onShowWorkspaceRenameDialog: (LayoutWorkspace) -> Unit,
    onShowWorkspaceDeleteDialog: (LayoutWorkspace) -> Unit,
    onShowTabBookmarkDialog: (TabConfig, String) -> Unit,
    onShowTabRemoveDialog: (TabConfig) -> Unit,
    coroutineScope: CoroutineScope
) {
    val collections by viewModel.collections.collectAsState()
    val favoriteWorkspaces by viewModel.favoriteWorkspaces.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentWorkspace by viewModel.currentWorkspace.collectAsState()

    // Filtered data based on search query
    val filteredCollections = remember(collections, searchQuery) {
        filterCollections(collections, searchQuery)
    }
    val filteredFavoriteWorkspaces = remember(favoriteWorkspaces, workspaces, searchQuery) {
        val favoriteWorkspacesList = favoriteWorkspaces.mapNotNull { fav ->
            workspaces.find { it.id == fav.workspaceId }
        }
        filterWorkspaces(favoriteWorkspacesList, searchQuery) { viewModel.buildTabStructure(it) }
    }
    val filteredAllWorkspaces = remember(workspaces, searchQuery) {
        filterWorkspaces(workspaces, searchQuery) { viewModel.buildTabStructure(it) }
    }

    // Filter favorites collection bookmarks
    val favoritesCollection = collections.find { it.isFavorite }
    val filteredFavorites = remember(favoritesCollection?.bookmarks, searchQuery) {
        favoritesCollection?.let { filterBookmarks(it.bookmarks, searchQuery) } ?: emptyList()
    }

    // Section expansion states
    var favoritesExpanded by remember { mutableStateOf(true) }
    var collectionsExpanded by remember { mutableStateOf(false) }
    var allWorkspacesExpanded by remember { mutableStateOf(false) }
    var favoriteWorkspacesExpanded by remember { mutableStateOf(true) }

    // Track expansion state for each collection and workspace
    var expandedCollections by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedWorkspaces by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Scrollbar state
    val listState = rememberLazyListState()

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
                onSearchQueryChange = { viewModel.updateSearchQuery(it) }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .lazyListScrollbar(
                    listState = listState,
                    direction = Orientation.Vertical,
                    config = getPanelScrollbarConfig()
                )
        ) {
            // Favorites section
            if (favoritesCollection != null) {
                item {
                    CollapsibleSection(
                        title = favoritesCollection.name,
                        isExpanded = favoritesExpanded,
                        onToggle = { favoritesExpanded = !favoritesExpanded },
                        icon = Icons.Outlined.Star,
                        contextMenuModifier = contextMenuModifier,
                        contextMenuItems = buildList {
                            if (favoritesCollection.bookmarks.isNotEmpty()) {
                                add(ContextMenuItemData("Clear All Favorites", Icons.Outlined.DeleteSweep) {
                                    onShowClearFavoritesDialog()
                                })
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
                                onClick = { viewModel.onBookmarkClick(bookmark, coroutineScope) },
                                faviconLoader = faviconLoader,
                                contextMenuModifier = contextMenuModifier,
                                onShowRemoveDialog = { onShowBookmarkRemoveDialog(bookmark, favoritesCollection.id) },
                                onShowCopyDialog = { onShowBookmarkCopyDialog(bookmark, favoritesCollection.id) },
                                onShowMoveDialog = { onShowBookmarkMoveDialog(bookmark, favoritesCollection.id) },
                                onShowWorkspaceDialog = { onShowBookmarkWorkspaceDialog(bookmark, favoritesCollection.id) }
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
                    contextMenuModifier = contextMenuModifier,
                    trailingAction = {
                        IconButton(
                            onClick = onShowNewCollectionDialog,
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
                    contextMenuItems = listOf(
                        ContextMenuItemData("New Collection", Icons.Outlined.CreateNewFolder) {
                            onShowNewCollectionDialog()
                        }
                    )
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
                                viewModel.onBookmarkClick(bookmark, coroutineScope)
                            },
                            collectionId = collection.id,
                            searchQuery = searchQuery,
                            faviconLoader = faviconLoader,
                            contextMenuModifier = contextMenuModifier,
                            onShowRenameDialog = { onShowCollectionRenameDialog(collection) },
                            onShowDeleteDialog = { onShowCollectionDeleteDialog(collection) },
                            onShowBookmarkRemoveDialog = { bookmark -> onShowBookmarkRemoveDialog(bookmark, collection.id) },
                            onShowBookmarkCopyDialog = { bookmark -> onShowBookmarkCopyDialog(bookmark, collection.id) },
                            onShowBookmarkMoveDialog = { bookmark -> onShowBookmarkMoveDialog(bookmark, collection.id) },
                            onShowBookmarkWorkspaceDialog = { bookmark -> onShowBookmarkWorkspaceDialog(bookmark, collection.id) }
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
                    contextMenuModifier = contextMenuModifier,
                    contextMenuItems = buildList {
                        if (favoriteWorkspaces.isNotEmpty()) {
                            add(ContextMenuItemData("Unfavorite All", Icons.Outlined.DeleteSweep) {
                                onShowUnfavoriteAllWorkspacesDialog()
                            })
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
                            onWorkspaceClick = { viewModel.onWorkspaceClick(workspace, coroutineScope) },
                            onTabClick = { tabConfig -> viewModel.onWorkspaceTabClick(tabConfig) },
                            buildStructure = { viewModel.buildTabStructure(it) },
                            isFavorite = viewModel.isFavorite(workspace.id),
                            isCurrentWorkspace = currentWorkspace?.id == workspace.id,
                            faviconLoader = faviconLoader,
                            contextMenuModifier = contextMenuModifier,
                            onToggleFavorite = {
                                if (viewModel.isFavorite(workspace.id)) {
                                    viewModel.removeFavoriteWorkspace(workspace.id)
                                } else {
                                    viewModel.addFavoriteWorkspace(workspace.id, workspace.name)
                                }
                            },
                            onShowRenameDialog = { onShowWorkspaceRenameDialog(workspace) },
                            onShowDeleteDialog = { onShowWorkspaceDeleteDialog(workspace) },
                            onExport = { viewModel.exportWorkspace(workspace) },
                            onShowTabBookmarkDialog = onShowTabBookmarkDialog,
                            onShowTabRemoveDialog = onShowTabRemoveDialog,
                            viewModel = viewModel
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
                    contextMenuModifier = contextMenuModifier,
                    trailingAction = {
                        IconButton(
                            onClick = onShowNewWorkspaceDialog,
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
                    contextMenuItems = listOf(
                        ContextMenuItemData("New Workspace", Icons.Outlined.CreateNewFolder) {
                            onShowNewWorkspaceDialog()
                        }
                    )
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
                            onWorkspaceClick = { viewModel.onWorkspaceClick(workspace, coroutineScope) },
                            onTabClick = { tabConfig -> viewModel.onWorkspaceTabClick(tabConfig) },
                            buildStructure = { viewModel.buildTabStructure(it) },
                            isFavorite = viewModel.isFavorite(workspace.id),
                            isCurrentWorkspace = currentWorkspace?.id == workspace.id,
                            faviconLoader = faviconLoader,
                            contextMenuModifier = contextMenuModifier,
                            onToggleFavorite = {
                                if (viewModel.isFavorite(workspace.id)) {
                                    viewModel.removeFavoriteWorkspace(workspace.id)
                                } else {
                                    viewModel.addFavoriteWorkspace(workspace.id, workspace.name)
                                }
                            },
                            onShowRenameDialog = { onShowWorkspaceRenameDialog(workspace) },
                            onShowDeleteDialog = { onShowWorkspaceDeleteDialog(workspace) },
                            onExport = { viewModel.exportWorkspace(workspace) },
                            onShowTabBookmarkDialog = onShowTabBookmarkDialog,
                            onShowTabRemoveDialog = onShowTabRemoveDialog,
                            viewModel = viewModel
                        )
                    }
                }
            }

            // Bottom spacer
            item {
                Spacer(modifier = Modifier.height(16.dp))
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
    icon: ImageVector? = null,
    contextMenuModifier: @Composable (Modifier, List<ContextMenuItemData>) -> Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
    contextMenuItems: List<ContextMenuItemData> = emptyList()
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
            modifier = contextMenuModifier(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle),
                contextMenuItems
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

            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = BossDarkAccent
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

        if (trailingAction != null) {
            trailingAction()
        }
    }
}

/**
 * Bookmark item
 */
@Composable
private fun BookmarkItem(
    bookmark: Bookmark,
    collectionId: String,
    onClick: () -> Unit,
    faviconLoader: @Composable (String?, ImageVector, Modifier, Color?) -> Unit,
    contextMenuModifier: @Composable (Modifier, List<ContextMenuItemData>) -> Modifier,
    onShowRemoveDialog: () -> Unit,
    onShowCopyDialog: () -> Unit,
    onShowMoveDialog: () -> Unit,
    onShowWorkspaceDialog: () -> Unit
) {
    val contextMenuItems = listOf(
        ContextMenuItemData("Remove from Collection", Icons.Outlined.Delete) { onShowRemoveDialog() },
        ContextMenuItemData("", null, isDivider = true),
        ContextMenuItemData("Copy to Collection", Icons.Outlined.ContentCopy) { onShowCopyDialog() },
        ContextMenuItemData("Move to Collection", Icons.AutoMirrored.Outlined.DriveFileMove) { onShowMoveDialog() },
        ContextMenuItemData("", null, isDivider = true),
        ContextMenuItemData("Add to Workspace", Icons.Outlined.AddCircleOutline) { onShowWorkspaceDialog() },
        ContextMenuItemData("Edit Target Workspaces", Icons.Outlined.Edit) { onShowWorkspaceDialog() }
    )

    Row(
        modifier = contextMenuModifier(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 6.dp),
            contextMenuItems
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        faviconLoader(
            bookmark.tabConfig.faviconCacheKey,
            when (bookmark.tabConfig.type) {
                "browser" -> Icons.Outlined.Language
                "editor" -> Icons.Outlined.Code
                "terminal" -> Icons.Outlined.Terminal
                else -> Icons.AutoMirrored.Outlined.InsertDriveFile
            },
            Modifier.size(16.dp),
            Color(0xFF9CA3AF)
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
                .clickable(onClick = onShowRemoveDialog),
            tint = Color(0xFFFBBF24)
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
    searchQuery: String = "",
    faviconLoader: @Composable (String?, ImageVector, Modifier, Color?) -> Unit,
    contextMenuModifier: @Composable (Modifier, List<ContextMenuItemData>) -> Modifier,
    onShowRenameDialog: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onShowBookmarkRemoveDialog: (Bookmark) -> Unit,
    onShowBookmarkCopyDialog: (Bookmark) -> Unit,
    onShowBookmarkMoveDialog: (Bookmark) -> Unit,
    onShowBookmarkWorkspaceDialog: (Bookmark) -> Unit
) {
    val filteredBookmarks = remember(collection.bookmarks, searchQuery) {
        filterBookmarks(collection.bookmarks, searchQuery)
    }

    val collectionMenuItems = buildList {
        if (!collection.isFavorite) {
            add(ContextMenuItemData("Rename Collection", Icons.Outlined.Edit) { onShowRenameDialog() })
        }
        add(ContextMenuItemData("Export Collection", Icons.Outlined.FileDownload) {
            bookmarksLogger.debug(LogCategory.UI, "Export collection", mapOf("name" to collection.name))
        })
        if (!collection.isFavorite) {
            add(ContextMenuItemData("", null, isDivider = true))
            add(ContextMenuItemData("Delete Collection", Icons.Outlined.Delete) { onShowDeleteDialog() })
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onToggleExpand() },
                tint = Color(0xFF9CA3AF)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Row(
                modifier = contextMenuModifier(Modifier.weight(1f), collectionMenuItems),
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

                Text(
                    text = "(${filteredBookmarks.size})",
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

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
                        fontStyle = FontStyle.Italic
                    )
                }
            } else {
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    filteredBookmarks.forEach { bookmark ->
                        BookmarkItem(
                            bookmark = bookmark,
                            collectionId = collectionId,
                            onClick = { onBookmarkClick(bookmark) },
                            faviconLoader = faviconLoader,
                            contextMenuModifier = contextMenuModifier,
                            onShowRemoveDialog = { onShowBookmarkRemoveDialog(bookmark) },
                            onShowCopyDialog = { onShowBookmarkCopyDialog(bookmark) },
                            onShowMoveDialog = { onShowBookmarkMoveDialog(bookmark) },
                            onShowWorkspaceDialog = { onShowBookmarkWorkspaceDialog(bookmark) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Workspace item (expandable to show tabs)
 */
@Composable
private fun WorkspaceItem(
    workspace: LayoutWorkspace,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onWorkspaceClick: () -> Unit,
    onTabClick: (TabConfig) -> Unit,
    buildStructure: (SplitConfig) -> List<WorkspaceTabStructure>,
    isFavorite: Boolean = false,
    isCurrentWorkspace: Boolean = false,
    faviconLoader: @Composable (String?, ImageVector, Modifier, Color?) -> Unit,
    contextMenuModifier: @Composable (Modifier, List<ContextMenuItemData>) -> Modifier,
    onToggleFavorite: () -> Unit,
    onShowRenameDialog: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onExport: () -> Unit,
    onShowTabBookmarkDialog: (TabConfig, String) -> Unit,
    onShowTabRemoveDialog: (TabConfig) -> Unit,
    viewModel: BookmarksViewModel
) {
    val workspaceMenuItems = buildList {
        add(ContextMenuItemData("Load Workspace", Icons.Outlined.FolderOpen) { onWorkspaceClick() })
        add(ContextMenuItemData("", null, isDivider = true))
        add(ContextMenuItemData(
            if (isFavorite) "Unfavorite Workspace" else "Favorite Workspace",
            if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder
        ) { onToggleFavorite() })
        add(ContextMenuItemData("", null, isDivider = true))
        if (workspace.name != "Last Session") {
            add(ContextMenuItemData("Rename Workspace", Icons.Outlined.Edit) { onShowRenameDialog() })
        }
        add(ContextMenuItemData("Export Workspace", Icons.Outlined.FileDownload) { onExport() })
        if (!isCurrentWorkspace && workspace.name != "Last Session") {
            add(ContextMenuItemData("", null, isDivider = true))
            add(ContextMenuItemData("Delete Workspace", Icons.Outlined.Delete) { onShowDeleteDialog() })
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onToggleExpand() },
                tint = Color(0xFF9CA3AF)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Row(
                modifier = contextMenuModifier(
                    Modifier
                        .weight(1f)
                        .clickable { onWorkspaceClick() },
                    workspaceMenuItems
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

            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onToggleFavorite() },
                tint = if (isFavorite) Color(0xFFFBBF24) else Color(0xFF9CA3AF)
            )
        }

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
                    onTabClick = onTabClick,
                    faviconLoader = faviconLoader,
                    contextMenuModifier = contextMenuModifier,
                    viewModel = viewModel,
                    onShowBookmarkDialog = onShowTabBookmarkDialog,
                    onShowRemoveDialog = onShowTabRemoveDialog
                )
            }
        }
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
    faviconLoader: @Composable (String?, ImageVector, Modifier, Color?) -> Unit,
    contextMenuModifier: @Composable (Modifier, List<ContextMenuItemData>) -> Modifier,
    viewModel: BookmarksViewModel,
    onShowBookmarkDialog: (TabConfig, String) -> Unit,
    onShowRemoveDialog: (TabConfig) -> Unit,
    baseIndentation: Int = 44
) {
    structure.forEach { item ->
        when (item) {
            is WorkspaceTabStructure.TabItem -> {
                WorkspaceTabItem(
                    tabConfig = item.tabConfig,
                    workspaceName = workspaceName,
                    onClick = { onTabClick(item.tabConfig) },
                    faviconLoader = faviconLoader,
                    contextMenuModifier = contextMenuModifier,
                    viewModel = viewModel,
                    onShowBookmarkDialog = { onShowBookmarkDialog(item.tabConfig, workspaceName) },
                    onShowRemoveDialog = { onShowRemoveDialog(item.tabConfig) },
                    indentation = baseIndentation.dp
                )
            }

            is WorkspaceTabStructure.SplitSection -> {
                SplitSectionHeader(
                    sectionName = item.sectionName,
                    level = item.level
                )

                RenderTabStructure(
                    structure = item.children,
                    workspaceName = workspaceName,
                    onTabClick = onTabClick,
                    faviconLoader = faviconLoader,
                    contextMenuModifier = contextMenuModifier,
                    viewModel = viewModel,
                    onShowBookmarkDialog = onShowBookmarkDialog,
                    onShowRemoveDialog = onShowRemoveDialog,
                    baseIndentation = baseIndentation + (item.level * 16)
                )
            }
        }
    }
}

/**
 * Split section header
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
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(1.dp)
                .background(Color(0xFF4B5563))
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = sectionName,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF9CA3AF),
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFF4B5563))
        )
    }
}

/**
 * Workspace tab item
 */
@Composable
private fun WorkspaceTabItem(
    tabConfig: TabConfig,
    workspaceName: String,
    onClick: () -> Unit,
    faviconLoader: @Composable (String?, ImageVector, Modifier, Color?) -> Unit,
    contextMenuModifier: @Composable (Modifier, List<ContextMenuItemData>) -> Modifier,
    viewModel: BookmarksViewModel,
    onShowBookmarkDialog: () -> Unit,
    onShowRemoveDialog: () -> Unit,
    indentation: Dp = 44.dp
) {
    val isBookmarked = remember(tabConfig) { viewModel.isTabBookmarked(tabConfig) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = indentation, end = 24.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        faviconLoader(
            tabConfig.faviconCacheKey,
            when (tabConfig.type) {
                "browser" -> Icons.Outlined.Language
                "editor" -> Icons.Outlined.Code
                "terminal" -> Icons.Outlined.Terminal
                else -> Icons.AutoMirrored.Outlined.InsertDriveFile
            },
            Modifier.size(14.dp),
            Color(0xFF9CA3AF)
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
                        onShowRemoveDialog()
                    } else {
                        onShowBookmarkDialog()
                    }
                }),
            tint = if (isBookmarked) Color(0xFFFBBF24) else Color(0xFF9CA3AF)
        )
    }
}

/**
 * Search bar
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
        cursorBrush = SolidColor(Color(0xFFFBBF24)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color(0xFF1E1F22),
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        Color(0xFF555555),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
 * Empty state message
 */
@Composable
private fun EmptyState(
    icon: ImageVector,
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

// ==================== Filtering Functions ====================

private fun filterBookmarks(bookmarks: List<Bookmark>, query: String): List<Bookmark> {
    if (query.isBlank()) return bookmarks
    val lowerQuery = query.lowercase()
    return bookmarks.filter { bookmark ->
        bookmark.tabConfig.title.lowercase().contains(lowerQuery) ||
        (bookmark.tabConfig.url?.lowercase()?.contains(lowerQuery) == true) ||
        bookmark.tags.any { it.lowercase().contains(lowerQuery) }
    }
}

private fun filterCollections(collections: List<BookmarkCollection>, query: String): List<BookmarkCollection> {
    if (query.isBlank()) return collections
    val lowerQuery = query.lowercase()
    return collections.filter { collection ->
        collection.name.lowercase().contains(lowerQuery)
    }
}

private fun filterWorkspaces(
    workspaces: List<LayoutWorkspace>,
    query: String,
    buildStructure: (SplitConfig) -> List<WorkspaceTabStructure>
): List<LayoutWorkspace> {
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
