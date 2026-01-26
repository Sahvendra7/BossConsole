package ai.rever.boss.plugin.api

import kotlinx.coroutines.CoroutineScope

/**
 * Context provided to plugins for registration and runtime access.
 *
 * This interface abstracts the host application's services that plugins need.
 * Plugins should depend on this interface rather than concrete implementations.
 */
interface PluginContext {
    /**
     * Registry for panel registration.
     * Plugins use this to register their panel components.
     */
    val panelRegistry: PanelRegistry

    /**
     * Registry for tab type registration.
     * Plugins use this to register custom tab types.
     */
    val tabRegistry: TabRegistry

    /**
     * Coroutine scope tied to the plugin's lifecycle.
     * Use this for long-running operations that should be cancelled when the plugin is disposed.
     */
    val pluginScope: CoroutineScope
}

/**
 * Interface for plugin modules.
 *
 * Each plugin module should expose an object implementing this interface
 * to allow the host application to register the plugin.
 */
interface Plugin {
    /**
     * Unique identifier for this plugin.
     */
    val pluginId: String

    /**
     * Human-readable name for this plugin.
     */
    val displayName: String

    /**
     * Register this plugin's panels and tab types with the host application.
     *
     * @param context The plugin context providing access to registries
     */
    fun register(context: PluginContext)

    /**
     * Called when the plugin is being disposed.
     * Override to clean up any resources.
     */
    fun dispose() {}
}
