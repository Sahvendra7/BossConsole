package ai.rever.boss.v4.components.window_panel.components

import BossDarkBackground
import BossDarkSurface
import ai.rever.boss.v4.components.buttons.BossActionButton
import ai.rever.boss.v4.components.dividers.VDivider
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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

    val leftPanelWidth by derivedStateOf { windowPanelModel.leftPanelWidth }
    val rightPanelWidth by derivedStateOf { windowPanelModel.rightPanelWidth }
    val bottomPanelHeight by derivedStateOf { windowPanelModel.bottomPanelHeight }

    val isVisible by derivedStateOf { windowPanelModel.isVisible(panel) }

    if (!isVisible) {
        return
    }

    if (panel == Panel.RIGHT()) {
        VDivider()
    } else if (panel == Panel.BOTTOM) {
        Divider()
    }

    fun Modifier.fillSize() = run {
        when (panel) {
            Panel.LEFT(),
            Panel.LEFT(Panel.TOP),
            Panel.LEFT(Panel.BOTTOM) ->
                fillMaxHeight().width(leftPanelWidth)
            Panel.RIGHT(),
            Panel.RIGHT(Panel.TOP),
            Panel.RIGHT(Panel.BOTTOM) ->
                fillMaxHeight().width(rightPanelWidth)
            else -> fillMaxWidth().height(bottomPanelHeight)
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

            if (panel == Panel.LEFT()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val isTopVisible = windowPanelModel.isVisible(Panel.LEFT(Panel.TOP))
                    val isBottomVisible = windowPanelModel.isVisible(Panel.LEFT(Panel.BOTTOM))

                    if (isTopVisible) {
                        Box(modifier = Modifier.weight(if (isBottomVisible) 1f else 2f)) {
                            BossSideWindowPanel(windowPanelModel, Panel.LEFT(Panel.TOP)) {}
                        }
                    }

                    if (isBottomVisible) {
                        Box(modifier = Modifier.weight(if (isTopVisible) 1f else 2f)) {
                            BossSideWindowPanel(windowPanelModel, Panel.LEFT(Panel.BOTTOM)) {}
                        }
                    }
                }
            } else if (panel == Panel.RIGHT()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val isTopVisible = windowPanelModel.isVisible(Panel.RIGHT(Panel.TOP))
                    val isBottomVisible = windowPanelModel.isVisible(Panel.RIGHT(Panel.BOTTOM))

                    if (isTopVisible) {
                        Box(modifier = Modifier.weight(if (isBottomVisible) 1f else 2f)) {
                            BossSideWindowPanel(windowPanelModel, Panel.RIGHT(Panel.TOP)) {}
                        }
                    }

                    if (isBottomVisible) {
                        Box(modifier = Modifier.weight(if (isTopVisible) 1f else 2f)) {
                            BossSideWindowPanel(windowPanelModel, Panel.RIGHT(Panel.BOTTOM)) {}
                        }
                    }
                }
            } else {

                BossSideWindowPanelTopBar(
                    title = windowPanelModel.getPanelTitle(panel),
                    onMinimize = {
                        windowPanelModel.setPanelVisible(panel, false)
                    }
                )
            }

            content()
        }
    }

    if (panel == Panel.LEFT()) {
        VDivider()
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
