package ai.rever.boss.plugin.panel.terminal

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelEventProvider
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.SettingsProvider
import ai.rever.boss.plugin.api.TerminalContentProvider
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin for Terminal panel
 *
 * This plugin provides the Terminal panel which allows users to:
 * - Access a full-featured terminal emulator
 * - Multiple tabs within the panel
 * - Split panes (horizontal/vertical)
 * - Run commands from run configurations
 *
 * Access Control:
 * - Available to all users
 *
 * Note: This plugin requires platform-specific terminal implementation.
 * The actual terminal content is provided via the TerminalContentProvider.
 */
object TerminalPanelPlugin : Plugin {
    override val pluginId = "terminal-panel"
    override val displayName = "Terminal Panel"

    /**
     * Register the plugin with a component factory.
     *
     * @param context The plugin context for registration
     * @param componentFactory Factory to create the terminal component
     */
    fun register(
        context: PluginContext,
        componentFactory: (ctx: ComponentContext, panelInfo: PanelInfo) -> PanelComponentWithUI
    ) {
        context.panelRegistry.registerPanel(TerminalInfo) { ctx, panelInfo ->
            componentFactory(ctx, panelInfo)
        }
    }

    /**
     * Register the plugin with providers for clean plugin architecture.
     *
     * @param context The plugin context for registration
     * @param terminalContentProvider Provider for terminal content composables
     * @param panelEventProvider Provider for panel event operations
     * @param settingsProvider Provider for settings operations
     */
    fun registerWithProviders(
        context: PluginContext,
        terminalContentProvider: TerminalContentProvider,
        panelEventProvider: PanelEventProvider,
        settingsProvider: SettingsProvider
    ) {
        context.panelRegistry.registerPanel(TerminalInfo) { ctx, panelInfo ->
            TerminalComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                terminalContentProvider = terminalContentProvider,
                panelEventProvider = panelEventProvider,
                settingsProvider = settingsProvider
            )
        }
    }

    /**
     * Unregister the panel.
     *
     * @param context The plugin context for unregistration
     */
    fun unregister(context: PluginContext) {
        context.panelRegistry.unregisterPanel(TerminalInfo.id)
    }

    override fun register(context: PluginContext) {
        val terminalContentProvider = context.terminalContentProvider
        val panelEventProvider = context.panelEventProvider
        val settingsProvider = context.settingsProvider

        if (terminalContentProvider == null || panelEventProvider == null || settingsProvider == null) {
            // Providers not available - cannot register
            return
        }

        context.panelRegistry.registerPanel(TerminalInfo) { ctx, panelInfo ->
            TerminalComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                terminalContentProvider = terminalContentProvider,
                panelEventProvider = panelEventProvider,
                settingsProvider = settingsProvider
            )
        }
    }
}
