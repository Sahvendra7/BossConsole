package ai.rever.boss.components.plugin.panels.bottom

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

object BugReportInfo : PanelInfo {
    override val id = PanelId("bugReport", 8)
    override val displayName = "Bug Report"
    override val icon = Icons.Outlined.BugReport
    override val defaultSlotPosition = left.bottom
}

class BugReportComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Bug Report")
    }
}

fun DefaultPlugin.registerBugReport() = panelRegistry.registerPanel(BugReportInfo) {
    ctx, panelInfo -> BugReportComponent(ctx, panelInfo)
}