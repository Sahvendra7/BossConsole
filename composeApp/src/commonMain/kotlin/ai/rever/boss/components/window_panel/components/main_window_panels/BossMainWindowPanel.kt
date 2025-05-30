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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.LifecycleRegistry

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

    HorizontalBar(height = 42.dp, backgroundColor = BossDarkBackground) {
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
            }
            Spacer(modifier = Modifier.weight(0.1f))
        }
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
            tabComponents.remove(it.id)
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

interface Child {
    @Composable
    fun Content()
}
