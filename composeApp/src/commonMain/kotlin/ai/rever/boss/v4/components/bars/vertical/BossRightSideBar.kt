package ai.rever.boss.v4.components.bars.vertical

import ai.rever.boss.v4.components.dividers.SDivider
import ai.rever.boss.v4.components.misc.DraggableSidebarSection
import ai.rever.boss.v4.components.model.DraggableSidebarModel
import ai.rever.boss.v4.components.model.SidebarSlot
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossRightSideBar(sidebarModel: DraggableSidebarModel) {
    VerticalBar(40.dp) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DraggableSidebarSection(slot = SidebarSlot.RIGHT_TOP_TOP, sidebarModel = sidebarModel)
            SDivider()
            DraggableSidebarSection(slot = SidebarSlot.RIGHT_TOP_BOTTOM, sidebarModel = sidebarModel)
            
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}