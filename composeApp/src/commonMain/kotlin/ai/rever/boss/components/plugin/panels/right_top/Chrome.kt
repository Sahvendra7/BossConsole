package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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

class LighthouseComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Chrome")
    }
}

fun DefaultPlugin.registerChrome() = panelRegistry.registerPanel(ChromeInfo) {
    ctx, panelInfo -> LighthouseComponent(ctx, panelInfo)
}