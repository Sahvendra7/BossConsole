package ai.rever.boss.plugin.panel.adminrolemanagement

import ai.rever.boss.plugin.api.AuthDataProvider
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.UserManagementProvider

/**
 * Plugin for Admin Role Management panel
 *
 * This plugin provides the Admin Role Management panel which allows administrators to:
 * - View all users in the system
 * - Assign roles to users
 * - Remove roles from users
 * - Delete users
 *
 * Access Control:
 * - Only accessible to users with 'admin' role
 * - Should be dynamically registered/unregistered based on admin status
 */
object AdminRoleManagementPanelPlugin : Plugin {
    override val pluginId = "admin-role-management-panel"
    override val displayName = "Admin Role Management Panel"

    private var userManagementProvider: UserManagementProvider? = null
    private var authDataProvider: AuthDataProvider? = null

    /**
     * Register the plugin with the provided data providers.
     *
     * @param context The plugin context for registration
     * @param userManagementProvider Provider for user management operations
     * @param authDataProvider Provider for authentication state
     */
    fun register(
        context: PluginContext,
        userManagementProvider: UserManagementProvider,
        authDataProvider: AuthDataProvider
    ) {
        this.userManagementProvider = userManagementProvider
        this.authDataProvider = authDataProvider

        context.panelRegistry.registerPanel(AdminRoleManagementInfo) { ctx, panelInfo ->
            AdminRoleManagementComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                userManagementProvider = userManagementProvider,
                authDataProvider = authDataProvider
            )
        }
    }

    /**
     * Unregister the panel.
     *
     * @param context The plugin context for unregistration
     */
    fun unregister(context: PluginContext) {
        context.panelRegistry.unregisterPanel(AdminRoleManagementInfo.id)
        userManagementProvider = null
        authDataProvider = null
    }

    override fun register(context: PluginContext) {
        // No-op: This plugin requires explicit registration with data providers
        // Use register(context, userManagementProvider, authDataProvider) instead
    }
}
