package ai.rever.boss.plugin.panel.terminal

import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal

/**
 * Terminal panel info
 *
 * Priority 13 = Position in left.bottom panel
 */
object TerminalInfo : PanelInfo {
    override val id = PanelId("terminal", 13)
    override val displayName = "Terminal"
    override val icon = Icons.Outlined.Terminal
    override val defaultSlotPosition = left.bottom
}
