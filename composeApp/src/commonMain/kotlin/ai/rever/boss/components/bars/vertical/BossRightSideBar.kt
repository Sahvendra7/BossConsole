package ai.rever.boss.components.bars.vertical

import ai.rever.boss.components.dividers.SDivider
import ai.rever.boss.components.dividers.VDivider
import ai.rever.boss.components.misc.DraggableSidebarSection
import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.components.sidebar.SidebarVisibilitySettings
import ai.rever.boss.components.sidebar.SidebarVisibilitySettingsManager
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossDraggableComponent.BossRightSideBar() {
    val visibility by SidebarVisibilitySettingsManager.currentSettings.collectAsState()
    val customizeSlotId = visibility.customizeButtonSlotId

    VDivider()
    VerticalBar(40.dp) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DraggableSidebarSection(slot = right.top.top)
            if (customizeSlotId == SidebarVisibilitySettings.SLOT_RIGHT_TOP_TOP) {
                SidebarCustomizeMenu(slot = right.top.top)
            }
            SDivider()
            DraggableSidebarSection(slot = right.top.bottom)
            if (customizeSlotId == SidebarVisibilitySettings.SLOT_RIGHT_TOP_BOTTOM) {
                SidebarCustomizeMenu(slot = right.top.bottom)
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
