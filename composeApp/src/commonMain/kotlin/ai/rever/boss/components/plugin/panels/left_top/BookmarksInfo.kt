package ai.rever.boss.components.plugin.panels.left_top

import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star

/**
 * Bookmarks panel info
 *
 * Priority 1 = First position in left.top.top panel
 */
object BookmarksInfo : PanelInfo {
    override val id = PanelId("bookmarks", 1)
    override val displayName = "Bookmarks"
    override val icon = Icons.Outlined.Star
    override val defaultSlotPosition = left.top.top
}
