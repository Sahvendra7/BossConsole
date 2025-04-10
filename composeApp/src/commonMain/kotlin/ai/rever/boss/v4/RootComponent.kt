package ai.rever.boss.v4

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable

/**
 * Root component for the BOSS app using Decompose for navigation
 */
class RootComponent(
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