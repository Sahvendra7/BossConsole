package ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind

import ai.rever.boss.components.workspaces.WorkspaceManager
import ai.rever.boss.components.workspaces.applyWorkspace
import ai.rever.boss.components.workspaces.BreadcrumbConfig
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.window_panel.SplitViewState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Data class for active tabs (all types)
data class ActiveTab(
    val tabInfo: TabInfo,
    val workspaceId: String,
    val workspaceName: String,
    val panelId: String
)

// Hierarchical tree structure for organizing tabs
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
        val children: MutableList<TabTreeNode> = mutableListOf()
    ) : TabTreeNode()
    
    data class SplitNode(
        override val id: String,
        override val name: String,
        override val level: Int,
        val splitType: String, // "vertical", "horizontal", "panel"
        val panelId: String? = null,
        var isExpanded: Boolean = true,
        val children: MutableList<TabTreeNode> = mutableListOf()
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
}

// Utility to build tree structure from active tabs
object TabTreeBuilder {
    fun buildTree(activeTabs: List<ActiveTab>): List<TabTreeNode> {
        val workspaceGroups = activeTabs.groupBy { it.workspaceId }
        val rootNodes = mutableListOf<TabTreeNode>()
        
        workspaceGroups.forEach { (workspaceId, tabs) ->
            // Use the workspace name from the first tab (they should all be the same)
            val workspaceName = tabs.firstOrNull()?.workspaceName ?: "Unknown"
            
            val workspaceNode = TabTreeNode.WorkspaceNode(
                id = "workspace-$workspaceId",
                name = workspaceName,
                workspaceId = workspaceId,
                level = 0
            )
            
            // Group tabs by panel for this workspace
            val panelGroups = tabs.groupBy { it.panelId }
            
            panelGroups.forEach { (panelId, panelTabs) ->
                val splitNode = TabTreeNode.SplitNode(
                    id = "panel-$workspaceId-$panelId", // Make panel IDs unique per workspace
                    name = "Panel $panelId",
                    level = 1,
                    splitType = "panel",
                    panelId = panelId
                )
                
                // Add individual tabs to this panel
                panelTabs.forEach { activeTab ->
                    val tabNode = TabTreeNode.TabNode(
                        id = "tab-${activeTab.workspaceId}-${activeTab.tabInfo.id}", // Make tab IDs unique per workspace
                        name = activeTab.tabInfo.title,
                        level = 2,
                        activeTab = activeTab
                    )
                    splitNode.children.add(tabNode)
                }
                
                workspaceNode.children.add(splitNode)
            }
            
            rootNodes.add(workspaceNode)
        }
        
        return rootNodes
    }
    
    // Filter tree nodes based on search query
    fun filterTreeNodes(nodes: List<TabTreeNode>, searchQuery: String): List<TabTreeNode> {
        return nodes.mapNotNull { node ->
            when (node) {
                is TabTreeNode.WorkspaceNode -> {
                    val matchingChildren = filterTreeNodes(node.children, searchQuery)
                    val workspaceMatches = node.name.contains(searchQuery, ignoreCase = true)
                    
                    if (workspaceMatches || matchingChildren.isNotEmpty()) {
                        node.copy(children = matchingChildren.toMutableList())
                    } else null
                }
                
                is TabTreeNode.SplitNode -> {
                    val matchingChildren = filterTreeNodes(node.children, searchQuery)
                    val splitMatches = node.name.contains(searchQuery, ignoreCase = true)
                    
                    if (splitMatches || matchingChildren.isNotEmpty()) {
                        node.copy(children = matchingChildren.toMutableList())
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
    
    // Update active tabs whenever the split view state changes or tabs are added/removed
    LaunchedEffect(splitViewState, workspaceManager) {
        if (splitViewState != null) {
            val tabs = splitViewState.collectAllActiveTabs(workspaceManager)
            TopOfMindState.updateActiveTabs(tabs)
            
            // Initialize tree expansion state
            val treeNodes = TabTreeBuilder.buildTree(tabs)
            TabTreeState.initializeDefaultExpansion(treeNodes)
        }
    }
    
    // Subscribe to real-time tab state changes from all panels
    if (splitViewState != null) {
        val allPanels = splitViewState.getAllPanels()
        
        // Create a key that changes when panels change
        val panelsKey = allPanels.map { it.id }.sorted().joinToString(",")
        
        LaunchedEffect(panelsKey) {
            // Update tabs when panel structure changes
            val tabs = splitViewState.collectAllActiveTabs(workspaceManager)
            TopOfMindState.updateActiveTabs(tabs)
        }
        
        // Listen to tab state changes in each panel
        allPanels.forEach { panel ->
            val panelTabsState by panel.tabsComponent.tabsState.subscribeAsState()
            
            LaunchedEffect(panel.id, panelTabsState.tabs.size, panelTabsState.tabs.map { tab -> tab.id + tab.title }) {
                // Update when tabs are added/removed or their content changes in this panel
                val updatedTabs = splitViewState.collectAllActiveTabs(workspaceManager)
                TopOfMindState.updateActiveTabs(updatedTabs)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2B2D30))
            .padding(12.dp)
    ) {
        // Search bar (styled like browser URL bar)
        BasicTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colors.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colors.surface,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search active tabs...",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                }
            }
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
                text = "Workspaces",
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
        val treeNodes = TabTreeBuilder.buildTree(filteredTabs)
        
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
                modifier = Modifier.fillMaxSize(),
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
                    node.children.forEach { childNode ->
                        TreeNodeItem(
                            node = childNode,
                            workspaceManager = workspaceManager,
                            splitViewState = splitViewState,
                            onTabClick = onTabClick,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
            
            is TabTreeNode.SplitNode -> {
                SplitFolderItem(
                    node = node,
                    isExpanded = isExpanded,
                    onToggleExpand = { TabTreeState.toggleExpansion(node.id) }
                )
                
                if (isExpanded) {
                    node.children.forEach { childNode ->
                        TreeNodeItem(
                            node = childNode,
                            workspaceManager = workspaceManager,
                            splitViewState = splitViewState,
                            onTabClick = onTabClick,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
            
            is TabTreeNode.TabNode -> {
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
private fun SplitFolderItem(
    node: TabTreeNode.SplitNode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isExpanded) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier.size(14.dp),
            tint = Color.Gray.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Icon(
            Icons.Outlined.ViewModule,
            contentDescription = "Split Panel",
            modifier = Modifier.size(14.dp),
            tint = Color(0xFF569CD6) // VS Code folder color
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = node.name,
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TabCardItem(
    node: TabTreeNode.TabNode,
    onTabClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
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
            // Tab icon based on type
            val tabIcon = when (node.activeTab.tabInfo) {
                is FluckTabInfo -> Icons.Outlined.Language
                else -> Icons.Outlined.Tab // Default icon for other tab types
            }
            
            Icon(
                tabIcon,
                contentDescription = "Tab",
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


fun DefaultPlugin.registerTopOfMind() = panelRegistry.registerPanel(TopOfMindInfo) {
    ctx, panelInfo -> TopOfMindComponent(ctx, panelInfo)
}
