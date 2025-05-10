package ai.rever.boss.components.plugin.panels.bottom

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

object TerminalInfo : PanelInfo {
    override val id = PanelId("terminal", 11)
    override val displayName = "Terminal"
    override val icon = Icons.Outlined.Terminal
    override val defaultSlotPosition = left.bottom
}

class TerminalComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Terminal")
    }
}

fun DefaultPlugin.registerTerminal() = panelRegistry.registerPanel(TerminalInfo) {
    ctx, panelInfo -> TerminalComponent(ctx, panelInfo)
}