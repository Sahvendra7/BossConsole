package ai.rever.boss.v4.components.plugin.panels.left_bottom

import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.top
import ai.rever.boss.v4.components.plugin.DefaultPlugin
import ai.rever.boss.v4.components.registery.PanelComponentWithUI
import ai.rever.boss.v4.components.registery.PanelId
import ai.rever.boss.v4.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Diversity2
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

object LanagerInfo : PanelInfo {
    override val id = PanelId("lanager", 3)
    override val displayName = "Lanager"
    override val icon = Icons.Outlined.Diversity2
    override val defaultSlotPosition = left.top.bottom
}

class LanagerComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Lanager")
    }
}

fun DefaultPlugin.registerLanager() = panelRegistry.registerPanel(LanagerInfo) {
    ctx, panelInfo -> LanagerComponent(ctx, panelInfo)
}
