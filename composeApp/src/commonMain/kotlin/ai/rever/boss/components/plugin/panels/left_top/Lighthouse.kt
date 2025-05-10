package ai.rever.boss.components.plugin.panels.left_top

import ai.rever.boss.components.model.Panel.Companion.left
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

object LighthouseInfo : PanelInfo {
    override val id = PanelId("lighthouse", 0)
    override val displayName = "Lighthouse"
    override val icon = Icons.Outlined.Tungsten
    override val defaultSlotPosition = left.top.top
}

class LighthouseComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Lighthouse")
    }
}

fun DefaultPlugin.registerLighthouse() = panelRegistry.registerPanel(LighthouseInfo) {
     ctx, panelInfo -> LighthouseComponent(ctx, panelInfo)
}
