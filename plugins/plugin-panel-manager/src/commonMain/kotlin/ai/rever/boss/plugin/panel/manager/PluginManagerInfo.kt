package ai.rever.boss.plugin.panel.manager

import ai.rever.boss.plugin.api.Panel
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Panel information for the Plugin Manager.
 */
object PluginManagerInfo : PanelInfo {
    override val id: PanelId = PanelId(
        panelId = "plugin-manager",
        defaultOrder = 99, // Near the bottom of the sidebar
        pluginId = "ai.rever.boss.plugin-manager"
    )

    override val displayName: String = "Plugins"

    override val icon: ImageVector = Icons.Default.Extension

    override val defaultSlotPosition: Panel = left.bottom
}

/**
 * Tab types for the Plugin Manager panel.
 */
enum class PluginManagerTab {
    /**
     * List of installed plugins.
     */
    INSTALLED,

    /**
     * Browse available plugins.
     */
    AVAILABLE,

    /**
     * Available updates.
     */
    UPDATES,

    /**
     * Publish a plugin to the store.
     */
    PUBLISH
}
