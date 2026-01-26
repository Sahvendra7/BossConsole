package ai.rever.boss.plugin.tab.codeeditor

import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.TabComponentWithUI
import ai.rever.boss.plugin.api.TabInfo
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin for Code Editor tab type
 *
 * This plugin provides code editing capabilities with syntax highlighting,
 * line numbers, and main function detection/execution.
 */
object CodeEditorTabPlugin : Plugin {
    override val pluginId = "code-editor-tab"
    override val displayName = "Code Editor Tab"

    /**
     * Register the plugin with a component factory.
     *
     * @param context The plugin context with tab registry
     * @param componentFactory Factory function that creates the actual code editor component.
     *                         This allows the implementation to stay in composeApp.
     */
    fun register(
        context: PluginContext,
        componentFactory: (tabInfo: TabInfo, componentContext: ComponentContext) -> TabComponentWithUI
    ) {
        context.tabRegistry.registerTabType(CodeEditorTabType) { tabInfo, ctx ->
            componentFactory(tabInfo, ctx)
        }
    }

    override fun register(context: PluginContext) {
        // No-op: This plugin requires explicit registration with component factory
    }
}
