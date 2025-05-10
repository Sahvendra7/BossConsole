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