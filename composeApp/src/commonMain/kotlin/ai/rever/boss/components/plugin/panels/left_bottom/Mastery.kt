package ai.rever.boss.components.plugin.panels.left_bottom

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mediation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext

object MasteryInfo : PanelInfo {
    override val id = PanelId("mastery", 4)
    override val displayName = "Mastery"
    override val icon = Icons.Outlined.Mediation
    override val defaultSlotPosition = left.top.bottom
}

class MasteryComponent(
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
                    "Mastery",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Composer for agentic workflows that inherits",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    "TaskResolver properties. Contains workflow composer,",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    "task master, and TaskResolver registry access.",
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

fun DefaultPlugin.registerMastery() = panelRegistry.registerPanel(MasteryInfo) {
    ctx, panelInfo -> MasteryComponent(ctx, panelInfo)
}