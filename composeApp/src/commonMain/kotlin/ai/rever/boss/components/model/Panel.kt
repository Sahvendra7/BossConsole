@file:Suppress("UNUSED")
package ai.rever.boss.components.model

import ai.rever.boss.plugin.api.Panel as PluginPanel

/**
 * Re-exports from plugin-api module for backward compatibility.
 * New code should import directly from ai.rever.boss.plugin.api
 */

// Re-export Panel and nested types from plugin-api
typealias Panel = PluginPanel

// Re-export nested types
typealias TOP = PluginPanel.TOP
typealias LEFT = PluginPanel.LEFT
typealias RIGHT = PluginPanel.RIGHT
typealias BOTTOM = PluginPanel.BOTTOM

// Re-export companion object values for direct import support
// e.g., import ai.rever.boss.components.model.Panel.Companion.left
object PanelCompanionExtensions {
    val top = PluginPanel.top
    val left = PluginPanel.left
    val right = PluginPanel.right
    val bottom = PluginPanel.bottom
}
