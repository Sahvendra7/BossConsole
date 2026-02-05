package ai.rever.boss.plugin.panel.rolecreation

import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.RoleManagementProvider

/**
 * Plugin for Role Creation panel
 *
 * This plugin provides the Role Creation panel which allows administrators to:
 * - Create new roles dynamically
 * - Create new permissions dynamically
 * - Assign permissions to roles
 * - Remove permissions from roles
 * - Delete roles and permissions
 *
 * Access Control:
 * - Only accessible to users with 'admin' role
 * - Should be dynamically registered/unregistered based on admin status
 */
object RoleCreationPanelPlugin : Plugin {
    override val pluginId = "role-creation-panel"
    override val displayName = "Role Creation Panel"

    private var roleManagementProvider: RoleManagementProvider? = null

    /**
     * Register the plugin with the provided data provider.
     *
     * @param context The plugin context for registration
     * @param roleManagementProvider Provider for role and permission operations
     */
    fun register(
        context: PluginContext,
        roleManagementProvider: RoleManagementProvider
    ) {
        this.roleManagementProvider = roleManagementProvider

        context.panelRegistry.registerPanel(RoleCreationInfo) { ctx, panelInfo ->
            RoleCreationComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                roleManagementProvider = roleManagementProvider
            )
        }
    }

    /**
     * Unregister the panel.
     *
     * @param context The plugin context for unregistration
     */
    fun unregister(context: PluginContext) {
        context.panelRegistry.unregisterPanel(RoleCreationInfo.id)
        roleManagementProvider = null
    }

    override fun register(context: PluginContext) {
        val roleMgmtProvider = context.roleManagementProvider

        if (roleMgmtProvider == null) {
            // Provider not available - cannot register
            return
        }

        this.roleManagementProvider = roleMgmtProvider

        context.panelRegistry.registerPanel(RoleCreationInfo) { ctx, panelInfo ->
            RoleCreationComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                roleManagementProvider = roleMgmtProvider
            )
        }
    }
}
