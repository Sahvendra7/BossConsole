package ai.rever.boss.v4.components.plugin.panels.bottom

import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.plugin.DefaultPlugin
import ai.rever.boss.v4.components.registery.PanelComponentWithUI
import ai.rever.boss.v4.components.registery.PanelId
import ai.rever.boss.v4.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

object ErrorInfo : PanelInfo {
    override val id = PanelId("errors", 9)
    override val displayName = "Errors"
    override val icon = Icons.Outlined.Info
    override val defaultSlotPosition = left.bottom
}

class ErrorsComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Errors")
    }
}

fun DefaultPlugin.registerErrors() = panelRegistry.registerPanel(ErrorInfo) {
    ctx, panelInfo -> ErrorsComponent(ctx, panelInfo)
}