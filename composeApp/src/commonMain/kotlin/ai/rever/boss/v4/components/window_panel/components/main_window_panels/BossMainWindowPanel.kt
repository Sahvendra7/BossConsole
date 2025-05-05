package ai.rever.boss.v4.components.window_panel.components.main_window_panels

import BossDarkBackground
import BossDarkBorder
import BossDarkTextSecondary
import ai.rever.boss.v4.components.bars.ScrollbarConfig
import ai.rever.boss.v4.components.bars.horizontal.HorizontalBar
import ai.rever.boss.v4.components.bars.horizontal.HorizontalBarRow
import ai.rever.boss.v4.components.bars.horizontalScrollWithScrollbar
import ai.rever.boss.v4.components.buttons.BossTabButton
import ai.rever.boss.v4.components.tabs_navigation.TabsNavigation
import ai.rever.boss.v4.components.tabs_navigation.childTabs
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.Child.CodeEditor
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.Child.WebBrowser
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.TabConfig.CodeEditorConfig
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.TabConfig.WebBrowserConfig
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.screens.CodeEditorComponent
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.screens.CodeEditorPanel
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.screens.WebBrowserComponent
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.screens.WebBrowserPanel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.serialization.Serializable

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
                tabsState.value.tabs.forEachIndexed { index, tabConfig ->
                    val isSelected = index == tabsState.value.activeIndex
                    BossTabButton(
                        fileName = when(tabConfig) {
                            is CodeEditorConfig -> tabConfig.filePath.substringAfterLast('/')
                            is WebBrowserConfig -> tabConfig.url
                        },
                        isSelected = isSelected,
                        onClick = { selectTab(index) },
                        onClose = { closeTab(index) }
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
        BossMainPanelContent()
    }
}

/**
 * Main UI composable that displays the root component
 */
@Composable
fun BossTabsComponent.BossMainPanelContent() {
    val activeChild = activeChild.subscribeAsState()

    Box(modifier = Modifier) {
        when (val child = activeChild.value) {
            is CodeEditor -> CodeEditorPanel(child.component)
            is WebBrowser -> WebBrowserPanel(child.component)
            else -> Text("No tabs open")
        }
    }
}

/**
 * Helper function to create bossApp component with DefaultComponentContext
 */
fun createBossAppComponent(): BossTabsComponent {
    val lifecycle = LifecycleRegistry()
    return BossTabsComponent(DefaultComponentContext(lifecycle))
}

/**
 * Root component for the BOSS app using Decompose for navigation
 */
class BossTabsComponent(
    componentContext: ComponentContext
) : ComponentContext by componentContext {

    val tabsNavigation = TabsNavigation<TabConfig>()

    private val tabsComponentContext = childTabs (
        tabsNavigation = tabsNavigation,
        childFactory = ::createChild
    )

    // Expose tab state for UI
    val tabsState: Value<TabsNavigation.TabsState<TabConfig>> = tabsComponentContext.tabsState

    object NoChild : Child()

    // Active child for display
    val activeChild: Value<Child> = MutableValue<Child>(NoChild).also { mutableValue ->
        tabsComponentContext.activeChild.subscribe { value ->
            mutableValue.update { value }
        }
    }

    // Factory method to create children based on configuration
    private fun createChild(tabConfig: TabConfig, componentContext: ComponentContext): Child =
        when (tabConfig) {
            is CodeEditorConfig -> CodeEditor(CodeEditorComponent(componentContext, this, tabConfig))
            is WebBrowserConfig -> WebBrowser(WebBrowserComponent(componentContext, this, tabConfig))
        }

    // Tab management methods
    fun openTab(config: TabConfig) = tabsComponentContext.addTab(config)
    fun closeTab(index: Int) = tabsComponentContext.removeTab(index)
    fun selectTab(index: Int) = tabsComponentContext.selectTab(index)
}

// Child classes for different screens
sealed class Child {
    data class CodeEditor(val component: CodeEditorComponent) : Child()
    data class WebBrowser(val component: WebBrowserComponent) : Child()
}

// Configuration classes for different screens
@Serializable
sealed class TabConfig {
    @Serializable
    data class CodeEditorConfig(val filePath: String) : TabConfig()

    @Serializable
    data class WebBrowserConfig(val url: String) : TabConfig()
}
