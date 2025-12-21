package ai.rever.boss.components.settings.sections

import ai.rever.bossterm.compose.settings.SettingsManager
import ai.rever.bossterm.compose.settings.SettingsPanel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * Terminal settings using BossTerm's full settings panel.
 *
 * This integrates BossTerm's comprehensive settings system directly into BOSS,
 * providing access to all terminal customization options:
 * - Visual: Font, size, line spacing, cursor style
 * - Themes: Built-in color themes with live preview
 * - Behavior: Mouse, keyboard, selection options
 * - Scrollbar: Appearance and markers
 * - Performance: Refresh rate, buffer size
 * - Emulation: Terminal compatibility
 * - Search: Search behavior defaults
 * - Hyperlinks: URL detection and click behavior
 * - Type-Ahead: Latency prediction for SSH
 * - And more...
 *
 * Settings are automatically saved to ~/.bossterm/settings.json
 * and changes take effect immediately for new terminal sessions.
 */
@Composable
fun TerminalSettings() {
    // Use BossTerm's singleton settings manager for reactive updates
    val settingsManager = remember { SettingsManager.instance }
    val currentSettings by settingsManager.settings.collectAsState()

    SettingsPanel(
        settings = currentSettings,
        onSettingsChange = { newSettings ->
            settingsManager.updateSettings(newSettings)
        },
        onResetToDefaults = {
            settingsManager.resetToDefaults()
        },
        onRestartApp = null, // Not needed in embedded mode
        modifier = Modifier.fillMaxSize()
    )
}
