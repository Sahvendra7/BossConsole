package ai.rever.bosseditor.ui

import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Go to line dialog for quick navigation.
 *
 * Supports formats:
 * - Line number: "42" -> goes to line 42
 * - Line:Column: "42:10" -> goes to line 42, column 10
 * - Offset: "+100" or "-50" -> relative line movement
 *
 * ## Keyboard Shortcuts
 * - Enter: Go to line
 * - Escape: Cancel
 */
@Composable
fun GoToLineDialog(
    currentLine: Int,
    totalLines: Int,
    currentColumn: Int = 1,
    onGoTo: (line: Int, column: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    var inputValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Focus input on show
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .background(colors.gutterBackground, RoundedCornerShape(8.dp))
                .border(1.dp, colors.gutterBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = "Go to Line",
                color = colors.text,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Input field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(
                        width = 1.dp,
                        color = if (errorMessage != null) colors.error else colors.gutterBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputValue.isEmpty()) {
                    Text(
                        text = "Line[:Column]",
                        color = colors.text.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                BasicTextField(
                    value = inputValue,
                    onValueChange = { newValue ->
                        // Allow only valid characters (digits, colon, +, -)
                        if (newValue.all { it.isDigit() || it == ':' || it == '+' || it == '-' }) {
                            inputValue = newValue
                            errorMessage = null
                        }
                    },
                    textStyle = TextStyle(
                        color = colors.text,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.caret),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            when {
                                event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                    val result = parseInput(inputValue, currentLine, totalLines)
                                    if (result != null) {
                                        onGoTo(result.first, result.second)
                                        onDismiss()
                                    } else {
                                        errorMessage = "Invalid line number"
                                    }
                                    true
                                }
                                event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                else -> false
                            }
                        }
                )
            }

            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = colors.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Help text
            Text(
                text = "Current: $currentLine:$currentColumn • Total: $totalLines lines",
                color = colors.text.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Format help
            Text(
                text = "Format: line or line:column or +/-offset",
                color = colors.text.copy(alpha = 0.4f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Parses the input string and returns (line, column) if valid.
 */
private fun parseInput(input: String, currentLine: Int, totalLines: Int): Pair<Int, Int>? {
    if (input.isEmpty()) return null

    // Relative offset
    if (input.startsWith('+') || input.startsWith('-')) {
        val offset = input.toIntOrNull() ?: return null
        val newLine = (currentLine + offset).coerceIn(1, totalLines)
        return newLine to 1
    }

    // Line:Column format
    if (input.contains(':')) {
        val parts = input.split(':')
        if (parts.size != 2) return null
        val line = parts[0].toIntOrNull() ?: return null
        val column = parts[1].toIntOrNull() ?: return null
        if (line < 1 || line > totalLines) return null
        if (column < 1) return null
        return line to column
    }

    // Simple line number
    val line = input.toIntOrNull() ?: return null
    if (line < 1 || line > totalLines) return null
    return line to 1
}

/**
 * Quick command palette for editor actions.
 *
 * Provides quick access to common actions:
 * - Go to line
 * - Go to symbol (if available)
 * - Go to file (if available)
 * - Editor commands
 */
@Composable
fun CommandPalette(
    commands: List<EditorCommand>,
    onCommandSelected: (EditorCommand) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    var filter by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val filteredCommands = remember(filter, commands) {
        if (filter.isEmpty()) {
            commands
        } else {
            commands.filter {
                it.name.contains(filter, ignoreCase = true) ||
                it.shortcut?.contains(filter, ignoreCase = true) == true
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .heightIn(max = 300.dp)
                .background(colors.gutterBackground, RoundedCornerShape(8.dp))
                .border(1.dp, colors.gutterBorder, RoundedCornerShape(8.dp))
        ) {
            // Search input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .height(32.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (filter.isEmpty()) {
                    Text(
                        text = "Type to search commands...",
                        color = colors.text.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
                BasicTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    textStyle = TextStyle(
                        color = colors.text,
                        fontSize = 13.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.caret),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                onDismiss()
                                true
                            } else {
                                false
                            }
                        }
                )
            }

            // Command list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                filteredCommands.take(10).forEach { command ->
                    CommandItem(
                        command = command,
                        onClick = {
                            onCommandSelected(command)
                            onDismiss()
                        },
                        colors = colors
                    )
                }

                if (filteredCommands.isEmpty()) {
                    Text(
                        text = "No matching commands",
                        color = colors.text.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandItem(
    command: EditorCommand,
    onClick: () -> Unit,
    colors: ai.rever.bosseditor.theme.EditorColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                colors.background.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = command.name,
            color = colors.text,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        if (command.shortcut != null) {
            Text(
                text = command.shortcut,
                color = colors.text.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * An editor command for the command palette.
 */
data class EditorCommand(
    val id: String,
    val name: String,
    val shortcut: String? = null,
    val category: String = "General"
)

/**
 * Default editor commands.
 */
object EditorCommands {
    // Navigation
    val GO_TO_LINE = EditorCommand("goToLine", "Go to Line", "Ctrl+G", "Navigation")

    // Search
    val FIND = EditorCommand("find", "Find", "Ctrl+F", "Search")
    val REPLACE = EditorCommand("replace", "Replace", "Ctrl+H", "Search")
    val FIND_NEXT = EditorCommand("findNext", "Find Next", "F3", "Search")
    val FIND_PREVIOUS = EditorCommand("findPrevious", "Find Previous", "Shift+F3", "Search")

    // Selection
    val SELECT_ALL = EditorCommand("selectAll", "Select All", "Ctrl+A", "Selection")

    // Editing
    val DUPLICATE_LINE = EditorCommand("duplicateLine", "Duplicate Line", "Ctrl+D", "Editing")
    val DELETE_LINE = EditorCommand("deleteLine", "Delete Line", "Ctrl+Shift+K", "Editing")
    val MOVE_LINE_UP = EditorCommand("moveLineUp", "Move Line Up", "Alt+Up", "Editing")
    val MOVE_LINE_DOWN = EditorCommand("moveLineDown", "Move Line Down", "Alt+Down", "Editing")
    val TOGGLE_COMMENT = EditorCommand("toggleComment", "Toggle Comment", "Ctrl+/", "Editing")
    val INDENT = EditorCommand("indent", "Indent", "Tab", "Editing")
    val OUTDENT = EditorCommand("outdent", "Outdent", "Shift+Tab", "Editing")

    // Folding
    val FOLD = EditorCommand("fold", "Fold", "Ctrl+Shift+[", "Folding")
    val UNFOLD = EditorCommand("unfold", "Unfold", "Ctrl+Shift+]", "Folding")
    val FOLD_ALL = EditorCommand("foldAll", "Fold All", "Ctrl+K Ctrl+0", "Folding")
    val UNFOLD_ALL = EditorCommand("unfoldAll", "Unfold All", "Ctrl+K Ctrl+J", "Folding")

    // Refactoring
    val RENAME = EditorCommand("rename", "Rename Symbol", "Shift+F6", "Refactoring")
    val REFACTOR_MENU = EditorCommand("refactorMenu", "Refactor This...", "Ctrl+Shift+R", "Refactoring")
    val EXTRACT_VARIABLE = EditorCommand("extractVariable", "Extract Variable", "Ctrl+Alt+V", "Refactoring")
    val EXTRACT_METHOD = EditorCommand("extractMethod", "Extract Method", "Ctrl+Alt+M", "Refactoring")
    val EXTRACT_CONSTANT = EditorCommand("extractConstant", "Extract Constant", "Ctrl+Alt+C", "Refactoring")
    val INLINE = EditorCommand("inline", "Inline", "Ctrl+Alt+N", "Refactoring")
    val SAFE_DELETE = EditorCommand("safeDelete", "Safe Delete", "Alt+Delete", "Refactoring")
    val CHANGE_SIGNATURE = EditorCommand("changeSignature", "Change Signature", "Ctrl+F6", "Refactoring")
    val INTRODUCE_PARAMETER = EditorCommand("introduceParameter", "Introduce Parameter", "Ctrl+Alt+P", "Refactoring")

    val ALL = listOf(
        // Navigation
        GO_TO_LINE,
        // Search
        FIND, REPLACE, FIND_NEXT, FIND_PREVIOUS,
        // Selection
        SELECT_ALL,
        // Editing
        DUPLICATE_LINE, DELETE_LINE,
        MOVE_LINE_UP, MOVE_LINE_DOWN, TOGGLE_COMMENT,
        INDENT, OUTDENT,
        // Folding
        FOLD, UNFOLD, FOLD_ALL, UNFOLD_ALL,
        // Refactoring
        RENAME, REFACTOR_MENU, EXTRACT_VARIABLE, EXTRACT_METHOD,
        EXTRACT_CONSTANT, INLINE, SAFE_DELETE, CHANGE_SIGNATURE, INTRODUCE_PARAMETER
    )

    /**
     * Get all refactoring commands.
     */
    val REFACTORING_COMMANDS = listOf(
        RENAME, EXTRACT_VARIABLE, EXTRACT_METHOD, EXTRACT_CONSTANT,
        INLINE, SAFE_DELETE, CHANGE_SIGNATURE, INTRODUCE_PARAMETER
    )
}
