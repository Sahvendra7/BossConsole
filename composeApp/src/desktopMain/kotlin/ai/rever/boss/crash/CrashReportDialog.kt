package ai.rever.boss.crash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Crash report dialog content shown when BOSS encounters an unhandled exception.
 *
 * This component renders the crash report UI directly without a Dialog wrapper,
 * as it's designed to be displayed in a standalone window (JFrame with ComposePanel).
 * This ensures the crash dialog appears even when the main Compose UI is broken.
 *
 * Features:
 * - Error summary with exception type and message
 * - Expandable technical details (stack trace)
 * - Copy to clipboard button
 * - User notes text field
 * - Optional inclusion of recent activity logs
 * - Submit to GitHub and dismiss buttons
 *
 * @param crashReport The crash report to display
 * @param onDismiss Called when user dismisses without submitting
 * @param onSubmit Called when user wants to submit the report
 */
@Composable
fun CrashReportDialog(
    crashReport: CrashReport,
    onDismiss: () -> Unit,
    onSubmit: (userNotes: String?, includeLogs: Boolean) -> Unit,
    onCleanAndRestart: (() -> Unit)? = null
) {
    var userNotes by remember { mutableStateOf("") }
    var includeLogs by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitResult by remember { mutableStateOf<CrashReportService.SubmitResult?>(null) }

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Render directly in the window (no Dialog wrapper needed since this is shown in its own JFrame)
    Card(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && !isSubmitting) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        shape = RoundedCornerShape(0.dp),
        backgroundColor = Color(0xFF2B2D30),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Header with error icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "Error",
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "BOSS Has Crashed",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error summary
            Card(
                backgroundColor = Color(0xFF3C3F41),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = crashReport.exceptionType,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE57373)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = crashReport.exceptionMessage,
                        fontSize = 13.sp,
                        color = Color(0xFFCCCCCC),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expandable technical details
            Card(
                backgroundColor = Color(0xFF3C3F41),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Header row (clickable)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDetails = !showDetails }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Technical Details",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showDetails) "Collapse" else "Expand",
                            tint = Color(0xFF999999)
                        )
                    }

                    // Expandable content
                    AnimatedVisibility(
                        visible = showDetails,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            // Copy button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(crashReport.stackTrace))
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color(0xFF4A9EFF)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy to Clipboard", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Stack trace
                            SelectionContainer {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .background(
                                            Color(0xFF1E1E1E),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(8.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = crashReport.stackTrace,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFB0B0B0),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // User notes input
            Text(
                text = "What were you doing when this happened? (optional)",
                fontSize = 13.sp,
                color = Color(0xFFCCCCCC)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = userNotes,
                onValueChange = { userNotes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 120.dp),
                placeholder = {
                    Text(
                        "Describe what you were doing...",
                        color = Color(0xFF666666)
                    )
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    backgroundColor = Color(0xFF3C3F41),
                    focusedBorderColor = Color(0xFF4A9EFF),
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = Color(0xFF4A9EFF)
                ),
                enabled = !isSubmitting
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Include logs checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isSubmitting) { includeLogs = !includeLogs }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = includeLogs,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF4A9EFF),
                        uncheckedColor = Color(0xFF666666),
                        checkmarkColor = Color.White
                    ),
                    enabled = !isSubmitting
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Include recent activity logs",
                        fontSize = 14.sp,
                        color = Color(0xFFCCCCCC)
                    )
                    Text(
                        text = "Helps with debugging (logs are sanitized)",
                        fontSize = 11.sp,
                        color = Color(0xFF888888)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit result message
            submitResult?.let { result ->
                Card(
                    backgroundColor = when (result) {
                        is CrashReportService.SubmitResult.Success -> Color(0xFF2E7D32)
                        is CrashReportService.SubmitResult.Error -> Color(0xFFC62828)
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when (result) {
                            is CrashReportService.SubmitResult.Success ->
                                if (result.isNewIssue) "Issue created successfully!"
                                else "Added to existing issue."
                            is CrashReportService.SubmitResult.Error -> result.message
                        },
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // Clean & Restart button
                if (onCleanAndRestart != null) {
                    Button(
                        onClick = onCleanAndRestart,
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFC62828),
                            contentColor = Color.White,
                            disabledBackgroundColor = Color(0xFF3C3F41),
                            disabledContentColor = Color(0xFF666666)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Clean Data & Restart")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Don't Send button
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF999999)
                    )
                ) {
                    Text("Don't Send")
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Report Issue button
                Button(
                    onClick = {
                        isSubmitting = true
                        coroutineScope.launch {
                            // Update report with user input
                            CrashHandler.updateReportWithUserInput(
                                userNotes = userNotes.takeIf { it.isNotBlank() },
                                includeLogs = includeLogs
                            )?.let { updatedReport ->
                                val result = CrashReportService.submitCrashReport(updatedReport)
                                submitResult = result
                                isSubmitting = false

                                // If successful, call onSubmit after a brief delay
                                if (result is CrashReportService.SubmitResult.Success) {
                                    kotlinx.coroutines.delay(2000)
                                    onSubmit(userNotes.takeIf { it.isNotBlank() }, includeLogs)
                                }
                            } ?: run {
                                submitResult = CrashReportService.SubmitResult.Error("Failed to prepare report")
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = !isSubmitting && submitResult !is CrashReportService.SubmitResult.Success,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF4A9EFF),
                        contentColor = Color.White,
                        disabledBackgroundColor = Color(0xFF3C3F41),
                        disabledContentColor = Color(0xFF666666)
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submitting...")
                    } else {
                        Text("Report Issue")
                    }
                }
            }

            // Close button after successful submission
            if (submitResult is CrashReportService.SubmitResult.Success) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF3C3F41),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
