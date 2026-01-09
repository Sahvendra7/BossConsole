package ai.rever.boss.components.window_panel.components.main_window_panels

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import BossDarkTextSecondary
import ai.rever.boss.components.bars.ScrollbarConfig
import ai.rever.boss.components.bars.horizontal.HorizontalBar
import ai.rever.boss.components.bars.horizontal.HorizontalBarRow
import ai.rever.boss.components.bars.horizontalScrollWithScrollbar
import ai.rever.boss.components.buttons.BossTabButton
import ai.rever.boss.components.common.rememberFaviconLoader
import ai.rever.boss.components.model.TabDraggableComponent
import ai.rever.boss.components.model.TabDropResult
import ai.rever.boss.components.model.TabDropTarget
import ai.rever.boss.components.registery.TabComponentWithUI
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.registery.TabIcon
import ai.rever.boss.components.registery.TabRegistry
import ai.rever.boss.components.tabs_navigation.TabsNavigation
import ai.rever.boss.components.bookmarks.Bookmark
import ai.rever.boss.components.bookmarks.WorkspacePanelTarget
import ai.rever.boss.components.bookmarks.bookmarkManager
import ai.rever.boss.components.dialogs.BookmarkDialog
import ai.rever.boss.components.dialogs.NewTabDialog
import ai.rever.boss.components.dialogs.RemoveBookmarkConfirmationDialog
import ai.rever.boss.components.dialogs.TabType
import ai.rever.boss.components.dividers.VDivider
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.overlays.contextMenu
import ai.rever.boss.components.plugin.tab_types.CodeEditor
import ai.rever.boss.icons.FileIcons
import ai.rever.boss.components.plugin.tab_types.EditorTabInfo
import ai.rever.boss.components.plugin.tab_types.TerminalTabInfo
import ai.rever.boss.components.plugin.tab_types.fluck.Fluck
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.workspaces.TabConfig
import ai.rever.boss.components.workspaces.workspaceManager
import ai.rever.boss.components.workspaces.PredefinedWorkspaces
import ai.rever.boss.components.workspaces.applyWorkspace
import ai.rever.boss.components.window_panel.SplitOrientation
import ai.rever.boss.components.dashboard.Dashboard
import ai.rever.boss.window.LocalWindowProjectState
import ai.rever.boss.components.plugin.panels.left_top.ProjectState
import ai.rever.boss.dashboard.SplitTemplate
import ai.rever.boss.dashboard.SplitTemplatesManager
import ai.rever.boss.run.RUNNER_TERMINAL_PREFIX
import ai.rever.boss.run.RunnerTerminalService
import ai.rever.boss.window.WindowOperations
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlin.time.Clock
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Wrapper for BossTabButton that loads and displays favicons from cache
 * Uses shared rememberFaviconLoader composable for DRY and error handling
 */
@Composable
private fun BossTabButtonWithFavicon(
    config: TabInfo,
    isSelected: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    contextMenuItems: List<ContextMenuItem>,
    // Drag-related parameters
    tabDragComponent: TabDraggableComponent? = null,
    panelId: String? = null,
    tabIndex: Int = -1,
    onDragEnd: () -> Unit = {}
) {
    // Load favicon using shared composable (with error handling and caching)
    val loadedFavicon = rememberFaviconLoader(config)

    // Determine which icon to use: loaded favicon > config.tabIcon > fallback to config.icon
    val effectiveTabIcon = loadedFavicon ?: config.tabIcon

    // Middle-click handling is now in BossTabButton.kt (Issue #328)
    BossTabButton(
        fileName = config.title,
        icon = config.icon,
        tabIcon = effectiveTabIcon,
        isSelected = isSelected,
        isFocused = isFocused,
        onClick = onClick,
        onClose = onClose,
        contextMenuItems = contextMenuItems,
        tabDragComponent = tabDragComponent,
        tabInfo = config,
        panelId = panelId,
        tabIndex = tabIndex,
        onDragEnd = onDragEnd
    )
}

