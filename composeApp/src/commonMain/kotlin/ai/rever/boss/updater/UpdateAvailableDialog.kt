package ai.rever.boss.updater

import BossDarkBackground
import BossDarkTextPrimary
import BossDarkTextSecondary
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Matches the banner colors in UpdateUI.kt
private val AccentBlue = Color(0xFF4A90E2)
private val TextGray = Color(0xFF808080)

/**
 * Dismissible dialog shown when a new BossConsole version is available.
 *
 * "Update Now" starts the download (the top banner then shows progress);
 * "Later" (or clicking outside / Esc) persists the dismissal for this
 * version — it won't be prompted again until a different version is
 * published or the user manually checks for updates.
 */
@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateInfo,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        modifier = Modifier.widthIn(min = 360.dp, max = 480.dp),
        title = {
            Text(
                "Update available",
                color = BossDarkTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 320.dp)) {
                Text(
                    "BossConsole v${updateInfo.latestVersion} is available " +
                        "(you have v${updateInfo.currentVersion}).",
                    color = BossDarkTextSecondary,
                    fontSize = 13.sp
                )
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "What's new",
                        color = BossDarkTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            updateInfo.releaseNotes,
                            color = BossDarkTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdateNow) {
                Text("Update Now", color = AccentBlue, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("Later", color = TextGray, fontSize = 13.sp)
            }
        },
        backgroundColor = BossDarkBackground,
        contentColor = BossDarkTextPrimary
    )
}
