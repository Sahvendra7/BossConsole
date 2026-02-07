package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.runtime.Composable

// Note: TerminalComponent has been moved to plugin-panel-terminal module.
// This file only contains the expect/actual platform-specific composables.

/**
 * Platform-specific tabbed terminal content composable.
 * Desktop: Uses BossTerm's TabbedTerminal with multi-tab and split support
 * Other platforms: Shows placeholder (terminal not supported)
 *
 * This is the full-featured terminal for the sidebar panel.
 *
 * @param workingDirectory Optional working directory for the terminal (defaults to home directory)
 * @param onExit Called when the last terminal tab is closed
 * @param onShowSettings Called when user requests settings (right-click menu)
 */
@Composable
expect fun TabbedTerminalContent(
    workingDirectory: String? = null,
    onExit: () -> Unit = {},
    onShowSettings: () -> Unit = {}
)

/**
 * Platform-specific embedded terminal content composable.
 * Desktop: Uses BossTerm's EmbeddableTerminal (single terminal instance)
 * Other platforms: Shows placeholder (terminal not supported)
 *
 * This is used for terminal tabs where BOSS manages the tab lifecycle.
 *
 * @param terminalId Unique ID for this terminal instance. Used to preserve state across
 *                   composition tree changes (e.g., when splitting panels). If null, state
 *                   is tied to composition position and may be lost on tree restructuring.
 * @param initialCommand Optional command to run after terminal starts
 * @param workingDirectory Optional working directory for the terminal (defaults to home directory)
 * @param onExit Called when terminal process exits
 */
@Composable
expect fun TerminalContent(
    terminalId: String? = null,
    initialCommand: String? = null,
    workingDirectory: String? = null,
    onExit: () -> Unit = {}
)

/**
 * Platform-specific tabbed terminal with persistent state.
 * Desktop: Uses BossTerm's TabbedTerminal with TabbedTerminalStateRegistry
 * Other platforms: Shows placeholder (terminal not supported)
 *
 * This provides full-featured terminal (splits, multiple tabs) with state
 * persistence across composition changes (e.g., when switching parent tabs).
 *
 * @param terminalId Unique ID for this terminal instance, used as key in state registry
 * @param initialCommand Optional command to run after terminal starts (only for new terminals)
 * @param workingDirectory Optional working directory for the terminal (defaults to home directory)
 * @param onExit Called when the last terminal tab is closed
 * @param onShowSettings Called when user requests settings
 * @param onTitleChange Called when terminal window title changes via escape sequences (OSC 0/1/2)
 * @param onLinkClick Optional callback for hyperlink handling. If provided and returns true,
 *                    the link is considered handled. If returns false or not provided,
 *                    default link handling is used.
 */
@Composable
expect fun PersistentTabbedTerminalContent(
    terminalId: String,
    initialCommand: String? = null,
    workingDirectory: String? = null,
    onExit: () -> Unit = {},
    onShowSettings: () -> Unit = {},
    onTitleChange: ((String) -> Unit)? = null,
    onLinkClick: ((url: String, linkType: String) -> Boolean)? = null
)

/**
 * Platform-specific function to reset all terminal states.
 * Called when user triggers reset from panel's more menu.
 */
expect fun resetTerminals()
