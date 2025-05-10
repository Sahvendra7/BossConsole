package ai.rever.boss.components.registery

import ai.rever.boss.components.model.Panel
import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.model.SidebarItem
import androidx.compose.runtime.mutableStateMapOf
import com.arkivanov.decompose.ComponentContext

class PanelRegistry {
    private val contentProviders = mutableStateMapOf<PanelId, (ComponentContext, PanelInfo) -> PanelComponentWithUI>()
    private val availablePanelInfo = mutableStateMapOf<PanelId, PanelInfo>()

    fun registerPanel(
        content: PanelInfo,
        factory: (ComponentContext, PanelInfo) -> PanelComponentWithUI
    ) {
        contentProviders[content.id] = factory
        availablePanelInfo[content.id] = content
    }

    fun createComponent(id: PanelId, componentContext: ComponentContext): PanelComponentWithUI? {
        return getPanelContent(id)?.let { contentProviders[id]?.invoke(componentContext, it) }
    }

    fun getPanelContent(id: PanelId): PanelInfo? {
        return availablePanelInfo[id]
    }

    fun getAllPanels(): List<PanelInfo> = availablePanelInfo.values.sortedBy { it.id.defaultOrder }

    fun getDefaultSidebarMap(): Map<Panel, List<SidebarItem>> =
        mapOf<Panel, MutableList<SidebarItem>>(
            left.top.top to mutableListOf<SidebarItem>(),
            left.top.bottom to mutableListOf<SidebarItem>(),
            left.bottom to mutableListOf<SidebarItem>(),
            right.top.top to mutableListOf<SidebarItem>(),
            right.top.bottom to mutableListOf<SidebarItem>(),
        ).apply {
            getAllPanels().forEach {
                get(it.defaultSlotPosition)?.add(it.sidebarItem)
            }
        }

}