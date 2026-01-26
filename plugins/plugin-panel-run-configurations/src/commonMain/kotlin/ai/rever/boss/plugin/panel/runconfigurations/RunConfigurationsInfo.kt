package ai.rever.boss.plugin.panel.runconfigurations

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Zap

/**
 * Run Configurations Panel Metadata
 *
 * Detects runnable files in the current project (main functions, scripts, tests).
 * Unlike the top bar run dropdown which shows run history, this plugin auto-detects
 * configurations from the project source code.
 *
 * IntelliJ-style separation:
 * - Top bar dropdown: Shows run history (previously executed configs)
 * - This plugin: Auto-detects and shows all runnable files in project
 */
object RunConfigurationsInfo : PanelInfo {
    override val id = PanelId("run-configurations", 6)
    override val displayName = "Run Configurations"
    override val icon = FeatherIcons.Zap
    override val defaultSlotPosition = left.top.bottom
}
