package ai.rever.boss.v4.components.bars.vertical

import ai.rever.boss.v4.components.dividers.SDivider
import ai.rever.boss.v4.components.dividers.VDivider
import ai.rever.boss.v4.components.misc.DraggableSidebarSection
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossLeftSideBar(sidebarModel: BossWindowPanelModel) {
    VerticalBar(40.dp) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DraggableSidebarSection(slot = Panel.leftTopTop, sidebarModel = sidebarModel)
            SDivider()
            DraggableSidebarSection(slot = Panel.leftTopBottom, sidebarModel = sidebarModel)
            
            Spacer(modifier = Modifier.weight(1f))
            
            DraggableSidebarSection(slot = Panel.leftBottom, sidebarModel = sidebarModel)
        }
    }
    VDivider()
}