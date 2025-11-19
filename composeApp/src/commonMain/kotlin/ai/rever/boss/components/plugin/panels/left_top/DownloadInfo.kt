package ai.rever.boss.components.plugin.panels.left_top

import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download

/**
 * Downloads panel info
 *
 * Priority 2 = Second position in left.top.bottom panel (below bookmarks)
 */
object DownloadInfo : PanelInfo {
    override val id = PanelId("downloads", 2)
    override val displayName = "Downloads"
    override val icon = Icons.Outlined.Download
    override val defaultSlotPosition = left.top.bottom
}
