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
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Docker

object DockerInfo : PanelInfo {
    override val id = PanelId("docker", 15)
    override val displayName = "Docker"
    override val icon = FontAwesomeIcons.Brands.Docker
    override val defaultSlotPosition = right.top.top
}

class DockerComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Docker")
    }
}

fun DefaultPlugin.registerDocker() = panelRegistry.registerPanel(DockerInfo) {
    ctx, panelInfo -> DockerComponent(ctx, panelInfo)
}