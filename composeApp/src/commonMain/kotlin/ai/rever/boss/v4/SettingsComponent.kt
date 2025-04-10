package ai.rever.boss.v4

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.pop

/**
 * Settings screen component
 */
class SettingsComponent(
    componentContext: ComponentContext,
    private val navigation: StackNavigation<RootComponent.Config>
) : ComponentContext by componentContext {

    fun onBackClicked() {
        navigation.pop()
    }
}

