package ai.rever.boss.components.settings.sections

import BossDarkAccent
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import ai.rever.boss.run.RunnerSettingsManager
import ai.rever.boss.run.RunnerTerminalTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Settings UI section for runner configuration.
 *
 * Issue #347: Runner settings UI
 */
@Composable
fun RunnerSettings() {
    val settings by RunnerSettingsManager.currentSettings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SectionHeader(
            title = "Runner",
            description = "Configure how run configurations are executed"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Terminal Target Selection
        SettingSection(
            title = "Terminal Target",
            description = "Choose where runner output appears"
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                TerminalTargetOption(
                    title = "Sidebar Panel",
                    description = "Open in the left sidebar terminal area (like VS Code)",
                    selected = settings.terminalTarget == RunnerTerminalTarget.SIDEBAR_PANEL,
                    onClick = {
                        coroutineScope.launch {
                            RunnerSettingsManager.setTerminalTarget(RunnerTerminalTarget.SIDEBAR_PANEL)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                TerminalTargetOption(
                    title = "Main Panel",
                    description = "Open in the main content area (like IntelliJ IDEA)",
                    selected = settings.terminalTarget == RunnerTerminalTarget.MAIN_PANEL,
                    onClick = {
                        coroutineScope.launch {
                            RunnerSettingsManager.setTerminalTarget(RunnerTerminalTarget.MAIN_PANEL)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Focus on Run
        SettingSection(
            title = "Focus on Run",
            description = "Automatically focus the terminal when a runner starts"
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (settings.focusOnRun) "Enabled" else "Disabled",
                    fontSize = 14.sp,
                    fontWeight = if (settings.focusOnRun) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (settings.focusOnRun) BossDarkAccent else MaterialTheme.colors.onSurface
                )

                Switch(
                    checked = settings.focusOnRun,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            RunnerSettingsManager.setFocusOnRun(enabled)
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

        // Notify on Exit
        SettingSection(
            title = "Notify on Exit",
            description = "Show a notification when a runner process completes"
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (settings.notifyOnExit) "Enabled" else "Disabled",
                    fontSize = 14.sp,
                    fontWeight = if (settings.notifyOnExit) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (settings.notifyOnExit) BossDarkAccent else MaterialTheme.colors.onSurface
                )

                Switch(
                    checked = settings.notifyOnExit,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            RunnerSettingsManager.setNotifyOnExit(enabled)
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

        // Re-run Delay
        SettingSection(
            title = "Re-run Delay",
            description = "Delay between Ctrl+C and new command (for sidebar terminal)"
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${settings.rerunDelayMs}ms",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BossDarkAccent
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = settings.rerunDelayMs.toFloat(),
                    onValueChange = { value ->
                        coroutineScope.launch {
                            RunnerSettingsManager.setRerunDelayMs(value.toLong())
                        }
                    },
                    valueRange = 0f..2000f,
                    steps = 19, // 0, 100, 200, ... 2000
                    colors = SliderDefaults.colors(
                        thumbColor = BossDarkAccent,
                        activeTrackColor = BossDarkAccent
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "0ms",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "2000ms",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Run/Stop Buttons Info
        SettingSection(
            title = "Run Controls",
            description = "How the run/stop buttons work"
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                RunControlInfoItem(
                    icon = "▶",
                    iconColor = Color(0xFF59A869),
                    title = "Run",
                    description = "Execute the selected configuration"
                )
                Spacer(modifier = Modifier.height(12.dp))
                RunControlInfoItem(
                    icon = "↻",
                    iconColor = Color(0xFF59A869),
                    title = "Re-run",
                    description = "Stop current run and execute again"
                )
                Spacer(modifier = Modifier.height(12.dp))
                RunControlInfoItem(
                    icon = "■",
                    iconColor = Color(0xFFE05555),
                    title = "Stop",
                    description = "Terminate the running process (Ctrl+C)"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Note about behavior
        SettingSection(
            title = "Notes",
            description = "How the runner system works"
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                LimitationItem(text = "Re-run creates new terminal tab (closes old one)")
                Spacer(modifier = Modifier.height(8.dp))
                LimitationItem(text = "Sidebar terminal must be open to use Sidebar Panel target")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TerminalTargetOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) BossDarkAccent else Color(0xFF3C3C3C)
    val backgroundColor = if (selected) BossDarkAccent.copy(alpha = 0.1f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) BossDarkAccent else MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }

        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Selected",
                tint = BossDarkAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RunControlInfoItem(
    icon: String,
    iconColor: Color,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = 12.sp,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun LimitationItem(text: String) {
    Text(
        text = "• $text",
        fontSize = 12.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        lineHeight = 18.sp
    )
}
