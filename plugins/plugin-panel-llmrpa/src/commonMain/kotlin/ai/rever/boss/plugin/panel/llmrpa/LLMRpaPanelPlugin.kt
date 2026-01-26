package ai.rever.boss.plugin.panel.llmrpa

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin for LLM RPA panel
 *
 * This plugin provides natural language automation powered by AI.
 * Users can write instructions in plain language to automate browser tasks.
 *
 * Access Control:
 * - Available to all users
 *
 * Note: This plugin requires platform-specific LLM and RPA implementation.
 * The actual component is provided via a factory.
 */
object LLMRpaPanelPlugin : Plugin {
    override val pluginId = "llmrpa-panel"
    override val displayName = "LLM RPA Panel"

    /**
     * Register the plugin with a component factory.
     *
     * @param context The plugin context for registration
     * @param componentFactory Factory to create the LLM RPA component
     */
    fun register(
        context: PluginContext,
        componentFactory: (ctx: ComponentContext, panelInfo: PanelInfo) -> PanelComponentWithUI
    ) {
        context.panelRegistry.registerPanel(LLMRpaInfo) { ctx, panelInfo ->
            componentFactory(ctx, panelInfo)
        }
    }

    /**
     * Unregister the panel.
     *
     * @param context The plugin context for unregistration
     */
    fun unregister(context: PluginContext) {
        context.panelRegistry.unregisterPanel(LLMRpaInfo.id)
    }

    override fun register(context: PluginContext) {
        // No-op: This plugin requires explicit registration with component factory
        // Use register(context, componentFactory) instead
    }
}
