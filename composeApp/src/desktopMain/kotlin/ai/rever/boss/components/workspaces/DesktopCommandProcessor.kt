package ai.rever.boss.components.workspaces

import ai.rever.boss.run.ShellUtils

actual object CommandProcessor {
    actual fun normalizeCommand(command: String): String {
        // On Windows, replace " && " with "; " for PowerShell/CMD compatibility
        // Note: Preserves "&&" without surrounding spaces (edge case: logical operators)
        return if (ShellUtils.isWindows) {
            command.replace(" && ", "; ")
        } else {
            command // Keep Unix-style && on macOS/Linux
        }
    }
}
