package ai.rever.boss.plugin.panel.gitstatus

import ai.rever.boss.plugin.api.GitDataProvider
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Plugin for the Git Status panel.
 *
 * Displays changed, staged, and untracked files with staging controls.
 *
 * Usage:
 * Call register(context, dataProvider, windowIdProvider) to register the panel
 */
object GitStatusPanelPlugin : Plugin {
    override val pluginId = "ai.rever.boss.plugin.git-status"
    override val displayName = "Git Status"

    /**
     * Register the panel with window context.
     *
     * @param context Plugin context for registration
     * @param dataProvider Git data provider (window-specific)
     * @param windowIdProvider Provider for current window ID
     */
    fun register(
        context: PluginContext,
        dataProvider: GitDataProvider,
        windowIdProvider: () -> String?
    ) {
        context.panelRegistry.registerPanel(GitStatusInfo) { ctx, panelInfo ->
            GitStatusComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                dataProvider = dataProvider,
                windowIdProvider = windowIdProvider
            )
        }
    }

    override fun register(context: PluginContext) {
        throw IllegalStateException(
            "Use register(context, dataProvider, windowIdProvider) instead. " +
            "This plugin requires window-specific context."
        )
    }
}
