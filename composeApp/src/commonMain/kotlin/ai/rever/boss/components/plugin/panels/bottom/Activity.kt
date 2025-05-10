package ai.rever.boss.components.plugin.panels.bottom

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import compose.icons.FeatherIcons
import compose.icons.feathericons.Activity

object ActivityInfo : PanelInfo {
    override val id = PanelId("activity", 7)
    override val displayName = "Activity"
    override val icon = FeatherIcons.Activity
    override val defaultSlotPosition = left.bottom
}

class ActivityComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Activity")
    }
}

fun DefaultPlugin.registerActivity() = panelRegistry.registerPanel(ActivityInfo) {
    ctx, panelInfo -> ActivityComponent(ctx, panelInfo)
}