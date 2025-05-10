package ai.rever.boss.components.plugin.panels.left_top

import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
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