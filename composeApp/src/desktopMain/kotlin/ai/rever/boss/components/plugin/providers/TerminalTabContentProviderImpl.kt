package ai.rever.boss.components.plugin.providers

import ai.rever.boss.components.plugin.panels.bottom.terminal.PersistentTabbedTerminalContent as PersistentTerminalContentImpl
import ai.rever.boss.components.plugin.panels.bottom.terminal.TabbedTerminalStateRegistry
import ai.rever.boss.keymap.model.ShortcutContext
import ai.rever.boss.plugin.api.TerminalTabContentProvider
import ai.rever.boss.run.RUNNER_TERMINAL_PREFIX
import ai.rever.boss.run.RunnerTerminalService
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
        onShowSettings: () -> Unit,
        onTitleChange: ((String) -> Unit)?,
        onLinkClick: ((url: String, linkType: String) -> Boolean)?
    ) {
        val windowId = LocalWindowId.current

        PersistentTerminalContentImpl(
            terminalId = terminalId,
            initialCommand = initialCommand,
            workingDirectory = workingDirectory,
            onExit = onExit,
            onShowSettings = {
                // Always use host's settings handler for terminal settings
                windowId?.let { MenuActionsHandler.triggerOpenSettings(it, "TERMINAL") }
            },
            onTitleChange = onTitleChange,
            onLinkClick = onLinkClick
        )
    }

    override fun hasTerminalState(windowId: String, terminalId: String): Boolean {
        return TabbedTerminalStateRegistry.contains(windowId, terminalId)
    }

    override fun removeTerminalState(windowId: String, terminalId: String) {
        TabbedTerminalStateRegistry.remove(windowId, terminalId)
    }

    // ============ Phase 1: Terminal Control APIs ============

    override fun sendCommand(windowId: String, terminalId: String, command: String): Boolean {
        return try {
            val state = TabbedTerminalStateRegistry.get(windowId, terminalId)
            if (state != null) {
                // Send the command to the active terminal session
                val commandWithEnter = if (command.endsWith("\n")) command else "$command\n"
                state.sendInput(commandWithEnter.toByteArray(Charsets.UTF_8))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun sendInterrupt(windowId: String, terminalId: String): Boolean {
        return try {
            val state = TabbedTerminalStateRegistry.get(windowId, terminalId)
            if (state != null) {
                // Send Ctrl+C to the active terminal session
                state.sendInput(byteArrayOf(0x03)) // ASCII ETX (End of Text) = Ctrl+C
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun requestCloseTab(windowId: String, terminalId: String) {
        // Remove the terminal state which will trigger cleanup
        TabbedTerminalStateRegistry.remove(windowId, terminalId)
    }

    override fun removeAllForWindow(windowId: String) {
        TabbedTerminalStateRegistry.removeAllForWindow(windowId)
    }

    // ============ Phase 2: Runner Terminal Integration ============

    override fun isRunnerTerminal(terminalId: String): Boolean {
        return terminalId.startsWith(RUNNER_TERMINAL_PREFIX)
    }

    override fun markRunnerTerminalStopped(terminalId: String) {
        if (isRunnerTerminal(terminalId)) {
            RunnerTerminalService.markTerminalStopped(terminalId)
        }
    }

    override fun getRunConfigurationId(terminalId: String): String? {
        return if (isRunnerTerminal(terminalId)) {
            RunnerTerminalService.getConfigForTerminal(terminalId)
        } else {
            null
        }
    }

    // ============ Phase 2: Terminal Settings ============

    override fun getShellPath(): String {
        // BossTerm uses ShellUtils for shell detection
        // Return the platform default shell
        return defaultShellPath()
    }

    override fun setShellPath(path: String) {
        // Shell path is managed by BossTerm internally via its settings
        // This is a no-op for now - BossTerm handles shell selection
    }

    override fun getAvailableShells(): List<String> {
        return when {
            System.getProperty("os.name").lowercase().contains("windows") -> {
                listOf("cmd.exe", "powershell.exe", "pwsh.exe")
            }
            else -> {
                listOf("/bin/zsh", "/bin/bash", "/bin/sh", "/usr/local/bin/fish")
            }
        }
    }

    private fun defaultShellPath(): String {
        return when {
            System.getProperty("os.name").lowercase().contains("windows") -> "cmd.exe"
            System.getProperty("os.name").lowercase().contains("mac") -> "/bin/zsh"
            else -> "/bin/bash"
        }
    }

    // ============ Phase 3: Split Pane Support ============

    override fun createHorizontalSplit(windowId: String, terminalId: String): String? {
        // Split pane support would require BossTerm library enhancements
        // For now, return null to indicate not supported
        return null
    }

    override fun createVerticalSplit(windowId: String, terminalId: String): String? {
        // Split pane support would require BossTerm library enhancements
        // For now, return null to indicate not supported
        return null
    }

    // ============ Phase 4: Reset and Keyboard Context ============

    override fun getResetGeneration(): Int {
        return TabbedTerminalStateRegistry.resetGeneration.value
    }

    override fun getShortcutContext(): String {
        return ShortcutContext.TERMINAL.name
    }
}
