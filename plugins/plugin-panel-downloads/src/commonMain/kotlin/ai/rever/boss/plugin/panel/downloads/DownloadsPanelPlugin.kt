package ai.rever.boss.plugin.panel.downloads

import ai.rever.boss.plugin.api.DownloadDataProvider
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Plugin for the Downloads panel.
 *
 * Displays active and completed downloads in a compact sidebar format.
 *
 * Usage:
 * Call register(context, dataProvider) to register the panel
 */
object DownloadsPanelPlugin : Plugin {
    override val pluginId = "ai.rever.boss.plugin.downloads"
    override val displayName = "Downloads"

    /**
     * Register the panel with data provider.
     *
     * @param context Plugin context for registration
     * @param dataProvider Download data provider
     */
    fun register(context: PluginContext, dataProvider: DownloadDataProvider) {
        context.panelRegistry.registerPanel(DownloadsInfo) { ctx, panelInfo ->
            DownloadsComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                dataProvider = dataProvider
            )
        }
    }

    override fun register(context: PluginContext) {
        throw IllegalStateException(
            "Use register(context, dataProvider) instead. " +
            "This plugin requires a DownloadDataProvider."
        )
    }
}
