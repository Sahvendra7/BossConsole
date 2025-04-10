package ai.rever.boss.v4

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.push

/**
 * Home screen component
 */
class HomeComponent(
    componentContext: ComponentContext,
    private val navigation: StackNavigation<RootComponent.Config>
) : ComponentContext by componentContext {

    fun onSettingsClicked() {
        navigation.push(RootComponent.Config.Settings)
    }

    fun onDetailClicked(id: String) {
        navigation.push(RootComponent.Config.Detail(id))
    }
}