package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.components.model.Panel.Companion.bottom
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
import compose.icons.feathericons.PlusCircle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val roleCreationLogger = BossLogger.forComponent("RoleCreationPanel")

/**
 * Panel info for Role Creation
 *
 * This panel allows administrators to:
 * - Create new roles dynamically (table-based, not ENUM)
 * - Create new permissions dynamically (table-based, not ENUM)
 * - Assign permissions to roles
 * - Remove permissions from roles
 * - View all roles and their permissions
 *
 * Access Control:
 * - Only accessible to users with 'admin' role
 * - RLS policies enforce server-side authorization
 * - Non-admin users will see permission errors if they try to access
 */
object RoleCreationInfo : PanelInfo {
    override val id = PanelId("role-creation", 23)
    override val displayName = "Admin: Create Roles"
    override val icon = FeatherIcons.PlusCircle
    override val defaultSlotPosition = right.top.bottom
}

/**
 * Component for Role Creation panel
 *
 * This component provides the UI for creating roles and permissions dynamically.
 * It integrates with:
 * - RoleCreationService: Backend RPC calls for role/permission management
 * - RoleCreationViewModel: State management
 * - RoleCreationView: Main UI
 * - RoleCreationDialogs: Dialog components
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
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = RoleCreationViewModel()

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

/**
 * Register the Role Creation panel (admin-only)
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
fun DefaultPlugin.registerRoleCreation() {
    roleCreationLogger.debug(LogCategory.UI, "Initializing role creation panel registration")

    // Observe auth state and dynamically register/unregister panel
    // Use pluginScope instead of GlobalScope to tie the lifecycle to the plugin
    pluginScope.launch(Dispatchers.Main) {
        AuthStateManager.currentUser
            .map { user -> user?.isAdmin == true }  // Extract just the admin status
            .distinctUntilChanged()  // Only emit when admin status actually changes
            .collect { isAdmin ->
                val user = AuthStateManager.currentUser.value
                roleCreationLogger.debug(LogCategory.UI, "Admin status changed", mapOf("isAdmin" to isAdmin, "user" to (user?.email ?: "null")))

                if (isAdmin) {
                    // User is admin - register panel
                    roleCreationLogger.info(LogCategory.UI, "Registering role creation panel", mapOf("user" to (user?.email ?: "unknown")))
                    panelRegistry.registerPanel(RoleCreationInfo) { ctx, panelInfo ->
                        RoleCreationComponent(ctx, panelInfo)
                    }
                } else {
                    // User is not admin - unregister panel
                    roleCreationLogger.info(LogCategory.UI, "Unregistering role creation panel")
                    panelRegistry.unregisterPanel(RoleCreationInfo.id)
                }
            }
    }
}
