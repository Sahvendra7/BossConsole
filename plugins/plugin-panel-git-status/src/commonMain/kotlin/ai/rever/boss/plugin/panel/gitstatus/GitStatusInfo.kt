package ai.rever.boss.plugin.panel.gitstatus

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitCommit

/**
 * Git Status panel info.
 * Displays changed, staged, and untracked files with staging controls.
 */
object GitStatusInfo : PanelInfo {
    override val id = PanelId("git-status", 14)
    override val displayName = "Git Changes"
    override val icon = FeatherIcons.GitCommit
    override val defaultSlotPosition = left.bottom
}
