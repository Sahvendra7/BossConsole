package ai.rever.boss.aiassistant

/**
 * Desktop implementation of AIAssistantChecker.
 * Uses AIAssistantDetector for installation checks and AIAssistantInstaller for commands.
 *
 * Issue #445: Auto-install AI assistants for workspaces
 */
actual object AIAssistantChecker {
    /**
     * Check if an AI assistant is installed using AIAssistantDetector.
     */
    actual suspend fun isInstalled(assistant: AIAssistant): Boolean {
        return AIAssistantDetector.isInstalled(assistant)
    }

    /**
     * Get the installation command using AIAssistantInstaller.
     */
    actual fun getInstallCommand(assistant: AIAssistant): String {
        return AIAssistantInstaller.getInstallCommand(assistant)
    }
}
