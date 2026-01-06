package ai.rever.boss.aiassistant

import kotlinx.serialization.Serializable

/**
 * Supported AI coding assistants with their default configurations.
 *
 * Issue #445: Terminal context menu for AI coding assistants
 */
@Serializable
enum class AIAssistant(
    val displayName: String,
    val defaultCommand: String,
    val yoloFlag: String,
    val menuLabel: String,
    // Installation properties
    val npmPackage: String?,
    val installScriptUrl: String?,
    val homebrewPackage: String?,
    val requiresNode: Boolean
) {
    CLAUDE_CODE(
        displayName = "Claude Code",
        defaultCommand = "claude",
        yoloFlag = "--dangerously-skip-permissions",
        menuLabel = "Auto Mode",
        npmPackage = "@anthropic-ai/claude-code",
        installScriptUrl = "https://claude.ai/install.sh",
        homebrewPackage = null,
        requiresNode = false
    ),
    CODEX(
        displayName = "Codex",
        defaultCommand = "codex",
        yoloFlag = "--full-auto",
        menuLabel = "Full Auto",
        npmPackage = "@openai/codex",
        installScriptUrl = null,
        homebrewPackage = "codex",
        requiresNode = true
    ),
    GEMINI_CLI(
        displayName = "Gemini CLI",
        defaultCommand = "gemini",
        yoloFlag = "-y",
        menuLabel = "Auto",
        npmPackage = "@google/gemini-cli",
        installScriptUrl = null,
        homebrewPackage = null,
        requiresNode = true
    ),
    OPENCODE(
        displayName = "OpenCode",
        defaultCommand = "opencode",
        yoloFlag = "--auto-approve",
        menuLabel = "Auto",
        npmPackage = "opencode-ai",
        installScriptUrl = "https://opencode.ai/install",
        homebrewPackage = "opencode",
        requiresNode = false
    )
}

/**
 * Configuration for a single AI assistant.
 */
@Serializable
data class AIAssistantConfig(
    val assistant: AIAssistant,
    val customCommand: String? = null,
    val yoloEnabled: Boolean = true,
    val customYoloFlag: String? = null,
    val enabled: Boolean = true
) {
    /**
     * Get the effective command to run.
     */
    fun getCommand(): String = customCommand ?: assistant.defaultCommand

    /**
     * Get the effective YOLO flag.
     */
    fun getYoloFlag(): String = customYoloFlag ?: assistant.yoloFlag

    /**
     * Build the full command with YOLO mode if enabled.
     */
    fun buildFullCommand(): String {
        val baseCommand = getCommand()
        return if (yoloEnabled) {
            "$baseCommand ${getYoloFlag()}"
        } else {
            baseCommand
        }
    }

    /**
     * Build the menu label with YOLO indicator.
     */
    fun buildMenuLabel(): String {
        val base = assistant.displayName
        return if (yoloEnabled) {
            "$base (${assistant.menuLabel})"
        } else {
            base
        }
    }
}

/**
 * Settings for the AI Assistant launcher feature.
 *
 * Issue #445: Terminal context menu for AI coding assistants
 */
@Serializable
data class AIAssistantSettings(
    val assistants: Map<AIAssistant, AIAssistantConfig> = defaultConfigs(),
    val showUnavailableAssistants: Boolean = true,
    val cacheInstallationStatus: Boolean = true,
    val installationCacheDurationMs: Long = 300_000 // 5 minutes
) {
    companion object {
        fun defaultConfigs(): Map<AIAssistant, AIAssistantConfig> =
            AIAssistant.entries.associateWith { AIAssistantConfig(assistant = it) }
    }

    fun getConfig(assistant: AIAssistant): AIAssistantConfig {
        return assistants[assistant] ?: AIAssistantConfig(assistant = assistant)
    }

    fun updateConfig(config: AIAssistantConfig): AIAssistantSettings {
        return copy(assistants = assistants + (config.assistant to config))
    }
}
