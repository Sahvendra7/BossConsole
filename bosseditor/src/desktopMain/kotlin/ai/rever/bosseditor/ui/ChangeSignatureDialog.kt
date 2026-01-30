package ai.rever.bosseditor.ui

import ai.rever.bosseditor.refactoring.ChangeSignatureParams
import ai.rever.bosseditor.refactoring.ParameterInfo
import ai.rever.bosseditor.refactoring.psi.ChangeSignatureRefactoring
import ai.rever.bosseditor.theme.EditorColors
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Dialog for changing function signature.
 *
 * Shows:
 * - Function name field
 * - Return type field
 * - Parameter table with add/remove/reorder buttons
 * - Preview of the new signature
 *
 * @param signatureInfo The current function signature
 * @param onApply Called when changes are confirmed
 * @param onCancel Called when cancelled
 */
@Composable
fun ChangeSignatureDialog(
    signatureInfo: ChangeSignatureRefactoring.SignatureInfo,
    onApply: (ChangeSignatureParams) -> Unit,
    onCancel: () -> Unit
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    var functionName by remember { mutableStateOf(signatureInfo.name) }
    var returnType by remember { mutableStateOf(signatureInfo.returnType ?: "") }
    var visibility by remember { mutableStateOf(signatureInfo.visibility ?: "public") }
    var parameters by remember { mutableStateOf<List<ParameterInfo>>(signatureInfo.parameters.toList()) }
    var selectedParamIndex by remember { mutableStateOf(-1) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }

    // Focus function name on show
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Validate on changes
    LaunchedEffect(functionName, parameters) {
        delay(200)
        errorMessage = validateSignature(functionName, parameters)
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .width(600.dp)
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
            // Title
            Text(
                text = "Change Signature",
                color = colors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Function name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Name:",
                    color = colors.text,
                    fontSize = 12.sp,
                    modifier = Modifier.width(80.dp)
                )
                SignatureTextField(
                    value = functionName,
                    onValueChange = { functionName = it },
                    placeholder = "function name",
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Return type
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Return type:",
                    color = colors.text,
                    fontSize = 12.sp,
                    modifier = Modifier.width(80.dp)
                )
                SignatureTextField(
                    value = returnType,
                    onValueChange = { returnType = it },
                    placeholder = "Unit",
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Visibility
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Visibility:",
                    color = colors.text,
                    fontSize = 12.sp,
                    modifier = Modifier.width(80.dp)
                )
                Row {
                    listOf("public", "internal", "protected", "private").forEach { vis ->
                        VisibilityChip(
                            text = vis,
                            selected = visibility == vis,
                            onClick = { visibility = vis },
                            colors = colors
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Parameters section
            Text(
                text = "Parameters:",
                color = colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Parameter table header
            ParameterTableHeader(colors)

            // Parameter list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
            ) {
                if (parameters.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No parameters",
                            color = colors.text.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(parameters) { index, param ->
                            ParameterRow(
                                param = param,
                                index = index,
                                isSelected = index == selectedParamIndex,
                                onSelect = { selectedParamIndex = index },
                                onUpdate = { newParam ->
                                    parameters = parameters.mapIndexed { i, p -> if (i == index) newParam else p }
                                },
                                colors = colors
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Parameter action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconActionButton(
                    icon = Icons.Default.Add,
                    tooltip = "Add parameter",
                    onClick = {
                        val newParam = ParameterInfo(
                            name = "param${parameters.size + 1}",
                            type = "Any",
                            defaultValue = null,
                            isVararg = false
                        )
                        parameters = parameters + newParam
                        selectedParamIndex = parameters.size - 1
                    },
                    colors = colors
                )
                IconActionButton(
                    icon = Icons.Default.Delete,
                    tooltip = "Remove parameter",
                    enabled = selectedParamIndex >= 0,
                    onClick = {
                        if (selectedParamIndex >= 0) {
                            parameters = parameters.filterIndexed { i, _ -> i != selectedParamIndex }
                            selectedParamIndex = (selectedParamIndex - 1).coerceAtLeast(-1)
                        }
                    },
                    colors = colors
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconActionButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    tooltip = "Move up",
                    enabled = selectedParamIndex > 0,
                    onClick = {
                        if (selectedParamIndex > 0) {
                            val mutableParams = parameters.toMutableList()
                            val temp = mutableParams[selectedParamIndex]
                            mutableParams[selectedParamIndex] = mutableParams[selectedParamIndex - 1]
                            mutableParams[selectedParamIndex - 1] = temp
                            parameters = mutableParams.toList()
                            selectedParamIndex--
                        }
                    },
                    colors = colors
                )
                IconActionButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    tooltip = "Move down",
                    enabled = selectedParamIndex >= 0 && selectedParamIndex < parameters.size - 1,
                    onClick = {
                        if (selectedParamIndex < parameters.size - 1) {
                            val mutableParams = parameters.toMutableList()
                            val temp = mutableParams[selectedParamIndex]
                            mutableParams[selectedParamIndex] = mutableParams[selectedParamIndex + 1]
                            mutableParams[selectedParamIndex + 1] = temp
                            parameters = mutableParams.toList()
                            selectedParamIndex++
                        }
                    },
                    colors = colors
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preview
            Text(
                text = "Preview:",
                color = colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = buildSignaturePreview(visibility, functionName, parameters, returnType),
                    color = colors.text,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = colors.error,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SignatureDialogButton(
                    text = "Cancel",
                    onClick = onCancel,
                    colors = colors
                )
                Spacer(modifier = Modifier.width(8.dp))
                SignatureDialogButton(
                    text = "Refactor",
                    onClick = {
                        val params = ChangeSignatureParams(
                            newName = if (functionName != signatureInfo.name) functionName else null,
                            newParameters = parameters,
                            newReturnType = if (returnType.isNotBlank() && returnType != signatureInfo.returnType) returnType else null,
                            newVisibility = if (visibility != signatureInfo.visibility) visibility else null
                        )
                        onApply(params)
                    },
                    enabled = errorMessage == null && functionName.isNotBlank(),
                    primary = true,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun ParameterTableHeader(colors: EditorColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.gutterBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Name",
            color = colors.text.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Type",
            color = colors.text.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Default",
            color = colors.text.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ParameterRow(
    param: ParameterInfo,
    index: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (ParameterInfo) -> Unit,
    colors: EditorColors
) {
    val backgroundColor = if (isSelected) colors.selectionBackground.copy(alpha = 0.3f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name
        BasicTextField(
            value = param.name,
            onValueChange = { onUpdate(param.copy(name = it)) },
            textStyle = TextStyle(
                color = colors.text,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            ),
            singleLine = true,
            cursorBrush = SolidColor(colors.caret),
            modifier = Modifier.weight(1f)
        )

        // Type
        BasicTextField(
            value = param.type,
            onValueChange = { onUpdate(param.copy(type = it)) },
            textStyle = TextStyle(
                color = colors.text,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            ),
            singleLine = true,
            cursorBrush = SolidColor(colors.caret),
            modifier = Modifier.weight(1f)
        )

        // Default value
        BasicTextField(
            value = param.defaultValue ?: "",
            onValueChange = { onUpdate(param.copy(defaultValue = it.ifBlank { null })) },
            textStyle = TextStyle(
                color = colors.text.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            ),
            singleLine = true,
            cursorBrush = SolidColor(colors.caret),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box {
                    if (param.defaultValue == null) {
                        Text(
                            text = "(none)",
                            color = colors.text.copy(alpha = 0.3f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun SignatureTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    colors: EditorColors,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .background(colors.background, RoundedCornerShape(4.dp))
            .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = colors.text,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            ),
            singleLine = true,
            cursorBrush = SolidColor(colors.caret),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.text.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun VisibilityChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    colors: EditorColors
) {
    val backgroundColor = if (selected) colors.selectionBackground else colors.background
    val borderColor = if (selected) colors.selectionBackground else colors.gutterBorder

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = colors.text,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun IconActionButton(
    icon: ImageVector,
    tooltip: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    colors: EditorColors
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                if (enabled) colors.gutterBackground else colors.gutterBackground.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp)
            )
            .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = if (enabled) colors.text else colors.text.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SignatureDialogButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    colors: EditorColors
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

/**
 * Builds a preview of the new signature.
 */
private fun buildSignaturePreview(
    visibility: String,
    name: String,
    parameters: List<ParameterInfo>,
    returnType: String
): String {
    return buildString {
        if (visibility != "public") {
            append(visibility)
            append(" ")
        }
        append("fun ")
        append(name)
        append("(")
        append(parameters.joinToString(", ") { param ->
            buildString {
                if (param.isVararg) append("vararg ")
                append(param.name)
                append(": ")
                append(param.type)
                if (param.defaultValue != null) {
                    append(" = ")
                    append(param.defaultValue)
                }
            }
        })
        append(")")
        if (returnType.isNotBlank() && returnType != "Unit") {
            append(": ")
            append(returnType)
        }
    }
}

/**
 * Validates the signature.
 */
private fun validateSignature(name: String, parameters: List<ParameterInfo>): String? {
    if (name.isBlank()) {
        return "Function name cannot be empty"
    }

    if (!isValidKotlinIdentifier(name)) {
        return "Invalid function name: '$name'"
    }

    for (param in parameters) {
        if (param.name.isBlank()) {
            return "Parameter name cannot be empty"
        }
        if (!isValidKotlinIdentifier(param.name)) {
            return "Invalid parameter name: '${param.name}'"
        }
        if (param.type.isBlank()) {
            return "Parameter type cannot be empty for '${param.name}'"
        }
    }

    // Check for duplicate names
    val names = parameters.map { it.name }
    val duplicates = names.groupBy { it }.filter { it.value.size > 1 }.keys
    if (duplicates.isNotEmpty()) {
        return "Duplicate parameter name: '${duplicates.first()}'"
    }

    return null
}

/**
 * Checks if a name is a valid Kotlin identifier.
 */
private fun isValidKotlinIdentifier(name: String): Boolean {
    if (name.isEmpty()) return false

    // Backtick-escaped identifiers
    if (name.startsWith('`') && name.endsWith('`') && name.length > 2) {
        val inner = name.substring(1, name.length - 1)
        return inner.isNotEmpty() && !inner.contains('`')
    }

    // Regular identifiers
    val firstChar = name.first()
    if (!firstChar.isLetter() && firstChar != '_') {
        return false
    }

    return name.all { it.isLetterOrDigit() || it == '_' }
}
