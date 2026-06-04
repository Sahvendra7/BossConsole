package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.api.TabTypeId
import ai.rever.boss.plugin.api.TabUpdateProvider
import ai.rever.boss.plugin.api.TabUpdateProviderFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Global registry for TabUpdateProviderFactory instances.
 *
 * Since there can be multiple BossTabsComponent instances (for split views),
 * this registry allows plugins to find the correct factory for any tab by ID.
 *
 * Each BossTabsComponent registers itself when created, and the registry
 * delegates to the appropriate factory based on which component owns the tab.
 */
object TabUpdateRegistry : TabUpdateProviderFactory {
    /**
     * Map of registered factories by component ID.
     */
    private val factories = ConcurrentHashMap<String, TabUpdateProviderFactory>()

    /**
     * Map of tab ID to component ID for quick lookup.
     */
    private val tabToComponent = ConcurrentHashMap<String, String>()

    /**
     * Register a TabUpdateProviderFactory for a component.
     *
     * @param componentId Unique ID for the component (e.g., windowId + panel ID)
     * @param factory The factory to register
     */
    fun register(componentId: String, factory: TabUpdateProviderFactory) {
        factories[componentId] = factory
    }

    /**
     * Unregister a factory.
     *
     * @param componentId The component ID to unregister
     */
    fun unregister(componentId: String) {
        factories.remove(componentId)
        // Clean up tab mappings for this component
        tabToComponent.entries.removeIf { it.value == componentId }
    }

    /**
     * Register that a tab is owned by a specific component.
     *
     * @param tabId The tab ID
     * @param componentId The component ID that owns this tab
     */
    fun registerTab(tabId: String, componentId: String) {
        tabToComponent[tabId] = componentId
    }

    /**
     * Unregister a tab mapping owned by [componentId]. Atomically a no-op if the tab id has
     * already been re-registered to another component — e.g. a move that adds the tab to its
     * destination before removing it from the source (panel-host tabs share a fixed id), where
     * the source's close must not wipe the destination's fresh mapping.
     *
     * @param tabId The tab ID to unregister
     * @param componentId The component that owned the tab
     */
    fun unregisterTab(tabId: String, componentId: String) {
        tabToComponent.remove(tabId, componentId)
    }

    /**
     * Create a TabUpdateProvider for the specified tab.
     *
     * Delegates to the factory that owns this tab.
     */
    override fun createProvider(tabId: String, typeId: TabTypeId): TabUpdateProvider? {
        // First, check if we know which component owns this tab
        val componentId = tabToComponent[tabId]
        if (componentId != null) {
            return factories[componentId]?.createProvider(tabId, typeId)
        }

        // If not found, search all factories (slower but works for new tabs)
        for ((_, factory) in factories) {
            val provider = factory.createProvider(tabId, typeId)
            if (provider != null) {
                return provider
            }
        }

        return null
    }

    /**
     * Clear all registrations. Used for testing.
     */
    fun clear() {
        factories.clear()
        tabToComponent.clear()
    }
}
