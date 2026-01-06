package ai.rever.boss.aiassistant

import java.util.concurrent.TimeUnit

/**
 * Utility for generating platform-appropriate installation commands for AI assistants.
 * Automatically handles Node.js/npm installation when needed.
 *
 * Issue #445: Terminal context menu for AI coding assistants
 */
object AIAssistantInstaller {

    /**
     * Available installation methods.
     */
    enum class InstallMethod {
        NATIVE_SCRIPT,  // curl | bash (Unix) or irm | iex (Windows)
        NPM,            // npm install -g
        HOMEBREW        // brew install (macOS only)
    }

    /**
     * Get the recommended installation command for an assistant.
     * - Prefers native scripts for Claude Code and OpenCode (no npm needed)
     * - Prefers Homebrew for Codex on macOS (no npm needed)
     * - Uses npm with auto Node.js installation for Gemini CLI and Codex on other platforms
     *
     * @param assistant The assistant to install
     * @return The installation command to run in terminal
     */
    fun getInstallCommand(assistant: AIAssistant): String {
        return when (assistant) {
            AIAssistant.CLAUDE_CODE, AIAssistant.OPENCODE -> {
                // Native script available - no npm needed
                getNativeInstallCommand(assistant)
            }
            AIAssistant.CODEX -> {
                // Prefer Homebrew on macOS (no npm needed)
                if (isMacOS() && isHomebrewAvailable()) {
                    getHomebrewInstallCommand(assistant)
                } else {
                    // Use npm with Node.js auto-install if needed
                    getNpmInstallCommandWithNodeCheck(assistant)
                }
            }
            AIAssistant.GEMINI_CLI -> {
                // npm is the only option, with Node.js auto-install if needed
                getNpmInstallCommandWithNodeCheck(assistant)
            }
        }
    }

    /**
     * Get the native script installation command.
     */
    fun getNativeInstallCommand(assistant: AIAssistant): String {
        val scriptUrl = assistant.installScriptUrl ?: return ""
        return if (isWindows()) {
            // Windows uses PowerShell with .ps1 script
            val psUrl = scriptUrl.replace(".sh", ".ps1")
            "powershell -Command \"irm $psUrl | iex\""
        } else {
            // Unix uses curl | bash
            "curl -fsSL $scriptUrl | bash"
        }
    }

    /**
     * Get the npm installation command.
     */
    fun getNpmInstallCommand(assistant: AIAssistant): String {
        val pkg = assistant.npmPackage ?: return ""
        return "npm install -g $pkg"
    }

    /**
     * Get npm installation command with automatic Node.js installation if npm is not available.
     * - macOS: Uses Homebrew to install Node.js
     * - Linux: Uses nvm (Node Version Manager)
     * - Windows: Uses winget
     *
     * After installation, sources shell config to make the command available in current session.
     */
    fun getNpmInstallCommandWithNodeCheck(assistant: AIAssistant): String {
        val npmPackage = assistant.npmPackage ?: return ""
        val command = assistant.defaultCommand

        return when {
            isWindows() -> {
                // Windows: check if npm exists, if not install Node.js via winget
                "powershell -Command \"" +
                    "if (!(Get-Command npm -ErrorAction SilentlyContinue)) { " +
                    "Write-Host 'Installing Node.js via winget...' ; " +
                    "winget install OpenJS.NodeJS.LTS --accept-source-agreements --accept-package-agreements ; " +
                    "\$env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' + " +
                    "[System.Environment]::GetEnvironmentVariable('Path','User') " +
                    "} ; " +
                    "npm install -g $npmPackage ; " +
                    "Write-Host '' ; Write-Host '✓ Installation complete! Run ''$command'' to start.'\""
            }
            isMacOS() -> {
                // macOS: use Homebrew to install Node.js if npm not available
                // After install, rehash to update command cache
                "{ command -v npm >/dev/null 2>&1 || { echo 'Installing Node.js via Homebrew...' && brew install node; }; } && " +
                    "npm install -g $npmPackage && " +
                    "hash -r 2>/dev/null; " +
                    "echo '' && echo '✓ Installation complete! Run \"$command\" to start.'"
            }
            else -> {
                // Linux: use nvm to install Node.js if npm not available
                // After install, source shell config and rehash to make command available
                "{ command -v npm >/dev/null 2>&1 || { " +
                    "echo 'Installing Node.js via nvm...' && " +
                    "curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/master/install.sh | bash && " +
                    "export NVM_DIR=\"\$HOME/.nvm\" && " +
                    "[ -s \"\$NVM_DIR/nvm.sh\" ] && . \"\$NVM_DIR/nvm.sh\" && " +
                    "nvm install --lts; " +
                    "}; } && " +
                    "npm install -g $npmPackage && " +
                    "export NVM_DIR=\"\$HOME/.nvm\" && [ -s \"\$NVM_DIR/nvm.sh\" ] && . \"\$NVM_DIR/nvm.sh\" && " +
                    "hash -r 2>/dev/null; " +
                    "echo '' && echo '✓ Installation complete! Run \"$command\" to start.'"
            }
        }
    }

    /**
     * Get the Homebrew installation command (macOS only).
     */
    fun getHomebrewInstallCommand(assistant: AIAssistant): String {
        val pkg = assistant.homebrewPackage ?: return ""
        // Some packages are casks (like Codex)
        return if (assistant == AIAssistant.CODEX) {
            "brew install --cask $pkg"
        } else {
            "brew install $pkg"
        }
    }

    /**
     * Get available installation methods for an assistant on the current platform.
     */
    fun getAvailableMethods(assistant: AIAssistant): List<InstallMethod> {
        val methods = mutableListOf<InstallMethod>()

        // Native script available?
        if (assistant.installScriptUrl != null) {
            methods.add(InstallMethod.NATIVE_SCRIPT)
        }

        // Homebrew available? (macOS only)
        if (assistant.homebrewPackage != null && isMacOS()) {
            methods.add(InstallMethod.HOMEBREW)
        }

        // npm always available (requires Node.js)
        if (assistant.npmPackage != null) {
            methods.add(InstallMethod.NPM)
        }

        return methods
    }

    /**
     * Get the installation command for a specific method.
     */
    fun getInstallCommand(assistant: AIAssistant, method: InstallMethod): String {
        return when (method) {
            InstallMethod.NATIVE_SCRIPT -> getNativeInstallCommand(assistant)
            InstallMethod.NPM -> getNpmInstallCommand(assistant)
            InstallMethod.HOMEBREW -> getHomebrewInstallCommand(assistant)
        }
    }

    /**
     * Get a human-readable description of the install method.
     */
    fun getMethodDescription(method: InstallMethod): String {
        return when (method) {
            InstallMethod.NATIVE_SCRIPT -> "Native Installer (Recommended)"
            InstallMethod.NPM -> "npm (requires Node.js)"
            InstallMethod.HOMEBREW -> "Homebrew (macOS)"
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("windows")

    private fun isMacOS(): Boolean =
        System.getProperty("os.name").lowercase().contains("mac")

    /**
     * Check if Homebrew is available on macOS.
     */
    private fun isHomebrewAvailable(): Boolean {
        if (!isMacOS()) return false
        var process: Process? = null
        return try {
            process = ProcessBuilder("which", "brew")
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (completed) {
                process.exitValue() == 0
            } else {
                process.destroyForcibly()
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            process?.let { p ->
                runCatching { p.destroyForcibly() }
                runCatching { p.inputStream.close() }
                runCatching { p.errorStream.close() }
                runCatching { p.outputStream.close() }
            }
        }
    }
}
