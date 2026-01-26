package ai.rever.boss.plugin.panel.rolecreation

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.RoleManagementProvider
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy

/**
 * Component for Role Creation panel
 *
 * This component provides the UI for creating roles and permissions dynamically.
 * It integrates with:
 * - RoleManagementProvider: Backend operations for role/permission management
 * - RoleCreationViewModel: State management
 *
 * Access Control:
 * - Panel is only registered when user is admin (dynamic registration)
 * - Non-admin users will never see this panel in the sidebar
 *
 * Lifecycle management:
 * - ViewModel is disposed when component is destroyed to prevent memory leaks
 */
class RoleCreationComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val roleManagementProvider: RoleManagementProvider
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = RoleCreationViewModel(roleManagementProvider)

    init {
        // Dispose ViewModel when component is destroyed to cancel all coroutines
        lifecycle.doOnDestroy {
            viewModel.dispose()
        }
    }

    @Composable
    override fun Content() {
        RoleCreationContent(viewModel)
    }
}
