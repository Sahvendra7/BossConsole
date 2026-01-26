package ai.rever.boss.plugin.panel.rpaengine

import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Cpu

/**
 * RPA Engine panel info
 *
 * Priority 20 = Position in right.top.top panel
 */
object RpaEngineInfo : PanelInfo {
    override val id = PanelId("rpa_engine", 20)
    override val displayName = "RPA Engine"
    override val icon = FeatherIcons.Cpu
    override val defaultSlotPosition = right.top.top
}
