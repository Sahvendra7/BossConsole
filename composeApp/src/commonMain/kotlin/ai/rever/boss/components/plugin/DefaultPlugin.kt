package ai.rever.boss.components.plugin

import ai.rever.boss.components.plugin.panels.bottom.console.registerConsole
import ai.rever.boss.components.plugin.panels.bottom.performance.registerPerformance
import ai.rever.boss.components.plugin.panels.bottom.terminal.registerTerminal
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.registerTopOfMind
import ai.rever.boss.components.plugin.panels.left_bottom.registerRunConfigurations
import ai.rever.boss.components.plugin.panels.left_top.registerBookmarks
import ai.rever.boss.components.plugin.panels.left_top.registerCodeBase
import ai.rever.boss.components.plugin.panels.left_top.registerDownloads
import ai.rever.boss.components.plugin.panels.right_top.registerAdminRoleManagement
import ai.rever.boss.components.plugin.panels.right_top.registerFluckPanel
import ai.rever.boss.components.plugin.panels.right_top.registerLLMRpa
import ai.rever.boss.components.plugin.panels.right_top.registerRoleCreation
import ai.rever.boss.components.plugin.panels.right_top.registerRpaEngine
import ai.rever.boss.components.plugin.panels.right_top.registerRpaRecorder
import ai.rever.boss.components.plugin.panels.right_top.registerSecretManager
import ai.rever.boss.components.plugin.panels.right_top.registerUserSecretList
import ai.rever.boss.components.plugin.tab_types.fluck.registerFluck
import ai.rever.boss.components.plugin.tab_types.registerCodeEditor
import ai.rever.boss.components.plugin.tab_types.registerTerminalTab
import ai.rever.boss.components.registery.PanelRegistry
import ai.rever.boss.components.registery.TabRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class DefaultPlugin(
    val panelRegistry: PanelRegistry,
    val tabRegistry: TabRegistry
) {
    // Lifecycle-aware scope for long-running operations like dynamic panel registration
    // This scope should be cancelled when the plugin is disposed
    internal val pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    init {
        // Panels
        registerBookmarks()  // Priority 1 - First position
        registerDownloads()  // Priority 2 - Below bookmarks
        registerCodeBase()
        registerTerminal()
        registerConsole()
        registerPerformance()
        registerTopOfMind()
        registerRunConfigurations()

        registerFluckPanel()
        registerLLMRpa()
        registerRpaRecorder()
        registerRpaEngine()

        registerAdminRoleManagement()
        registerRoleCreation()
        registerSecretManager()
        registerUserSecretList()

        // Tab Types
        registerFluck()
        registerCodeEditor()
        registerTerminalTab()
    }

    /**
     * Dispose the plugin and cancel all coroutines
     * Should be called when the plugin is no longer needed
     */
    fun dispose() {
        pluginScope.cancel()
    }
}

