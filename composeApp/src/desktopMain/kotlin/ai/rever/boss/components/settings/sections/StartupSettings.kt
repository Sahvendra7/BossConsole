package ai.rever.boss.components.settings.sections

import BossDarkAccent
import BossDarkBorder
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import ai.rever.boss.startup.StartupSettingsManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Settings UI section for startup configuration.
 */
@Composable
fun StartupSettingsSection() {
    val settings by StartupSettingsManager.currentSettings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Local state for the text field
    var timeoutText by remember(settings) { mutableStateOf(settings.workspaceLoadTimeoutMs.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SectionHeader(
            title = "Startup",
            description = "Configure application startup behavior"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Workspace Load Timeout
        SettingSection(
            title = "Workspace Load Timeout",
            description = "Time to wait for workspace to load before showing New Tab dialog (milliseconds)"
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = timeoutText,
                    onValueChange = { newValue ->
                        // Only allow numeric input
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            timeoutText = newValue
                        }
                    },
                    label = { Text("Timeout (ms)") },
                    singleLine = true,
                    modifier = Modifier.width(200.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        focusedBorderColor = BossDarkAccent,
                        unfocusedBorderColor = BossDarkBorder,
                        focusedLabelColor = BossDarkAccent,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        val timeout = timeoutText.toLongOrNull()
                        if (timeout != null && timeout >= 100) {
                            coroutineScope.launch {
                                StartupSettingsManager.setWorkspaceLoadTimeout(timeout)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossDarkAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    enabled = timeoutText.toLongOrNull()?.let { it >= 100 } == true &&
                            timeoutText.toLongOrNull() != settings.workspaceLoadTimeoutMs
                ) {
                    Text("Apply")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Minimum: 100ms. Higher values give more time for workspace to load but delay the New Tab dialog on fresh installs.",
                fontSize = 12.sp,
                color = Color.Gray
            )

            // Reset button
            if (settings.workspaceLoadTimeoutMs != 500L) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            StartupSettingsManager.resetToDefault()
                        }
                        timeoutText = "500"
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = BossDarkAccent
                    )
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset to Default (500ms)", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = BossDarkAccent.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            elevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "About Workspace Loading",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "On startup, the app waits for the workspace manager to load your Last Session. " +
                            "If no workspaces are found within the timeout, it assumes a fresh install and shows the New Tab dialog.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
