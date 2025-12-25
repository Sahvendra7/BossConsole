package ai.rever.boss.components.settings.sections

import BossDarkAccent
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import ai.rever.boss.window.WindowAppearanceSettingsManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WindowAppearanceSettings() {
    val settings by WindowAppearanceSettingsManager.currentSettings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SectionHeader(
            title = "Window Appearance",
            description = "Customize the appearance of the application window"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Show Title Bar Setting
        SettingSection(
            title = "Show Title Bar",
            description = "Show the \"Boss Console\" title bar at the top of the window"
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (settings.showTitleBar) "Shown" else "Hidden",
                    fontSize = 14.sp,
                    fontWeight = if (settings.showTitleBar) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (settings.showTitleBar) BossDarkAccent else Color.Gray
                )

                Switch(
                    checked = settings.showTitleBar,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            WindowAppearanceSettingsManager.updateSettings(
                                settings.copy(showTitleBar = enabled)
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BossDarkAccent,
                        checkedTrackColor = BossDarkAccent.copy(alpha = 0.5f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Platform note
        val os = System.getProperty("os.name").lowercase()
        val platformNote = when {
            os.contains("mac") -> "Default: Shown (macOS)"
            os.contains("linux") -> "Default: Hidden (Linux)"
            os.contains("windows") -> "Default: Hidden (Windows)"
            else -> "Default: Based on platform"
        }

        Text(
            text = platformNote,
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}
