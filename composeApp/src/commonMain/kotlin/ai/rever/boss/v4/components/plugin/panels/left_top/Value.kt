package ai.rever.boss.v4.components.plugin.panels.left_top

import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.top
import ai.rever.boss.v4.components.plugin.DefaultPlugin
import ai.rever.boss.v4.components.registery.PanelComponentWithUI
import ai.rever.boss.v4.components.registery.PanelId
import ai.rever.boss.v4.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

object ValueInfo : PanelInfo {
    override val id = PanelId("value", 2)
    override val displayName = "North Star"
    override val icon = Icons.Outlined.AutoGraph
    override val defaultSlotPosition = left.top.top
}

class ValueComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("North Star")
    }
}

fun DefaultPlugin.registerValue() = panelRegistry.registerPanel(ValueInfo) {
    ctx, panelInfo -> ValueComponent(ctx, panelInfo)
}
