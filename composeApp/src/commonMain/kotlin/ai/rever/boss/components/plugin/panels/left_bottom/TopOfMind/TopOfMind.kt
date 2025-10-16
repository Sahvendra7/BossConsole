package ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind

import ai.rever.boss.components.configuration.ConfigurationManager
import ai.rever.boss.components.configuration.applyConfiguration
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
    val configurationId: String,
    val configurationName: String,
    val panelId: String
)

// Hierarchical tree structure for organizing tabs
sealed class TabTreeNode {
    abstract val id: String
    abstract val name: String
    abstract val level: Int
    
    data class ConfigurationNode(
        override val id: String,
        override val name: String,
        override val level: Int = 0,
        val configurationId: String,
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
    
    // Track which configurations have been modified
    private val _modifiedConfigurations = MutableStateFlow<Set<String>>(emptySet())
    val modifiedConfigurations: StateFlow<Set<String>> = _modifiedConfigurations
    
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
        // Expand all configuration nodes by default
        val configNodes = nodes.filterIsInstance<TabTreeNode.ConfigurationNode>()
        _expandedNodes.value = configNodes.map { it.id }.toSet()
    }
    
    fun markConfigurationAsModified(configId: String) {
        val current = _modifiedConfigurations.value.toMutableSet()
        current.add(configId)
        _modifiedConfigurations.value = current
    }
    
    fun markConfigurationAsSaved(configId: String) {
        val current = _modifiedConfigurations.value.toMutableSet()
        current.remove(configId)
        _modifiedConfigurations.value = current
    }

}

// Utility to build tree structure from active tabs
object TabTreeBuilder {
    fun buildTree(activeTabs: List<ActiveTab>): List<TabTreeNode> {
        val configGroups = activeTabs.groupBy { it.configurationId }
        val rootNodes = mutableListOf<TabTreeNode>()
        
        configGroups.forEach { (configId, tabs) ->
            // Use the configuration name from the first tab (they should all be the same)
            val configName = tabs.firstOrNull()?.configurationName ?: "Unknown"
            
            val configNode = TabTreeNode.ConfigurationNode(
                id = "config-$configId",
                name = configName,
                configurationId = configId,
                level = 0
            )
            
            // Group tabs by panel for this configuration
            val panelGroups = tabs.groupBy { it.panelId }
            
            panelGroups.forEach { (panelId, panelTabs) ->
                val splitNode = TabTreeNode.SplitNode(
                    id = "panel-$configId-$panelId", // Make panel IDs unique per config
                    name = "Panel $panelId",
                    level = 1,
                    splitType = "panel",
                    panelId = panelId
                )
                
                // Add individual tabs to this panel
                panelTabs.forEach { activeTab ->
                    val tabNode = TabTreeNode.TabNode(
                        id = "tab-${activeTab.configurationId}-${activeTab.tabInfo.id}", // Make tab IDs unique per config
                        name = activeTab.tabInfo.title,
                        level = 2,
                        activeTab = activeTab
                    )
                    splitNode.children.add(tabNode)
                }
                
                configNode.children.add(splitNode)
            }
            
            rootNodes.add(configNode)
        }
        
        return rootNodes
    }
    
