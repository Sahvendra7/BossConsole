package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
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
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Chrome

object ChromeInfo : PanelInfo {
    override val id = PanelId("chrome", 13)
    override val displayName = "Chrome"
    override val icon = FontAwesomeIcons.Brands.Chrome
    override val defaultSlotPosition = right.top.top
}

class ChromeComponent(
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
                    "Chrome",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Browser automation tool for web-based workflows.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    "Automates Chrome browser tasks, form filling,",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    "and web scraping operations.",
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

fun DefaultPlugin.registerChrome() = panelRegistry.registerPanel(ChromeInfo) {
    ctx, panelInfo -> ChromeComponent(ctx, panelInfo)
}