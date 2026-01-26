package ai.rever.boss.plugin.panel.usersecretlist

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.SecretDataProvider
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.flow.SharedFlow

/**
 * Component for User Secret List panel
 *
 * Provides read-only access to secrets accessible by the current user.
 * Uses SecretDataProvider interface for data operations.
 *
 * Lifecycle management:
 * - ViewModel is disposed when component is destroyed to prevent memory leaks
 */
class UserSecretListComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    secretDataProvider: SecretDataProvider,
    secretChangeEvents: SharedFlow<Any>? = null
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = UserSecretListViewModel(
        secretDataProvider = secretDataProvider,
        secretChangeEvents = secretChangeEvents
    )

    init {
        // Dispose ViewModel when component is destroyed to cancel all coroutines
        lifecycle.doOnDestroy {
            viewModel.dispose()
        }
    }

    @Composable
    override fun Content() {
        UserSecretListContent(viewModel)
    }
}
