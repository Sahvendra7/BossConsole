package ai.rever.boss.plugin.tab.codeeditor

import ai.rever.boss.plugin.api.TabIcon
import ai.rever.boss.plugin.api.TabInfo
import ai.rever.boss.plugin.api.TabTypeId
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Tab info for code editor tabs.
 *
 * Contains configuration for a code editor tab instance including:
 * - Standard tab properties (id, title, icon)
 * - Editor-specific properties (filePath, isModified)
 */
data class EditorTabInfo(
    override val id: String,
    override val typeId: TabTypeId,
    override val title: String,
    override val icon: ImageVector = Icons.Outlined.Code,
    override val tabIcon: TabIcon? = null,
    val filePath: String = "",
    val isModified: Boolean = false
) : TabInfo {
    /**
     * Returns the display title with a modification indicator (*) if modified.
     */
    val displayTitle: String
        get() = if (isModified) "$title *" else title
}
