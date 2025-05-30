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

object CodeBaseInfo : PanelInfo {
    override val id = PanelId("lighthouse", 0)
    override val displayName = "Lighthouse"
    override val icon = Icons.Outlined.Tungsten
    override val defaultSlotPosition = left.top.top
}

class CodeBaseComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("CodeBase")
    }
}

fun DefaultPlugin.registerCodeBase() = panelRegistry.registerPanel(CodeBaseInfo) {
        ctx, panelInfo -> LighthouseComponent(ctx, panelInfo)
}
