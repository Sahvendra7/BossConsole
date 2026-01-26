package ai.rever.boss.plugin.panel.performance

import ai.rever.boss.plugin.api.FileOpenCallback
import ai.rever.boss.plugin.api.PerformanceDataProvider
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Performance panel plugin.
 *
 * Displays JVM metrics, CPU, memory, and resource counts.
 * Uses interface-based dependency injection for the data provider.
 */
object PerformancePanelPlugin : Plugin {
    override val pluginId: String = "ai.rever.boss.plugin.performance"
    override val displayName: String = "Performance"

    /**
     * Data provider injected by composeApp.
     * Must be set before register() is called.
     */
    var dataProvider: PerformanceDataProvider? = null

    /**
     * Callback for opening files (e.g., exported metrics).
     * Injected by composeApp.
     */
    var fileOpenCallback: FileOpenCallback? = null

    override fun register(context: PluginContext) {
        val provider = dataProvider
            ?: throw IllegalStateException("PerformancePanelPlugin.dataProvider must be set before registration")

        context.panelRegistry.registerPanel(PerformanceInfo) { ctx, panelInfo ->
            PerformanceComponent(ctx, panelInfo, provider, fileOpenCallback)
        }
    }
}
