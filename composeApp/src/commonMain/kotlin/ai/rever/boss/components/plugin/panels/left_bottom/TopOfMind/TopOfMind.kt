package ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind

import ai.rever.boss.cache.loadFaviconFromCache
import ai.rever.boss.components.bars.PanelScrollbarConfig
import ai.rever.boss.components.bars.lazyListScrollbar
import ai.rever.boss.components.common.BossSearchBar
import ai.rever.boss.components.workspaces.WorkspaceManager
import ai.rever.boss.components.workspaces.applyWorkspace
import ai.rever.boss.components.workspaces.BreadcrumbConfig
import ai.rever.boss.components.workspaces.SplitConfig
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.components.registery.TabIcon
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.window_panel.SplitViewState
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data class for active tabs (all types)
data class ActiveTab(
    val tabInfo: TabInfo,
    val workspaceId: String,
    val workspaceName: String,
    val panelId: String,
    val windowId: String, // Window identifier for multi-window support
    val splitPosition: String? = null // "Left", "Right", "Top", "Bottom", or null for single panel
)

// Hierarchical structure for workspace tab sections
sealed class WorkspaceTabStructure {
    data class TabItem(
        val activeTab: ActiveTab
    ) : WorkspaceTabStructure()

    data class SplitSection(
        val sectionName: String,  // "Left", "Right", "Top", "Bottom"
        val children: List<WorkspaceTabStructure>,
        val level: Int = 0
    ) : WorkspaceTabStructure()
}

// Simplified tree structure for organizing tabs (workspace level only)
sealed class TabTreeNode {
    abstract val id: String
    abstract val name: String
    abstract val level: Int

    data class WorkspaceNode(
        override val id: String,
        override val name: String,
        override val level: Int = 0,
        val workspaceId: String,
        var isExpanded: Boolean = true,
        val tabStructure: List<WorkspaceTabStructure> = emptyList()
    ) : TabTreeNode()

    data class TabNode(
        override val id: String,
        override val name: String,
        override val level: Int,
        val activeTab: ActiveTab
    ) : TabTreeNode()
}

// State management for tree expansion
object TabTreeState {
    private val _expandedNodes = MutableStateFlow<Set<String>>(emptySet())
    val expandedNodes: StateFlow<Set<String>> = _expandedNodes
    
    // Track which workspaces have been modified
    private val _modifiedWorkspaces = MutableStateFlow<Set<String>>(emptySet())
    val modifiedWorkspaces: StateFlow<Set<String>> = _modifiedWorkspaces
    
    fun toggleExpansion(nodeId: String) {
        val current = _expandedNodes.value.toMutableSet()
        if (current.contains(nodeId)) {
            current.remove(nodeId)
        } else {
            current.add(nodeId)
        }
        _expandedNodes.value = current
    }

    fun initializeDefaultExpansion(nodes: List<TabTreeNode>) {
        // Expand all workspace nodes by default
        val workspaceNodes = nodes.filterIsInstance<TabTreeNode.WorkspaceNode>()
        _expandedNodes.value = workspaceNodes.map { it.id }.toSet()
    }
    
    fun markWorkspaceAsModified(workspaceId: String) {
        val current = _modifiedWorkspaces.value.toMutableSet()
        current.add(workspaceId)
        _modifiedWorkspaces.value = current
    }
    
    fun markWorkspaceAsSaved(workspaceId: String) {
        val current = _modifiedWorkspaces.value.toMutableSet()
        current.remove(workspaceId)
        _modifiedWorkspaces.value = current
    }

    fun isWorkspaceModified(workspaceId: String): Boolean {
        return _modifiedWorkspaces.value.contains(workspaceId)
    }

    // Track expanded sections (workspace:sectionPath) - sections collapsed by default
    private val _expandedSections = MutableStateFlow<Set<String>>(emptySet())
    val expandedSections: StateFlow<Set<String>> = _expandedSections

