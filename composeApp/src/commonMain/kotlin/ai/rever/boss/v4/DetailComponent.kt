package ai.rever.boss.v4

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.pop

/**
 * Detail screen component
 */
class DetailComponent(
    componentContext: ComponentContext,
    private val navigation: StackNavigation<RootComponent.Config>,
    val id: String
) : ComponentContext by componentContext {

    fun onBackClicked() {
        navigation.pop()
    }
}