package ai.rever.boss.components.settings.sections

import BossDarkAccent
import ai.rever.boss.aiassistant.AIAssistant
import ai.rever.boss.aiassistant.AIAssistantConfig
import ai.rever.boss.aiassistant.AIAssistantDetector
import ai.rever.boss.aiassistant.AIAssistantInstaller
import ai.rever.boss.aiassistant.AIAssistantSettingsManager
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Settings UI section for AI Assistant configuration.
 *
 * Issue #445: Terminal context menu for AI coding assistants
 */
@Composable
fun AIAssistantSettings() {
    val settings by AIAssistantSettingsManager.currentSettings.collectAsState()
    val installationStatuses by AIAssistantDetector.installationStatuses.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Refresh installation status on mount (skips if recently refreshed)
    LaunchedEffect(Unit) {
        AIAssistantDetector.refreshIfStale()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SectionHeader(
            title = "AI Assistants",
            description = "Configure AI coding assistants for terminal launch"
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Refresh button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Installation Status",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onSurface
            )

            IconButton(
                onClick = {
                    coroutineScope.launch {
                        AIAssistantDetector.refreshAll()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh installation status",
                    tint = BossDarkAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Per-assistant configuration cards
        AIAssistant.entries.forEach { assistant ->
            val config = settings.getConfig(assistant)
            val status = installationStatuses[assistant]

            AIAssistantConfigCard(
                assistant = assistant,
                config = config,
                isInstalled = status?.installed ?: false,
                installPath = status?.path,
                onEnabledChange = { enabled ->
                    coroutineScope.launch {
                        AIAssistantSettingsManager.setAssistantEnabled(assistant, enabled)
                    }
                },
                onYoloChange = { enabled ->
                    coroutineScope.launch {
                        AIAssistantSettingsManager.setYoloEnabled(assistant, enabled)
                    }
                },
                onCustomCommandChange = { command ->
                    coroutineScope.launch {
                        AIAssistantSettingsManager.setCustomCommand(assistant, command.ifBlank { null })
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Global settings
        SettingSection(
            title = "Display Options",
            description = "Control how assistants appear in context menus"
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Show unavailable assistants",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Display disabled items for uninstalled assistants",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                Switch(
                    checked = settings.showUnavailableAssistants,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            AIAssistantSettingsManager.setShowUnavailableAssistants(enabled)
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

        // Keyboard shortcuts reference
        SettingSection(
            title = "Keyboard Shortcuts",
            description = "Quick launch shortcuts (configure in Keyboard Shortcuts settings)"
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                ShortcutInfoItem(
                    assistant = "Claude Code",
                    shortcut = "Ctrl+Shift+1"
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShortcutInfoItem(
                    assistant = "Codex",
                    shortcut = "Ctrl+Shift+2"
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShortcutInfoItem(
                    assistant = "Gemini CLI",
                    shortcut = "Ctrl+Shift+3"
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShortcutInfoItem(
                    assistant = "OpenCode",
                    shortcut = "Ctrl+Shift+4"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Usage notes
        SettingSection(
            title = "Notes",
            description = "How the AI Assistant launcher works"
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                NoteItem(text = "Right-click in any terminal to access the AI Assistant submenu")
                Spacer(modifier = Modifier.height(8.dp))
                NoteItem(text = "YOLO mode runs assistants with auto-approve flags")
                Spacer(modifier = Modifier.height(8.dp))
                NoteItem(text = "Custom commands override the default executable path")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AIAssistantConfigCard(
    assistant: AIAssistant,
    config: AIAssistantConfig,
    isInstalled: Boolean,
    installPath: String?,
    onEnabledChange: (Boolean) -> Unit,
    onYoloChange: (Boolean) -> Unit,
    onCustomCommandChange: (String) -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var customCommand by remember(config.customCommand) {
        mutableStateOf(config.customCommand ?: "")
    }

    val borderColor = if (config.enabled && isInstalled) BossDarkAccent else Color(0xFF3C3C3C)
    val backgroundColor = if (config.enabled && isInstalled) BossDarkAccent.copy(alpha = 0.05f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        // Header row with name, status badge, and enable toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = assistant.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colors.onSurface
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Installation status badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isInstalled) Color(0xFF2E7D32).copy(alpha = 0.2f)
                                else Color(0xFFD32F2F).copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isInstalled) "Installed" else "Not Found",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isInstalled) Color(0xFF4CAF50) else Color(0xFFE57373)
                        )
                    }
                }

                if (installPath != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = installPath,
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            if (isInstalled) {
                Switch(
                    checked = config.enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BossDarkAccent,
                        checkedTrackColor = BossDarkAccent.copy(alpha = 0.5f)
                    )
                )
            }
        }

        // Install section for uninstalled assistants
        if (!isInstalled) {
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color(0xFF3C3C3C))
            Spacer(modifier = Modifier.height(16.dp))

            val installCommand = AIAssistantInstaller.getInstallCommand(assistant)
            var copied by remember { mutableStateOf(false) }

            Column {
                Text(
                    text = "Install Command",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Run this command in your terminal to install ${assistant.displayName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(12.dp)
                ) {
                    Text(
                        text = installCommand,
                        fontSize = 12.sp,
                        color = Color(0xFFCE9178),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(installCommand), null)
                            copied = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Copy command",
                            tint = if (copied) Color(0xFF4CAF50) else BossDarkAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Or right-click in a terminal and select \"Install ${assistant.displayName}...\"",
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                )
            }
        }

        if (config.enabled && isInstalled) {
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color(0xFF3C3C3C))
            Spacer(modifier = Modifier.height(16.dp))

            // YOLO mode toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "${assistant.menuLabel} Mode",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Flag: ${config.getYoloFlag()}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }

                Switch(
                    checked = config.yoloEnabled,
                    onCheckedChange = onYoloChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BossDarkAccent,
                        checkedTrackColor = BossDarkAccent.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Advanced settings toggle
            Text(
                text = if (showAdvanced) "Hide Advanced" else "Show Advanced",
                fontSize = 12.sp,
                color = BossDarkAccent,
                modifier = Modifier.clickable { showAdvanced = !showAdvanced }
            )

            if (showAdvanced) {
                Spacer(modifier = Modifier.height(12.dp))

                // Custom command field
                Column {
                    Text(
                        text = "Custom Command",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Override the default command (leave blank for default: ${assistant.defaultCommand})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customCommand,
                        onValueChange = { customCommand = it },
                        placeholder = {
                            Text(
                                text = assistant.defaultCommand,
                                fontSize = 13.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = BossDarkAccent,
                            cursorColor = BossDarkAccent
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onCustomCommandChange(customCommand) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = BossDarkAccent
                        ),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutInfoItem(
    assistant: String,
    shortcut: String
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = assistant,
            fontSize = 13.sp,
            color = MaterialTheme.colors.onSurface
        )
        Text(
            text = shortcut,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = BossDarkAccent
        )
    }
}

@Composable
private fun NoteItem(text: String) {
    Text(
        text = "• $text",
        fontSize = 12.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        lineHeight = 18.sp
    )
}
