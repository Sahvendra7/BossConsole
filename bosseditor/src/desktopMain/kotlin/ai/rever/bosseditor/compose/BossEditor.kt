package ai.rever.bosseditor.compose

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.EditorState
import ai.rever.bosseditor.input.EditorInputHandler
import ai.rever.bosseditor.rendering.EditorCanvas
import ai.rever.bosseditor.rendering.EditorToken
import ai.rever.bosseditor.theme.EditorTheme
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily

/**
 * Main composable entry point for BossEditor.
 *
 * A full-featured code editor built with Compose Canvas, designed to replace
 * RSyntaxTextArea with native Compose rendering.
 *
 * ## Features
 * - Syntax highlighting (via token provider)
 * - Code folding (planned)
 * - Search and replace
 * - Multiple themes
 * - Undo/redo with typing coalescing
 * - Selection (mouse and keyboard)
 * - Clipboard operations (copy, cut, paste)
 *
 * ## Usage
 * ```kotlin
 * val editorState = remember { EditorState("fun main() {\n    println(\"Hello\")\n}") }
 *
 * BossEditor(
 *     state = editorState,
 *     theme = EditorTheme.Dark,
 *     modifier = Modifier.fillMaxSize()
 * )
 * ```
 *
 * @param state The editor state containing document, caret, selection
 * @param theme The visual theme (Dark, Light, Dracula, etc.)
 * @param modifier Modifier for the root composable
 * @param fontFamily Font family (should be monospace)
 * @param fontSize Font size in scaled pixels
 * @param showLineNumbers Whether to show line number gutter
 * @param highlightCurrentLine Whether to highlight current line
 * @param readOnly If true, editing operations are disabled
 * @param searchQuery Current search query (null if not searching)
 * @param searchMatches List of search match ranges
 * @param currentSearchMatchIndex Index of current search match
 * @param tokenProvider Function to get tokens for a line (for syntax highlighting)
 * @param onTextChanged Callback when text changes
 * @param onCaretPositionChanged Callback when caret position changes
 * @param onSelectionChanged Callback when selection changes
 */
@Composable
fun BossEditor(
    state: EditorState,
    modifier: Modifier = Modifier,
    theme: EditorTheme = EditorTheme.Dark,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontSize: Float = 14f,
    showLineNumbers: Boolean = true,
    highlightCurrentLine: Boolean = true,
    readOnly: Boolean = false,
    searchQuery: String? = null,
    searchMatches: List<EditorRange> = emptyList(),
    currentSearchMatchIndex: Int = -1,
    tokenProvider: (Int) -> List<EditorToken> = { emptyList() },
    onTextChanged: () -> Unit = {},
    onCaretPositionChanged: (EditorPosition) -> Unit = {},
    onSelectionChanged: (EditorRange?) -> Unit = {}
) {
    // Create input handler
    val inputHandler = remember(state) {
        EditorInputHandler(
            state = state,
            onTextChanged = onTextChanged
        )
    }

    // Provide theme via CompositionLocal
    CompositionLocalProvider(LocalEditorTheme provides theme) {
        Box(
            modifier = modifier
                .onKeyEvent { event ->
                    if (!readOnly) {
                        inputHandler.handleKeyEvent(event)
                    } else {
                        // In read-only mode, still allow navigation but not editing
                        handleReadOnlyKeyEvent(state, event)
                    }
                }
        ) {
            EditorCanvas(
                editorState = state,
                modifier = Modifier.fillMaxSize(),
                fontFamily = fontFamily,
                fontSize = fontSize,
                showLineNumbers = showLineNumbers,
                highlightCurrentLine = highlightCurrentLine,
                searchQuery = searchQuery,
                searchMatches = searchMatches,
                currentSearchMatchIndex = currentSearchMatchIndex,
                getLineTokens = tokenProvider,
                onCaretPositionChanged = onCaretPositionChanged,
                onSelectionChanged = onSelectionChanged
            )
        }
    }
}

/**
 * Handles key events in read-only mode (navigation only).
 */
private fun handleReadOnlyKeyEvent(state: EditorState, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    val isShift = event.isShiftPressed
    val isCtrl = event.isCtrlPressed
    val isMeta = event.isMetaPressed
    val isCmdOrCtrl = isMeta || isCtrl

    return when {
        // Navigation
        event.key == Key.DirectionLeft -> {
            if (isCmdOrCtrl) {
                state.moveCaretToLineStart(isShift)
            } else {
                state.moveCaretBy(0, -1, isShift)
            }
            true
        }
        event.key == Key.DirectionRight -> {
            if (isCmdOrCtrl) {
                state.moveCaretToLineEnd(isShift)
            } else {
                state.moveCaretBy(0, 1, isShift)
            }
            true
        }
        event.key == Key.DirectionUp -> {
            if (isCmdOrCtrl) {
                state.moveCaretToStart(isShift)
            } else {
                state.moveCaretBy(-1, 0, isShift)
            }
            true
        }
        event.key == Key.DirectionDown -> {
            if (isCmdOrCtrl) {
                state.moveCaretToEnd(isShift)
            } else {
                state.moveCaretBy(1, 0, isShift)
            }
            true
        }
        event.key == Key.Home -> {
            if (isCmdOrCtrl) {
                state.moveCaretToStart(isShift)
            } else {
                state.moveCaretToLineStart(isShift)
            }
            true
        }
        event.key == Key.MoveEnd -> {
            if (isCmdOrCtrl) {
                state.moveCaretToEnd(isShift)
            } else {
                state.moveCaretToLineEnd(isShift)
            }
            true
        }

        // Select all (Cmd/Ctrl + A)
        isCmdOrCtrl && event.key == Key.A -> {
            state.selectAll()
            true
        }

        // Copy (Cmd/Ctrl + C) - allowed in read-only mode
        isCmdOrCtrl && event.key == Key.C -> {
            copyToClipboard(state)
            true
        }

        else -> false
    }
}

/**
 * Copies selected text to clipboard.
 */
private fun copyToClipboard(state: EditorState) {
    val text = state.selectedText
    if (text.isNotEmpty()) {
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
        } catch (e: Exception) {
            // Ignore clipboard errors
        }
    }
}

/**
 * Creates and remembers an EditorState.
 *
 * @param initialText The initial text content
 * @param filePath Optional file path for the document
 */
@Composable
fun rememberEditorState(
    initialText: String = "",
    filePath: String? = null
): EditorState {
    return remember(filePath) {
        EditorState(initialText, filePath)
    }
}
