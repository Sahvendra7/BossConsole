package ai.rever.boss.components.settings.sections

import ai.rever.bosseditor.settings.EditorSettingsManager
import ai.rever.bosseditor.settings.EditorSettingsPanel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * BossEditor settings using BossEditor's full settings panel.
 *
 * This integrates BossEditor's comprehensive settings system directly into BOSS,
 * providing access to all editor customization options:
 * - Visual: Theme, font size, line spacing, line numbers
 * - Behavior: Scroll speed, tab size, word wrap
 * - Features: Code folding, rainbow brackets, indent guides
 * - Caret: Style, blink rate
 * - Minimap: Enable/disable, width
 *
 * Settings are automatically saved to ~/.boss/editor-settings.json
 * and changes take effect immediately for all BossEditor instances.
 */
@Composable
fun BossEditorSettings() {
    // Use BossEditor's singleton settings manager for reactive updates
    val settingsManager = remember { EditorSettingsManager.instance }
    val currentSettings by settingsManager.settings.collectAsState()

    EditorSettingsPanel(
        settings = currentSettings,
        onSettingsChange = { newSettings ->
            settingsManager.updateSettings(newSettings)
        },
        onResetToDefaults = {
            settingsManager.resetToDefaults()
        },
        modifier = Modifier.fillMaxSize()
    )
}
