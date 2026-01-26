package ai.rever.boss.plugin.panel.adminrolemanagement

import ai.rever.boss.plugin.api.AuthDataProvider
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.UserManagementProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy

/**
 * Component for Admin Role Management panel
 *
 * This component provides the UI for managing user roles and permissions.
 * It integrates with:
 * - UserManagementProvider: User and role operations
 * - AuthDataProvider: Current user state for self-account protection
 * - AdminRoleManagementViewModel: State management
 *
 * Access Control:
 * - Panel is only registered when user is admin (dynamic registration)
 * - Non-admin users will never see this panel in the sidebar
 *
 * Lifecycle management:
 * - ViewModel is disposed when component is destroyed to prevent memory leaks
 */
class AdminRoleManagementComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val userManagementProvider: UserManagementProvider,
    private val authDataProvider: AuthDataProvider
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = AdminRoleManagementViewModel(userManagementProvider)

    init {
        // Dispose ViewModel when component is destroyed to cancel all coroutines
        lifecycle.doOnDestroy {
            viewModel.dispose()
        }
    }

    @Composable
    override fun Content() {
        val currentUser by authDataProvider.currentUser.collectAsState()
        AdminRoleManagementContent(
            viewModel = viewModel,
            currentUserId = currentUser?.id
        )
    }
}
