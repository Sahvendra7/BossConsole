package ai.rever.boss.components.settings.sections

import BossDarkAccent
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import ai.rever.boss.focusmode.FocusModeSettingsManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun FocusModeSettings() {
    val settings by FocusModeSettingsManager.currentSettings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SectionHeader(
            title = "Focus Mode",
            description = "Minimize distractions by hiding UI elements while you work"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Enable Focus Mode
        SettingSection(
            title = "Enable Focus Mode",
            description = "Hide top bar, sidebars, and bottom bar to maximize content area"
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (settings.enabled) "Enabled" else "Disabled",
                    fontSize = 14.sp,
                    fontWeight = if (settings.enabled) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (settings.enabled) BossDarkAccent else MaterialTheme.colors.onSurface
                )

                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            FocusModeSettingsManager.updateSettings(
                                settings.copy(enabled = enabled)
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

        Spacer(modifier = Modifier.height(24.dp))

        // What stays visible
        SettingSection(
            title = "What Stays Visible",
            description = "These elements remain visible in Focus Mode"
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                InfoItem(text = "✓ Tab bar - for switching between open files")
                Spacer(modifier = Modifier.height(8.dp))
                InfoItem(text = "✓ Main content panel - your primary work area")
                Spacer(modifier = Modifier.height(8.dp))
                InfoItem(text = "✓ Window title bar - for window controls")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // What gets hidden
        SettingSection(
            title = "What Gets Hidden",
            description = "These elements are hidden to maximize focus"
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                InfoItem(text = "× Top action bar - project selector, settings, etc.")
                Spacer(modifier = Modifier.height(8.dp))
                InfoItem(text = "× Left sidebar - plugin panels")
                Spacer(modifier = Modifier.height(8.dp))
                InfoItem(text = "× Right sidebar - plugin panels")
                Spacer(modifier = Modifier.height(8.dp))
                InfoItem(text = "× Bottom status bar")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Auto-reveal section (Phase 3 feature)
        SettingSection(
            title = "Auto-Reveal on Hover",
            description = "Automatically show hidden UI elements when mouse approaches window edges"
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (settings.autoRevealEnabled) "Enabled" else "Disabled",
                        fontSize = 14.sp,
                        fontWeight = if (settings.autoRevealEnabled) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (settings.autoRevealEnabled) BossDarkAccent else MaterialTheme.colors.onSurface
                    )
                    if (!settings.enabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enable Focus Mode to use this feature",
                            fontSize = 12.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Switch(
                    checked = settings.autoRevealEnabled,
                    enabled = settings.enabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            FocusModeSettingsManager.updateSettings(
                                settings.copy(autoRevealEnabled = enabled)
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

        // Reveal offset slider (when auto-reveal is enabled)
        if (settings.enabled && settings.autoRevealEnabled) {
            Spacer(modifier = Modifier.height(24.dp))

            SettingSection(
                title = "Reveal Sensitivity",
                description = "Distance in pixels from window edge to trigger reveal"
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${settings.revealOffsetPx.toInt()} px",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BossDarkAccent
                        )
                        Text(
                            text = "Hover near edges to reveal",
                            fontSize = 12.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = settings.revealOffsetPx,
                        onValueChange = { value ->
                            coroutineScope.launch {
                                FocusModeSettingsManager.updateSettings(
                                    settings.copy(revealOffsetPx = value)
                                )
                            }
                        },
                        valueRange = 5f..50f,
                        steps = 8, // 5, 10, 15, 20, 25, 30, 35, 40, 45, 50
                        colors = SliderDefaults.colors(
                            thumbColor = BossDarkAccent,
                            activeTrackColor = BossDarkAccent,
                            inactiveTrackColor = BossDarkAccent.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Keyboard shortcut hint
        SettingSection(
            title = "Keyboard Shortcut",
            description = "Quick toggle for Focus Mode"
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Cmd+Shift+F (macOS) / Ctrl+Shift+F (Windows/Linux)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = BossDarkAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Customize this shortcut in Settings > Keyboard Shortcuts",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InfoItem(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
        lineHeight = 20.sp
    )
}
