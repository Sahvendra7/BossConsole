package ai.rever.boss.components.registery

import ai.rever.boss.components.model.Panel
import ai.rever.boss.components.model.SidebarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext

data class PanelId(
    val panelId: String,
    val defaultOrder: Int,
    val pluginId: String = "ai.rever.boss"
)

interface PanelInfo {
    val id: PanelId
    val displayName: String
    val icon: ImageVector
    val defaultSlotPosition: Panel

    val sidebarItem get() = SidebarItem(id, icon, displayName)
}

interface PanelComponentWithUI: ComponentContext {
    val panelInfo: PanelInfo

    @Composable
    fun Content()
}