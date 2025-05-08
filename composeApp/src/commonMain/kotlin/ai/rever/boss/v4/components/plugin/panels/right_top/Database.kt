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
import compose.icons.feathericons.Database

object DatabaseInfo : PanelInfo {
    override val id = PanelId("database", 12)
    override val displayName = "Database"
    override val icon = FeatherIcons.Database
    override val defaultSlotPosition = right.top.top
}

class DatabaseComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("Database")
    }
}

fun DefaultPlugin.registerDatabase() = panelRegistry.registerPanel(DatabaseInfo) {
    ctx, panelInfo -> DatabaseComponent(ctx, panelInfo)
}
