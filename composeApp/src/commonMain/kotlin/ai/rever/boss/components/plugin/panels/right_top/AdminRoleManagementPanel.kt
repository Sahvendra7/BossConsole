package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.services.auth.AuthStateManager
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import compose.icons.FeatherIcons
import compose.icons.feathericons.Shield
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Panel info for Admin Role Management
 *
 * This panel allows administrators to:
 * - View all users in the system
 * - Assign roles to users
 * - Remove roles from users
 * - Manage user permissions
 *
 * Access Control:
 * - Only accessible to users with 'admin' role
 * - RLS policies enforce server-side authorization
 * - Non-admin users will see permission errors if they try to access
 */
object AdminRoleManagementInfo : PanelInfo {
    override val id = PanelId("admin-role-management", 20)
    override val displayName = "Admin: Roles"
    override val icon = FeatherIcons.Shield
    override val defaultSlotPosition = right.top.top
}

/**
 * Component for Admin Role Management panel
 *
 * This component provides the UI for managing user roles and permissions.
 * It integrates with:
 * - UserService: Fetching all users
 * - RoleService: Role assignment/removal operations
 * - AdminRoleManagementViewModel: State management
 * - AdminUserListView: User list UI
 * - RoleManagementDialogs: Role operation dialogs
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
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = AdminRoleManagementViewModel()

    init {
        // Dispose ViewModel when component is destroyed to cancel all coroutines
        lifecycle.doOnDestroy {
            viewModel.dispose()
        }
    }

    @Composable
    override fun Content() {
        AdminRoleManagementContent(viewModel)
    }
}

/**
 * Register the Admin Role Management panel (admin-only)
 *
 * Observes auth state and dynamically registers/unregisters panel based on admin status.
 * Panel will only appear in panel list for admin users.
 *
 * Uses the plugin's lifecycle-aware scope instead of GlobalScope to prevent memory leaks.
 * The coroutine will be automatically cancelled when the plugin is disposed.
 *
 * Race condition safety:
 * - Uses distinctUntilChanged() to only react to actual admin status changes
 * - Eliminates the need for thread-unsafe isRegistered flag
 * - Guarantees exactly one register/unregister per status change
 */
fun DefaultPlugin.registerAdminRoleManagement() {
    println("🔧 [AdminPanel] Initializing admin panel registration")

    // Observe auth state and dynamically register/unregister panel
    // Use pluginScope instead of GlobalScope to tie the lifecycle to the plugin
    pluginScope.launch(Dispatchers.Main) {
        AuthStateManager.currentUser
            .map { user -> user?.isAdmin == true }  // Extract just the admin status
            .distinctUntilChanged()  // Only emit when admin status actually changes
            .collect { isAdmin ->
                val user = AuthStateManager.currentUser.value
                println("🔧 [AdminPanel] Admin status changed: isAdmin=$isAdmin, user=${user?.email}")

                if (isAdmin) {
                    // User is admin - register panel
                    println("✅ [AdminPanel] Registering admin panel for ${user?.email}")
                    panelRegistry.registerPanel(AdminRoleManagementInfo) { ctx, panelInfo ->
                        AdminRoleManagementComponent(ctx, panelInfo)
                    }
                } else {
                    // User is not admin - unregister panel
                    println("❌ [AdminPanel] Unregistering admin panel")
                    panelRegistry.unregisterPanel(AdminRoleManagementInfo.id)
                }
            }
    }
}
