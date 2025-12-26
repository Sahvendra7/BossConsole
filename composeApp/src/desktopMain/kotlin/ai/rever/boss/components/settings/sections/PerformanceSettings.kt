package ai.rever.boss.components.settings.sections

import BossDarkAccent
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import ai.rever.boss.performance.PerformanceSettingsManager
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
fun PerformanceSettings() {
    val settings by PerformanceSettingsManager.currentSettings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SectionHeader(
            title = "Performance",
            description = "Monitor system resources and configure performance indicators"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Enable Performance Monitoring
        SettingSection(
            title = "Enable Performance Monitoring",
            description = "Track memory, CPU, and resource usage in real-time"
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
                            PerformanceSettingsManager.updateSettings(
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

        // Show Status Bar Indicator
        SettingSection(
            title = "Status Bar Indicator",
            description = "Show memory and CPU usage in the bottom status bar"
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (settings.showIndicator) "Visible" else "Hidden",
                        fontSize = 14.sp,
                        fontWeight = if (settings.showIndicator) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (settings.showIndicator) BossDarkAccent else MaterialTheme.colors.onSurface
                    )
                    if (!settings.enabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enable monitoring to show indicator",
                            fontSize = 12.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Switch(
                    checked = settings.showIndicator,
                    enabled = settings.enabled,
                    onCheckedChange = { show ->
                        coroutineScope.launch {
                            PerformanceSettingsManager.updateSettings(
                                settings.copy(showIndicator = show)
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

        Spacer(modifier = Modifier.height(32.dp))

        // Memory Thresholds
        SettingSection(
            title = "Memory Thresholds",
            description = "Configure when memory usage triggers warnings"
        ) {
            Column {
                // Warning threshold
                ThresholdSlider(
                    label = "Warning",
                    value = settings.memoryWarningThresholdPercent,
                    onValueChange = { value ->
                        coroutineScope.launch {
                            PerformanceSettingsManager.updateSettings(
                                settings.copy(memoryWarningThresholdPercent = value)
                            )
                        }
                    },
                    color = WarningColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Critical threshold
                ThresholdSlider(
                    label = "Critical",
                    value = settings.memoryCriticalThresholdPercent,
                    onValueChange = { value ->
                        coroutineScope.launch {
                            PerformanceSettingsManager.updateSettings(
                                settings.copy(memoryCriticalThresholdPercent = value)
                            )
                        }
                    },
                    color = CriticalColor
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // CPU Thresholds
        SettingSection(
            title = "CPU Thresholds",
            description = "Configure when CPU usage triggers warnings"
        ) {
            Column {
                // Warning threshold
                ThresholdSlider(
                    label = "Warning",
                    value = settings.cpuWarningThresholdPercent,
                    onValueChange = { value ->
                        coroutineScope.launch {
                            PerformanceSettingsManager.updateSettings(
                                settings.copy(cpuWarningThresholdPercent = value)
                            )
                        }
                    },
                    color = WarningColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Critical threshold
                ThresholdSlider(
                    label = "Critical",
                    value = settings.cpuCriticalThresholdPercent,
                    onValueChange = { value ->
                        coroutineScope.launch {
                            PerformanceSettingsManager.updateSettings(
                                settings.copy(cpuCriticalThresholdPercent = value)
                            )
                        }
                    },
                    color = CriticalColor
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // History Retention
        SettingSection(
            title = "History Retention",
            description = "How long to keep performance history for charts"
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${settings.historyRetentionMinutes} minutes",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BossDarkAccent
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = settings.historyRetentionMinutes.toFloat(),
                    onValueChange = { value ->
                        coroutineScope.launch {
                            PerformanceSettingsManager.updateSettings(
                                settings.copy(historyRetentionMinutes = value.toInt())
                            )
                        }
                    },
                    valueRange = 5f..60f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = BossDarkAccent,
                        activeTrackColor = BossDarkAccent,
                        inactiveTrackColor = BossDarkAccent.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Reset to Defaults
        SettingSection(
            title = "Reset Settings",
            description = "Restore all performance settings to defaults"
        ) {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        PerformanceSettingsManager.resetToDefault()
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            ) {
                Text("Reset to Defaults")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    color: androidx.compose.ui.graphics.Color
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
            )
            Text(
                text = "$value%",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 50f..100f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val WarningColor = androidx.compose.ui.graphics.Color(0xFFFFA726)
private val CriticalColor = androidx.compose.ui.graphics.Color(0xFFEF5350)
