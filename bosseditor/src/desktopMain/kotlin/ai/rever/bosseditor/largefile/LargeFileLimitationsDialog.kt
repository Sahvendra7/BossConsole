package ai.rever.bosseditor.largefile

import ai.rever.bosseditor.theme.EditorTheme
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Informational dialog explaining large file mode limitations.
 *
 * This dialog is shown when the user clicks the info icon in the large file
 * viewer header. It explains what features are available and which are limited
 * in read-only large file mode.
 *
 * @param fileName The name of the file being viewed
 * @param fileSize Human-readable file size (e.g., "15.3 MB")
 * @param onDismiss Called when the dialog is dismissed
 * @param onOpenInEditor Called when the user chooses to open the file in the full editor
 * @param theme The editor theme to use for styling
 */
@Composable
fun LargeFileLimitationsDialog(
    fileName: String,
    fileSize: String,
    onDismiss: () -> Unit,
    onOpenInEditor: () -> Unit,
    theme: EditorTheme = LocalEditorTheme.current
) {
    val colors = theme.colors
    val textPrimary = colors.text
    val textSecondary = colors.lineNumber
    val backgroundColor = colors.background
    val surfaceColor = colors.gutterBackground
    val accentColor = Color(0xFF3B82F6) // Blue accent

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.width(450.dp).wrapContentHeight(),
            shape = RoundedCornerShape(8.dp),
            color = surfaceColor
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Large File Mode",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                Spacer(Modifier.height(16.dp))

                // File info
                Text(
                    "$fileName ($fileSize)",
                    fontSize = 14.sp,
                    color = textPrimary
                )

                Spacer(Modifier.height(16.dp))

                // Explanation
                Text(
                    "This file is opened in read-only mode for better performance. " +
                        "Large files are loaded page-by-page to avoid memory issues.",
                    fontSize = 14.sp,
                    color = textSecondary,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(16.dp))

                // Available features
                Text(
                    "Available features:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary
                )

                Spacer(Modifier.height(8.dp))

                FeatureItem("View file content", available = true, textColor = textSecondary)
                FeatureItem("Scroll and navigate", available = true, textColor = textSecondary)
                FeatureItem("Text selection and copy", available = true, textColor = textSecondary)
                FeatureItem("Search with Ctrl+F", available = true, textColor = textSecondary)

                Spacer(Modifier.height(12.dp))

                // Limitations
                Text(
                    "Limitations:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary
                )

                Spacer(Modifier.height(8.dp))

                FeatureItem("Editing (read-only)", available = false, textColor = textSecondary)
                FeatureItem("Syntax highlighting", available = false, textColor = textSecondary)
                FeatureItem("Code analysis and navigation", available = false, textColor = textSecondary)
                FeatureItem("Code folding", available = false, textColor = textSecondary)

                Spacer(Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onOpenInEditor()
                        }
                    ) {
                        Text(
                            "Open in Full Editor",
                            color = Color(0xFFFFA500) // Orange to indicate caution
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = accentColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Got it")
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(
    text: String,
    available: Boolean,
    textColor: Color
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = if (available) "\u2713" else "\u2717" // Check mark or X
        val iconColor = if (available) Color(0xFF4CAF50) else Color(0xFFE57373)
        Text(
            icon,
            color = iconColor,
            fontSize = 14.sp
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = textColor,
            fontSize = 13.sp
        )
    }
}

/**
 * A compact info icon button for triggering the limitations dialog.
 *
 * @param onClick Called when the icon is clicked
 * @param theme The editor theme to use for styling
 * @param modifier Modifier for the icon
 */
@Composable
fun LargeFileInfoIcon(
    onClick: () -> Unit,
    theme: EditorTheme = LocalEditorTheme.current,
    modifier: Modifier = Modifier
) {
    Icon(
        Icons.Default.Info,
        contentDescription = "Large file limitations info",
        tint = theme.colors.lineNumber,
        modifier = modifier
            .size(16.dp)
            .clickable { onClick() }
    )
}
