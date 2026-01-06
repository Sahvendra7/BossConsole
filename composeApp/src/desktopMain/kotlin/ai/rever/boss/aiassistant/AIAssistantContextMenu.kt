package ai.rever.boss.aiassistant

import ai.rever.bossterm.compose.ContextMenuItem
import ai.rever.bossterm.compose.ContextMenuSubmenu
import ai.rever.bossterm.compose.ContextMenuElement
import ai.rever.boss.run.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Builds context menu items for AI Assistant launcher.
 *
 * Issue #445: Terminal context menu for AI coding assistants
 */
object AIAssistantContextMenu {

    /**
     * Build the "Launch AI Assistant" submenu for terminal context menus.
     *
     * @param workingDirectory Current terminal working directory (for cd command)
     * @param onLaunchCommand Callback to execute command in terminal
     * @param onCreateTab Callback to create new tab with command (for installation)
     * @param scope CoroutineScope for async operations
     * @param installationStatuses Map of assistant installation statuses
     * @return ContextMenuSubmenu with items for each enabled assistant
     */
    fun buildSubmenu(
        workingDirectory: String?,
        onLaunchCommand: (String) -> Unit,
        onCreateTab: (String) -> Unit,
        scope: CoroutineScope,
        installationStatuses: Map<AIAssistant, AIAssistantDetector.InstallationStatus>
    ): ContextMenuSubmenu {
        val settings = AIAssistantSettingsManager.currentSettings.value

        val items = AIAssistant.entries.mapNotNull { assistant ->
            val config = settings.getConfig(assistant)

            // Skip if assistant is disabled in settings
            if (!config.enabled) return@mapNotNull null

            val isInstalled = installationStatuses[assistant]?.installed ?: false

            // Skip unavailable if setting says so
            if (!isInstalled && !settings.showUnavailableAssistants) {
                return@mapNotNull null
            }

            if (isInstalled) {
                // Launch menu item for installed assistants
                ContextMenuItem(
                    id = "ai_assistant_${assistant.name.lowercase()}",
                    label = config.buildMenuLabel(),
                    enabled = true
                ) {
                    scope.launch {
                        launchAssistant(assistant, workingDirectory, onLaunchCommand)
                    }
                }
            } else {
                // Install menu item for uninstalled assistants
                ContextMenuItem(
                    id = "ai_assistant_install_${assistant.name.lowercase()}",
                    label = "Install ${assistant.displayName}...",
                    enabled = true
                ) {
                    installAssistant(assistant, onCreateTab, scope)
                }
            }
        }

        return ContextMenuSubmenu(
            id = "ai_assistant_submenu",
            label = "AI Assistant",
            items = items
        )
    }

    /**
     * Build a list of context menu items (for cases where submenu is not desired).
     */
    fun buildItems(
        workingDirectory: String?,
        onLaunchCommand: (String) -> Unit,
        onCreateTab: (String) -> Unit,
        scope: CoroutineScope,
        installationStatuses: Map<AIAssistant, AIAssistantDetector.InstallationStatus>
    ): List<ContextMenuElement> {
        return listOf(buildSubmenu(workingDirectory, onLaunchCommand, onCreateTab, scope, installationStatuses))
    }

    /**
     * Install an assistant by opening a new terminal tab with the install command.
     * Schedules a delayed refresh to detect installation completion.
     */
    private fun installAssistant(
        assistant: AIAssistant,
        onCreateTab: (String) -> Unit,
        scope: CoroutineScope
    ) {
        val installCommand = AIAssistantInstaller.getInstallCommand(assistant)
        if (installCommand.isNotEmpty()) {
            println("[AIAssistant] Installing ${assistant.displayName}: $installCommand")
            onCreateTab(installCommand)

            // Schedule delayed refresh to detect installation completion
            scope.launch {
                schedulePostInstallRefresh(assistant)
            }
        } else {
            println("[AIAssistant] No install command available for ${assistant.displayName}")
        }
    }

    /**
     * Schedule periodic refresh checks after installation is initiated.
     * Checks at 30s, 60s, and 90s to catch when installation completes.
     */
    private suspend fun schedulePostInstallRefresh(assistant: AIAssistant) {
        val checkDelays = listOf(30_000L, 60_000L, 90_000L) // 30s, 60s, 90s

        for (checkDelay in checkDelays) {
            delay(checkDelay)
            val status = AIAssistantDetector.checkInstallation(assistant)
            println("[AIAssistant] Post-install check for ${assistant.displayName}: installed=${status.installed}")

            if (status.installed) {
                println("[AIAssistant] ${assistant.displayName} is now installed!")
                break
            }
        }
    }

    private suspend fun launchAssistant(
        assistant: AIAssistant,
        workingDirectory: String?,
        onLaunchCommand: (String) -> Unit
    ) {
        val config = AIAssistantSettingsManager.currentSettings.value.getConfig(assistant)
        val command = config.buildFullCommand()

        // Build command with cd if working directory is specified
        val fullCommand = ShellUtils.buildCommandWithWorkingDirectory(command, workingDirectory)

        println("[AIAssistant] Launching ${assistant.displayName}: $fullCommand")
        onLaunchCommand(fullCommand)
    }

    /**
     * Launch an assistant directly (for keyboard shortcuts).
     *
     * @param assistant The assistant to launch
     * @param workingDirectory Optional working directory
     * @param onLaunchCommand Callback to execute command in terminal
     */
    suspend fun launchAssistantDirect(
        assistant: AIAssistant,
        workingDirectory: String?,
        onLaunchCommand: (String) -> Unit
    ) {
        // Check if installed first
        val isInstalled = AIAssistantDetector.isInstalled(assistant)
        if (!isInstalled) {
            println("[AIAssistant] ${assistant.displayName} is not installed, cannot launch")
            return
        }

        launchAssistant(assistant, workingDirectory, onLaunchCommand)
    }
}
