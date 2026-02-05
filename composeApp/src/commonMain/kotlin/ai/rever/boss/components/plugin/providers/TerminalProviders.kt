package ai.rever.boss.components.plugin.providers

import ai.rever.boss.components.events.PanelEventBus
import ai.rever.boss.window.MenuActionsHandler
import androidx.compose.runtime.Composable

class TerminalContentProviderImpl : ai.rever.boss.plugin.api.TerminalContentProvider {
    @Composable
    override fun TabbedTerminalContent(
        workingDirectory: String?,
        onExit: () -> Unit,
        onShowSettings: () -> Unit
    ) {
        ai.rever.boss.components.plugin.panels.bottom.terminal.TabbedTerminalContent(
            workingDirectory = workingDirectory,
            onExit = onExit,
            onShowSettings = onShowSettings
        )
    }

    override fun resetTerminals() {
        ai.rever.boss.components.plugin.panels.bottom.terminal.resetTerminals()
    }
}

class PanelEventProviderImpl : ai.rever.boss.plugin.api.PanelEventProvider {
    override suspend fun closePanel(panelId: ai.rever.boss.plugin.api.PanelId, windowId: String) {
        PanelEventBus.closePanel(panelId, sourceWindowId = windowId)
    }
}

class SettingsProviderImpl : ai.rever.boss.plugin.api.SettingsProvider {
    override fun openSettings(windowId: String, section: String) {
        MenuActionsHandler.triggerOpenSettings(windowId, section)
    }
}
