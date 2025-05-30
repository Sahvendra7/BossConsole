package ai.rever.boss.components.plugin.panels.bottom

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import compose.icons.FeatherIcons
import compose.icons.feathericons.Activity

object ActivityInfo : PanelInfo {
    override val id = PanelId("activity", 7)
    override val displayName = "Activity"
    override val icon = FeatherIcons.Activity
    override val defaultSlotPosition = left.bottom
}

class ActivityComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF2B2D30)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Activity Monitor",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Daily actionable items and worklist integration.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    "Monitors tasks from ClickUp, Trello, Slack,",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    "email inboxes, EHR systems, and more.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "To be implemented",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

fun DefaultPlugin.registerActivity() = panelRegistry.registerPanel(ActivityInfo) {
    ctx, panelInfo -> ActivityComponent(ctx, panelInfo)
}