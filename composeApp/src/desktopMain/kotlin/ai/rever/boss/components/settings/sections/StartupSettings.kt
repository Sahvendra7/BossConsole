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

// Validation constants for workspace load timeout
private const val MIN_TIMEOUT_MS = 100L
private const val MAX_TIMEOUT_MS = 30000L  // 30 seconds max
private const val DEFAULT_TIMEOUT_MS = 1000L  // 1 second - adequate for slower machines

/**
 * Validates timeout value and returns error message if invalid, null if valid.
 */
private fun validateTimeout(value: Long?): String? {
    return when {
        value == null -> "Please enter a valid number"
        value < MIN_TIMEOUT_MS -> "Minimum value is ${MIN_TIMEOUT_MS}ms"
        value > MAX_TIMEOUT_MS -> "Maximum value is ${MAX_TIMEOUT_MS}ms (30 seconds)"
        else -> null
    }
}

/**
 * Settings UI section for startup configuration.
 */
@Composable
fun StartupSettingsSection() {
    val settings by StartupSettingsManager.currentSettings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Local state for the text field
    var timeoutText by remember(settings) { mutableStateOf(settings.workspaceLoadTimeoutMs.toString()) }

    // Validation state
    val timeoutValue = timeoutText.toLongOrNull()
    val validationError = validateTimeout(timeoutValue)
    val isValid = validationError == null
    val hasChanges = timeoutValue != null && timeoutValue != settings.workspaceLoadTimeoutMs

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
                    isError = timeoutText.isNotEmpty() && !isValid,
                    modifier = Modifier.width(200.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        focusedBorderColor = if (isValid) BossDarkAccent else Color(0xFFE57373),
                        unfocusedBorderColor = if (isValid) BossDarkBorder else Color(0xFFE57373),
                        errorBorderColor = Color(0xFFE57373),
                        focusedLabelColor = if (isValid) BossDarkAccent else Color(0xFFE57373),
                        unfocusedLabelColor = Color.Gray,
                        errorLabelColor = Color(0xFFE57373)
                    )
                )

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        if (isValid && timeoutValue != null) {
                            coroutineScope.launch {
                                StartupSettingsManager.setWorkspaceLoadTimeout(timeoutValue)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BossDarkAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    enabled = isValid && hasChanges
                ) {
                    Text("Apply")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Show validation error or help text
            if (timeoutText.isNotEmpty() && validationError != null) {
                Text(
                    text = validationError,
                    fontSize = 12.sp,
                    color = Color(0xFFE57373)
                )
            } else {
                Text(
                    text = "Range: ${MIN_TIMEOUT_MS}ms - ${MAX_TIMEOUT_MS}ms. Higher values give more time for workspace to load but delay the New Tab dialog on fresh installs.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Reset button
            if (settings.workspaceLoadTimeoutMs != DEFAULT_TIMEOUT_MS) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            StartupSettingsManager.resetToDefault()
                        }
                        timeoutText = DEFAULT_TIMEOUT_MS.toString()
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
                    Text("Reset to Default (${DEFAULT_TIMEOUT_MS}ms)", fontSize = 13.sp)
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
