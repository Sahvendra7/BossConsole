package ai.rever.boss.v4.components.plugin

import ai.rever.boss.v4.components.plugin.panels.bottom.*
import ai.rever.boss.v4.components.plugin.panels.left_bottom.registerLanager
import ai.rever.boss.v4.components.plugin.panels.left_bottom.registerMastery
import ai.rever.boss.v4.components.plugin.panels.left_bottom.registerTaskResolver
import ai.rever.boss.v4.components.plugin.panels.left_top.registerLighthouse
import ai.rever.boss.v4.components.plugin.panels.left_top.registerSystemOfRecord
import ai.rever.boss.v4.components.plugin.panels.left_top.registerValue
import ai.rever.boss.v4.components.plugin.panels.right_bottom.registerEhrExplorer
import ai.rever.boss.v4.components.plugin.panels.right_bottom.registerRpa
import ai.rever.boss.v4.components.plugin.panels.right_top.*
import ai.rever.boss.v4.components.plugin.tab_types.registerCodeEditor
import ai.rever.boss.v4.components.plugin.tab_types.registerWebBrowser
import ai.rever.boss.v4.components.registery.PanelRegistry
import ai.rever.boss.v4.components.registery.TabRegistry

class DefaultPlugin(
    val panelRegistry: PanelRegistry,
    val tabRegistry: TabRegistry
) {
    init {
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
        registerChrome()
        registerAgent()
        registerLLMRpa()

        registerRpa()
        registerEhrExplorer()

        registerCodeEditor()
        registerWebBrowser()
    }
}

