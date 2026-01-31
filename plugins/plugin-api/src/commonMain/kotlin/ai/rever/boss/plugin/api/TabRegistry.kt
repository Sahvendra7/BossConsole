package ai.rever.boss.plugin.api

import androidx.compose.runtime.mutableStateMapOf
import com.arkivanov.decompose.ComponentContext

open class TabRegistry {
    // Map of tab type handlers
    private val tabHandlers = mutableStateMapOf<TabTypeId, TabTypeInfo>()

    // Map of tab component factories by tab type
    private val tabFactories = mutableStateMapOf<TabTypeId, (TabInfo, ComponentContext) -> TabComponentWithUI>()

    // Register a tab type from a plugin
    open fun registerTabType(
        content: TabTypeInfo,
        factory: (TabInfo, ComponentContext) -> TabComponentWithUI
    ) {
        tabHandlers[content.typeId] = content
        tabFactories[content.typeId] = factory
    }

    // Create a component for a tab configuration
    open fun createTabComponent(config: TabInfo, componentContext: ComponentContext): TabComponentWithUI? {
        return tabFactories[config.typeId]?.invoke(config, componentContext)
    }
}
