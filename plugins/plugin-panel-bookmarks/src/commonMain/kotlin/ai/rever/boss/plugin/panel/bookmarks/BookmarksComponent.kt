package ai.rever.boss.plugin.panel.bookmarks

import ai.rever.boss.plugin.api.BookmarkDataProvider
import ai.rever.boss.plugin.api.LocalBookmarkDataProvider
import ai.rever.boss.plugin.api.LocalProjectPath
import ai.rever.boss.plugin.api.LocalSplitViewOperations
import ai.rever.boss.plugin.api.LocalWorkspaceDataProvider
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.SplitViewOperations
import ai.rever.boss.plugin.api.TabIcon
import ai.rever.boss.plugin.api.WorkspaceDataProvider
import ai.rever.boss.plugin.bookmark.Bookmark
import ai.rever.boss.plugin.bookmark.BookmarkCollection
import ai.rever.boss.plugin.bookmark.WorkspacePanelTarget
import ai.rever.boss.plugin.workspace.LayoutWorkspace
import ai.rever.boss.plugin.workspace.TabConfig
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext

/**
 * Bookmarks panel component
 *
 * This component provides the bookmarks functionality including:
 * - Favorites: Quick access to bookmarked tabs
 * - Collections: Organized bookmark groups
 * - Workspaces: Saved tab layouts
 *
 * Note: This component uses CompositionLocals for accessing:
 * - LocalSplitViewOperations: For tab/workspace operations
 * - LocalBookmarkDataProvider: For bookmark management
 * - LocalWorkspaceDataProvider: For workspace management
 * - LocalProjectPath: For current project path
 *
 * These must be provided by the parent composition.
 */
class BookmarksComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val faviconLoaderProvider: @Composable (String?) -> TabIcon.Image?,
    private val contextMenuProvider: @Composable (Modifier, List<ContextMenuItemData>) -> Modifier,
    private val dialogProvider: BookmarksDialogProvider
) : PanelComponentWithUI, ComponentContext by ctx {

    // ViewModel will be created in Content() with access to composition locals
    private var viewModel: BookmarksViewModel? = null

    @Composable
    override fun Content() {
        val coroutineScope = rememberCoroutineScope()

        // Get providers from composition locals
        val splitViewOperations = LocalSplitViewOperations.current
        val bookmarkDataProvider = LocalBookmarkDataProvider.current
        val workspaceDataProvider = LocalWorkspaceDataProvider.current
        val projectPath = LocalProjectPath.current

        // Create or update ViewModel when providers are available
        val currentViewModel = remember(bookmarkDataProvider, workspaceDataProvider) {
            if (bookmarkDataProvider != null && workspaceDataProvider != null) {
                BookmarksViewModel(
                    bookmarkDataProvider = bookmarkDataProvider,
                    workspaceDataProvider = workspaceDataProvider,
                    splitViewOperations = splitViewOperations,
                    projectPathProvider = { projectPath }
                ).also { viewModel = it }
            } else {
                viewModel
            }
        }

        // Update split view operations when it changes
        LaunchedEffect(splitViewOperations, projectPath) {
            if (bookmarkDataProvider != null && workspaceDataProvider != null) {
                viewModel = BookmarksViewModel(
                    bookmarkDataProvider = bookmarkDataProvider,
                    workspaceDataProvider = workspaceDataProvider,
                    splitViewOperations = splitViewOperations,
                    projectPathProvider = { projectPath }
                )
            }
        }

        // Show loading or error if providers not available
        if (currentViewModel == null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier,
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material.Text(
                    text = "Loading bookmarks...",
                    color = Color.Gray
                )
            }
            return
        }

        // Dialog states
        var showNewCollectionDialog by remember { mutableStateOf(false) }
        var showNewWorkspaceDialog by remember { mutableStateOf(false) }
        var showClearFavoritesDialog by remember { mutableStateOf(false) }
        var showUnfavoriteAllWorkspacesDialog by remember { mutableStateOf(false) }

        // Bookmark dialogs
        var bookmarkToRemove by remember { mutableStateOf<Pair<Bookmark, String>?>(null) }
        var bookmarkToCopy by remember { mutableStateOf<Pair<Bookmark, String>?>(null) }
        var bookmarkToMove by remember { mutableStateOf<Pair<Bookmark, String>?>(null) }
        var bookmarkToEditWorkspaces by remember { mutableStateOf<Pair<Bookmark, String>?>(null) }

        // Collection dialogs
        var collectionToRename by remember { mutableStateOf<BookmarkCollection?>(null) }
        var collectionToDelete by remember { mutableStateOf<BookmarkCollection?>(null) }

        // Workspace dialogs
        var workspaceToRename by remember { mutableStateOf<LayoutWorkspace?>(null) }
        var workspaceToDelete by remember { mutableStateOf<LayoutWorkspace?>(null) }

        // Tab bookmark dialogs
        var tabToBookmark by remember { mutableStateOf<Pair<TabConfig, String>?>(null) }
        var tabToRemoveBookmark by remember { mutableStateOf<TabConfig?>(null) }

        // Access providers for dialogs
        val collections by currentViewModel.collections.collectAsState()
        val workspaces by currentViewModel.workspaces.collectAsState()
        val favoriteWorkspaces by currentViewModel.favoriteWorkspaces.collectAsState()

        BookmarksContent(
            viewModel = currentViewModel,
            faviconLoader = { cacheKey, fallbackIcon, modifier, tint ->
                FaviconIcon(
                    faviconCacheKey = cacheKey,
                    fallbackIcon = fallbackIcon,
                    modifier = modifier,
                    tint = tint,
                    faviconLoaderProvider = faviconLoaderProvider
                )
            },
            contextMenuModifier = { modifier, items -> contextMenuProvider(modifier, items) },
            onShowNewCollectionDialog = { showNewCollectionDialog = true },
            onShowNewWorkspaceDialog = { showNewWorkspaceDialog = true },
            onShowClearFavoritesDialog = { showClearFavoritesDialog = true },
            onShowUnfavoriteAllWorkspacesDialog = { showUnfavoriteAllWorkspacesDialog = true },
            onShowBookmarkRemoveDialog = { bookmark, collectionId ->
                bookmarkToRemove = bookmark to collectionId
            },
            onShowBookmarkCopyDialog = { bookmark, collectionId ->
                bookmarkToCopy = bookmark to collectionId
            },
            onShowBookmarkMoveDialog = { bookmark, collectionId ->
                bookmarkToMove = bookmark to collectionId
            },
            onShowBookmarkWorkspaceDialog = { bookmark, collectionId ->
                bookmarkToEditWorkspaces = bookmark to collectionId
            },
            onShowCollectionRenameDialog = { collectionToRename = it },
            onShowCollectionDeleteDialog = { collectionToDelete = it },
            onShowWorkspaceRenameDialog = { workspaceToRename = it },
            onShowWorkspaceDeleteDialog = { workspaceToDelete = it },
            onShowTabBookmarkDialog = { tabConfig, workspaceName ->
                tabToBookmark = tabConfig to workspaceName
            },
            onShowTabRemoveDialog = { tabToRemoveBookmark = it },
            coroutineScope = coroutineScope
        )

        // Dialogs
        if (showNewCollectionDialog) {
            dialogProvider.NewCollectionDialog(
                onDismiss = { showNewCollectionDialog = false },
                onCreate = { name ->
                    if (name.isNotEmpty()) {
                        currentViewModel.createCollection(name)
                    }
                    showNewCollectionDialog = false
                }
            )
        }

        if (showNewWorkspaceDialog) {
            dialogProvider.NewWorkspaceDialog(
                onDismiss = { showNewWorkspaceDialog = false },
                onCreate = { name ->
                    if (name.isNotEmpty()) {
                        currentViewModel.createNewWorkspace(name)
                    }
                    showNewWorkspaceDialog = false
                }
            )
        }

        if (showClearFavoritesDialog) {
            val favoritesCollection = collections.find { it.isFavorite }
            dialogProvider.ConfirmationDialog(
                title = "Clear All Favorites?",
                message = "All bookmarks will be removed from your Favorites collection. The bookmarks will remain in their other collections.",
                confirmText = "Clear All",
                onDismiss = { showClearFavoritesDialog = false },
                onConfirm = {
                    if (favoritesCollection != null) {
                        favoritesCollection.bookmarks.forEach { bookmark ->
                            currentViewModel.removeBookmark(favoritesCollection.id, bookmark.id)
                        }
                    }
                    showClearFavoritesDialog = false
                }
            )
        }

        if (showUnfavoriteAllWorkspacesDialog) {
            dialogProvider.ConfirmationDialog(
                title = "Unfavorite All Workspaces?",
                message = "All ${favoriteWorkspaces.size} workspaces will be removed from your Favorite Workspaces. The workspaces themselves will not be deleted.",
                confirmText = "Unfavorite All",
                onDismiss = { showUnfavoriteAllWorkspacesDialog = false },
                onConfirm = {
                    favoriteWorkspaces.forEach { fav ->
                        currentViewModel.removeFavoriteWorkspace(fav.workspaceId)
                    }
                    showUnfavoriteAllWorkspacesDialog = false
                }
            )
        }

        // Bookmark remove confirmation
        bookmarkToRemove?.let { (bookmark, collectionId) ->
            dialogProvider.RemoveBookmarkConfirmationDialog(
                bookmarkTitle = bookmark.tabConfig.title,
                onDismiss = { bookmarkToRemove = null },
                onConfirm = {
                    currentViewModel.removeBookmark(collectionId, bookmark.id)
                    bookmarkToRemove = null
                }
            )
        }

        // Bookmark copy dialog
        bookmarkToCopy?.let { (bookmark, collectionId) ->
            dialogProvider.CollectionSelectionDialog(
                title = "Copy Bookmark to Collections",
                collections = collections,
                excludeCollectionId = collectionId,
                isMoveMode = false,
                onDismiss = { bookmarkToCopy = null },
                onConfirm = { selectedCollections ->
                    selectedCollections.forEach { targetCollectionId ->
                        val collection = collections.find { it.id == targetCollectionId }
                        collection?.let {
                            currentViewModel.addBookmark(it.name, bookmark)
                        }
                    }
                    bookmarkToCopy = null
                }
            )
        }

        // Bookmark move dialog
        bookmarkToMove?.let { (bookmark, collectionId) ->
            dialogProvider.CollectionSelectionDialog(
                title = "Move Bookmark to Collection",
                collections = collections,
                excludeCollectionId = collectionId,
                isMoveMode = true,
                onDismiss = { bookmarkToMove = null },
                onConfirm = { selectedCollections ->
                    selectedCollections.firstOrNull()?.let { targetCollectionId ->
                        currentViewModel.moveBookmark(bookmark.id, collectionId, targetCollectionId)
                    }
                    bookmarkToMove = null
                }
            )
        }

        // Bookmark workspace selection dialog
        bookmarkToEditWorkspaces?.let { (bookmark, collectionId) ->
            dialogProvider.WorkspaceSelectionDialog(
                title = "Edit Target Workspaces",
                workspaces = workspaces,
                preselectedWorkspaces = bookmark.targetWorkspaces.associate { it.workspaceName to it.panelId },
                onDismiss = { bookmarkToEditWorkspaces = null },
                onConfirm = { workspacePanelMap ->
                    val updatedTargets = workspacePanelMap.map { (wsName, panelId) ->
                        WorkspacePanelTarget(wsName, panelId)
                    }
                    val updatedBookmark = bookmark.copy(targetWorkspaces = updatedTargets)
                    currentViewModel.updateBookmark(collectionId, updatedBookmark)
                    bookmarkToEditWorkspaces = null
                }
            )
        }

        // Collection rename dialog
        collectionToRename?.let { collection ->
            dialogProvider.RenameDialog(
                title = "Rename Collection",
                currentName = collection.name,
                label = "Collection Name",
                onDismiss = { collectionToRename = null },
                onRename = { newName ->
                    currentViewModel.renameCollection(collection.id, newName)
                    collectionToRename = null
                }
            )
        }

        // Collection delete dialog
        collectionToDelete?.let { collection ->
            dialogProvider.ConfirmationDialog(
                title = "Delete Collection?",
                message = "Collection '${collection.name}' and all its bookmarks will be permanently deleted. This action cannot be undone.",
                confirmText = "Delete",
                onDismiss = { collectionToDelete = null },
                onConfirm = {
                    currentViewModel.deleteCollection(collection.id)
                    collectionToDelete = null
                }
            )
        }

        // Workspace rename dialog
        workspaceToRename?.let { workspace ->
            dialogProvider.RenameDialog(
                title = "Rename Workspace",
                currentName = workspace.name,
                label = "Workspace Name",
                onDismiss = { workspaceToRename = null },
                onRename = { newName ->
                    currentViewModel.renameWorkspace(workspace.name, newName)
                    workspaceToRename = null
                }
            )
        }

        // Workspace delete dialog
        workspaceToDelete?.let { workspace ->
            dialogProvider.ConfirmationDialog(
                title = "Delete Workspace?",
                message = "Workspace '${workspace.name}' and all its tabs will be permanently deleted. This action cannot be undone.",
                confirmText = "Delete",
                onDismiss = { workspaceToDelete = null },
                onConfirm = {
                    currentViewModel.deleteWorkspace(workspace.name)
                    workspaceToDelete = null
                }
            )
        }

        // Tab bookmark dialog
        tabToBookmark?.let { (tabConfig, workspaceName) ->
            dialogProvider.BookmarkDialog(
                tabTitle = tabConfig.title,
                collections = collections,
                workspaces = workspaces,
                onDismiss = { tabToBookmark = null },
                onConfirm = { collectionIds, workspacePanelMap ->
                    val targetWorkspaces = workspacePanelMap.map { (wsName, panelId) ->
                        WorkspacePanelTarget(workspaceName = wsName, panelId = panelId)
                    }
                    collectionIds.forEach { collectionId ->
                        val bookmark = Bookmark(
                            tabConfig = tabConfig,
                            workspaceName = workspaceName,
                            targetWorkspaces = targetWorkspaces
                        )
                        val collection = collections.find { it.id == collectionId }
                        collection?.let {
                            currentViewModel.addBookmark(it.name, bookmark)
                        }
                    }
                    tabToBookmark = null
                }
            )
        }

        // Tab remove bookmark dialog
        tabToRemoveBookmark?.let { tabConfig ->
            dialogProvider.RemoveBookmarkConfirmationDialog(
                bookmarkTitle = tabConfig.title,
                onDismiss = { tabToRemoveBookmark = null },
                onConfirm = {
                    currentViewModel.findBookmarkForTab(tabConfig)?.let { (collectionId, bookmarkId) ->
                        currentViewModel.removeBookmark(collectionId, bookmarkId)
                    }
                    tabToRemoveBookmark = null
                }
            )
        }
    }
}

