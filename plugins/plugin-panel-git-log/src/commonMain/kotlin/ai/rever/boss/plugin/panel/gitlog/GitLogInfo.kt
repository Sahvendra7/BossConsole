package ai.rever.boss.plugin.panel.gitlog

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitBranch

/**
 * Git Log panel info.
 * Displays commit history with graph visualization.
 */
object GitLogInfo : PanelInfo {
    override val id = PanelId("git-log", 15)
    override val displayName = "Git Log"
    override val icon = FeatherIcons.GitBranch
    override val defaultSlotPosition = left.bottom
}
