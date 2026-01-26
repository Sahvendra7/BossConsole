package ai.rever.boss.plugin.panel.gitlog

import ai.rever.boss.plugin.api.GitDataProvider
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Plugin for the Git Log panel.
 *
 * Displays commit history with graph visualization.
 *
 * Usage:
 * Call register(context, dataProvider) to register the panel
 */
object GitLogPanelPlugin : Plugin {
    override val pluginId = "ai.rever.boss.plugin.git-log"
    override val displayName = "Git Log"

    /**
     * Register the panel with data provider.
     *
     * @param context Plugin context for registration
     * @param dataProvider Git data provider (window-specific)
     */
    fun register(context: PluginContext, dataProvider: GitDataProvider) {
        context.panelRegistry.registerPanel(GitLogInfo) { ctx, panelInfo ->
            GitLogComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                dataProvider = dataProvider
            )
        }
    }

    override fun register(context: PluginContext) {
        throw IllegalStateException(
            "Use register(context, dataProvider) instead. " +
            "This plugin requires window-specific context."
        )
    }
}
