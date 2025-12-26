package ai.rever.boss.components.windows

import BossDarkBackground
import BossDarkSurface
import BossDarkBorder
import ai.rever.boss.components.settings.sections.*
import ai.rever.boss.components.settings.keymap.EditableKeymapSettings
import ai.rever.boss.components.settings.sidebar.SettingsSection
import ai.rever.boss.components.settings.sidebar.SettingsSidebar
import ai.rever.boss.updater.UpdateSettingsSection
import ai.rever.boss.utils.DisplayUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState

@Composable
actual fun SettingsWindow(
    onClose: () -> Unit,
    initialSection: String?
) {
    var isOpen by remember { mutableStateOf(true) }

    if (isOpen) {
        Window(
            onCloseRequest = {
                isOpen = false
                onClose()
            },
            title = "BOSS Settings",
            state = rememberWindowState(
                size = DisplayUtils.calculateSettingsWindowSize(),
                position = WindowPosition.Aligned(Alignment.Center)
            )
        ) {
            SettingsContent(initialSection = initialSection)
        }
    }
}

@Composable
private fun SettingsContent(initialSection: String? = null) {
    // Convert initial section string to enum, defaulting to FLUCK
    val startSection = remember(initialSection) {
        initialSection?.let { name ->
            SettingsSection.entries.find { it.name.equals(name, ignoreCase = true) }
        } ?: SettingsSection.FLUCK
    }
    var selectedSection by remember { mutableStateOf(startSection) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BossDarkBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            SettingsHeader()
            
            Divider(color = BossDarkBorder, thickness = 1.dp)
            
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BossDarkSurface)
            ) {
                // Sidebar with sections
                SettingsSidebar(
                    selectedSection = selectedSection,
                    onSectionChange = { selectedSection = it }
                )
                
                // Vertical divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(BossDarkBorder)
                )
                
                // Content area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    when (selectedSection) {
                        SettingsSection.FLUCK -> FluckBrowserSettings()
                        SettingsSection.CODE_EDITOR -> CodeEditorSettings()
                        SettingsSection.TERMINAL -> TerminalSettings()
                        SettingsSection.LLM_PROVIDERS -> LLMProvidersSettings()
                        SettingsSection.UPDATES -> UpdatesSettings()
                        SettingsSection.SECURITY -> SecuritySettings()
                        SettingsSection.KEYMAP -> EditableKeymapSettings()
                        SettingsSection.FOCUS_MODE -> FocusModeSettings()
                        SettingsSection.WINDOW_APPEARANCE -> WindowAppearanceSettings()
                        SettingsSection.PERFORMANCE -> PerformanceSettings()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BossDarkBackground,
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun UpdatesSettings() {
    UpdateSettingsSection()
}
