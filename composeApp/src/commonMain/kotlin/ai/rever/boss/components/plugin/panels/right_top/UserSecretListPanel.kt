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
import compose.icons.feathericons.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val userSecretListLogger = BossLogger.forComponent("UserSecretListPanel")

/**
 * Panel info for User Secret List (Read-Only)
 *
 * This panel allows authenticated users to view secrets they own or that have been shared with them.
 * Features:
 * - Read-only view (website:username only, no password display)
 * - Ownership badges (Owner vs Shared)
 * - Client-side search/filter
 * - Copy website and username to clipboard
 * - View metadata (tags, notes, expiration, shared by info)
 * - No edit/delete/share actions
 *
 * Access Control:
 * - Accessible to all authenticated users
 * - TODO: Add permission check for 'secrets.read' when permission system is implemented
 */
object UserSecretListInfo : PanelInfo {
    override val id = PanelId("user-secret-list", 25)
    override val displayName = "My Secrets"
    override val icon = FeatherIcons.Key
    override val defaultSlotPosition = right.top.bottom
}

/**
 * Component for User Secret List panel
 *
 * Provides read-only access to secrets accessible by the current user.
 * Integrates with:
 * - SecretService: Load secrets with sharing info
 * - UserSecretListViewModel: State management
 * - UserSecretListContent: Main UI
 * - UserSecretCardView: Individual secret card
 * - UserSecretListView: List with pagination
 *
 * Lifecycle management:
 * - ViewModel is disposed when component is destroyed to prevent memory leaks
 */
class UserSecretListComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = UserSecretListViewModel()

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

/**
 * Register the User Secret List panel
 *
 * Observes auth state and dynamically registers/unregisters panel based on user authentication.
 * Currently allows all authenticated users. In the future, this should check for 'secrets.read' permission.
 *
 * Uses the plugin's lifecycle-aware scope instead of GlobalScope to prevent memory leaks.
 * The coroutine will be automatically cancelled when the plugin is disposed.
 *
 * Race condition safety:
 * - Uses distinctUntilChanged() to only react to actual authentication changes
 * - Guarantees exactly one register/unregister per status change
 */
fun DefaultPlugin.registerUserSecretList() {
    userSecretListLogger.debug(LogCategory.UI, "Initializing user secret list panel registration")

    // Observe auth state and dynamically register/unregister panel
    // Use pluginScope instead of GlobalScope to tie the lifecycle to the plugin
    pluginScope.launch(Dispatchers.Main) {
        AuthStateManager.currentUser
            .map { user ->
                // Currently: All authenticated users have access
                // TODO: Check for secrets.read permission once permission system is implemented
                // user?.hasPermission("secrets.read") == true
                user != null
            }
            .distinctUntilChanged()  // Only emit when auth status actually changes
            .collect { hasAccess ->
                val user = AuthStateManager.currentUser.value
                userSecretListLogger.debug(LogCategory.UI, "Auth status changed", mapOf("hasAccess" to hasAccess, "user" to (user?.email ?: "null")))

                if (hasAccess) {
                    // User is authenticated - register panel
                    userSecretListLogger.info(LogCategory.UI, "Registering user secret list panel", mapOf("user" to (user?.email ?: "unknown")))
                    panelRegistry.registerPanel(UserSecretListInfo) { ctx, panelInfo ->
                        UserSecretListComponent(ctx, panelInfo)
                    }
                } else {
                    // User is not authenticated - unregister panel
                    userSecretListLogger.info(LogCategory.UI, "Unregistering user secret list panel")
                    panelRegistry.unregisterPanel(UserSecretListInfo.id)
                }
            }
    }
}
