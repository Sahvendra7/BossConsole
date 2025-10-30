package ai.rever.boss.components.plugin.panels.bottom.terminal

import BossDarkTextSecondary
import BossDarkAccent
import BossDarkBorder
import ai.rever.boss.components.bars.ScrollbarConfig
import ai.rever.boss.components.bars.verticalScrollWithScrollbar
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlin.math.max

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TerminalView(viewModel: TerminalViewModel) {
    // State collection
    val terminalLines by viewModel.terminalLines.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val terminalCursorPosition by viewModel.terminalCursorPosition.collectAsState()
    val terminalCursorVisible by viewModel.terminalCursorVisible.collectAsState()

    // UI state
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var userHasScrolled by remember { mutableStateOf(false) }
    
    // Terminal sizing
    var terminalSize by remember { mutableStateOf(Pair(120, 30)) }
    var ptySize by remember { mutableStateOf(Pair(114, 30)) }
    var hasInitialSize by remember { mutableStateOf(false) }
    var pendingResize by remember { mutableStateOf<Triple<Int, Int, Pair<Int, Int>>?>(null) }


    val density = LocalDensity.current

    // Character dimensions
    val fontSize = TerminalSettings.fontSize.sp
    val charHeightDp = (TerminalSettings.fontSize + 3).dp
    val charWidthDp = remember(TerminalSettings.fontSize) {
        (TerminalSettings.fontSize * 0.6).dp
    }
    val charWidthPx = with(density) { charWidthDp.toPx() }
    val charHeightPx = with(density) { charHeightDp.toPx() }
    
    // Colors and styles
    val backgroundColor = TerminalSettings.getBackgroundColor()
    val textColor = TerminalSettings.getForegroundColor()
    val cursorColor = TerminalSettings.getCursorColor()
    val borderColor = if (hasFocus) BossDarkAccent else BossDarkBorder
    val terminalFontFamily = rememberTerminalFontFamily()
    
    val terminalTextStyle = TextStyle(
        fontFamily = terminalFontFamily,
        fontSize = fontSize,
        fontWeight = FontWeight.Normal
    )
    
    // Cursor animation
    val cursorAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(530),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .border(2.dp, borderColor)
            .clipToBounds()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
            }
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = null
            }
    ) {
        // Hidden input field
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val oldText = textFieldValue.text
                val newText = newValue.text
                
                if (newText.length > oldText.length) {
                    val addedText = newText.substring(oldText.length)
                    if (addedText.all { char -> char.code < 32 || char.code >= 127 }) {
                        viewModel.sendInput(addedText)
                    }
                }
                textFieldValue = TextFieldValue("")
            },
            modifier = Modifier
                .size(0.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { hasFocus = it.hasFocus }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        handleKeyEvent(keyEvent, viewModel)
                    } else false
                },
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent)
        )
        
        // Terminal content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0) {
                        with(density) {
                            val horizontalPaddingPx = 24.dp.toPx() + 12.dp.toPx()
                            val verticalPaddingPx = 24.dp.toPx()
                            val availableWidth = size.width - horizontalPaddingPx
                            val availableHeight = size.height - verticalPaddingPx

                            val calculatedColumns = kotlin.math.floor(availableWidth / charWidthPx).toInt()
                            val displayColumns = calculatedColumns.coerceIn(80, 150)
                            val ptyColumns = (displayColumns * 0.95).toInt().coerceIn(75, 140)

                            val calculatedRows = kotlin.math.floor(availableHeight / charHeightPx).toInt()
                            val displayRows = max(24, calculatedRows)
                            val ptyRows = displayRows

                            if (terminalSize.first != displayColumns || terminalSize.second != displayRows) {
                                pendingResize = Triple(displayColumns, displayRows, Pair(ptyColumns, ptyRows))
                            }
                        }
                    }
                }
        ) {
            // Enable multi-line text selection in Terminal
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .clipToBounds()
                        .verticalScrollWithScrollbar(
                            scrollState = scrollState,
                            scrollbarConfig = ScrollbarConfig(
                                indicatorThickness = 6.dp,
                                indicatorColor = BossDarkTextSecondary,
                                padding = PaddingValues(end = 0.dp)
                            )
                        )
                ) {
                    if (!isRunning || terminalLines.isEmpty()) {
                        Text(
                            text = if (!hasInitialSize) "Waiting for layout..." else "Terminal starting...",
                            style = terminalTextStyle,
                            color = BossDarkTextSecondary
                        )
                    }

                    terminalLines.forEachIndexed { rowIndex, line ->
                    val needsWrapping = line.text.length > terminalSize.first
                    
                    Box(
                        modifier = Modifier
                            .width(charWidthDp * terminalSize.first)
                            .height(charHeightDp)
                            .clipToBounds()
                            .graphicsLayer {
                                compositingStrategy = if (needsWrapping) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                            }
                    ) {
                        Text(
                            text = if (line.text.isEmpty()) AnnotatedString(" ") else line,
                            style = terminalTextStyle.copy(
                                lineHeight = charHeightDp.value.sp
                            ),
                            modifier = Modifier
                                .width(charWidthDp * terminalSize.first)
                                .height(charHeightDp)
                                .wrapContentHeight(align = Alignment.Top),
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            maxLines = 1
                        )
                        
                        // Cursor
                        if (rowIndex == terminalCursorPosition.first && hasFocus && terminalCursorVisible) {
                            val cursorCol = terminalCursorPosition.second
                            val scaleFactor = terminalSize.first.toFloat() / ptySize.first.toFloat()
                            val displayCursorCol = (cursorCol * scaleFactor).toInt()
                            
                            Box(
                                modifier = Modifier
                                    .offset(x = charWidthDp * displayCursorCol)
                                    .size(charWidthDp, charHeightDp)
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
    
    // Terminal initialization
    LaunchedEffect(terminalSize) {
        if (terminalSize.first > 0 && terminalSize.second > 0 && !hasInitialSize) {
            focusRequester.requestFocus()
            hasInitialSize = true
            viewModel.ensureStarted()
        }
    }
    
    // Resize handling
    LaunchedEffect(pendingResize) {
        pendingResize?.let { (cols, rows, ptyResize) ->
            delay(50) // Simple debouncing
            
            if (!hasInitialSize) {
                hasInitialSize = true
                viewModel.ensureStarted()
            }
            
            if (terminalSize.first != cols || terminalSize.second != rows) {
                terminalSize = Pair(cols, rows)
                ptySize = ptyResize
                viewModel.resizeWithDeception(cols, rows, ptyResize.first, ptyResize.second)
            }
            
            pendingResize = null
        }
    }
    
    // Scroll tracking
    LaunchedEffect(scrollState.value) {
        if (scrollState.isScrollInProgress) {
            val currentMax = scrollState.maxValue
            val currentValue = scrollState.value
            
            userHasScrolled = currentValue < (currentMax - 50)
            
            if (currentValue >= (currentMax - 50)) {
                userHasScrolled = false
            }
        }
    }
    
    // Auto-scroll to bottom
    LaunchedEffect(terminalLines.size, userHasScrolled) {
        if (terminalLines.isNotEmpty() && !userHasScrolled) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun handleKeyEvent(keyEvent: KeyEvent, viewModel: TerminalViewModel): Boolean {
    return when (keyEvent.key) {
        Key.Enter -> { viewModel.sendInput("\r"); true }
        Key.Backspace -> { viewModel.sendInput("\u007F"); true }
        Key.Delete -> { viewModel.sendInput("\u001B[3~"); true }
        Key.Escape -> { viewModel.sendInput("\u001B"); true }
        Key.DirectionLeft -> { viewModel.sendInput("\u001B[D"); true }
        Key.DirectionRight -> { viewModel.sendInput("\u001B[C"); true }
        Key.DirectionUp -> { viewModel.sendInput("\u001B[A"); true }
        Key.DirectionDown -> { viewModel.sendInput("\u001B[B"); true }
        Key.Tab -> { viewModel.sendInput("\t"); true }
        Key.MoveHome -> { viewModel.sendInput("\u001B[H"); true }
        Key.MoveEnd -> { viewModel.sendInput("\u001B[F"); true }
        
        else -> {
            when {
                keyEvent.isCtrlPressed -> handleCtrlKey(keyEvent, viewModel)
                keyEvent.isAltPressed -> handleAltKey(keyEvent, viewModel)
                else -> {
                    val char = keyEvent.utf16CodePoint.toChar()
                    if (char.code in 32..126) {
                        viewModel.sendInput(char.toString())
                        true
                    } else false
                }
            }
        }
    }
}

private fun handleCtrlKey(keyEvent: KeyEvent, viewModel: TerminalViewModel): Boolean {
    return when (keyEvent.key) {
        Key.C -> { viewModel.sendInput("\u0003"); true }
        Key.D -> { viewModel.sendInput("\u0004"); true }
        Key.Z -> { viewModel.sendInput("\u001A"); true }
        Key.L -> { viewModel.sendInput("\u000C"); true }
        Key.A -> { viewModel.sendInput("\u0001"); true }
        Key.E -> { viewModel.sendInput("\u0005"); true }
        Key.K -> { viewModel.sendInput("\u000B"); true }
        Key.U -> { viewModel.sendInput("\u0015"); true }
        Key.W -> { viewModel.sendInput("\u0017"); true }
        else -> false
    }
}

private fun handleAltKey(keyEvent: KeyEvent, viewModel: TerminalViewModel): Boolean {
    return when (keyEvent.key) {
        Key.B -> { viewModel.sendInput("\u001Bb"); true }
        Key.F -> { viewModel.sendInput("\u001Bf"); true }
        else -> {
            val char = keyEvent.utf16CodePoint.toChar()
            if (char.code in 32..126) {
                viewModel.sendInput("\u001B$char")
                true
            } else false
        }
    }
}
