package ai.rever.boss.components.plugin.providers

import ai.rever.boss.components.events.PanelEventBus
import ai.rever.boss.components.plugin.panels.bottom.terminal.TabbedTerminalContent
import ai.rever.boss.components.plugin.panels.bottom.terminal.resetTerminals
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.panel.terminal.PanelEventProvider
import ai.rever.boss.plugin.panel.terminal.SettingsProvider
import ai.rever.boss.plugin.panel.terminal.TerminalContentProvider
import ai.rever.boss.window.MenuActionsHandler
import androidx.compose.runtime.Composable

/**
 * Implementation of TerminalContentProvider that wraps platform-specific terminal composables.
 */
class TerminalContentProviderImpl : TerminalContentProvider {
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

/**
 * Implementation of PanelEventProvider that wraps PanelEventBus.
 */
class PanelEventProviderImpl : PanelEventProvider {
    override suspend fun closePanel(panelId: PanelId, windowId: String) {
        PanelEventBus.closePanel(panelId, sourceWindowId = windowId)
    }
}

/**
 * Implementation of SettingsProvider that wraps MenuActionsHandler.
 */
class SettingsProviderImpl : SettingsProvider {
    override fun openSettings(windowId: String, section: String) {
        MenuActionsHandler.triggerOpenSettings(windowId, section)
    }
}
