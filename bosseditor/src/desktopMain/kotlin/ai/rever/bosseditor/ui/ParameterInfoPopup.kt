package ai.rever.bosseditor.ui

import ai.rever.bosseditor.features.FunctionSignature
import ai.rever.bosseditor.features.ParameterDefinition
import ai.rever.bosseditor.features.ParameterInfoState
import ai.rever.bosseditor.theme.EditorColors
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Parameter info popup for displaying function signatures.
 *
 * Shows the signature of the function being called with the current
 * parameter highlighted. Supports multiple overloads with navigation.
 *
 * ## Keyboard Shortcuts
 * - Up/Down: Navigate between overloads
 * - Escape: Dismiss popup
 */
@Composable
fun ParameterInfoPopup(
    state: ParameterInfoState,
    position: IntOffset,
    onDismiss: () -> Unit,
    onPreviousOverload: () -> Unit = {},
    onNextOverload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val colors = theme.colors

    Popup(
        offset = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = modifier
                .widthIn(max = 600.dp)
                .background(colors.gutterBackground, RoundedCornerShape(4.dp))
                .border(1.dp, colors.gutterBorder, RoundedCornerShape(4.dp))
                .padding(8.dp)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionUp -> {
                                onPreviousOverload()
                                true
                            }
                            Key.DirectionDown -> {
                                onNextOverload()
                                true
                            }
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            // Overload indicator (if multiple overloads)
            if (state.hasOverloads) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous overload button
                    Text(
                        text = "▲",
                        color = colors.text.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clickable { onPreviousOverload() }
                            .padding(4.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "${state.activeSignatureIndex + 1} of ${state.overloadCount}",
                        color = colors.text.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Next overload button
                    Text(
                        text = "▼",
                        color = colors.text.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clickable { onNextOverload() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Signature display
            state.activeSignature?.let { signature ->
                SignatureDisplay(
                    signature = signature,
                    activeParameterIndex = state.activeParameterIndex,
                    colors = colors
                )

                // Parameter documentation
                state.activeParameter?.documentation?.let { doc ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = doc,
                        color = colors.text.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                // Function documentation
                signature.documentation?.let { doc ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = doc,
                        color = colors.text.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Displays a function signature with the active parameter highlighted.
 */
@Composable
private fun SignatureDisplay(
    signature: FunctionSignature,
    activeParameterIndex: Int,
    colors: EditorColors
) {
    val annotatedString = buildAnnotatedString {
        // Function name
        withStyle(SpanStyle(color = colors.function, fontWeight = FontWeight.Bold)) {
            append(signature.name)
        }

        append("(")

        // Parameters
        signature.parameters.forEachIndexed { index, param ->
            val isActive = index == activeParameterIndex
            val paramColor = if (isActive) colors.keyword else colors.text
            val paramWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

            withStyle(SpanStyle(color = paramColor, fontWeight = paramWeight)) {
                append(param.name)
            }

            param.type?.let { type ->
                withStyle(SpanStyle(color = colors.text.copy(alpha = 0.7f))) {
                    append(": ")
                }
                withStyle(SpanStyle(color = if (isActive) colors.dataType else colors.text.copy(alpha = 0.8f))) {
                    append(type)
                }
            }

            param.defaultValue?.let { default ->
                withStyle(SpanStyle(color = colors.text.copy(alpha = 0.5f))) {
                    append(" = $default")
                }
            }

            if (index < signature.parameters.size - 1) {
                withStyle(SpanStyle(color = colors.text.copy(alpha = 0.5f))) {
                    append(", ")
                }
            }
        }

        append(")")

        // Return type
        signature.returnType?.let { returnType ->
            withStyle(SpanStyle(color = colors.text.copy(alpha = 0.5f))) {
                append(": ")
            }
            withStyle(SpanStyle(color = colors.dataType)) {
                append(returnType)
            }
        }
    }

    Text(
        text = annotatedString,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 16.sp
    )
}

/**
 * Simplified parameter info popup for a single signature.
 */
@Composable
fun SimpleParameterInfoPopup(
    signature: FunctionSignature,
    activeParameterIndex: Int,
    position: IntOffset,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = ParameterInfoState(
        signatures = listOf(signature),
        activeSignatureIndex = 0,
        activeParameterIndex = activeParameterIndex,
        position = ai.rever.bosseditor.core.EditorPosition(0, 0)
    )

    ParameterInfoPopup(
        state = state,
        position = position,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

/**
 * Parameter info popup that displays at a specific editor position.
 * Calculates the popup position based on editor metrics.
 */
@Composable
fun PositionedParameterInfoPopup(
    state: ParameterInfoState,
    charWidth: Float,
    lineHeight: Float,
    gutterWidth: Float,
    scrollOffsetX: Float,
    scrollOffsetY: Float,
    onDismiss: () -> Unit,
    onPreviousOverload: () -> Unit = {},
    onNextOverload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Calculate position above the line
    val x = gutterWidth + state.position.column * charWidth - scrollOffsetX
    val y = state.position.line * lineHeight - scrollOffsetY - 40f // Offset above the line

    ParameterInfoPopup(
        state = state,
        position = IntOffset(x.toInt(), y.toInt()),
        onDismiss = onDismiss,
        onPreviousOverload = onPreviousOverload,
        onNextOverload = onNextOverload,
        modifier = modifier
    )
}
