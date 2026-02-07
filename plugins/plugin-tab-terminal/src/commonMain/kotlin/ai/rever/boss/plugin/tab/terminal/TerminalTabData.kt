package ai.rever.boss.plugin.tab.terminal

import ai.rever.boss.plugin.api.TabIcon
import ai.rever.boss.plugin.api.TabTypeId
import ai.rever.boss.plugin.api.TerminalTabInfoInterface
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Tab info for terminal tabs.
 *
 * Contains configuration for a terminal tab instance including:
 * - Standard tab properties (id, title, icon)
 * - Terminal-specific properties (initialCommand, workingDirectory)
 *
 * Implements [TerminalTabInfoInterface] to allow dynamic plugins to access
 * terminal-specific properties without reflection.
 */
data class TerminalTabInfo(
    override val id: String,
    override val typeId: TabTypeId,
    override val title: String = "Terminal",
    override val icon: ImageVector = TerminalTabType.icon,
    override val tabIcon: TabIcon = TabIcon.Vector(icon),
    override val initialCommand: String? = null,
    override val workingDirectory: String? = null
) : TerminalTabInfoInterface {
    companion object {
        /** Maximum length for terminal tab titles - fits typical "user@hostname:/path" patterns */
        const val MAX_TITLE_LENGTH = 64
    }

    /**
     * Returns a copy of this tab info with an updated title.
     * Used when terminal window title changes via escape sequences (OSC 0/1/2).
     * Title is truncated to [MAX_TITLE_LENGTH] characters.
     */
    fun updateTitle(newTitle: String): TerminalTabInfo {
        val truncatedTitle = if (newTitle.length > MAX_TITLE_LENGTH) {
            newTitle.take(MAX_TITLE_LENGTH)
        } else {
            newTitle
        }
        return copy(title = truncatedTitle)
    }
}
