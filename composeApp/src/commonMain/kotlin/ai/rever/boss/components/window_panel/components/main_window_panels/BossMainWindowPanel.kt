package ai.rever.boss.components.window_panel.components.main_window_panels

import BossDarkBackground
import BossDarkBorder
import BossDarkTextSecondary
import ai.rever.boss.components.bars.ScrollbarConfig
import ai.rever.boss.components.bars.horizontal.HorizontalBar
import ai.rever.boss.components.bars.horizontal.HorizontalBarRow
import ai.rever.boss.components.bars.horizontalScrollWithScrollbar
import ai.rever.boss.components.buttons.BossTabButton
import ai.rever.boss.components.registery.TabComponentWithUI
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.registery.TabRegistry
import ai.rever.boss.components.tabs_navigation.TabsNavigation
import ai.rever.boss.components.dialogs.NewTabDialog
import ai.rever.boss.components.dialogs.TabType
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.overlays.contextMenu
import ai.rever.boss.components.plugin.tab_types.CodeEditor
import ai.rever.boss.components.plugin.tab_types.fluck.Fluck
import ai.rever.boss.components.registery.TabIcon
import ai.rever.boss.components.registery.TabTypeId
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.LifecycleRegistry

// Simple implementation of TabInfo
data class SimpleTabInfo(
    override val id: String,
    override val title: String,
    override val typeId: TabTypeId,
    override val icon: androidx.compose.ui.graphics.vector.ImageVector,
    override val tabIcon: TabIcon? = null
) : TabInfo

@Composable
fun RowScope.BossLeftTabBar(content: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.weight(2f).padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier
                .horizontalScrollWithScrollbar(
                    rememberScrollState(),
                    scrollbarConfig = ScrollbarConfig(
                        indicatorThickness = 2.dp,
                        indicatorColor = BossDarkTextSecondary,
                        indicatorCornerRadius = 4.dp,
                        horizontalScrollbarAtTop = true
                    )
                )
            ,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun BossTabsComponent.BossMainTabBar() {
    val tabsState = tabsState.subscribeAsState()
    var showNewTabDialog by remember { mutableStateOf(false) }

    HorizontalBar(
        height = 42.dp, 
        backgroundColor = BossDarkBackground,
        modifier = Modifier
            .contextMenu(
                items = listOf(
                    ContextMenuItem("New Tab", Icons.Default.Add) {
                        showNewTabDialog = true
                    }
                )
            )
    ) {
        HorizontalBarRow {
            BossLeftTabBar {
                tabsState.value.tabs.forEachIndexed { index, config ->
                    val isSelected = index == tabsState.value.activeIndex
                    BossTabButton(
                        fileName = config.title,
                        icon = config.icon,
                        tabIcon = config.tabIcon,
                        isSelected = isSelected,
                        onClick = { selectTab(index) },
                        onClose = { removeTab(index) }
                    )
                }
                
                // Plus button for new tab
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .width(32.dp)
                        .padding(4.dp)
                        .background(
                            color = Color(0xFF3C3F41),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        )
                        .clickable { showNewTabDialog = true },
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
            Spacer(modifier = Modifier.weight(0.1f))
        }
    }
    
    // New Tab Dialog
    if (showNewTabDialog) {
        NewTabDialog(
            onDismiss = { showNewTabDialog = false },
            onCreateTab = { type, path ->
                when (type) {
                    TabType.URL -> {
                        val timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
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
                        val timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        val fileName = path.substringAfterLast('/').ifEmpty { "untitled.txt" }
                        val editorTab = ai.rever.boss.components.plugin.tab_types.EditorTabInfo(
                            id = "editor-$timestamp",
                            title = fileName,
                            typeId = CodeEditor.typeId,
                            icon = CodeEditor.icon,
                            filePath = path
                        )
                        val tabIndex = addTab(editorTab)
                        if (tabIndex >= 0) {
                            selectTab(tabIndex)
                        }
                    }
                    TabType.TERMINAL -> {
                        val timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        val terminalTab = ai.rever.boss.components.plugin.tab_types.TerminalTabInfo(
                            id = "terminal-$timestamp",
                            typeId = ai.rever.boss.components.plugin.tab_types.TerminalTab.typeId,
                            title = "Terminal"
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

@Composable
fun BossTabsComponent.BossMainPanel(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        BossMainTabBar()
        Divider(color = BossDarkBorder)
        BossMainPanelContent(modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/**
 * Main UI composable that displays the root component
 */
@Composable
fun BossTabsComponent.BossMainPanelContent(modifier: Modifier) {
    // Subscribe to tab state changes to trigger recomposition
    val tabsState = tabsState.subscribeAsState()
    
    Box(modifier = modifier) {
        // Force recomposition when tab changes by reading the state
        val activeIndex = tabsState.value.activeIndex
        val activeComponent = getActiveComponent()
        
        activeComponent?.Content() ?: EmptyContent()
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize().background(BossDarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Text("No tabs open")
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

    // Remove a tab
    fun removeTab(index: Int) {
        val config = tabsState.value.tabs.getOrNull(index)
        config?.let { 
            // Dispose the component if it has a dispose method
            val component = tabComponents.remove(it.id)
            if (component is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabComponent) {
                component.dispose()
            }
        }
        tabsNavigation.removeTab(index)
    }

    // Select a tab
    fun selectTab(index: Int) {
        tabsNavigation.selectTab(index)
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
}

