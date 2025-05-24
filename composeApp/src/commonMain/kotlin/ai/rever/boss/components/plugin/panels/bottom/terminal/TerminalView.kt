package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
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
    ) {
        // Terminal content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Terminal output area with custom scroll behavior
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = scrollState,
                    // Adjust content padding to prevent over-scrolling
                    contentPadding = PaddingValues(vertical = 4.dp),
                    // Fine-tune the fling behavior for smoother scrolling
                    flingBehavior = ScrollableDefaults.flingBehavior()
                ) {
                    itemsIndexed(terminalLines) { rowIndex, line ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = line,
                                style = terminalTextStyle,
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Show cursor if this is the cursor row
                            if (rowIndex == terminalCursorPosition.first && hasFocus) {
                                // Calculate cursor position
                                val cursorCol = terminalCursorPosition.second
                                Box(
                                    modifier = Modifier
                                        .offset(x = (cursorCol * 7).dp) // Approximate character width
                                        .width(7.dp)
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
        
        // Invisible overlay to capture all input
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onFocusChanged { focusState ->
                    hasFocus = focusState.hasFocus
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // No ripple effect
                ) { 
                    if (!hasFocus) {
                        focusRequester.requestFocus()
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (hasFocus && keyEvent.type == KeyEventType.KeyDown) {
                        handleKeyEvent(keyEvent, viewModel)
                    } else {
                        false
                    }
                }
        )
    }
    
    // Request focus on composition
    LaunchedEffect(Unit) {
        delay(300) // Slightly longer delay for stability
        focusRequester.requestFocus()
    }
    
    // Auto-scroll to bottom when terminal updates
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            // Only auto-scroll if we're already near the bottom
            val visibleItemsInfo = scrollState.layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isNotEmpty()) {
                val lastVisibleItem = visibleItemsInfo.last()
                val totalItems = scrollState.layoutInfo.totalItemsCount
                
                // Check if we're viewing the last few items (within 5 lines of bottom)
                if (lastVisibleItem.index >= totalItems - 5) {
                    // Smooth scroll to bottom
                    scrollState.animateScrollToItem(
                        index = terminalLines.size - 1,
                        scrollOffset = 0
                    )
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