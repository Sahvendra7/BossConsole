package ai.rever.boss.v4.components.plugin.panels.right_bottom

import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.right
import ai.rever.boss.v4.components.model.Panel.Companion.top
import ai.rever.boss.v4.components.plugin.DefaultPlugin
import ai.rever.boss.v4.components.registery.PanelComponentWithUI
import ai.rever.boss.v4.components.registery.PanelId
import ai.rever.boss.v4.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tungsten
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.React

object RpaInfo : PanelInfo {
    override val id = PanelId("rpa", 18)
    override val displayName = "RPA"
    override val icon = FontAwesomeIcons.Brands.React
    override val defaultSlotPosition = right.top.bottom
}

class RpaComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("RPA")
    }
}

fun DefaultPlugin.registerRpa() = panelRegistry.registerPanel(RpaInfo) {
    ctx, panelInfo -> RpaComponent(ctx, panelInfo)
}