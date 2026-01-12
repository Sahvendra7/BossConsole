package ai.rever.boss.components.workspaces

/**
 * Platform-aware command processing for terminal commands.
 *
 * This utility normalizes shell commands that use Unix-style && separators
 * to work correctly on Windows PowerShell, which requires semicolons (;).
 *
 * TRADE-OFF: Windows uses semicolon which doesn't propagate errors like && does.
 * See ShellUtils.commandSeparator documentation for detailed trade-off analysis.
 *
 * DESIGN NOTE: This is implemented as expect/actual to allow for future platform-specific
 * enhancements (e.g., special handling for Windows CMD vs PowerShell detection).
 */
expect object CommandProcessor {
    /**
     * Normalize shell command separators for the current platform.
     *
     * Replaces Unix-style " && " with platform-appropriate separators:
     * - Windows: Uses "; " for PowerShell/CMD compatibility (no error propagation)
     * - Unix/macOS/Linux: Keeps " && " (stops on first failure)
     *
     * @param command Command string (may contain placeholders)
     * @return Command with platform-appropriate separators
     */
    fun normalizeCommand(command: String): String
}
