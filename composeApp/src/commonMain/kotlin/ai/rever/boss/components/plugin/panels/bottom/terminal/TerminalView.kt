package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TerminalView(viewModel: TerminalViewModel) {
    val terminalLines by viewModel.terminalLines.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val terminalCursorPosition by viewModel.terminalCursorPosition.collectAsState()
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    
    // Cursor blink animation
    val cursorAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(530),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // Terminal colors
    val backgroundColor = Color(0xFF1E1E1E)
    val textColor = Color(0xFFD4D4D4)
    val cursorColor = Color(0xFF608B4E)
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable { 
                // Use coroutine to defer focus request
                coroutineScope.launch {
                    try {
                        focusRequester.requestFocus()
                    } catch (e: Exception) {
                        // Ignore if focus requester is not ready
                    }
                }
            },
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Terminal output area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = scrollState
                ) {
                    itemsIndexed(terminalLines) { rowIndex, line ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = line,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Show cursor if this is the cursor row
                            if (rowIndex == terminalCursorPosition.first) {
                                // Calculate cursor position
                                val cursorCol = terminalCursorPosition.second
                                Box(
                                    modifier = Modifier
                                        .offset(x = (cursorCol * 7).dp) // Approximate character width
                                        .width(7.dp)
                                        .height(16.dp)
                                        .alpha(if (hasFocus) cursorAlpha else 0f)
                                        .background(cursorColor)
                                )
                            }
                        }
                    }
                    
                    // Input line at the bottom
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Simple prompt indicator
                            Text(
                                text = "$ ",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = Color(0xFF569CD6),
                                    fontWeight = FontWeight.Normal
                                )
                            )
                            
                            // Input field
                            Box {
                                BasicTextField(
                                    value = viewModel.currentInput,
                                    onValueChange = { /* Handled by key events */ },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { hasFocus = it.hasFocus }
                                        .onPreviewKeyEvent { keyEvent ->
                                            handleKeyEvent(keyEvent, viewModel)
                                        },
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        color = textColor,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(cursorColor),
                                    singleLine = true
                                )
                                
                                // Show cursor position in input
                                if (hasFocus && viewModel.currentInput.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = (viewModel.cursorPosition * 7).dp)
                                            .width(1.dp)
                                            .height(16.dp)
                                            .alpha(cursorAlpha)
                                            .background(cursorColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Request focus on composition
    LaunchedEffect(Unit) {
        // Small delay to ensure the focus target is ready
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore if focus requester is not ready
        }
    }
    
    // Auto-scroll to bottom when terminal updates
    LaunchedEffect(terminalLines) {
        if (terminalLines.isNotEmpty()) {
            coroutineScope.launch {
                scrollState.animateScrollToItem(terminalLines.size)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun handleKeyEvent(keyEvent: KeyEvent, viewModel: TerminalViewModel): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) {
        return false
    }
    
    return when (keyEvent.key) {
        Key.Enter -> {
            viewModel.onKeyPress(TerminalKey.Enter)
            true
        }
        Key.Backspace -> {
            viewModel.onKeyPress(TerminalKey.Backspace)
            true
        }
        Key.Delete -> {
            viewModel.onKeyPress(TerminalKey.Delete)
            true
        }
        Key.DirectionLeft -> {
            viewModel.onKeyPress(TerminalKey.Left)
            true
        }
        Key.DirectionRight -> {
            viewModel.onKeyPress(TerminalKey.Right)
            true
        }
        Key.MoveHome -> {
            viewModel.onKeyPress(TerminalKey.Home)
            true
        }
        Key.MoveEnd -> {
            viewModel.onKeyPress(TerminalKey.End)
            true
        }
        else -> {
            // Handle control keys
            if (keyEvent.isCtrlPressed) {
                when (keyEvent.key) {
                    Key.C -> {
                        viewModel.onKeyPress(TerminalKey.ControlKey('\u0003')) // Ctrl+C
                        true
                    }
                    Key.D -> {
                        viewModel.onKeyPress(TerminalKey.ControlKey('\u0004')) // Ctrl+D
                        true
                    }
                    Key.Z -> {
                        viewModel.onKeyPress(TerminalKey.ControlKey('\u001A')) // Ctrl+Z
                        true
                    }
                    Key.L -> {
                        viewModel.onKeyPress(TerminalKey.ControlKey('\u000C')) // Ctrl+L (clear)
                        true
                    }
                    else -> false
                }
            } else {
                // Regular character input
                val char = keyEvent.utf16CodePoint.toChar()
                if (char.code >= 32 && char.code < 127) {
                    viewModel.onKeyPress(TerminalKey.Character(char))
                    true
                } else {
                    false
                }
            }
        }
    }
}