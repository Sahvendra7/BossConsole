package ai.rever.boss.components.plugin.panels.bottom.terminal

import BossDarkTextSecondary
import ai.rever.boss.components.bars.DraggableVerticalScrollbar
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TerminalView(viewModel: TerminalViewModel) {
    val terminalLines by viewModel.terminalLines.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val terminalCursorPosition by viewModel.terminalCursorPosition.collectAsState()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var hasFocus by remember { mutableStateOf(false) }
    
    // Use a text field value to capture input
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    
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
    val borderColor = if (hasFocus) Color(0xFF007ACC) else Color(0xFF3E3E3E)
    
    // Terminal font - try to use Nerd Fonts for powerline symbols
    val terminalFontFamily = rememberTerminalFontFamily()
    
    // Terminal text style
    val terminalTextStyle = TextStyle(
        fontFamily = terminalFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .border(2.dp, borderColor) // Visual feedback for focus
            .clipToBounds() // Ensure nothing overflows
    ) {
        // Hidden BasicTextField to capture keyboard input
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                // Handle text input
                val oldText = textFieldValue.text
                val newText = newValue.text
                
                if (newText.length > oldText.length) {
                    // Characters were added
                    val addedText = newText.substring(oldText.length)
                    viewModel.sendInput(addedText)
                }
                
                // Reset the field to prevent accumulation
                textFieldValue = TextFieldValue("")
            },
            modifier = Modifier
                .size(0.dp) // Make it invisible
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    hasFocus = focusState.hasFocus
                }
                .onPreviewKeyEvent { keyEvent ->
                    // Use onPreviewKeyEvent to intercept keys before BasicTextField processes them
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        // Handle special keys that don't produce text input
                        when (keyEvent.key) {
                            Key.Enter -> {
                                viewModel.sendInput("\n")
                                true
                            }
                            Key.Backspace -> {
                                viewModel.sendInput("\u007F")
                                true
                            }
                            Key.Delete -> {
                                viewModel.sendInput("\u001B[3~")
                                true
                            }
                            Key.Escape -> {
                                viewModel.sendInput("\u001B")
                                true
                            }
                            Key.DirectionLeft -> {
                                viewModel.sendInput("\u001B[D")
                                true
                            }
                            Key.DirectionRight -> {
                                viewModel.sendInput("\u001B[C")
                                true
                            }
                            Key.DirectionUp -> {
                                viewModel.sendInput("\u001B[A")
                                true
                            }
                            Key.DirectionDown -> {
                                viewModel.sendInput("\u001B[B")
                                true
                            }
                            Key.Tab -> {
                                viewModel.sendInput("\t")
                                true
                            }
                            Key.MoveHome -> {
                                viewModel.sendInput("\u001B[H")
                                true
                            }
                            Key.MoveEnd -> {
                                viewModel.sendInput("\u001B[F")
                                true
                            }
                            else -> {
                                // Handle Ctrl and Alt combinations
                                if (keyEvent.isCtrlPressed || keyEvent.isAltPressed) {
                                    handleKeyEvent(keyEvent, viewModel)
                                } else {
                                    false // Let BasicTextField handle regular characters
                                }
                            }
                        }
                    } else {
                        false
                    }
                },
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent)
        )
        
        // Terminal content with draggable scrollbar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Request focus when clicked
                    focusRequester.requestFocus()
                }
        ) {
            // Terminal output area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // If terminal is not running, show status
                if (!isRunning) {
                    Text(
                        text = "Terminal starting...",
                        style = terminalTextStyle,
                        color = Color.Yellow
                    )
                }

                terminalLines.forEachIndexed { rowIndex, line ->
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = line,
                            style = terminalTextStyle,
                            color = textColor
                        )
                        
                        // Show cursor if this is the cursor row
                        if (rowIndex == terminalCursorPosition.first && hasFocus) {
                            // Calculate cursor position
                            val cursorCol = terminalCursorPosition.second
                            Box(
                                modifier = Modifier
                                    .offset(x = (cursorCol * 8.4f).dp) // More accurate character width
                                    .width(8.4f.dp)
                                    .height(16.dp)
                                    .alpha(if (hasFocus) cursorAlpha else 0f)
                                    .background(cursorColor)
                            )
                        }
                    }
                }
            }
            
            // Draggable scrollbar
            DraggableVerticalScrollbar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
                indicatorThickness = 6.dp,
                indicatorColor = BossDarkTextSecondary
            )
        }
    }
    
    // Request focus when terminal becomes visible
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
    
    // Keep requesting focus if we lose it
    LaunchedEffect(hasFocus) {
        if (!hasFocus) {
            // Wait a bit then try to regain focus
            delay(200)
            if (!hasFocus) { // Check again to avoid focus fighting
                focusRequester.requestFocus()
            }
        }
    }
    
    // Auto-scroll to bottom when terminal updates (only if already at bottom)
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            // Check if we're already at or near the bottom
            val isAtBottom = scrollState.value >= (scrollState.maxValue - 100) // 100px tolerance
            
            // Only auto-scroll if we're already following the output
            if (isAtBottom && scrollState.maxValue > 0) {
                coroutineScope.launch {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
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
            viewModel.sendInput("\n")
            true
        }
        Key.Backspace -> {
            viewModel.sendInput("\u007F") // DEL character
            true
        }
        Key.Delete -> {
            viewModel.sendInput("\u001B[3~") // Delete key sequence
            true
        }
        Key.Escape -> {
            viewModel.sendInput("\u001B") // ESC character
            true
        }
        Key.DirectionLeft -> {
            viewModel.sendInput("\u001B[D") // Left arrow
            true
        }
        Key.DirectionRight -> {
            viewModel.sendInput("\u001B[C") // Right arrow
            true
        }
        Key.DirectionUp -> {
            viewModel.sendInput("\u001B[A") // Up arrow (for history)
            true
        }
        Key.DirectionDown -> {
            viewModel.sendInput("\u001B[B") // Down arrow (for history)
            true
        }
        Key.MoveHome -> {
            viewModel.sendInput("\u001B[H") // Home
            true
        }
        Key.MoveEnd -> {
            viewModel.sendInput("\u001B[F") // End
            true
        }
        Key.Tab -> {
            viewModel.sendInput("\t") // Tab for completion
            true
        }
        else -> {
            // Handle control keys
            if (keyEvent.isCtrlPressed) {
                when (keyEvent.key) {
                    Key.C -> {
                        viewModel.sendInput("\u0003") // Ctrl+C
                        true
                    }
                    Key.D -> {
                        viewModel.sendInput("\u0004") // Ctrl+D
                        true
                    }
                    Key.Z -> {
                        viewModel.sendInput("\u001A") // Ctrl+Z
                        true
                    }
                    Key.L -> {
                        viewModel.sendInput("\u000C") // Ctrl+L (clear)
                        true
                    }
                    Key.A -> {
                        viewModel.sendInput("\u0001") // Ctrl+A (beginning of line)
                        true
                    }
                    Key.E -> {
                        viewModel.sendInput("\u0005") // Ctrl+E (end of line)
                        true
                    }
                    Key.K -> {
                        viewModel.sendInput("\u000B") // Ctrl+K (kill to end of line)
                        true
                    }
                    Key.U -> {
                        viewModel.sendInput("\u0015") // Ctrl+U (kill to beginning of line)
                        true
                    }
                    Key.W -> {
                        viewModel.sendInput("\u0017") // Ctrl+W (kill word)
                        true
                    }
                    else -> false
                }
            } else if (keyEvent.isAltPressed) {
                // Handle Alt key combinations
                when (keyEvent.key) {
                    Key.B -> {
                        viewModel.sendInput("\u001Bb") // Alt+B (backward word)
                        true
                    }
                    Key.F -> {
                        viewModel.sendInput("\u001Bf") // Alt+F (forward word)
                        true
                    }
                    else -> {
                        // For other Alt combinations, send ESC + character
                        val char = keyEvent.utf16CodePoint.toChar()
                        if (char.code >= 32 && char.code < 127) {
                            viewModel.sendInput("\u001B$char")
                            true
                        } else {
                            false
                        }
                    }
                }
            } else {
                // Regular character input
                val char = keyEvent.utf16CodePoint.toChar()
                if (char.code >= 32 && char.code < 127) {
                    viewModel.sendInput(char.toString())
                    true
                } else {
                    false
                }
            }
        }
    }
}