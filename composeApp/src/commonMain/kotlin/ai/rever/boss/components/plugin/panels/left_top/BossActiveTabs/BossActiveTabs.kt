package ai.rever.boss.components.plugin.panels.left_top.BossActiveTabs

import ai.rever.boss.components.configuration.ConfigurationManager
import ai.rever.boss.components.configuration.applyConfiguration
import ai.rever.boss.components.model.Panel.Companion.left
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

// Global state for tracking all active tabs
object BossActiveTabsState {
    private val _activeTabs = MutableStateFlow<List<ActiveTab>>(emptyList())
    val activeTabs: StateFlow<List<ActiveTab>> = _activeTabs
    
    fun updateActiveTabs(tabs: List<ActiveTab>) {
        _activeTabs.value = tabs
    }
    
    fun addActiveTab(tab: ActiveTab) {
        _activeTabs.value = _activeTabs.value + tab
    }
    
    fun removeActiveTab(tabId: String) {
        _activeTabs.value = _activeTabs.value.filter { it.tabInfo.id != tabId }
    }
}

object BossActiveTabsInfo : PanelInfo {
    override val id = PanelId("boss-active-tabs", 1)
    override val displayName = "Boss Active Tabs"
    override val icon = Icons.Outlined.Language
    override val defaultSlotPosition = left.top.top
}

class BossActiveTabsComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        val splitViewState = LocalSplitViewState.current
        val configurationManager = LocalConfigurationManager.current
        BossActiveTabsContent(splitViewState, configurationManager)
    }
}

@Composable
fun BossActiveTabsContent(
    splitViewState: SplitViewState?,
    configurationManager: ConfigurationManager?
) {
    val activeTabs by BossActiveTabsState.activeTabs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    // Update active tabs whenever the split view state changes or tabs are added/removed
    LaunchedEffect(splitViewState) {
        if (splitViewState != null) {
            val tabs = splitViewState.collectAllActiveTabs()
            BossActiveTabsState.updateActiveTabs(tabs)
        }
    }
    
    // Subscribe to real-time tab state changes from all panels
    if (splitViewState != null) {
        val allPanels = splitViewState.getAllPanels()
        
        // Create a key that changes when panels change
        val panelsKey = allPanels.map { it.id }.sorted().joinToString(",")
        
        LaunchedEffect(panelsKey) {
            // Update tabs when panel structure changes
            val tabs = splitViewState.collectAllActiveTabs()
            BossActiveTabsState.updateActiveTabs(tabs)
        }
        
        // Listen to tab state changes in each panel
        allPanels.forEach { panel ->
            val panelTabsState by panel.tabsComponent.tabsState.subscribeAsState()
            
            LaunchedEffect(panel.id, panelTabsState.tabs.size, panelTabsState.tabs.map { tab -> tab.id + tab.title }) {
                // Update when tabs are added/removed or their content changes in this panel
                val updatedTabs = splitViewState.collectAllActiveTabs()
                BossActiveTabsState.updateActiveTabs(updatedTabs)
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
        
        // Active tabs list
        val filteredTabs = if (searchQuery.isBlank()) {
            activeTabs
        } else {
            activeTabs.filter { tab ->
                tab.tabInfo.title.contains(searchQuery, ignoreCase = true) ||
                // Only check URL for Fluck tabs that have URL property
                (tab.tabInfo is FluckTabInfo && tab.tabInfo.url.contains(searchQuery, ignoreCase = true)) ||
                tab.configurationName.contains(searchQuery, ignoreCase = true)
            }
        }
        
        if (filteredTabs.isEmpty()) {
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredTabs) { activeTab ->
                    ActiveTabItem(
                        activeTab = activeTab,
                        onTabClick = {
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
private fun ActiveTabItem(
    activeTab: ActiveTab,
    onTabClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable { onTabClick() },
        color = Color(0xFF3C3F43),
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Tab title
            Text(
                text = activeTab.tabInfo.title,
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Tab type and URL (for browser tabs) or type info
            val secondaryText = when (val tabInfo = activeTab.tabInfo) {
                is FluckTabInfo -> tabInfo.url
                else -> tabInfo.typeId.typeId // Show tab type for non-browser tabs
            }
            
            if (secondaryText.isNotEmpty()) {
                Text(
                    text = secondaryText,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Configuration name
            Text(
                text = "in ${activeTab.configurationName}",
                fontSize = 10.sp,
                color = Color.Gray.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun DefaultPlugin.registerBossActiveTabs() = panelRegistry.registerPanel(BossActiveTabsInfo) {
    ctx, panelInfo -> BossActiveTabsComponent(ctx, panelInfo)
}