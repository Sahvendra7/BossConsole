package ai.rever.boss.components.plugin.providers

import ai.rever.boss.components.plugin.panels.bottom.terminal.PersistentTabbedTerminalContent
import ai.rever.boss.components.plugin.panels.bottom.terminal.TabbedTerminalStateRegistry
import ai.rever.boss.plugin.api.TerminalTabContentProvider
import ai.rever.boss.window.LocalWindowId
import ai.rever.boss.window.MenuActionsHandler
import androidx.compose.runtime.Composable

/**
 * Desktop implementation of TerminalTabContentProvider.
 *
 * This provider wraps the existing PersistentTabbedTerminalContent and TabbedTerminalStateRegistry
 * to enable dynamic terminal tab plugins to access terminal functionality.
 */
class TerminalTabContentProviderImpl : TerminalTabContentProvider {

    @Composable
    override fun PersistentTabbedTerminalContent(
        terminalId: String,
        initialCommand: String?,
        workingDirectory: String?,
        onExit: () -> Unit,
        onTitleChange: ((String) -> Unit)?
    ) {
        val windowId = LocalWindowId.current

        PersistentTabbedTerminalContent(
            terminalId = terminalId,
            initialCommand = initialCommand,
            workingDirectory = workingDirectory,
            onExit = onExit,
            onShowSettings = {
                windowId?.let { MenuActionsHandler.triggerOpenSettings(it, "TERMINAL") }
            },
            onTitleChange = onTitleChange
        )
    }

    override fun hasTerminalState(windowId: String, terminalId: String): Boolean {
        return TabbedTerminalStateRegistry.contains(windowId, terminalId)
    }

    override fun removeTerminalState(windowId: String, terminalId: String) {
        TabbedTerminalStateRegistry.remove(windowId, terminalId)
    }
}