    fun toggleSectionExpansion(sectionKey: String) {
        val current = _expandedSections.value.toMutableSet()
        if (current.contains(sectionKey)) {
            current.remove(sectionKey)
        } else {
            current.add(sectionKey)
        }
        _expandedSections.value = current
    }

    fun isSectionExpanded(sectionKey: String): Boolean {
        return _expandedSections.value.contains(sectionKey)
    }
}

// Utility to build tree structure from active tabs
object TabTreeBuilder {
    /**
     * Extract all panel IDs from layout in depth-first order
     */
    private fun extractPanelIds(layout: SplitConfig): List<String> {
        return when (layout) {
            is SplitConfig.SinglePanel -> listOf(layout.panel.id)
            is SplitConfig.VerticalSplit -> extractPanelIds(layout.left) + extractPanelIds(layout.right)
            is SplitConfig.HorizontalSplit -> extractPanelIds(layout.top) + extractPanelIds(layout.bottom)
        }
    }

    /**
     * Build hierarchical tab structure from workspace layout and panel assignments
     * Uses position-based mapping instead of ID-based filtering to handle randomly-generated panel IDs
     */
    private fun buildTabStructure(
        tabs: List<ActiveTab>,
        layout: SplitConfig?,
        panelIdMapping: Map<String, String>,
        level: Int = 0
    ): List<WorkspaceTabStructure> {
        if (layout == null || tabs.isEmpty()) {
            // No layout info or no tabs - return flat list
            return tabs.map { WorkspaceTabStructure.TabItem(it) }
        }

        return when (layout) {
            is SplitConfig.SinglePanel -> {
                // Map layout panel ID to runtime panel ID
                val runtimePanelId = panelIdMapping[layout.panel.id]
                val panelTabs = if (runtimePanelId != null) {
                    tabs.filter { it.panelId == runtimePanelId }
                } else {
                    // Fallback: if mapping fails, try direct ID match
                    tabs.filter { it.panelId == layout.panel.id }
                }
                panelTabs.map { WorkspaceTabStructure.TabItem(it) }
            }

            is SplitConfig.VerticalSplit -> {
                listOf(
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Left",
                        children = buildTabStructure(tabs, layout.left, panelIdMapping, level + 1),
                        level = level
                    ),
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Right",
                        children = buildTabStructure(tabs, layout.right, panelIdMapping, level + 1),
                        level = level
                    )
                )
            }

