package ai.rever.boss.plugin.panel.fluck

import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.SimpleIcons
import compose.icons.simpleicons.Openai

/**
 * Fluck/ChatGPT panel info
 *
 * Priority 15 = Position in right.top.top panel
 */
object FluckPanelInfo : PanelInfo {
    override val id = PanelId("fluck", 15)
    override val displayName = "ChatGPT"
    override val icon = SimpleIcons.Openai
    override val defaultSlotPosition = right.top.top
}
