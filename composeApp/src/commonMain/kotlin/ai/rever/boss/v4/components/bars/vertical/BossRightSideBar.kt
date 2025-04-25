package ai.rever.boss.v4.components.bars.vertical

import ai.rever.boss.v4.components.dividers.SDivider
import ai.rever.boss.v4.components.misc.DraggableSidebarSection
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.right
import ai.rever.boss.v4.components.model.Panel.Companion.top
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossRightSideBar(sidebarModel: BossWindowPanelModel) {
    VerticalBar(40.dp) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DraggableSidebarSection(slot = right.top.top, sidebarModel = sidebarModel)
            SDivider()
            DraggableSidebarSection(slot = right.top.bottom, sidebarModel = sidebarModel)
            
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}