            is SplitConfig.HorizontalSplit -> {
                listOf(
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Top",
                        children = buildTabStructure(tabs, layout.top, panelIdMapping, level + 1),
                        level = level
                    ),
                    WorkspaceTabStructure.SplitSection(
                        sectionName = "Bottom",
                        children = buildTabStructure(tabs, layout.bottom, panelIdMapping, level + 1),
                        level = level
                    )
                )
            }
        }
    }

    fun buildTree(activeTabs: List<ActiveTab>, workspaceManager: WorkspaceManager?): List<TabTreeNode> {
        val workspaceGroups = activeTabs.groupBy { it.workspaceId }
        val rootNodes = mutableListOf<TabTreeNode>()

        workspaceGroups.forEach { (workspaceId, tabs) ->
            // Use the workspace name from the first tab (they should all be the same)
            val workspaceName = tabs.firstOrNull()?.workspaceName ?: "Unknown"

            // Get workspace layout from WorkspaceManager
            val workspace = workspaceManager?.workspaces?.value?.find { it.id == workspaceId }
            val layout = workspace?.layout

            // Create panel ID mapping: layout panel ID -> runtime panel ID
            // Match panels by their position in depth-first traversal
            val panelIdMapping = if (layout != null) {
                val layoutPanelIds = extractPanelIds(layout)
                val runtimePanelIds = tabs.map { it.panelId }.distinct()

                println("🔍 [TopOfMind] Panel ID mapping for workspace '$workspaceName':")
                println("  Layout panel IDs: $layoutPanelIds")
                println("  Runtime panel IDs: $runtimePanelIds")

                // Validate panel count matches
                if (layoutPanelIds.size != runtimePanelIds.size) {
                    println("⚠️ [TopOfMind] Panel count mismatch! Layout: ${layoutPanelIds.size}, Runtime: ${runtimePanelIds.size}")
                    println("  This may indicate workspace layout has changed. Falling back to flat layout.")
                    // Fallback: return empty map to trigger flat layout rendering
                    emptyMap()
                } else {
                    // Map layout panel IDs to runtime panel IDs by position
                    val mapping = layoutPanelIds.zip(runtimePanelIds).toMap()
                    println("  Mapping: $mapping")
                    mapping
                }
            } else {
                emptyMap()
            }

            // Build tab structure based on layout with panel ID mapping
            val tabStructure = buildTabStructure(tabs, layout, panelIdMapping)

            val workspaceNode = TabTreeNode.WorkspaceNode(
                id = "workspace-$workspaceId",
                name = workspaceName,
                workspaceId = workspaceId,
                level = 0,
                tabStructure = tabStructure
            )

            rootNodes.add(workspaceNode)
        }

        return rootNodes
    }

    // Filter tab structure based on search query
    private fun filterTabStructure(structure: List<WorkspaceTabStructure>, searchQuery: String): List<WorkspaceTabStructure> {
        return structure.mapNotNull { item ->
            when (item) {
                is WorkspaceTabStructure.TabItem -> {
                    val tabMatches = item.activeTab.tabInfo.title.contains(searchQuery, ignoreCase = true) ||
                        (item.activeTab.tabInfo is FluckTabInfo &&
                         item.activeTab.tabInfo.url.contains(searchQuery, ignoreCase = true))

                    if (tabMatches) item else null
                }

                is WorkspaceTabStructure.SplitSection -> {
                    val matchingChildren = filterTabStructure(item.children, searchQuery)
                    if (matchingChildren.isNotEmpty()) {
                        item.copy(children = matchingChildren)
                    } else null
                }
            }
        }
    }

    // Filter tree nodes based on search query
    fun filterTreeNodes(nodes: List<TabTreeNode>, searchQuery: String): List<TabTreeNode> {
        return nodes.mapNotNull { node ->
            when (node) {
                is TabTreeNode.WorkspaceNode -> {
                    val filteredStructure = filterTabStructure(node.tabStructure, searchQuery)
                    val workspaceMatches = node.name.contains(searchQuery, ignoreCase = true)

                    if (workspaceMatches || filteredStructure.isNotEmpty()) {
                        node.copy(tabStructure = filteredStructure)
                    } else null
                }

                is TabTreeNode.TabNode -> {
                    val tabMatches = node.activeTab.tabInfo.title.contains(searchQuery, ignoreCase = true) ||
                        (node.activeTab.tabInfo is FluckTabInfo &&
                         node.activeTab.tabInfo.url.contains(searchQuery, ignoreCase = true))

                    if (tabMatches) node else null
                }
            }
        }
    }
}

// Data class for breadcrumb navigation
data class BreadcrumbItem(
    val text: String,
    val type: BreadcrumbType,
    val clickable: Boolean = true,
    val onClick: (() -> Unit)? = null
)

enum class BreadcrumbType {
    WORKSPACE,
    PANEL,
    TAB,
    SEPARATOR
}

// Breadcrumb utility functions
object BreadcrumbUtils {
    fun createBreadcrumb(
        activeTab: ActiveTab,
        config: BreadcrumbConfig,
        onWorkspaceClick: () -> Unit,
        onTabClick: () -> Unit
    ): List<BreadcrumbItem> {
        val items = mutableListOf<BreadcrumbItem>()

        if (config.showWorkspacePath) {
            // Add workspace name
            items.add(
                BreadcrumbItem(
                    text = truncateText(activeTab.workspaceName, config.maxLength / 3),
                    type = BreadcrumbType.WORKSPACE,
                    onClick = onWorkspaceClick
                )
            )

            // Add separator
            items.add(
                BreadcrumbItem(
                    text = config.separator,
                    type = BreadcrumbType.SEPARATOR,
                    clickable = false
                )
            )
        }

        if (config.showTabPath) {
            // Add tab info
            val tabText = when (val tabInfo = activeTab.tabInfo) {
                is FluckTabInfo -> {
                    if (tabInfo.url.isNotEmpty()) {
                        "${tabInfo.title} (${getDomainFromUrl(tabInfo.url)})"
                    } else {
                        tabInfo.title
                    }
                }
                else -> "${activeTab.tabInfo.title} (${activeTab.tabInfo.typeId.typeId})"
            }

            items.add(
                BreadcrumbItem(
                    text = truncateText(tabText, config.maxLength * 2 / 3),
                    type = BreadcrumbType.TAB,
                    onClick = onTabClick
                )
            )
        }

        return items
    }

