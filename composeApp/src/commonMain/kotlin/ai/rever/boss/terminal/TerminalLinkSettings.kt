package ai.rever.boss.terminal

import kotlinx.serialization.Serializable

/**
 * How to open links clicked in terminal.
 */
@Serializable
enum class TerminalLinkOpenMode {
    /** Always ask the user (show dialog) */
    ALWAYS_ASK,
    /** Open in vertical split alongside the panel */
    VERTICAL_SPLIT,
    /** Open in horizontal split */
    HORIZONTAL_SPLIT,
    /** Open in new tab (default behavior) */
    NEW_TAB
}

/**
 * Settings for terminal link handling.
 * Persisted to ~/.boss/terminal-link-settings.json
 */
@Serializable
data class TerminalLinkSettings(
    val openMode: TerminalLinkOpenMode = TerminalLinkOpenMode.ALWAYS_ASK
)
