package ai.rever.boss.v4.components.plugin.panels.bottom

import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.plugin.DefaultPlugin
import ai.rever.boss.v4.components.registery.PanelComponentWithUI
import ai.rever.boss.v4.components.registery.PanelId
import ai.rever.boss.v4.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitBranch

object GitInfo : PanelInfo {
    override val id = PanelId("git", 10)
    override val displayName = "Git"
    override val icon = FeatherIcons.GitBranch
    override val defaultSlotPosition = left.bottom
}

class GitComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Git")
    }
}

fun DefaultPlugin.registerGit() = panelRegistry.registerPanel(GitInfo) {
    ctx, panelInfo -> GitComponent(ctx, panelInfo)
}