package ai.rever.boss.v4.components.plugin.panels.left_top

import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.top
import ai.rever.boss.v4.components.plugin.DefaultPlugin
import ai.rever.boss.v4.components.registery.PanelComponentWithUI
import ai.rever.boss.v4.components.registery.PanelId
import ai.rever.boss.v4.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

object SystemOfRecordInfo: PanelInfo {
    override val id = PanelId("system_of_record", 1)
    override val displayName = "System of Record"
    override val icon = Icons.Outlined.Widgets
    override val defaultSlotPosition = left.top.top
}

class SystemOfRecordComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("System of Record")
    }
}

fun DefaultPlugin.registerSystemOfRecord() = run {
    panelRegistry.registerPanel(SystemOfRecordInfo) { ctx, panelInfo ->
        SystemOfRecordComponent(ctx, panelInfo)
    }
}