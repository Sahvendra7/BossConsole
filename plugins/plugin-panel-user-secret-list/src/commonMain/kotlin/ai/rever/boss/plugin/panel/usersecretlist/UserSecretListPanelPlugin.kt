package ai.rever.boss.plugin.panel.usersecretlist

import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.SecretDataProvider
import kotlinx.coroutines.flow.SharedFlow

/**
 * Plugin for User Secret List panel
 *
 * This plugin provides read-only access to secrets owned by or shared with the current user.
 * It uses the SecretDataProvider interface to fetch secrets, allowing it to be
 * in a separate module from the actual Supabase services.
 */
object UserSecretListPanelPlugin : Plugin {
    override val pluginId = "ai.rever.boss.plugin.user-secret-list"
    override val displayName = "User Secret List"

    /**
     * Default registration method required by Plugin interface.
     * For full functionality, use the overloaded register method with providers.
     */
    override fun register(context: PluginContext) {
        // Default registration without providers - panel will show but won't have data
        // Use the overloaded register method for full functionality
    }

    /**
     * Register the User Secret List panel with the provided context.
     *
     * @param context The plugin context for registration
     * @param secretDataProvider Provider for secret data operations
     * @param secretChangeEvents Optional flow of secret change events for auto-refresh
     */
    fun register(
        context: PluginContext,
        secretDataProvider: SecretDataProvider,
        secretChangeEvents: SharedFlow<Any>? = null
    ) {
        context.panelRegistry.registerPanel(UserSecretListInfo) { ctx, panelInfo ->
            UserSecretListComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                secretDataProvider = secretDataProvider,
                secretChangeEvents = secretChangeEvents
            )
        }
    }

    /**
     * Unregister the User Secret List panel.
     *
     * @param context The plugin context for unregistration
     */
    fun unregister(context: PluginContext) {
        context.panelRegistry.unregisterPanel(UserSecretListInfo.id)
    }
}
