package ai.rever.boss.v4.components.plugin.panels.right_top

import ai.rever.boss.v4.components.model.Panel.Companion.right
import ai.rever.boss.v4.components.model.Panel.Companion.top
import ai.rever.boss.v4.components.plugin.DefaultPlugin
import ai.rever.boss.v4.components.registery.PanelComponentWithUI
import ai.rever.boss.v4.components.registery.PanelId
import ai.rever.boss.v4.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import compose.icons.FeatherIcons
import compose.icons.feathericons.MessageSquare

object AgentInfo : PanelInfo {
    override val id = PanelId("agent", 14)
    override val displayName = "Agent"
    override val icon = FeatherIcons.MessageSquare
    override val defaultSlotPosition = right.top.top
}

class AgentComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Agent")
    }
}

fun DefaultPlugin.registerAgent() = panelRegistry.registerPanel(AgentInfo) {
    ctx, panelInfo -> AgentComponent(ctx, panelInfo)
}