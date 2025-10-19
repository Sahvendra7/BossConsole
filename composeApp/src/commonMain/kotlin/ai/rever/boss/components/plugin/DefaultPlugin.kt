package ai.rever.boss.components.plugin

import ai.rever.boss.components.plugin.panels.bottom.registerActivity
import ai.rever.boss.components.plugin.panels.bottom.registerBugReport
import ai.rever.boss.components.plugin.panels.bottom.registerErrors
import ai.rever.boss.components.plugin.panels.bottom.registerGit
import ai.rever.boss.components.plugin.panels.bottom.terminal.registerTerminal
import ai.rever.boss.components.plugin.panels.left_bottom.registerLanager
import ai.rever.boss.components.plugin.panels.left_bottom.registerMastery
import ai.rever.boss.components.plugin.panels.left_bottom.registerTaskResolver
import ai.rever.boss.components.plugin.panels.left_top.registerCodeBase
import ai.rever.boss.components.plugin.panels.left_top.registerLighthouse
import ai.rever.boss.components.plugin.panels.left_top.registerSystemOfRecord
import ai.rever.boss.components.plugin.panels.left_top.registerValue
import ai.rever.boss.components.plugin.panels.right_bottom.registerEhrExplorer
import ai.rever.boss.components.plugin.panels.right_top.*
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.registerTopOfMind
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
        registerCodeBase()
        registerLighthouse()
        registerSystemOfRecord()
        registerValue()

        registerLanager()
        registerMastery()
        registerTaskResolver()

        registerTerminal()
        registerBugReport()
        registerGit()
        registerActivity()
        registerErrors()

        registerDocker()
        registerDatabase()
        registerFluck()
        registerAgent()
        registerLLMRpa()
        registerRpaRecorder()
        registerRpaEngine()
        registerTopOfMind()
        registerSupabaseDemo()
        registerAdminRoleManagement()

        registerEhrExplorer()

        registerCodeEditor()
        registerFluckPanel()
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

