package ai.rever.boss.v4

import ai.rever.boss.v4.screens.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/**
 * Main UI composable that displays the root component
 */
@Composable
fun BossConsoleApp(modifier: Modifier = Modifier, bossConsoleComponent: BossConsoleComponent) {
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