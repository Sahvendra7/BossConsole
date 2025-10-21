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
import compose.icons.feathericons.Lock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Panel info for Secret Manager
 *
 * This panel allows users with 'secrets.write' permission to:
 * - View all their stored credentials (website:username)
 * - Add new secrets with encryption
 * - Update existing secrets
 * - Delete secrets
 * - Manage 2FA information and recovery codes
 * - Organize secrets with tags
 * - Track password expiration dates
 *
 * Access Control:
 * - Only accessible to users with 'secrets.write' permission OR admin role
 * - RLS policies enforce server-side authorization
 * - Users can only manage their own secrets
 * - Non-authorized users will never see this panel
 */
object SecretManagerInfo : PanelInfo {
    override val id = PanelId("secret-manager", 21)
    override val displayName = "Secret Manager"
    override val icon = FeatherIcons.Lock
    override val defaultSlotPosition = right.top.top  // Below admin panels
}

/**
 * Component for Secret Manager panel
 *
 * This component provides the UI for managing encrypted website credentials.
 * It integrates with:
 * - SecretService: CRUD operations for secrets
 * - SecretManagerViewModel: State management
 * - SecretListView: Secrets list UI
 * - SecretCardView: Individual secret card
 * - SecretDialogs: Create/Edit/Delete dialogs
 *
 * Access Control:
 * - Panel is only registered when user has secrets.write permission OR is admin
 * - Non-authorized users will never see this panel in the sidebar
 *
 * Lifecycle management:
 * - ViewModel is disposed when component is destroyed to prevent memory leaks
 */
class SecretManagerComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = SecretManagerViewModel()

    init {
        // Dispose ViewModel when component is destroyed to cancel all coroutines
        lifecycle.doOnDestroy {
            viewModel.dispose()
        }
    }

    @Composable
    override fun Content() {
        SecretManagerContent(viewModel)
    }
}

/**
 * Register the Secret Manager panel (requires secrets.write permission OR admin role)
 *
 * Observes auth state and dynamically registers/unregisters panel based on user permissions.
 * Panel will only appear in panel list for authorized users.
 *
 * Uses the plugin's lifecycle-aware scope instead of GlobalScope to prevent memory leaks.
 * The coroutine will be automatically cancelled when the plugin is disposed.
 *
 * Race condition safety:
 * - Uses distinctUntilChanged() to only react to actual permission changes
 * - Eliminates the need for thread-unsafe isRegistered flag
 * - Guarantees exactly one register/unregister per status change
 */
fun DefaultPlugin.registerSecretManager() {
    println("🔧 [SecretManagerPanel] Initializing secret manager panel registration")

    // Observe auth state and dynamically register/unregister panel
    // Use pluginScope instead of GlobalScope to tie the lifecycle to the plugin
    pluginScope.launch(Dispatchers.Main) {
        AuthStateManager.currentUser
            .map { user ->
                // Allow access if user is admin
                // TODO: Also check for secrets.write permission once permission checking is implemented
                user?.isAdmin == true
            }
            .distinctUntilChanged()  // Only emit when permission status actually changes
            .collect { hasPermission ->
                val user = AuthStateManager.currentUser.value
                println("🔧 [SecretManagerPanel] Permission status changed: hasPermission=$hasPermission, user=${user?.email}")

                if (hasPermission) {
                    // User has permission - register panel
                    println("✅ [SecretManagerPanel] Registering secret manager panel for ${user?.email}")
                    panelRegistry.registerPanel(SecretManagerInfo) { ctx, panelInfo ->
                        SecretManagerComponent(ctx, panelInfo)
                    }
                } else {
                    // User does not have permission - unregister panel
                    println("❌ [SecretManagerPanel] Unregistering secret manager panel")
                    panelRegistry.unregisterPanel(SecretManagerInfo.id)
                }
            }
    }
}
