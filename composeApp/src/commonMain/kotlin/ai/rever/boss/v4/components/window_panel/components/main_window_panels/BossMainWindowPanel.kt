package ai.rever.boss.v4.components.window_panel.components.main_window_panels

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import BossDarkTextSecondary
import ai.rever.boss.v4.components.bars.ScrollbarConfig
import ai.rever.boss.v4.components.bars.horizontal.HorizontalBar
import ai.rever.boss.v4.components.bars.horizontal.HorizontalBarRow
import ai.rever.boss.v4.components.bars.horizontal.RightArrow
import ai.rever.boss.v4.components.bars.horizontalScrollWithScrollbar
import ai.rever.boss.v4.components.buttons.BossActionButton
import ai.rever.boss.v4.components.buttons.BossTabButton
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.screens.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Web
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
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
fun BossMainTabBar() {
    HorizontalBar(height = 42.dp, backgroundColor = BossDarkBackground) {
        HorizontalBarRow {
            BossLeftTabBar {
                BossTabButton(fileName = "https://risalabs.ai", onClick = {})
                BossTabButton(fileName = "ContextMenu.kt", isSelected = true, onClick = {})
                BossTabButton(fileName = "BossWindow.kt", onClick = {})
                BossTabButton(fileName = "google.com", onClick = {})
                BossTabButton(fileName = "BossWindow.kt", onClick = {})
                BossTabButton(fileName = "ContextMenu.kt", onClick = {})
                BossTabButton(fileName = "google.com", onClick = {})
            }
            Spacer(modifier = Modifier.weight(0.1f))
        }
    }
}

@Composable
fun BossMainPanel(modifier: Modifier = Modifier, bossConsoleComponent: BossConsoleComponent) {
    Column(modifier = modifier) {
        BossMainTabBar()
        Divider(color = BossDarkBorder)
        BossMainPan(bossConsoleComponent = bossConsoleComponent)
    }
}

/**
 * Main UI composable that displays the root component
 */
@Composable
fun BossMainPan(modifier: Modifier = Modifier, bossConsoleComponent: BossConsoleComponent) {
    Children(
        stack = bossConsoleComponent.childStack,
        animation = stackAnimation(fade() + scale()),
        modifier = modifier,
    ) { child ->
        when (val instance = child.instance) {
            is BossConsoleComponent.Child.Home -> HomeScreen(instance.component)
            is BossConsoleComponent.Child.Settings -> SettingsScreen(instance.component)
            is BossConsoleComponent.Child.Detail -> DetailScreen(instance.component)
        }
    }
}

/**
 * Helper function to create bossApp component with DefaultComponentContext
 */
fun createBossAppComponent(): BossConsoleComponent {
    val lifecycle = LifecycleRegistry()
    return BossConsoleComponent(DefaultComponentContext(lifecycle))
}

/**
 * Root component for the BOSS app using Decompose for navigation
 */
class BossConsoleComponent(
    componentContext: ComponentContext
) : ComponentContext by componentContext {

    // Navigation controller
    private val navigation = StackNavigation<Config>()

    // Stack state for child components
    val childStack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Home,
        handleBackButton = true,
        childFactory = ::createChild
    )

    // Configuration classes for different screens
    @Serializable
    sealed class Config {
        @Serializable
        data object Home : Config()

        @Serializable
        data object Settings : Config()

        @Serializable
        data class Detail(val id: String) : Config()
    }

    // Child classes for different screens
    sealed class Child {
        data class Home(val component: HomeComponent) : Child()
        data class Settings(val component: SettingsComponent) : Child()
        data class Detail(val component: DetailComponent) : Child()
    }

    // Factory method to create children based on configuration
    private fun createChild(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            is Config.Home -> Child.Home(HomeComponent(componentContext, navigation))
            is Config.Settings -> Child.Settings(SettingsComponent(componentContext, navigation))
            is Config.Detail -> Child.Detail(DetailComponent(componentContext, navigation, config.id))
        }
}