package ai.rever.boss.v4.components.bars.vertical

import ai.rever.boss.v4.components.dividers.SDivider
import ai.rever.boss.v4.components.dividers.VDivider
import ai.rever.boss.v4.components.misc.DraggableSidebarSection
import ai.rever.boss.v4.components.model.BossDraggableComponent
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
fun BossDraggableComponent.BossRightSideBar() {
    VDivider()
    VerticalBar(40.dp) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DraggableSidebarSection(slot = right.top.top)
            SDivider()
            DraggableSidebarSection(slot = right.top.bottom)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}