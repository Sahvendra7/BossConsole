package ai.rever.boss.components.plugin.panels.right_bottom

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tungsten
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Gripfire

object EhrExplorerInfo : PanelInfo {
    override val id = PanelId("ehr_explorer", 17)
    override val displayName = "EHR Explorer"
    override val icon = FontAwesomeIcons.Brands.Gripfire
    override val defaultSlotPosition = right.top.bottom
}

class EhrExplorerComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("EHR Explorer")
    }
}

fun DefaultPlugin.registerEhrExplorer() = panelRegistry.registerPanel(EhrExplorerInfo) {
    ctx, panelInfo -> EhrExplorerComponent(ctx, panelInfo)
}