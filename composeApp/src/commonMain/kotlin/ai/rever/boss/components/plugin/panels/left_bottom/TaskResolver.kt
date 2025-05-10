package ai.rever.boss.components.plugin.panels.left_bottom

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

object TaskResolverInfo : PanelInfo {
    override val id = PanelId("taskResolver", 5)
    override val displayName = "Task Resolver"
    override val icon = Icons.Outlined.Grain
    override val defaultSlotPosition = left.top.bottom
}

class TaskResolverComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Task Resolver")
    }
}

fun DefaultPlugin.registerTaskResolver() = panelRegistry.registerPanel(TaskResolverInfo) {
    ctx, panelInfo -> TaskResolverComponent(ctx, panelInfo)
}