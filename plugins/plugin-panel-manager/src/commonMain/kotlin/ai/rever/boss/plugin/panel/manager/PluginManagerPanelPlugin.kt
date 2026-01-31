package ai.rever.boss.plugin.panel.manager

import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin that provides the Plugin Manager panel.
 *
 * This panel allows users to:
 * - View installed plugins
 * - Install new plugins from files
 * - Enable/disable plugins
 * - Uninstall plugins
 * - Check for and apply updates
 */
object PluginManagerPanelPlugin : Plugin {
    override val pluginId: String = "ai.rever.boss.plugin-manager"
    override val displayName: String = "Plugin Manager"

    /**
     * Holder for component binding to support late binding between operations and component.
     */
    class ComponentBinding {
        private var _component: PluginManagerComponent? = null

        /**
         * The current component instance.
         */
        val component: PluginManagerComponent?
            get() = _component

        /**
         * Update the component reference.
         */
        fun setComponent(component: PluginManagerComponent) {
            _component = component
        }
    }

    /**
     * Register the Plugin Manager panel with the given operations provider.
     *
     * @param context Plugin context
     * @param operationsProvider Factory for creating the operations interface
     */
    fun register(
        context: PluginContext,
        operationsProvider: () -> PluginManagerOperations
    ) {
        context.panelRegistry.registerPanel(
            content = PluginManagerInfo,
            factory = { componentContext: ComponentContext, _ ->
                PluginManagerComponent(
                    componentContext = componentContext,
                    operations = operationsProvider()
                )
            }
        )
    }

    /**
     * Register the Plugin Manager panel with a component binding.
     *
     * This overload allows the operations factory to receive a reference to
     * the component for state updates. The binding is updated each time
     * a new component is created.
     *
     * @param context Plugin context
     * @param binding Binding that will hold the component reference
     * @param operationsFactory Factory that takes the binding and returns operations
     */
    fun registerWithBinding(
        context: PluginContext,
        binding: ComponentBinding,
        operationsFactory: (ComponentBinding) -> PluginManagerOperations
    ) {
        context.panelRegistry.registerPanel(
            content = PluginManagerInfo,
            factory = { componentContext: ComponentContext, _ ->
                // Create operations first with binding
                val operations = operationsFactory(binding)

                // Create component
                val component = PluginManagerComponent(
                    componentContext = componentContext,
                    operations = operations
                )

                // Update binding with the new component
                binding.setComponent(component)

                component
            }
        )
    }

    override fun register(context: PluginContext) {
        // This plugin requires an operations provider
        // Use the register(context, operationsProvider) overload instead
        throw UnsupportedOperationException(
            "PluginManagerPanelPlugin requires an operations provider. " +
            "Use register(context, operationsProvider) instead."
        )
    }
}
