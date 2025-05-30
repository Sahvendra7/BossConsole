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
    
    // Force create a new component (removes existing if any)
    fun forceCreateComponent(panelId: PanelId): PanelComponentWithUI? {
        // Remove existing component if any
        removeComponent(panelId)
        
        // Create new component
        return getOrCreateComponent(panelId)
    }
}