package ai.rever.bosseditor.ui

import ai.rever.bosseditor.refactoring.RefactorKind
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Dialog for extract variable refactoring.
 *
 * @param suggestedName The suggested variable name
 * @param selectedExpression The selected expression text
 * @param onExtract Called when extract is confirmed
 * @param onCancel Called when cancelled
 * @param onValidate Validates the variable name
 */
@Composable
fun ExtractVariableDialog(
    suggestedName: String,
    selectedExpression: String,
    onExtract: (name: String, replaceAll: Boolean, isVal: Boolean) -> Unit,
    onCancel: () -> Unit,
    onValidate: suspend (String) -> String? = { null }
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = suggestedName,
                selection = TextRange(0, suggestedName.length)
            )
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isValidating by remember { mutableStateOf(false) }
    var replaceAll by remember { mutableStateOf(false) }
    var useVal by remember { mutableStateOf(true) }

    val focusRequester = remember { FocusRequester() }

    // Focus input on show
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Validate with debounce
    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text != suggestedName) {
            isValidating = true
            delay(200)
            errorMessage = onValidate(textFieldValue.text)
            isValidating = false
        } else {
            errorMessage = null
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .background(colors.gutterBackground, RoundedCornerShape(8.dp))
                .border(1.dp, colors.gutterBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = "Extract Variable",
                color = colors.text,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Expression preview
            Text(
                text = "Expression:",
                color = colors.text.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = selectedExpression.take(100) + if (selectedExpression.length > 100) "..." else "",
                    color = colors.text,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Variable name input
            Text(
                text = "Variable name:",
                color = colors.text,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(
                        width = 1.dp,
                        color = if (errorMessage != null) colors.error else colors.gutterBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    textStyle = TextStyle(
                        color = colors.text,
                        fontSize = 14.sp,
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
                                    if (errorMessage == null) {
                                        onExtract(textFieldValue.text, replaceAll, useVal)
                                    }
                                    true
                                }
                                event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                    onCancel()
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

            Spacer(modifier = Modifier.height(12.dp))

            // Options
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { useVal = true }
            ) {
                Checkbox(
                    checked = useVal,
                    onCheckedChange = { useVal = true },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.selectionBackground,
                        uncheckedColor = colors.text.copy(alpha = 0.5f)
                    )
                )
                Text(
                    text = "val (immutable)",
                    color = colors.text,
                    fontSize = 12.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { useVal = false }
            ) {
                Checkbox(
                    checked = !useVal,
                    onCheckedChange = { useVal = false },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.selectionBackground,
                        uncheckedColor = colors.text.copy(alpha = 0.5f)
                    )
                )
                Text(
                    text = "var (mutable)",
                    color = colors.text,
                    fontSize = 12.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { replaceAll = !replaceAll }
            ) {
                Checkbox(
                    checked = replaceAll,
                    onCheckedChange = { replaceAll = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.selectionBackground,
                        uncheckedColor = colors.text.copy(alpha = 0.5f)
                    )
                )
                Text(
                    text = "Replace all occurrences",
                    color = colors.text,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                DialogButton(
                    text = "Cancel",
                    onClick = onCancel,
                    colors = colors
                )
                Spacer(modifier = Modifier.width(8.dp))
                DialogButton(
                    text = "Extract",
                    onClick = { onExtract(textFieldValue.text, replaceAll, useVal) },
                    enabled = errorMessage == null && textFieldValue.text.isNotBlank(),
                    primary = true,
                    colors = colors
                )
            }
        }
    }
}

/**
 * Dialog for extract method refactoring.
 *
 * @param suggestedName The suggested method name
 * @param selectedCode Preview of the selected code
 * @param onExtract Called when extract is confirmed
 * @param onCancel Called when cancelled
 * @param onValidate Validates the method name
 */
@Composable
fun ExtractMethodDialog(
    suggestedName: String,
    selectedCode: String,
    onExtract: (name: String, visibility: String, makeStatic: Boolean) -> Unit,
    onCancel: () -> Unit,
    onValidate: suspend (String) -> String? = { null }
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = suggestedName,
                selection = TextRange(0, suggestedName.length)
            )
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isValidating by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf("private") }
    var makeStatic by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    // Focus input on show
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Validate with debounce
    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text != suggestedName) {
            isValidating = true
            delay(200)
            errorMessage = onValidate(textFieldValue.text)
            isValidating = false
        } else {
            errorMessage = null
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .width(450.dp)
                .background(colors.gutterBackground, RoundedCornerShape(8.dp))
                .border(1.dp, colors.gutterBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = "Extract Method",
                color = colors.text,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Code preview
            Text(
                text = "Selected code:",
                color = colors.text.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp)
                    .padding(vertical = 4.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = selectedCode.take(500) + if (selectedCode.length > 500) "..." else "",
                    color = colors.text,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Method name input
            Text(
                text = "Method name:",
                color = colors.text,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(
                        width = 1.dp,
                        color = if (errorMessage != null) colors.error else colors.gutterBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    textStyle = TextStyle(
                        color = colors.text,
                        fontSize = 14.sp,
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
                                    if (errorMessage == null) {
                                        onExtract(textFieldValue.text, visibility, makeStatic)
                                    }
                                    true
                                }
                                event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                    onCancel()
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

            Spacer(modifier = Modifier.height(12.dp))

            // Visibility options
            Text(
                text = "Visibility:",
                color = colors.text,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.padding(top = 4.dp)
            ) {
                listOf("private", "internal", "public").forEach { vis ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { visibility = vis }
                            .padding(end = 16.dp)
                    ) {
                        Checkbox(
                            checked = visibility == vis,
                            onCheckedChange = { if (it) visibility = vis },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.selectionBackground,
                                uncheckedColor = colors.text.copy(alpha = 0.5f)
                            )
                        )
                        Text(
                            text = vis,
                            color = colors.text,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                DialogButton(
                    text = "Cancel",
                    onClick = onCancel,
                    colors = colors
                )
                Spacer(modifier = Modifier.width(8.dp))
                DialogButton(
                    text = "Extract",
                    onClick = { onExtract(textFieldValue.text, visibility, makeStatic) },
                    enabled = errorMessage == null && textFieldValue.text.isNotBlank(),
                    primary = true,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
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
