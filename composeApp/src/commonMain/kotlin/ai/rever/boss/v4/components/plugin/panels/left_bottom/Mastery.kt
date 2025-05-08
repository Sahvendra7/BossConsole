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
import androidx.compose.material.icons.outlined.Mediation
import androidx.compose.runtime.Composable
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
        Text("Mastery")
    }
}

fun DefaultPlugin.registerMastery() = panelRegistry.registerPanel(MasteryInfo) {
    ctx, panelInfo -> MasteryComponent(ctx, panelInfo)
}