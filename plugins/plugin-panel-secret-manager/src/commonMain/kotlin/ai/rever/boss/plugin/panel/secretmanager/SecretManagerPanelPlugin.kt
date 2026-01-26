package ai.rever.boss.plugin.panel.secretmanager

import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.SecretDataProvider
import ai.rever.boss.plugin.api.UserManagementProvider

/**
 * Plugin that provides the Secret Manager panel.
 *
 * This panel allows users to manage their encrypted website credentials.
 * It requires both SecretDataProvider and UserManagementProvider for
 * full functionality including sharing secrets with users and roles.
 *
 * Registration is typically done dynamically based on user permissions.
 * Only users with 'secrets.write' permission or admin role should have
 * this panel registered.
 */
object SecretManagerPanelPlugin : Plugin {
    override val pluginId = "ai.rever.boss.plugin.secret-manager"
    override val displayName = "Secret Manager"

    /**
     * Register the Secret Manager panel with the provided data providers.
     *
     * @param context The plugin context for registration
     * @param secretDataProvider Provider for secret CRUD operations
     * @param userManagementProvider Provider for user/role listing (for sharing)
     * @param onSecretChanged Optional callback when secrets are modified
     */
    fun register(
        context: PluginContext,
        secretDataProvider: SecretDataProvider,
        userManagementProvider: UserManagementProvider,
        onSecretChanged: (() -> Unit)? = null
    ) {
        context.panelRegistry.registerPanel(SecretManagerInfo) { ctx, panelInfo ->
            SecretManagerComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                secretDataProvider = secretDataProvider,
                userManagementProvider = userManagementProvider,
                onSecretChanged = onSecretChanged
            )
        }
    }

    override fun register(context: PluginContext) {
        throw IllegalStateException(
            "SecretManagerPanelPlugin requires data providers. " +
            "Use register(context, secretDataProvider, userManagementProvider) instead."
        )
    }
}
