package ai.rever.boss.components.plugin.panels.left_top.FluckActiveTabs

import ai.rever.boss.components.configuration.ConfigurationManager
import ai.rever.boss.components.configuration.applyConfiguration
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Data class for active browser tabs
data class ActiveFluckTab(
    val tabInfo: FluckTabInfo,
    val configurationId: String,
    val configurationName: String,
    val panelId: String
)

// Global state for tracking active browser tabs
object FluckActiveTabsState {
    private val _activeTabs = MutableStateFlow<List<ActiveFluckTab>>(emptyList())
    val activeTabs: StateFlow<List<ActiveFluckTab>> = _activeTabs
    
    fun updateActiveTabs(tabs: List<ActiveFluckTab>) {
        _activeTabs.value = tabs
    }
    
    fun addActiveTab(tab: ActiveFluckTab) {
        _activeTabs.value = _activeTabs.value + tab
    }
    
    fun removeActiveTab(tabId: String) {
        _activeTabs.value = _activeTabs.value.filter { it.tabInfo.id != tabId }
    }
}

object FluckActiveTabsInfo : PanelInfo {
    override val id = PanelId("fluck-active-tabs", 1)
    override val displayName = "Fluck Active Tabs"
    override val icon = Icons.Outlined.Language
    override val defaultSlotPosition = left.top.top
}

class FluckActiveTabsComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        val splitViewState = LocalSplitViewState.current
        val configurationManager = LocalConfigurationManager.current
        FluckActiveTabsContent(splitViewState, configurationManager)
    }
}

@Composable
fun FluckActiveTabsContent(
    splitViewState: SplitViewState?,
    configurationManager: ConfigurationManager?
) {
    val activeTabs by FluckActiveTabsState.activeTabs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    // Update active tabs whenever the split view state changes
    LaunchedEffect(splitViewState) {
        if (splitViewState != null) {
            val tabs = splitViewState.collectAllActiveFluckTabs()
            FluckActiveTabsState.updateActiveTabs(tabs)
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
                tab.tabInfo.url.contains(searchQuery, ignoreCase = true) ||
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
                    text = if (searchQuery.isBlank()) "No active browser tabs" else "No tabs matching \"$searchQuery\"",
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
    activeTab: ActiveFluckTab,
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
            
            // URL
            Text(
                text = activeTab.tabInfo.url,
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
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

fun DefaultPlugin.registerFluckActiveTabs() = panelRegistry.registerPanel(FluckActiveTabsInfo) {
    ctx, panelInfo -> FluckActiveTabsComponent(ctx, panelInfo)
}