    // Filter tree nodes based on search query
    fun filterTreeNodes(nodes: List<TabTreeNode>, searchQuery: String): List<TabTreeNode> {
        return nodes.mapNotNull { node ->
            when (node) {
                is TabTreeNode.ConfigurationNode -> {
                    val matchingChildren = filterTreeNodes(node.children, searchQuery)
                    val configMatches = node.name.contains(searchQuery, ignoreCase = true)
                    
                    if (configMatches || matchingChildren.isNotEmpty()) {
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
        val configurationManager = LocalConfigurationManager.current
        TopOfMindContent(splitViewState, configurationManager)
    }
}

@Composable
fun TopOfMindContent(
    splitViewState: SplitViewState?,
    configurationManager: ConfigurationManager?
) {
    val activeTabs by TopOfMindState.activeTabs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showCurrentWorkspace by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    
    // Update active tabs whenever the split view state changes or tabs are added/removed
    LaunchedEffect(splitViewState, configurationManager) {
        if (splitViewState != null) {
            val tabs = splitViewState.collectAllActiveTabs(configurationManager)
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
            val tabs = splitViewState.collectAllActiveTabs(configurationManager)
            TopOfMindState.updateActiveTabs(tabs)
        }
        
        // Listen to tab state changes in each panel
        allPanels.forEach { panel ->
            val panelTabsState by panel.tabsComponent.tabsState.subscribeAsState()
            
            LaunchedEffect(panel.id, panelTabsState.tabs.size, panelTabsState.tabs.map { tab -> tab.id + tab.title }) {
                // Update when tabs are added/removed or their content changes in this panel
                val updatedTabs = splitViewState.collectAllActiveTabs(configurationManager)
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
            val currentConfigId = configurationManager?.currentConfiguration?.value?.id
            activeTabs.filter { it.configurationId != currentConfigId }
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
                        configurationManager = configurationManager,
                        splitViewState = splitViewState,
                        onTabClick = { activeTab ->
                            if (splitViewState != null && configurationManager != null) {
                                coroutineScope.launch {
                                    // Get current configuration
                                    val currentConfig = configurationManager.currentConfiguration.value
                                    
                                    if (currentConfig?.id == activeTab.configurationId) {
                                        // Tab is in current config, just focus it
                                        splitViewState.selectTabInPanel(activeTab.tabInfo.id, activeTab.panelId)
                                    } else {
                                        // Tab is in different config - switch configurations
                                        val targetConfig = configurationManager.configurations.value.find { 
                                            it.id == activeTab.configurationId 
                                        }
                                        
                                        if (targetConfig != null) {
                                            // Preserve current state before switching
                                            if (currentConfig != null && currentConfig.id.isNotEmpty()) {
                                                splitViewState.preserveCurrentState(currentConfig.id, currentConfig.name)
                                            }
                                            
                                            // Load and apply the target configuration
                                            configurationManager.loadConfiguration(targetConfig)
                                            applyConfiguration(targetConfig, splitViewState)
                                            
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
    configurationManager: ConfigurationManager?,
    splitViewState: SplitViewState?,
    onTabClick: (ActiveTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val expandedNodes by TabTreeState.expandedNodes.collectAsState()
    val isExpanded = expandedNodes.contains(node.id)
    val coroutineScope = rememberCoroutineScope()
    
    Column(modifier = modifier) {
        when (node) {
            is TabTreeNode.ConfigurationNode -> {
                ConfigurationFolderItem(
                    node = node,
                    isExpanded = isExpanded,
                    onToggleExpand = { TabTreeState.toggleExpansion(node.id) },
                    configurationManager = configurationManager,
                    onConfigClick = {
                        // Switch to this configuration
                        if (splitViewState != null && configurationManager != null) {
                            coroutineScope.launch {
                                val currentConfig = configurationManager.currentConfiguration.value
                                val targetConfig = configurationManager.configurations.value.find { 
                                    it.id == node.configurationId 
                                }
                                
                                if (targetConfig != null && currentConfig?.id != node.configurationId) {
                                    // Preserve current state before switching
                                    if (currentConfig != null && currentConfig.id.isNotEmpty()) {
                                        splitViewState.preserveCurrentState(currentConfig.id, currentConfig.name)
                                    }
                                    
                                    // Load and apply the target configuration
                                    configurationManager.loadConfiguration(targetConfig)
                                    applyConfiguration(targetConfig, splitViewState)
                                }
                            }
                        }
                    }
                )
                
                if (isExpanded) {
                    node.children.forEach { childNode ->
                        TreeNodeItem(
                            node = childNode,
                            configurationManager = configurationManager,
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
                            configurationManager = configurationManager,
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
private fun ConfigurationFolderItem(
    node: TabTreeNode.ConfigurationNode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onConfigClick: () -> Unit,
    configurationManager: ConfigurationManager?
) {
    val modifiedConfigurations by TabTreeState.modifiedConfigurations.collectAsState()
    val isModified = modifiedConfigurations.contains(node.configurationId)
    
    // Check if this is the currently active configuration
    val currentConfig = configurationManager?.currentConfiguration?.value
    val isActive = currentConfig?.id == node.configurationId
    
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
        
        // Configuration content area (clickable)
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onConfigClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Workspaces,
                contentDescription = "Configuration",
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
