package ai.rever.boss.plugin.panel.topofmind

import ai.rever.boss.plugin.api.SplitViewOperations
import ai.rever.boss.plugin.api.TabIcon
import ai.rever.boss.plugin.api.WorkspaceDataProvider
import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.lazyListScrollbar
import ai.rever.boss.plugin.search.BossSearchBar
import ai.rever.boss.plugin.workspace.LayoutWorkspace
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main content composable for the Top of Mind panel.
 *
 * @param splitViewOperations Operations for interacting with split view
 * @param workspaceDataProvider Provider for workspace data
 * @param collectAllActiveTabs Function to collect all active tabs
 * @param getAllPanelStates Function to get current panel states for reactivity
 * @param faviconLoader Function to load favicon for a tab
 * @param getTabUrl Function to get URL from a tab (for FluckTabInfo)
 * @param getFaviconCacheKey Function to get favicon cache key from a tab
 * @param getFallbackIcon Function to get fallback icon for a tab
 */
@Composable
fun TopOfMindContent(
    splitViewOperations: SplitViewOperations?,
    workspaceDataProvider: WorkspaceDataProvider?,
    collectAllActiveTabs: () -> List<ActiveTab>,
    getAllPanelStates: @Composable () -> List<Triple<String, Int, List<String>>>,
    faviconLoader: @Composable (String?) -> TabIcon.Image?,
    getTabUrl: (ActiveTab) -> String? = { null },
    getFaviconCacheKey: (ActiveTab) -> String? = { null },
    getFallbackIcon: (ActiveTab) -> ImageVector = { Icons.Outlined.Tab }
) {
    val activeTabs by TopOfMindStateHolder.activeTabs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showCurrentWorkspace by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Update active tabs whenever the split view state changes or tabs are added/removed
    LaunchedEffect(splitViewOperations, workspaceDataProvider) {
        if (splitViewOperations != null) {
            val tabs = collectAllActiveTabs()
            TopOfMindStateHolder.updateActiveTabs(tabs)

            // Initialize tree expansion state
            val treeNodes = TabTreeBuilder.buildTree(tabs, workspaceDataProvider, getTabUrl)
            TabTreeState.initializeDefaultExpansion(treeNodes)
        }
    }

    // Subscribe to real-time tab state changes from all panels with single debounced effect
    if (splitViewOperations != null) {
        val allPanelStates = getAllPanelStates()

        // Single LaunchedEffect that triggers on any panel state change
        LaunchedEffect(allPanelStates) {
            // Debounce to avoid rapid successive updates
            delay(100)

            // Single point of tab collection and state update
            val updatedTabs = collectAllActiveTabs()
            TopOfMindStateHolder.updateActiveTabs(updatedTabs)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2B2D30))
            .padding(12.dp)
    ) {
        // Search bar (styled like browser URL bar)
        BossSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search active tabs...",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Header with toggle for current workspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Running Workspaces",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = if (showCurrentWorkspace) "Hide Current" else "Show Current",
                fontSize = 9.sp,
                color = MaterialTheme.colors.primary.copy(alpha = 0.8f),
                modifier = Modifier.clickable { showCurrentWorkspace = !showCurrentWorkspace }
            )
        }

        // Build tree structure from active tabs
        val filteredTabs = if (showCurrentWorkspace) {
            activeTabs
        } else {
            // Filter out current workspace tabs
            val currentWorkspaceId = workspaceDataProvider?.currentWorkspace?.value?.id
            activeTabs.filter { it.workspaceId != currentWorkspaceId }
        }
        val treeNodes = TabTreeBuilder.buildTree(filteredTabs, workspaceDataProvider, getTabUrl)

        // Apply search filter to tree
        val filteredTreeNodes = if (searchQuery.isBlank()) {
            treeNodes
        } else {
            TabTreeBuilder.filterTreeNodes(treeNodes, searchQuery, getTabUrl)
        }

        if (filteredTreeNodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "No active tabs" else "No tabs matching \"$searchQuery\"",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(
                        listState = listState,
                        direction = Orientation.Vertical,
                        config = getPanelScrollbarConfig()
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredTreeNodes) { treeNode ->
                    TreeNodeItem(
                        node = treeNode,
                        workspaceDataProvider = workspaceDataProvider,
                        splitViewOperations = splitViewOperations,
                        faviconLoader = faviconLoader,
                        getFaviconCacheKey = getFaviconCacheKey,
                        getFallbackIcon = getFallbackIcon,
                        getTabUrl = getTabUrl,
                        onTabClick = { activeTab ->
                            if (splitViewOperations != null && workspaceDataProvider != null) {
                                coroutineScope.launch {
                                    // Get current workspace
                                    val currentWorkspace = workspaceDataProvider.currentWorkspace.value

                                    if (currentWorkspace?.id == activeTab.workspaceId) {
                                        // Tab is in current workspace, just focus it
                                        splitViewOperations.selectTabInPanel(activeTab.tabInfo.id, activeTab.panelId)
                                    } else {
                                        // Tab is in different workspace - switch workspaces
                                        val targetWorkspace = workspaceDataProvider.workspaces.value.find {
                                            it.id == activeTab.workspaceId
                                        }

                                        if (targetWorkspace != null) {
                                            // Preserve current state before switching
                                            if (currentWorkspace != null && currentWorkspace.id.isNotEmpty()) {
                                                splitViewOperations.preserveCurrentState(currentWorkspace.id, currentWorkspace.name)
                                            }

                                            // Load and apply the target workspace
                                            workspaceDataProvider.loadWorkspace(targetWorkspace)
                                            splitViewOperations.applyWorkspace(targetWorkspace)

                                            // Focus the specific tab after a short delay
                                            delay(100)
                                            splitViewOperations.selectTabInPanel(activeTab.tabInfo.id, activeTab.panelId)
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeNodeItem(
    node: TabTreeNode,
    workspaceDataProvider: WorkspaceDataProvider?,
    splitViewOperations: SplitViewOperations?,
    faviconLoader: @Composable (String?) -> TabIcon.Image?,
    getFaviconCacheKey: (ActiveTab) -> String?,
    getFallbackIcon: (ActiveTab) -> ImageVector,
    getTabUrl: (ActiveTab) -> String?,
    onTabClick: (ActiveTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val expandedNodes by TabTreeState.expandedNodes.collectAsState()
    val isExpanded = expandedNodes.contains(node.id)
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier) {
        when (node) {
            is TabTreeNode.WorkspaceNode -> {
                WorkspaceFolderItem(
                    node = node,
                    isExpanded = isExpanded,
                    onToggleExpand = { TabTreeState.toggleExpansion(node.id) },
                    workspaceDataProvider = workspaceDataProvider,
                    onWorkspaceClick = {
                        // Switch to this workspace
                        if (splitViewOperations != null && workspaceDataProvider != null) {
                            coroutineScope.launch {
                                val currentWorkspace = workspaceDataProvider.currentWorkspace.value
                                val targetWorkspace = workspaceDataProvider.workspaces.value.find {
                                    it.id == node.workspaceId
                                }

                                if (targetWorkspace != null && currentWorkspace?.id != node.workspaceId) {
                                    // Preserve current state before switching
                                    if (currentWorkspace != null && currentWorkspace.id.isNotEmpty()) {
                                        splitViewOperations.preserveCurrentState(currentWorkspace.id, currentWorkspace.name)
                                    }

                                    // Load and apply the target workspace
                                    workspaceDataProvider.loadWorkspace(targetWorkspace)
                                    splitViewOperations.applyWorkspace(targetWorkspace)
                                }
                            }
                        }
                    }
                )

                if (isExpanded) {
                    // Render tab structure with sections
                    RenderTabStructure(
                        structure = node.tabStructure,
                        onTabClick = onTabClick,
                        workspaceId = node.workspaceId,
                        faviconLoader = faviconLoader,
                        getFaviconCacheKey = getFaviconCacheKey,
                        getFallbackIcon = getFallbackIcon,
                        getTabUrl = getTabUrl
                    )
                }
            }

            is TabTreeNode.TabNode -> {
                // Individual tab nodes (used for search results or backward compatibility)
                TabCardItem(
                    node = node,
                    onTabClick = { onTabClick(node.activeTab) },
                    faviconLoader = faviconLoader,
                    getFaviconCacheKey = getFaviconCacheKey,
                    getFallbackIcon = getFallbackIcon,
                    getTabUrl = getTabUrl
                )
            }
        }
    }
}

@Composable
private fun WorkspaceFolderItem(
    node: TabTreeNode.WorkspaceNode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onWorkspaceClick: () -> Unit,
    workspaceDataProvider: WorkspaceDataProvider?
) {
    val modifiedWorkspaces by TabTreeState.modifiedWorkspaces.collectAsState()
    val isModified = modifiedWorkspaces.contains(node.workspaceId)

    // Check if this is the currently active workspace
    val currentWorkspace = workspaceDataProvider?.currentWorkspace?.value
    val isActive = currentWorkspace?.id == node.workspaceId

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .then(
                if (isActive) {
                    Modifier.background(
                        MaterialTheme.colors.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(4.dp)
                    ).padding(2.dp)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand/collapse button area
        Icon(
            if (isExpanded) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(16.dp)
                .clickable { onToggleExpand() },
            tint = Color.Gray.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Workspace content area (clickable)
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onWorkspaceClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Workspaces,
                contentDescription = "Workspace",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colors.primary.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${node.name}${if (isModified) " •" else ""}${if (isActive) " (Active)" else ""}",
                fontSize = 12.sp,
                color = if (isActive) {
                    MaterialTheme.colors.primary
                } else {
                    MaterialTheme.colors.onSurface
                },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Show a modified indicator
            if (isModified) {
                Text(
                    text = "●",
                    fontSize = 8.sp,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TabCardItem(
    node: TabTreeNode.TabNode,
    onTabClick: () -> Unit,
    faviconLoader: @Composable (String?) -> TabIcon.Image?,
    getFaviconCacheKey: (ActiveTab) -> String?,
    getFallbackIcon: (ActiveTab) -> ImageVector,
    getTabUrl: (ActiveTab) -> String?,
    indentation: Dp = 44.dp
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentation, end = 24.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onTabClick() },
        color = Color(0xFF3C3F43),
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab icon with favicon support
            val faviconCacheKey = getFaviconCacheKey(node.activeTab)
            val fallbackIcon = getFallbackIcon(node.activeTab)

            FaviconIcon(
                faviconCacheKey = faviconCacheKey,
                fallbackIcon = fallbackIcon,
                faviconLoader = faviconLoader,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Tab title
                Text(
                    text = node.activeTab.tabInfo.title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Tab subtitle (URL for browser, type for others)
                val url = getTabUrl(node.activeTab)
                val subtitle = url?.takeIf { it.isNotEmpty() } ?: node.activeTab.tabInfo.typeId.typeId

                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 9.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Favicon icon with fallback to Material icon
 * Displays cached favicon if available, otherwise shows fallback icon
 */
@Composable
private fun FaviconIcon(
    faviconCacheKey: String?,
    fallbackIcon: ImageVector,
    faviconLoader: @Composable (String?) -> TabIcon.Image?,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val tabIcon = faviconLoader(faviconCacheKey)

    when {
        tabIcon != null -> {
            // Display actual favicon
            Image(
                painter = tabIcon.asPainter(),
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
 * Split section header with collapse/expand functionality
 */
@Composable
private fun SplitSectionHeader(
    sectionName: String,
    level: Int,
    sectionKey: String,
    isExpanded: Boolean,
    onToggleExpansion: () -> Unit
) {
    val indentation = (44 + (level * 16)).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpansion() }
            .padding(start = indentation, end = 24.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chevron icon for collapse/expand
        Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse section" else "Expand section",
            modifier = Modifier.size(14.dp),
            tint = Color(0xFF9CA3AF)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Left divider
        Box(
            modifier = Modifier
                .width(16.dp)
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
    onTabClick: (ActiveTab) -> Unit,
    workspaceId: String,
    faviconLoader: @Composable (String?) -> TabIcon.Image?,
    getFaviconCacheKey: (ActiveTab) -> String?,
    getFallbackIcon: (ActiveTab) -> ImageVector,
    getTabUrl: (ActiveTab) -> String?,
    sectionPath: String = "",
    baseIndentation: Int = 44
) {
    val expandedSections by TabTreeState.expandedSections.collectAsState()

    structure.forEach { item ->
        when (item) {
            is WorkspaceTabStructure.TabItem -> {
                TabCardItem(
                    node = TabTreeNode.TabNode(
                        id = item.activeTab.tabInfo.id,
                        name = item.activeTab.tabInfo.title,
                        level = 0,
                        activeTab = item.activeTab
                    ),
                    onTabClick = { onTabClick(item.activeTab) },
                    faviconLoader = faviconLoader,
                    getFaviconCacheKey = getFaviconCacheKey,
                    getFallbackIcon = getFallbackIcon,
                    getTabUrl = getTabUrl,
                    indentation = baseIndentation.dp
                )
            }

            is WorkspaceTabStructure.SplitSection -> {
                // Generate unique section key for expansion state
                val currentPath = if (sectionPath.isEmpty()) item.sectionName else "$sectionPath/${item.sectionName}"
                val sectionKey = "$workspaceId:$currentPath"
                val isExpanded = expandedSections.contains(sectionKey)

                SplitSectionHeader(
                    sectionName = item.sectionName,
                    level = item.level,
                    sectionKey = sectionKey,
                    isExpanded = isExpanded,
                    onToggleExpansion = { TabTreeState.toggleSectionExpansion(sectionKey) }
                )

                // Only render children if section is expanded
                if (isExpanded) {
                    RenderTabStructure(
                        structure = item.children,
                        onTabClick = onTabClick,
                        workspaceId = workspaceId,
                        faviconLoader = faviconLoader,
                        getFaviconCacheKey = getFaviconCacheKey,
                        getFallbackIcon = getFallbackIcon,
                        getTabUrl = getTabUrl,
                        sectionPath = currentPath,
                        baseIndentation = baseIndentation + (item.level * 16)
                    )
                }
            }
        }
    }
}
