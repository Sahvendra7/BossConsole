package ai.rever.boss.v4.components.window_panel.components

import BossDarkBackground
import BossDarkSurface
import ai.rever.boss.v4.components.dividers.VDivider
import ai.rever.boss.v4.components.model.WindowPanelModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Panel {
    LEFT, RIGHT, BOTTOM
}

@Composable
fun BossSideWindowPanel(
    windowPanelModel: WindowPanelModel,
    panel: Panel,
    content: @Composable () -> Unit) {

    val leftPanelWidth by derivedStateOf { windowPanelModel.leftPanelWidth }
    val rightPanelWidth by derivedStateOf { windowPanelModel.rightPanelWidth }
    val bottomPanelHeight by derivedStateOf { windowPanelModel.bottomPanelHeight }
    val isLeftPanelVisible by derivedStateOf { windowPanelModel.isLeftPanelVisible }
    val isRightPanelVisible by derivedStateOf { windowPanelModel.isRightPanelVisible }
    val isBottomPanelVisible by derivedStateOf { windowPanelModel.isBottomPanelVisible }

    if ((panel == Panel.LEFT && !isLeftPanelVisible)
        || (panel == Panel.RIGHT && !isRightPanelVisible)
        || (panel == Panel.BOTTOM && !isBottomPanelVisible)) {
        return
    }

    if (panel == Panel.RIGHT) {
        VDivider()
    } else if (panel == Panel.BOTTOM) {
        Divider()
    }

    Surface(
        modifier = Modifier
            .run {
                when (panel) {
                    Panel.LEFT -> fillMaxHeight().width(leftPanelWidth)
                    Panel.RIGHT -> fillMaxHeight().width(rightPanelWidth)
                    Panel.BOTTOM -> fillMaxWidth().height(bottomPanelHeight)
                }
            },
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BossDarkBackground)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(BossDarkSurface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = windowPanelModel.title[panel] ?: "Title",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterVertically)
                )
            }
            content()
        }
    }

    if (panel == Panel.LEFT) {
        VDivider()
    }
}