/**
 * Favicon icon with fallback to Material icon
 */
@Composable
private fun FaviconIcon(
    faviconCacheKey: String?,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    faviconLoaderProvider: @Composable (String?) -> TabIcon.Image?
) {
    var tabIcon by remember(faviconCacheKey) { mutableStateOf<TabIcon.Image?>(null) }

    LaunchedEffect(faviconCacheKey) {
        tabIcon = null // Will be loaded by the provider
    }

    // Load favicon using provider
    val loadedIcon = faviconLoaderProvider(faviconCacheKey)
    if (loadedIcon != null) {
        tabIcon = loadedIcon
    }

    when {
        tabIcon != null -> {
            androidx.compose.foundation.Image(
                painter = tabIcon!!.asPainter(),
                contentDescription = null,
                modifier = modifier
            )
        }
        else -> {
            androidx.compose.material.Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = modifier,
                tint = tint ?: Color(0xFF9CA3AF)
            )
        }
    }
}

/**
 * Interface for providing dialog composables
 * This allows the composeApp to inject its own dialog implementations
 */
interface BookmarksDialogProvider {
    @Composable
    fun NewCollectionDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit)

    @Composable
    fun NewWorkspaceDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit)

    @Composable
    fun ConfirmationDialog(
        title: String,
        message: String,
        confirmText: String,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit
    )

    @Composable
    fun RemoveBookmarkConfirmationDialog(
        bookmarkTitle: String,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit
    )

    @Composable
    fun CollectionSelectionDialog(
        title: String,
        collections: List<BookmarkCollection>,
        excludeCollectionId: String,
        isMoveMode: Boolean,
        onDismiss: () -> Unit,
        onConfirm: (List<String>) -> Unit
    )

    @Composable
    fun WorkspaceSelectionDialog(
        title: String,
        workspaces: List<LayoutWorkspace>,
        preselectedWorkspaces: Map<String, String?>,
        onDismiss: () -> Unit,
        onConfirm: (Map<String, String?>) -> Unit
    )

    @Composable
    fun RenameDialog(
        title: String,
        currentName: String,
        label: String,
        onDismiss: () -> Unit,
        onRename: (String) -> Unit
    )

    @Composable
    fun BookmarkDialog(
        tabTitle: String,
        collections: List<BookmarkCollection>,
        workspaces: List<LayoutWorkspace>,
        onDismiss: () -> Unit,
        onConfirm: (List<String>, Map<String, String?>) -> Unit
    )
}
