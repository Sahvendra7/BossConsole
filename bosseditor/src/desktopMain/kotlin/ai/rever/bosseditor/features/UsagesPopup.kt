package ai.rever.bosseditor.features

import ai.rever.bosseditor.psi.DefinitionInfo
import ai.rever.bosseditor.psi.ReferenceLocation
import ai.rever.bosseditor.theme.EditorTheme
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Popup that shows all usages of a symbol.
 *
 * Displays a list of references grouped by file, with code context for each reference.
 * Similar to IntelliJ's "Show Usages" popup.
 *
 * @param references List of reference locations
 * @param definition Information about the definition being referenced
 * @param anchorOffset Screen position to anchor the popup (x, y)
 * @param onNavigate Callback when user clicks a reference to navigate to it
 * @param onDismiss Callback when popup should be dismissed
 * @param theme Editor theme for styling
 */
@Composable
fun UsagesPopup(
    references: List<ReferenceLocation>,
    definition: DefinitionInfo,
    anchorOffset: IntOffset,
    onNavigate: (filePath: String, line: Int, column: Int) -> Unit,
    onDismiss: () -> Unit,
    theme: EditorTheme = LocalEditorTheme.current
) {
    val focusRequester = remember { FocusRequester() }
    val colors = theme.colors

    // Group references by file
    val groupedReferences = references.groupBy { it.filePath }

    Popup(
        offset = anchorOffset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            color = colors.background,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .width(500.dp)
                    .heightIn(max = 400.dp)
            ) {
                // Header
                UsagesHeader(
                    symbolName = definition.name,
                    usageCount = references.size,
                    backgroundColor = colors.gutterBackground,
                    textColor = colors.text
                )

                Divider(color = colors.selectionBackground.copy(alpha = 0.5f))

                if (references.isEmpty()) {
                    // No usages found
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No usages found",
                            color = colors.text.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    // List of usages grouped by file
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        groupedReferences.forEach { (filePath, fileReferences) ->
                            // File header
                            item(key = "header:$filePath") {
                                FileHeader(
                                    filePath = filePath,
                                    usageCount = fileReferences.size,
                                    backgroundColor = colors.gutterBackground.copy(alpha = 0.5f),
                                    textColor = colors.text
                                )
                            }

                            // References in this file
                            items(
                                items = fileReferences,
                                key = { "${it.filePath}:${it.line}:${it.column}" }
                            ) { reference ->
                                UsageRow(
                                    reference = reference,
                                    onClick = {
                                        onNavigate(reference.filePath, reference.line, reference.column)
                                        onDismiss()
                                    },
                                    backgroundColor = colors.background,
                                    hoverColor = colors.selectionBackground.copy(alpha = 0.3f),
                                    textColor = colors.text,
                                    lineNumberColor = colors.lineNumber
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Request focus when popup appears (with delay to ensure modifier is attached)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        try {
            focusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            // FocusRequester not attached yet, ignore
        }
    }
}

@Composable
private fun UsagesHeader(
    symbolName: String,
    usageCount: Int,
    backgroundColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$usageCount ${if (usageCount == 1) "usage" else "usages"} of '$symbolName'",
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FileHeader(
    filePath: String,
    usageCount: Int,
    backgroundColor: Color,
    textColor: Color
) {
    val fileName = filePath.substringAfterLast('/')
    val directory = filePath.substringBeforeLast('/').substringAfterLast('/')

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fileName,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = directory,
                color = textColor.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
        Text(
            text = "$usageCount",
            color = textColor.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun UsageRow(
    reference: ReferenceLocation,
    onClick: () -> Unit,
    backgroundColor: Color,
    hoverColor: Color,
    textColor: Color,
    lineNumberColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHovered) hoverColor else backgroundColor)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Line number
        Text(
            text = "${reference.line}:",
            color = lineNumberColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(50.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Code context
        Text(
            text = reference.context,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * State for managing the usages popup.
 */
data class UsagesPopupState(
    val isVisible: Boolean = false,
    val references: List<ReferenceLocation> = emptyList(),
    val definition: DefinitionInfo? = null,
    val anchorOffset: IntOffset = IntOffset.Zero
) {
    companion object {
        val Hidden = UsagesPopupState()
    }
}
