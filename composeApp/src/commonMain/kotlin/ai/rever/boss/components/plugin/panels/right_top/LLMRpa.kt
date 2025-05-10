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
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Hotjar

object LLMRpaInfo : PanelInfo {
    override val id = PanelId("llm_rpa", 16)
    override val displayName = "LLM RPA"
    override val icon = FontAwesomeIcons.Brands.Hotjar
    override val defaultSlotPosition = right.top.top
}

class LLMRpaComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        Text("LLM RPA")
    }
}

fun DefaultPlugin.registerLLMRpa() = panelRegistry.registerPanel(LLMRpaInfo) {
    ctx, panelInfo -> LLMRpaComponent(ctx, panelInfo)
}