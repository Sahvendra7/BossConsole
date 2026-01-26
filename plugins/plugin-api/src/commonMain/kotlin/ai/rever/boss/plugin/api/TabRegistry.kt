package ai.rever.boss.plugin.api

import androidx.compose.runtime.mutableStateMapOf
import com.arkivanov.decompose.ComponentContext

class TabRegistry {
    // Map of tab type handlers
    private val tabHandlers = mutableStateMapOf<TabTypeId, TabTypeInfo>()

    // Map of tab component factories by tab type
    private val tabFactories = mutableStateMapOf<TabTypeId, (TabInfo, ComponentContext) -> TabComponentWithUI>()

    // Register a tab type from a plugin
    fun registerTabType(
        content: TabTypeInfo,
        factory: (TabInfo, ComponentContext) -> TabComponentWithUI
    ) {
        tabHandlers[content.typeId] = content
        tabFactories[content.typeId] = factory
    }

    // Create a component for a tab configuration
    fun createTabComponent(config: TabInfo, componentContext: ComponentContext): TabComponentWithUI? {
        return tabFactories[config.typeId]?.invoke(config, componentContext)
    }

}