@Composable
fun BossTabsComponent.BossMainTabBar(
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState? = null,
    currentPanelId: String? = null,
    focusRequester: FocusRequester? = null,
    tabDragComponent: TabDraggableComponent? = null,
    onTabDropResult: (TabDropResult) -> Unit = {}
) {
    val tabsState = tabsState.subscribeAsState()
    var showNewTabDialog by remember { mutableStateOf(false) }
    var selectedTabType by remember { mutableStateOf<TabType?>(null) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var tabToBookmark by remember { mutableStateOf<TabInfo?>(null) }

    // Observe collections for reactive context menu updates
    val collections by bookmarkManager.collections.collectAsState()

    // Remove bookmark dialog state
    var showRemoveBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkToRemove by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    // Triple = (collectionId, bookmarkId, tabTitle)

    // LazyListState for tab bar scrolling
    val listState = rememberLazyListState()

    // Track if tab bar is scrollable to determine plus button placement
    val isScrollable by remember {
        derivedStateOf {
            listState.canScrollForward || listState.canScrollBackward
        }
    }

    // Auto-scroll to active tab when it changes
    LaunchedEffect(tabsState.value.activeIndex) {
        val activeIndex = tabsState.value.activeIndex
        if (activeIndex >= 0 && activeIndex < tabsState.value.tabs.size) {
            // Only scroll if the tab is not fully visible
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo

            // Check if the tab is fully visible (both left and right edges within viewport)
            val activeItem = visibleItems.find { it.index == activeIndex }
            val isFullyVisible = activeItem?.let { item ->
                val itemStart = item.offset
                val itemEnd = item.offset + item.size
                val viewportStart = layoutInfo.viewportStartOffset
                val viewportEnd = layoutInfo.viewportEndOffset

                // Item is fully visible if both edges are within viewport
                itemStart >= viewportStart && itemEnd <= viewportEnd
            } ?: false

            if (!isFullyVisible) {
                // Scroll to bring the tab fully into view
                listState.scrollToItem(activeIndex)
            }
        }
    }

    // Track drop target for reorder indicator
    val dropTarget = tabDragComponent?.dropTarget

    HorizontalBar(
        height = 42.dp,
        backgroundColor = BossDarkBackground
    ) {
        HorizontalBarRow(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                // Register tab bar bounds for drag detection
                if (currentPanelId != null && tabDragComponent != null) {
                    val bounds = coordinates.boundsInWindow()
                    tabDragComponent.registerTabBarBounds(currentPanelId, bounds)
                }
            }
        ) {
            BossLeftTabBar(listState) {
                // Render tab buttons as lazy items
                itemsIndexed(tabsState.value.tabs) { index, config ->
                    val isSelected = index == tabsState.value.activeIndex
                    val totalTabs = tabsState.value.tabs.size

                    // Show reorder indicator before this tab if it's the drop target
                    val showIndicatorBefore = dropTarget is TabDropTarget.Reorder &&
                        (dropTarget as TabDropTarget.Reorder).panelId == currentPanelId &&
                        (dropTarget as TabDropTarget.Reorder).targetIndex == index

                    if (showIndicatorBefore) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp)
                                .background(BossDarkAccent)
                        )
                    }

                    BossTabButtonWithFavicon(
                        config = config,
                        isSelected = isSelected,
                        isFocused = true, // Tab bars are always considered focused when window is active
                        onClick = {
                            selectTab(index)
                            // Track this tab interaction for Cmd+R/Cmd+N
                            if (splitViewState != null && currentPanelId != null) {
                                splitViewState.trackTabInteraction(currentPanelId, config.id)
                            }
                        },
                        onClose = {
                            removeTab(index)
                            // Request focus back to the main panel after closing tab
                            // This ensures keyboard shortcuts continue to work
                            focusRequester?.requestFocus()
                        },
                        tabDragComponent = tabDragComponent,
                        panelId = currentPanelId,
                        tabIndex = index,
                        onDragEnd = {
                            // Handle drop result
                            tabDragComponent?.endDrag()?.let { result ->
                                onTabDropResult(result)
                            }
                        },
                        contextMenuItems = buildList {
                            // Track interaction when context menu is opened
                            if (splitViewState != null && currentPanelId != null) {
                                // Track this tab interaction when right-clicking
                                splitViewState.trackTabInteraction(currentPanelId, config.id)
                            }

                            // Split operations (if split state is available)
                            if (splitViewState != null && currentPanelId != null) {
                                add(ContextMenuItem("Split Right", Icons.Outlined.ViewColumn, onClick = {
                                    splitViewState.splitPanel(
                                        panelId = currentPanelId,
                                        orientation = ai.rever.boss.components.window_panel.SplitOrientation.VERTICAL,
                                        tabToMove = config
                                    )
                                }))
                                add(ContextMenuItem("Split Down", Icons.Outlined.Splitscreen, onClick = {
                                    splitViewState.splitPanel(
                                        panelId = currentPanelId,
                                        orientation = ai.rever.boss.components.window_panel.SplitOrientation.HORIZONTAL,
                                        tabToMove = config
                                    )
                                }))
                                add(ContextMenuItem(isDivider = true))
                            }

                            // Bookmark current tab
                            // Reference collections to ensure recomposition on bookmark changes
                            collections

                            val tabConfig = convertTabInfoToTabConfig(config)
                            val existingBookmark = bookmarkManager.findBookmarkForTab(tabConfig)

                            if (existingBookmark != null) {
                                // Tab is already bookmarked - show remove option WITH CONFIRMATION
                                val (collectionId, bookmarkId) = existingBookmark
                                add(ContextMenuItem("Remove from Bookmarks", Icons.Filled.Star, onClick = {
                                    bookmarkToRemove = Triple(collectionId, bookmarkId, config.title)
                                    showRemoveBookmarkDialog = true
                                }))
                            } else {
                                // Tab is not bookmarked - show add option
                                add(ContextMenuItem("Add to Bookmarks", Icons.Outlined.Star, onClick = {
                                    tabToBookmark = config
                                    showBookmarkDialog = true
                                }))
                            }

                            // Favorite current workspace
                            val currentWorkspace = workspaceManager.currentWorkspace.value
                            if (currentWorkspace != null) {
                                val isFavorited = bookmarkManager.isFavorite(currentWorkspace.id)
                                add(ContextMenuItem(
                                    if (isFavorited) "Unfavorite Workspace" else "Favorite Workspace",
                                    if (isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    onClick = {
                                        if (isFavorited) {
                                            bookmarkManager.removeFavoriteWorkspace(currentWorkspace.id)
                                        } else {
                                            bookmarkManager.addFavoriteWorkspace(currentWorkspace.id, currentWorkspace.name)
                                        }
                                    }
                                ))
                            }

                            add(ContextMenuItem(isDivider = true))

                            // Open in New Window (if multi-window is supported)
                            if (ai.rever.boss.window.WindowOperations.isMultiWindowSupported()) {
                                add(ContextMenuItem("Open in New Window", Icons.AutoMirrored.Outlined.OpenInNew, onClick = {
                                    ai.rever.boss.window.WindowOperations.openTabInNewWindow(config)
                                    // Remove tab from current window after opening in new window
                                    removeTab(index)
                                    // Request focus back to the main panel
                                    focusRequester?.requestFocus()
                                }))
                                add(ContextMenuItem(isDivider = true))
                            }

                            // Close current tab
                            add(ContextMenuItem("Close Tab", Icons.Outlined.Close, onClick = {
                                removeTab(index)
                                // Request focus back to the main panel
                                focusRequester?.requestFocus()
                            }))

                            // Close other tabs (only show if there are other tabs)
                            if (totalTabs > 1) {
                                add(ContextMenuItem("Close Other Tabs", Icons.Outlined.Clear, onClick = {
                                    closeOtherTabs(index)
                                    // Request focus back to the main panel
                                    focusRequester?.requestFocus()
                                }))
                            }

                            // Close tabs to the right (only show if there are tabs to the right)
                            if (index < totalTabs - 1) {
                                add(ContextMenuItem("Close Tabs to the Right", Icons.Outlined.ChevronRight, onClick = {
                                    closeTabsToRight(index)
                                    // Request focus back to the main panel
                                    focusRequester?.requestFocus()
                                }))
                            }

                            // Close tabs to the left (only show if there are tabs to the left)
                            if (index > 0) {
                                add(ContextMenuItem("Close Tabs to the Left", Icons.Outlined.ChevronLeft, onClick = {
                                    closeTabsToLeft(index)
                                    // Request focus back to the main panel
                                    focusRequester?.requestFocus()
                                }))
                            }
                        }
                    )

                    // Vertical divider after tab (only if not the last tab)
                    if (index < tabsState.value.tabs.size - 1) {
                        VDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp))
                    }

                    // Show reorder indicator after the last tab if dropping at the end
                    val isLastTab = index == tabsState.value.tabs.size - 1
                    val showIndicatorAfter = isLastTab &&
                        dropTarget is TabDropTarget.Reorder &&
                        (dropTarget as TabDropTarget.Reorder).panelId == currentPanelId &&
                        (dropTarget as TabDropTarget.Reorder).targetIndex == tabsState.value.tabs.size

                    if (showIndicatorAfter) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp)
                                .background(BossDarkAccent)
                        )
                    }
                }

                // Plus button as item when not scrollable (appears right after last tab)
                if (!isScrollable) {
                    item {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(32.dp)
                                .padding(4.dp)
                                .background(
                                    color = Color(0xFF3C3F41),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    showNewTabDialog = true
                                    // Track panel interaction when plus button is clicked
                                    if (splitViewState != null && currentPanelId != null) {
                                        splitViewState.setActivePanel(currentPanelId)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Tab",
                                tint = Color(0xFF999999),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Fixed plus button (stays visible when tabs scroll - only when scrollable)
            if (isScrollable) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .width(32.dp)
                        .padding(4.dp)
                        .background(
                            color = Color(0xFF3C3F41),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            showNewTabDialog = true
                            // Track panel interaction when plus button is clicked
                            if (splitViewState != null && currentPanelId != null) {
                                splitViewState.setActivePanel(currentPanelId)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Tab",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxHeight()
                    .contextMenu(
                        items = buildList {
                            add(ContextMenuItem("New Tab", Icons.Default.Add, onClick = {
                                showNewTabDialog = true
                                // Track panel interaction when context menu is used
                                if (splitViewState != null && currentPanelId != null) {
                                    splitViewState.setActivePanel(currentPanelId)
                                }
                            }))

                            add(ContextMenuItem(isDivider = true))

                            // Favorite current workspace
                            val currentWorkspace = workspaceManager.currentWorkspace.value
                            if (currentWorkspace != null) {
                                val isFavorited = bookmarkManager.isFavorite(currentWorkspace.id)
                                add(ContextMenuItem(
                                    if (isFavorited) "Unfavorite Workspace" else "Favorite Workspace",
                                    if (isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    onClick = {
                                        if (isFavorited) {
                                            bookmarkManager.removeFavoriteWorkspace(currentWorkspace.id)
                                        } else {
                                            bookmarkManager.addFavoriteWorkspace(currentWorkspace.id, currentWorkspace.name)
                                        }
                                    }
                                ))
                            }
                        }
                    )
            )
        }
    }
    
    // New Tab Dialog
    if (showNewTabDialog) {
        NewTabDialog(
            onDismiss = {
                showNewTabDialog = false
                selectedTabType = null
            },
            initialTabType = selectedTabType,
            onCreateTab = { type, path ->
                when (type) {
                    TabType.URL -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        val fluckTab = ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo(
                            id = "fluck-$timestamp",
                            typeId = Fluck.typeId,
                            _title = "Loading...",
                            url = path
                        )
                        val tabIndex = addTab(fluckTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                    TabType.FILE -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        val fileName = path.substringAfterLast('/').ifEmpty { "untitled.txt" }
                        val fileIconInfo = FileIcons.forFile(fileName)
                        val editorTab = ai.rever.boss.components.plugin.tab_types.EditorTabInfo(
                            id = "editor-$timestamp",
                            title = fileName,
                            typeId = CodeEditor.typeId,
                            icon = fileIconInfo.icon,
                            tabIcon = TabIcon.Vector(fileIconInfo.icon, fileIconInfo.color),
                            filePath = path
                        )
                        val tabIndex = addTab(editorTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                    TabType.TERMINAL -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        // Get current project path for terminal working directory
                        val projectPath = ai.rever.boss.components.plugin.panels.left_top.ProjectState.selectedProject.value.path
                        val terminalTab = ai.rever.boss.components.plugin.tab_types.TerminalTabInfo(
                            id = "terminal-$timestamp",
                            typeId = ai.rever.boss.components.plugin.tab_types.TerminalTab.typeId,
                            title = "Terminal",
                            icon = ai.rever.boss.components.plugin.tab_types.TerminalTab.icon,
                            initialCommand = path.ifBlank { null },
                            workingDirectory = projectPath.ifEmpty { null }
                        )
                        val tabIndex = addTab(terminalTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                }
            }
        )
    }

    // Bookmark dialog
    if (showBookmarkDialog && tabToBookmark != null) {
        val collections by bookmarkManager.collections.collectAsState()
        val workspaces by workspaceManager.workspaces.collectAsState()
        BookmarkDialog(
            tabTitle = tabToBookmark!!.title,
            collections = collections,
            workspaces = workspaces,
            onDismiss = {
                showBookmarkDialog = false
                tabToBookmark = null
            },
            onConfirm = { collectionIds, workspacePanelMap ->
                val tabConfig = convertTabInfoToTabConfig(tabToBookmark!!)
                val workspace = workspaceManager.currentWorkspace.value

                // Convert workspacePanelMap to list of WorkspacePanelTarget
                val targetWorkspaces = workspacePanelMap.map { (workspaceName, panelId) ->
                    WorkspacePanelTarget(workspaceName = workspaceName, panelId = panelId)
                }

                // Create bookmark for each selected collection
                collectionIds.forEach { collectionId ->
                    val bookmark = Bookmark(
                        tabConfig = tabConfig,
                        workspaceName = workspace?.name ?: "Unknown",
                        targetWorkspaces = targetWorkspaces
                    )
                    val collection = collections.find { it.id == collectionId }
                    if (collection != null) {
                        bookmarkManager.addBookmark(collection.name, bookmark)
                    }
                }

                showBookmarkDialog = false
                tabToBookmark = null
            }
        )
    }

    // Remove bookmark confirmation dialog
    if (showRemoveBookmarkDialog && bookmarkToRemove != null) {
        RemoveBookmarkConfirmationDialog(
            bookmarkTitle = bookmarkToRemove!!.third,
            onDismiss = {
                showRemoveBookmarkDialog = false
                bookmarkToRemove = null
            },
            onConfirm = {
                bookmarkToRemove?.let { (collectionId, bookmarkId, _) ->
                    bookmarkManager.removeBookmark(collectionId, bookmarkId)
                }
                showRemoveBookmarkDialog = false
                bookmarkToRemove = null
            }
        )
    }
}

@Composable
fun BossTabsComponent.BossMainPanel(
    modifier: Modifier = Modifier,
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState? = null,
    currentPanelId: String? = null,
    tabDragComponent: TabDraggableComponent? = null,
    onTabDropResult: (TabDropResult) -> Unit = {},
    onShowSettings: (() -> Unit)? = null,
    onOpenProjectDialog: (() -> Unit)? = null,
    onNewProject: (() -> Unit)? = null
) {
    val focusRequester = remember { FocusRequester() }
    val isFocused = remember { mutableStateOf(false) }

    // Track the active panel state to force recomposition
    val activePanelId by splitViewState?.activePanelIdState ?: remember { mutableStateOf("") }
    val isActivePanel = activePanelId == currentPanelId


    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused.value = focusState.isFocused || focusState.hasFocus
                if ((focusState.isFocused || focusState.hasFocus) && currentPanelId != null) {
                    splitViewState?.setActivePanel(currentPanelId)
                }
            }
            .focusable()
            // Removed .clickable() - it was stealing focus from child components (terminals)
            // Panel activation is handled by .onFocusChanged() above
            .then(
                if (isActivePanel) {
                    Modifier.border(2.dp, MaterialTheme.colors.primary.copy(alpha = 0.5f))
                } else {
                    Modifier
                }
            )
    ) {
        BossMainTabBar(
            splitViewState = splitViewState,
            currentPanelId = currentPanelId,
            focusRequester = focusRequester,
            tabDragComponent = tabDragComponent,
            onTabDropResult = onTabDropResult
        )
        Divider(color = BossDarkBorder)
        BossMainPanelContent(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            splitViewState = splitViewState,
            currentPanelId = currentPanelId,
            onShowSettings = onShowSettings,
            onOpenProjectDialog = onOpenProjectDialog,
            onNewProject = onNewProject
        )
    }
}

/**
 * Main UI composable that displays the root component
 */
@Composable
fun BossTabsComponent.BossMainPanelContent(
    modifier: Modifier,
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState? = null,
    currentPanelId: String? = null,
    onShowSettings: (() -> Unit)? = null,
    onOpenProjectDialog: (() -> Unit)? = null,
    onNewProject: (() -> Unit)? = null
) {
    // Subscribe to tab state changes to trigger recomposition
    val tabsState = tabsState.subscribeAsState()

    // State for new tab dialog (needed for EmptyContent callbacks)
    var showNewTabDialog by remember { mutableStateOf(false) }
    var selectedTabType by remember { mutableStateOf<TabType?>(null) }

    // Coroutine scope for async operations
    val scope = rememberCoroutineScope()

    // Per-window project state for Dashboard
    val windowProjectState = LocalWindowProjectState.current
    val selectedProject by windowProjectState?.selectedProject?.collectAsState()
        ?: ProjectState.selectedProject.collectAsState()

    Box(modifier = modifier) {
        val activeTab = tabsState.value.activeTab
        val activeComponent = getActiveComponent()

        // Only render the active tab - hidden tabs would still receive input
        // Terminal state is preserved by TerminalStateRegistry (keyed by tab ID)
        if (activeTab != null && activeComponent != null) {
            key(activeTab.id) {
                activeComponent.Content()
            }
        } else {
            // Show Dashboard when no tabs are open
            Dashboard(
                onOpenFile = { filePath ->
                    splitViewState?.openFileInActivePanel(
                        filePath,
                        filePath.substringAfterLast('/').ifEmpty { "untitled" }
                    )
                },
                onOpenUrl = { url ->
                    splitViewState?.openUrlInActivePanel(url, "Loading...")
                },
                onOpenProject = { project ->
                    windowProjectState?.selectProject(project) ?: ProjectState.selectProject(project)
                },
                selectedProject = selectedProject,
                onNewTab = {
                    selectedTabType = null
                    showNewTabDialog = true
                },
                onNewTerminal = {
                    // Create a new terminal tab
                    val timestamp = Clock.System.now().toEpochMilliseconds()
                    val projectPath = ProjectState.selectedProject.value.path
                    val terminalTab = TerminalTabInfo(
                        id = "terminal-$timestamp",
                        typeId = ai.rever.boss.components.plugin.tab_types.TerminalTab.typeId,
                        title = "Terminal",
                        icon = ai.rever.boss.components.plugin.tab_types.TerminalTab.icon,
                        workingDirectory = projectPath.ifEmpty { null }
                    )
                    val tabIndex = addTab(terminalTab)
                    if (tabIndex >= 0) {
                        selectTab(tabIndex)
                    }
                },
                onNewWindow = {
                    WindowOperations.createNewWindow()
                },
                onOpenProjectDialog = {
                    // Use the callback passed from BossApp which has access to windowId
                    onOpenProjectDialog?.invoke()
                },
                onOpenFileDialog = {
                    selectedTabType = TabType.FILE
                    showNewTabDialog = true
                },
                onApplySplitTemplate = { template ->
                    // Find matching predefined workspace by template ID
                    val workspaceId = "workspace-${template.id}"
                    val matchingWorkspace = PredefinedWorkspaces.allWorkspaces.find { it.id == workspaceId }
                        ?: PredefinedWorkspaces.allWorkspaces.find { it.name == template.name }

                    // Always apply the workspace first (Issue #445)
                    // This ensures terminal + browser both open in split view
                    if (matchingWorkspace != null && splitViewState != null) {
                        workspaceManager.loadWorkspace(matchingWorkspace)
                        scope.launch {
                            applyWorkspace(matchingWorkspace, splitViewState)
                        }
                    } else {
                        applySplitTemplate(template, splitViewState, currentPanelId)
                    }
                },
                onActivatePlugin = { pluginId ->
                    // Plugin activation is handled via sidebar panels
                    // Dashboard displays available plugins but activation uses existing sidebar UI
                    println("[Dashboard] Plugin activation requested: $pluginId")
                },
                onShowSettings = onShowSettings,
                onNewProject = { onNewProject?.invoke() }
            )
        }
    }

    // New Tab Dialog (for EmptyContent interactions)
    if (showNewTabDialog) {
        NewTabDialog(
            onDismiss = {
                showNewTabDialog = false
                selectedTabType = null
            },
            initialTabType = selectedTabType,
            onCreateTab = { type, path ->
                when (type) {
                    TabType.URL -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        val fluckTab = ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo(
                            id = "fluck-$timestamp",
                            typeId = Fluck.typeId,
                            _title = "Loading...",
                            url = path
                        )
                        val tabIndex = addTab(fluckTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                    TabType.FILE -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        val fileName = path.substringAfterLast('/').ifEmpty { "untitled.txt" }
                        val fileIconInfo = FileIcons.forFile(fileName)
                        val editorTab = ai.rever.boss.components.plugin.tab_types.EditorTabInfo(
                            id = "editor-$timestamp",
                            title = fileName,
                            typeId = CodeEditor.typeId,
                            icon = fileIconInfo.icon,
                            tabIcon = TabIcon.Vector(fileIconInfo.icon, fileIconInfo.color),
                            filePath = path
                        )
                        val tabIndex = addTab(editorTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                    TabType.TERMINAL -> {
                        val timestamp = Clock.System.now().toEpochMilliseconds()
                        // Get current project path for terminal working directory
                        val projectPath = ai.rever.boss.components.plugin.panels.left_top.ProjectState.selectedProject.value.path
                        val terminalTab = ai.rever.boss.components.plugin.tab_types.TerminalTabInfo(
                            id = "terminal-$timestamp",
                            typeId = ai.rever.boss.components.plugin.tab_types.TerminalTab.typeId,
                            title = "Terminal",
                            icon = ai.rever.boss.components.plugin.tab_types.TerminalTab.icon,
                            initialCommand = path.ifBlank { null },
                            workingDirectory = projectPath.ifEmpty { null }
                        )
                        val tabIndex = addTab(terminalTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                }
            }
        )
    }
}

/**
 * Apply a split template to create a split view with pre-configured tabs.
 *
 * @param template The split template to apply
 * @param splitViewState The split view state (if available)
 * @param currentPanelId The current panel ID
 */
private fun BossTabsComponent.applySplitTemplate(
    template: SplitTemplate,
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState?,
    currentPanelId: String?
) {
    if (splitViewState == null || currentPanelId == null) return

    val projectPath = ProjectState.selectedProject.value.path.ifEmpty {
        System.getProperty("user.home")
    }

    // Process the template panels
    val panels = template.panels
    if (panels.isEmpty()) return

    // Get left and right panel configs
    val leftPanelConfig = panels.find { it.position == "left" }
    val rightPanelConfig = panels.find { it.position == "right" }

    // Create left panel tab first (in current panel)
    val leftTab = leftPanelConfig?.let { createTabFromConfig(it, projectPath) }
    val rightTab = rightPanelConfig?.let { createTabFromConfig(it, projectPath) }

    if (leftTab != null) {
        // Add left tab to current panel
        val leftIndex = addTab(leftTab)
        if (leftIndex >= 0) {
            selectTab(leftIndex)
        }

        // If there's a right panel, create a split
        if (rightTab != null) {
            splitViewState.splitPanel(
                panelId = currentPanelId,
                orientation = SplitOrientation.VERTICAL,
                tabToMove = rightTab
            )
        }
    } else if (rightTab != null) {
        // Only right panel specified, just add it
        val rightIndex = addTab(rightTab)
        if (rightIndex >= 0) {
            selectTab(rightIndex)
        }
    }
}

/**
 * Create a tab from template panel configuration.
 */
private fun createTabFromConfig(
    panelConfig: ai.rever.boss.dashboard.TemplatePanelConfig,
    projectPath: String
): ai.rever.boss.components.registery.TabInfo? {
    val timestamp = Clock.System.now().toEpochMilliseconds()

    return when (panelConfig.type) {
        "terminal" -> {
            val command = panelConfig.content.command?.let {
                SplitTemplatesManager.processPlaceholders(it, projectPath, null)
            }
            TerminalTabInfo(
                id = "terminal-$timestamp",
                typeId = ai.rever.boss.components.plugin.tab_types.TerminalTab.typeId,
                title = "Terminal",
                icon = ai.rever.boss.components.plugin.tab_types.TerminalTab.icon,
                initialCommand = command,
                workingDirectory = projectPath
            )
        }
        "browser" -> {
            val url = panelConfig.content.url?.let {
                SplitTemplatesManager.processPlaceholders(it, projectPath, null)
            } ?: "https://google.com"
            FluckTabInfo(
                id = "fluck-$timestamp",
                typeId = Fluck.typeId,
                _title = "Loading...",
                url = url
            )
        }
        "editor" -> {
            val filePath = panelConfig.content.filePath?.let {
                SplitTemplatesManager.processPlaceholders(it, projectPath, null)
            }
            if (filePath != null) {
                val fileName = filePath.substringAfterLast('/').ifEmpty { "untitled" }
                val fileIconInfo = FileIcons.forFile(fileName)
                EditorTabInfo(
                    id = "editor-$timestamp",
                    title = fileName,
                    typeId = CodeEditor.typeId,
                    icon = fileIconInfo.icon,
                    tabIcon = TabIcon.Vector(fileIconInfo.icon, fileIconInfo.color),
                    filePath = filePath
                )
            } else null
        }
        else -> null
    }
}

val createBossAppContext get() = DefaultComponentContext(LifecycleRegistry())

/**
 * Root component for the BOSS app using Decompose for navigation
 */
class BossTabsComponent(
    componentContext: ComponentContext,
    val tabRegistry: TabRegistry
) : ComponentContext by componentContext {

    private val tabComponents = mutableStateMapOf<String, TabComponentWithUI>()
    private val tabsNavigation = TabsNavigation<TabInfo>()

    // Expose tab state for UI
    val tabsState: Value<TabsNavigation.TabsState<TabInfo>> = tabsNavigation.state

    // Add a new tab
    fun addTab(config: TabInfo): Int {
        // Create component for this tab
        val component = tabRegistry.createTabComponent(config, this)
        
        if (component != null) {
            // Store component
            tabComponents[config.id] = component
            
            // Add to navigation
            return tabsNavigation.addTab(config)
        }
        
        return -1 // Failed to create component
    }

    // Remove a tab by index
    fun removeTab(index: Int) {
        val config = tabsState.value.tabs.getOrNull(index)
        config?.let {
            // Dispose the component if it has a dispose method
            val component = tabComponents.remove(it.id)
            if (component is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabComponent) {
                component.dispose()
            }

            // If this is a runner terminal, notify the service to clean up tracking
            // This handles the case where user closes the tab directly (not via Stop button)
            if (it.id.startsWith(RUNNER_TERMINAL_PREFIX)) {
                RunnerTerminalService.removeTerminal(it.id)
            }
        }
        tabsNavigation.removeTab(index)
    }

    // Remove a tab by ID - safer than index-based removal when state may have changed
    fun removeTabById(tabId: String) {
        val index = tabsState.value.tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) {
            removeTab(index)
        }
    }

    // Select a tab
    fun selectTab(index: Int) {
        tabsNavigation.selectTab(index)
    }

    // Move a tab from one position to another
    fun moveTab(fromIndex: Int, toIndex: Int) {
        tabsNavigation.moveTab(fromIndex, toIndex)
    }

    // Update a tab
    fun updateTab(index: Int, config: TabInfo) {
        tabsNavigation.updateTab(index, config)
    }

    // Get active tab component
    fun getActiveComponent(): TabComponentWithUI? {
        val activeTab = tabsState.value.activeTab ?: return null
        return tabComponents[activeTab.id]
    }
    
    // Get tab component by ID
    fun getComponentById(tabId: String): TabComponentWithUI? {
        return tabComponents[tabId]
    }


    // Get the currently selected tab
    fun getCurrentTab(): TabInfo? {
        return tabsState.value.activeTab
    }
    
    // Clear all tabs safely
    fun clearAllTabs() {
        // Remove tabs in reverse order to avoid index issues
        val tabCount = tabsState.value.tabs.size
        for (i in tabCount - 1 downTo 0) {
            removeTab(i)
        }
    }
    
    // Close other tabs (keep only the specified tab)
    fun closeOtherTabs(keepIndex: Int) {
        val tabs = tabsState.value.tabs
        if (keepIndex < 0 || keepIndex >= tabs.size) return
        
        // Remove tabs in reverse order to avoid index issues
        for (i in tabs.size - 1 downTo 0) {
            if (i != keepIndex) {
                removeTab(i)
            }
        }
    }
    
    // Close tabs to the right of the specified index
    fun closeTabsToRight(fromIndex: Int) {
        val tabs = tabsState.value.tabs
        if (fromIndex < 0 || fromIndex >= tabs.size - 1) return
        
        // Remove tabs from right to left to avoid index issues
        for (i in tabs.size - 1 downTo fromIndex + 1) {
            removeTab(i)
        }
    }
    
    // Close tabs to the left of the specified index
    fun closeTabsToLeft(fromIndex: Int) {
        if (fromIndex <= 0) return

        // Remove tabs from right to left to avoid index issues
        for (i in fromIndex - 1 downTo 0) {
            removeTab(i)
        }
    }

    // Close tab by URL (used for auto-closing download redirects)
    fun closeTabByUrl(url: String) {
        val tabs = tabsState.value.tabs

        // Find all tabs with matching URL (might be multiple)
        val indicesToRemove = mutableListOf<Int>()
        for (i in tabs.indices) {
            val tab = tabs[i]
            val tabUrl = when (tab) {
                is FluckTabInfo -> tab.currentUrl
                else -> null
            }

            if (tabUrl == url) {
                indicesToRemove.add(i)
                println("TabsComponent: Found tab to close at index $i with URL: $url")
            }
        }

        // Remove tabs in reverse order to avoid index issues
        for (i in indicesToRemove.sortedDescending()) {
            removeTab(i)
        }

        if (indicesToRemove.isNotEmpty()) {
            println("TabsComponent: Closed ${indicesToRemove.size} tab(s) with URL: $url")
        }
    }

    // Close the most recently opened tab (used for auto-closing download redirects)
    fun closeMostRecentTab() {
        val tabs = tabsState.value.tabs
        if (tabs.isNotEmpty()) {
            val lastIndex = tabs.size - 1
            println("TabsComponent: Closing most recent tab at index $lastIndex")
            removeTab(lastIndex)
        } else {
            println("TabsComponent: No tabs to close")
        }
    }
}

/**
 * Convert TabInfo to TabConfig for bookmark storage
 */
private fun convertTabInfoToTabConfig(tabInfo: TabInfo): TabConfig {
    return when (tabInfo) {
        is FluckTabInfo -> TabConfig(
            type = "browser",
            title = tabInfo.title,
            url = tabInfo.url,
            faviconCacheKey = tabInfo.faviconCacheKey
        )
        is EditorTabInfo -> TabConfig(
            type = "editor",
            title = tabInfo.title,
            filePath = tabInfo.filePath
        )
        is TerminalTabInfo -> TabConfig(
            type = "terminal",
            title = tabInfo.title
        )
        else -> TabConfig(
            type = "unknown",
            title = tabInfo.title
        )
    }
}

