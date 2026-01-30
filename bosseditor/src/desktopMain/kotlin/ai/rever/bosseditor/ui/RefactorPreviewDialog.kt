package ai.rever.bosseditor.ui

import ai.rever.bosseditor.refactoring.FileChange
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

/**
 * Dialog for previewing refactoring changes before applying them.
 *
 * Shows a tree of files that will be modified with expandable diff views.
 * Users can include/exclude specific files from the refactoring.
 *
 * @param title The dialog title
 * @param description Description of the refactoring
 * @param changes List of file changes to preview
 * @param onApply Called when user confirms (with list of included file URIs)
 * @param onCancel Called when user cancels
 */
@Composable
fun RefactorPreviewDialog(
    title: String,
    description: String,
    changes: List<FileChange>,
    onApply: (includedUris: Set<String>) -> Unit,
    onCancel: () -> Unit
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    // Track which files are included
    var includedUris by remember {
        mutableStateOf(changes.map { it.uri }.toSet())
    }

    // Track which files are expanded to show diff
    var expandedFiles by remember { mutableStateOf(setOf<String>()) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .width(700.dp)
                .heightIn(min = 400.dp, max = 600.dp)
                .background(colors.gutterBackground, RoundedCornerShape(8.dp))
                .border(1.dp, colors.gutterBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onCancel()
                        true
                    } else {
                        false
                    }
                }
        ) {
            // Header
            Text(
                text = title,
                color = colors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = description,
                color = colors.text.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Summary
            Text(
                text = "${includedUris.size} of ${changes.size} files selected",
                color = colors.text.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // File list with diffs
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    items(changes) { change ->
                        FileChangeItem(
                            change = change,
                            isIncluded = change.uri in includedUris,
                            isExpanded = change.uri in expandedFiles,
                            onToggleInclude = {
                                includedUris = if (change.uri in includedUris) {
                                    includedUris - change.uri
                                } else {
                                    includedUris + change.uri
                                }
                            },
                            onToggleExpand = {
                                expandedFiles = if (change.uri in expandedFiles) {
                                    expandedFiles - change.uri
                                } else {
                                    expandedFiles + change.uri
                                }
                            },
                            colors = colors
                        )

                        if (change != changes.last()) {
                            Divider(
                                color = colors.gutterBorder,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                // Select all / deselect all
                Text(
                    text = "Select All",
                    color = colors.text.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { includedUris = changes.map { it.uri }.toSet() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = "Deselect All",
                    color = colors.text.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { includedUris = emptySet() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                PreviewDialogButton(
                    text = "Cancel",
                    onClick = onCancel,
                    colors = colors
                )
                Spacer(modifier = Modifier.width(8.dp))
                PreviewDialogButton(
                    text = "Apply",
                    onClick = { onApply(includedUris) },
                    enabled = includedUris.isNotEmpty(),
                    primary = true,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun FileChangeItem(
    change: FileChange,
    isIncluded: Boolean,
    isExpanded: Boolean,
    onToggleInclude: () -> Unit,
    onToggleExpand: () -> Unit,
    colors: ai.rever.bosseditor.theme.EditorColors
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // File header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = isIncluded,
                onCheckedChange = { onToggleInclude() },
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.selectionBackground,
                    uncheckedColor = colors.text.copy(alpha = 0.5f)
                ),
                modifier = Modifier.size(24.dp)
            )

            // Expand/collapse indicator
            Text(
                text = if (isExpanded) "▼" else "▶",
                color = colors.text.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // File name
            val fileName = File(change.filePath).name
            Text(
                text = fileName,
                color = if (isIncluded) colors.text else colors.text.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.width(8.dp))

            // File path
            Text(
                text = change.filePath,
                color = colors.text.copy(alpha = 0.4f),
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )

            // Edit count
            Text(
                text = "${change.edits.size} change${if (change.edits.size != 1) "s" else ""}",
                color = colors.text.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }

        // Diff view (when expanded)
        if (isExpanded) {
            DiffView(
                previewBefore = change.previewBefore,
                previewAfter = change.previewAfter,
                colors = colors
            )
        }
    }
}

@Composable
private fun DiffView(
    previewBefore: String?,
    previewAfter: String?,
    colors: ai.rever.bosseditor.theme.EditorColors
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .padding(start = 32.dp, top = 4.dp, bottom = 8.dp)
            .background(colors.background.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .border(1.dp, colors.gutterBorder.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(8.dp)
        ) {
            // Show before (deletions)
            previewBefore?.lines()?.forEach { line ->
                DiffLine(
                    line = line,
                    isAddition = false,
                    colors = colors
                )
            }

            // Show after (additions)
            previewAfter?.lines()?.forEach { line ->
                DiffLine(
                    line = line,
                    isAddition = true,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun DiffLine(
    line: String,
    isAddition: Boolean,
    colors: ai.rever.bosseditor.theme.EditorColors
) {
    val backgroundColor = when {
        line.startsWith("-") -> Color(0x30FF6B6B) // Red for deletions
        line.startsWith("+") -> Color(0x3069DB7C) // Green for additions
        line.startsWith("...") -> colors.gutterBackground
        else -> Color.Transparent
    }

    val textColor = when {
        line.startsWith("-") -> Color(0xFFFF6B6B)
        line.startsWith("+") -> Color(0xFF69DB7C)
        else -> colors.text.copy(alpha = 0.7f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = line,
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PreviewDialogButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    colors: ai.rever.bosseditor.theme.EditorColors
) {
    val backgroundColor = when {
        !enabled -> colors.gutterBackground.copy(alpha = 0.5f)
        primary -> colors.selectionBackground
        else -> colors.gutterBackground
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) colors.text else colors.text.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}
