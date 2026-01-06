package ai.rever.boss.aiassistant

/**
 * Platform-agnostic interface for checking AI assistant installation status.
 * Desktop implementation uses AIAssistantDetector.
 *
 * Issue #445: Auto-install AI assistants for workspaces
 */
expect object AIAssistantChecker {
    /**
     * Check if an AI assistant is installed.
     * @param assistant The assistant to check
     * @return true if installed, false otherwise
     */
    suspend fun isInstalled(assistant: AIAssistant): Boolean

    /**
     * Get the installation command for an assistant.
     * @param assistant The assistant to install
     * @return The shell command to install the assistant
     */
    fun getInstallCommand(assistant: AIAssistant): String
}
