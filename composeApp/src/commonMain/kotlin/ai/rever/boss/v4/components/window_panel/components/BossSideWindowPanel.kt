package ai.rever.boss.v4.components.window_panel.components

import BossDarkBackground
import BossDarkSurface
import ai.rever.boss.v4.components.buttons.BossActionButton
import ai.rever.boss.v4.components.dividers.VDivider
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.right
import ai.rever.boss.v4.components.model.Panel.Companion.top
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun BossSideWindowPanel(
    windowPanelModel: BossWindowPanelModel,
    panel: Panel,
    content: @Composable () -> Unit) {

    if (!windowPanelModel.isVisible(panel)) {
        return
    }

    if (panel == right) {
        VDivider()
    } else if (panel == bottom) {
        Divider()
    }

    fun Modifier.fillSize() = run {
        when (panel) {
            left,
            left.top,
            left.bottom ->
                fillMaxHeight().width(windowPanelModel.getSize(left))
            right,
            right.top,
            right.bottom ->
                fillMaxHeight().width(windowPanelModel.getSize(right))
            else -> fillMaxWidth().height(windowPanelModel.getSize(bottom))
        }
    }

    Surface(
        modifier = Modifier.fillSize(),
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BossDarkBackground)
        ) {

            when (panel) {
                left -> {
                    BossSideWindowPanels(windowPanelModel, listOf(left.top, left.bottom))
                }
                right -> {
                    BossSideWindowPanels(windowPanelModel, listOf(right.top, right.bottom))
                }
                else -> {
                    BossSideWindowPanelTopBar(
                        title = windowPanelModel.getPanelTitle(panel),
                        onMinimize = {
                            windowPanelModel.setPanelVisible(panel, false)
                        }
                    )
                }
            }

            content()
        }
    }

    if (panel == left) {
        VDivider()
    }
}

@Composable
fun ColumnScope.BossSideWindowPanels(windowPanelModel: BossWindowPanelModel, panels: List<Panel>) {
    panels.forEach { panel ->
        if (windowPanelModel.isVisible(panel)) {
            Column (modifier = Modifier.weight(1f)) {
                BossSideWindowPanel(windowPanelModel, panel) {}
            }
        }
    }
}

@Composable
fun BossSideWindowPanelTopBar(title: String?,
                              onMore: () -> Unit = {},
                              onMinimize: () -> Unit,
                              content: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(BossDarkSurface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title ?: "",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(bottom = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))


        Row (modifier = Modifier.padding(end = 4.dp)) {
            content?.invoke()

            BossActionButton(
                imageVector = Icons.Outlined.MoreVert,
                text = "More",
                color = Color.White,
                onClick = onMore
            )

            BossActionButton(
                imageVector = Icons.Outlined.Remove,
                text = "Minimize",
                color = Color.White,
                onClick = onMinimize
            )
        }
    }
}
