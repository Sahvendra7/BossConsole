package ai.rever.boss.plugin.api

import ai.rever.boss.plugin.browser.BrowserService
import kotlinx.coroutines.CoroutineScope

/**
 * Marker interface for plugin sandbox.
 *
 * This interface is defined here to avoid circular dependencies.
 * The actual implementation is in the plugin-sandbox module.
 *
 * Plugins can use this to record errors, heartbeats, and check health status.
 */
interface PluginSandboxRef {
    /**
     * Unique identifier of the plugin running in this sandbox.
     */
    val pluginId: String

    /**
     * Record that the plugin is alive and responsive.
     */
    fun recordHeartbeat()

    /**
     * Record that a successful operation completed.
     */
    fun recordSuccess()

    /**
     * Record an error that occurred in the plugin.
     */
    fun recordError(error: Throwable)
}

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

    /**
     * Optional reference to the plugin's sandbox for health reporting.
     *
     * Returns null if sandboxing is not enabled for this context.
     * Plugins can use this to record heartbeats and errors for health monitoring.
     */
    val sandbox: PluginSandboxRef?
        get() = null

    /**
     * Optional browser service for plugins that need embedded browser capabilities.
     *
     * Returns null if browser service is not available (e.g., JxBrowser not licensed
     * or browser engine failed to initialize).
     *
     * Plugins can use this to create browser instances for displaying web content.
     */
    val browserService: BrowserService?
        get() = null

    /**
     * The plugin's manifest, providing access to configuration declared in plugin.json.
     *
     * Returns null for built-in plugins that don't have a manifest file.
     * External plugins will always have their manifest available here.
     */
    val manifest: PluginManifest?
        get() = null
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