    private fun truncateText(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) {
            text
        } else {
            "${text.take(maxLength - 3)}..."
        }
    }

    private fun getDomainFromUrl(url: String): String {
        return try {
            val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val domain = cleanUrl.substringAfter("://").substringBefore("/")
            domain.removePrefix("www.")
        } catch (e: Exception) {
            url
        }
    }
}

// Global state for tracking all active tabs
object TopOfMindState {
    private val _activeTabs = MutableStateFlow<List<ActiveTab>>(emptyList())
    val activeTabs: StateFlow<List<ActiveTab>> = _activeTabs
    
    fun updateActiveTabs(tabs: List<ActiveTab>) {
        _activeTabs.value = tabs
    }

}

object TopOfMindInfo : PanelInfo {
    override val id = PanelId("top-of-mind", 5)
    override val displayName = "Top of mind"
    override val icon = Icons.Outlined.Language
    override val defaultSlotPosition = left.top.bottom
}

class TopOfMindComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        val splitViewState = LocalSplitViewState.current
        val workspaceManager = LocalWorkspaceManager.current
        TopOfMindContent(splitViewState, workspaceManager)
    }
}

@Composable
fun TopOfMindContent(
    splitViewState: SplitViewState?,
    workspaceManager: WorkspaceManager?
) {
    val activeTabs by TopOfMindState.activeTabs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showCurrentWorkspace by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Update active tabs whenever the split view state changes or tabs are added/removed
    LaunchedEffect(splitViewState, workspaceManager) {
        if (splitViewState != null) {
            val tabs = splitViewState.collectAllActiveTabs(workspaceManager)
            TopOfMindState.updateActiveTabs(tabs)

            // Initialize tree expansion state
            val treeNodes = TabTreeBuilder.buildTree(tabs, workspaceManager)
            TabTreeState.initializeDefaultExpansion(treeNodes)
        }
    }
    
    // Subscribe to real-time tab state changes from all panels with single debounced effect
    if (splitViewState != null) {
        val allPanels = splitViewState.getAllPanels()

        // Collect tab states from all panels as a single dependency
        val allPanelStates = allPanels.map { panel ->
            val tabsState by panel.tabsComponent.tabsState.subscribeAsState()
            // Create a snapshot of panel ID + tab count + tab identities
            Triple(
                panel.id,
                tabsState.tabs.size,
                tabsState.tabs.map { tab -> tab.id + tab.title }
            )
        }

        // Single LaunchedEffect that triggers on any panel state change
        LaunchedEffect(allPanelStates) {
            // Debounce to avoid rapid successive updates
            delay(100)

            // Single point of tab collection and state update
            val updatedTabs = splitViewState.collectAllActiveTabs(workspaceManager)
            TopOfMindState.updateActiveTabs(updatedTabs)
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
            val currentWorkspaceId = workspaceManager?.currentWorkspace?.value?.id
            activeTabs.filter { it.workspaceId != currentWorkspaceId }
        }
        val treeNodes = TabTreeBuilder.buildTree(filteredTabs, workspaceManager)
        
        // Apply search filter to tree
        val filteredTreeNodes = if (searchQuery.isBlank()) {
            treeNodes
        } else {
            TabTreeBuilder.filterTreeNodes(treeNodes, searchQuery)
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
                        config = PanelScrollbarConfig
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredTreeNodes) { treeNode ->
                    TreeNodeItem(
                        node = treeNode,
                        workspaceManager = workspaceManager,
                        splitViewState = splitViewState,
                        onTabClick = { activeTab ->
                            if (splitViewState != null && workspaceManager != null) {
                                coroutineScope.launch {
                                    // Get current workspace
                                    val currentWorkspace = workspaceManager.currentWorkspace.value
                                    
                                    if (currentWorkspace?.id == activeTab.workspaceId) {
                                        // Tab is in current workspace, just focus it
                                        splitViewState.selectTabInPanel(activeTab.tabInfo.id, activeTab.panelId)
                                    } else {
                                        // Tab is in different workspace - switch workspaces
                                        val targetWorkspace = workspaceManager.workspaces.value.find { 
                                            it.id == activeTab.workspaceId 
                                        }
                                        
                                        if (targetWorkspace != null) {
                                            // Preserve current state before switching
                                            if (currentWorkspace != null && currentWorkspace.id.isNotEmpty()) {
                                                splitViewState.preserveCurrentState(currentWorkspace.id, currentWorkspace.name)
                                            }
                                            
                                            // Load and apply the target workspace
                                            workspaceManager.loadWorkspace(targetWorkspace)
                                            applyWorkspace(targetWorkspace, splitViewState)
                                            
                                            // Focus the specific tab after a short delay
                                            delay(100)
                                            splitViewState.selectTabInPanel(activeTab.tabInfo.id, activeTab.panelId)
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
    workspaceManager: WorkspaceManager?,
    splitViewState: SplitViewState?,
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
                    workspaceManager = workspaceManager,
                    onWorkspaceClick = {
                        // Switch to this workspace
                        if (splitViewState != null && workspaceManager != null) {
                            coroutineScope.launch {
                                val currentWorkspace = workspaceManager.currentWorkspace.value
                                val targetWorkspace = workspaceManager.workspaces.value.find {
                                    it.id == node.workspaceId
                                }

                                if (targetWorkspace != null && currentWorkspace?.id != node.workspaceId) {
                                    // Preserve current state before switching
                                    if (currentWorkspace != null && currentWorkspace.id.isNotEmpty()) {
                                        splitViewState.preserveCurrentState(currentWorkspace.id, currentWorkspace.name)
                                    }

                                    // Load and apply the target workspace
                                    workspaceManager.loadWorkspace(targetWorkspace)
                                    applyWorkspace(targetWorkspace, splitViewState)
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
                        workspaceId = node.workspaceId
                    )
                }
            }

            is TabTreeNode.TabNode -> {
                // Individual tab nodes (used for search results or backward compatibility)
                TabCardItem(
                    node = node,
                    onTabClick = { onTabClick(node.activeTab) }
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
    workspaceManager: WorkspaceManager?
) {
    val modifiedWorkspaces by TabTreeState.modifiedWorkspaces.collectAsState()
    val isModified = modifiedWorkspaces.contains(node.workspaceId)
    
    // Check if this is the currently active workspace
    val currentWorkspace = workspaceManager?.currentWorkspace?.value
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
            val faviconCacheKey = (node.activeTab.tabInfo as? FluckTabInfo)?.faviconCacheKey
            val fallbackIcon = when (node.activeTab.tabInfo) {
                is FluckTabInfo -> Icons.Outlined.Language
                else -> Icons.Outlined.Tab
            }

            FaviconIcon(
                faviconCacheKey = faviconCacheKey,
                fallbackIcon = fallbackIcon,
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
                val subtitle = when (val tabInfo = node.activeTab.tabInfo) {
                    is FluckTabInfo -> tabInfo.url
                    else -> tabInfo.typeId.typeId
                }
                
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
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
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
                        sectionPath = currentPath,
                        baseIndentation = baseIndentation + (item.level * 16)
                    )
                }
            }
        }
    }
}


fun DefaultPlugin.registerTopOfMind() = panelRegistry.registerPanel(TopOfMindInfo) {
    ctx, panelInfo -> TopOfMindComponent(ctx, panelInfo)
}
