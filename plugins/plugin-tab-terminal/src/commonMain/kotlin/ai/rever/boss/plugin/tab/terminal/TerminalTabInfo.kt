package ai.rever.boss.plugin.tab.terminal

import ai.rever.boss.plugin.api.TabTypeId
import ai.rever.boss.plugin.api.TabTypeInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal

/**
 * Terminal tab type info
 */
object TerminalTabType : TabTypeInfo {
    override val typeId = TabTypeId("terminal")
    override val displayName = "Terminal"
    override val icon = Icons.Outlined.Terminal
}
