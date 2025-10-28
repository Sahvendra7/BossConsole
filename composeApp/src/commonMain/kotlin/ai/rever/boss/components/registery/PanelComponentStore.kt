package ai.rever.boss.components.registery

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.arkivanov.decompose.ComponentContext

class PanelComponentStore(
    private val rootContext: ComponentContext,
    private val registry: PanelRegistry
) {
    // Map of active components by panel ID
    val activeComponents: SnapshotStateMap<PanelId, PanelComponentWithUI> = mutableStateMapOf()

    // Get or create a component for a panel
    fun getOrCreateComponent(panelId: PanelId): PanelComponentWithUI? {
        // Return existing component if available
        activeComponents[panelId]?.let { return it }

        // Create new component
        val component = registry.createComponent(panelId, rootContext) ?: return null

        // Store and return
        activeComponents[panelId] = component
        return component
    }
    
    // Remove a component when panel is closed
    fun removeComponent(panelId: PanelId) {
        activeComponents.remove(panelId)
    }

    /**
     * Reset a panel by destroying and recreating its component.
     *
     * This method implements the "component recreation" reset strategy:
     * 1. Call onBeforeReset() on the current component for cleanup
     * 2. Remove component from activeComponents (triggers Decompose disposal)
     * 3. Create a fresh component instance
     * 4. Call onInitialized() on the new component
     * 5. Store and activate the new component
     *
     * This ensures the panel starts with completely fresh state,
     * as if it were just opened for the first time.
     *
     * All in-memory data will be lost during reset. Components that need
     * to persist data should save it to persistent storage before reset.
     *
     * @param panelId The panel to reset
     * @return true if reset was successful, false if panel doesn't exist
     */
    fun resetComponent(panelId: PanelId): Boolean {
        // Get current component
        val currentComponent = activeComponents[panelId]
        if (currentComponent == null) {
            println("⚠️  [PanelComponentStore] Cannot reset panel: ${panelId.panelId} (not active)")
            return false
        }

        println("🔄 [PanelComponentStore] Resetting panel: ${panelId.panelId}")

        try {
            // Call lifecycle hook for cleanup
            currentComponent.onBeforeReset()

            // Remove from active components (triggers Decompose disposal)
            activeComponents.remove(panelId)

            // Create new component instance
            val newComponent = registry.createComponent(panelId, rootContext)
            if (newComponent == null) {
                println("❌ [PanelComponentStore] Failed to create new component for: ${panelId.panelId}")
                return false
            }

            // Call initialization hook
            newComponent.onInitialized()

            // Store and activate new component
            activeComponents[panelId] = newComponent

            println("✅ [PanelComponentStore] Successfully reset panel: ${panelId.panelId}")
            return true
        } catch (e: Exception) {
            println("❌ [PanelComponentStore] Error resetting panel ${panelId.panelId}: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

}
