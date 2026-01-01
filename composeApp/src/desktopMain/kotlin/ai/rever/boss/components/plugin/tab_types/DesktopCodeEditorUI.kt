package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.keymap.model.KeymapActions
import ai.rever.boss.psi.NavigationEvent
import ai.rever.boss.run.DetectedMainFunction
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Desktop-specific Code Editor UI using RSyntaxTextArea.
 *
 * This composable provides a full-featured code editor with:
 * - Syntax highlighting (50+ languages via RSyntaxTextArea)
 * - Code folding
 * - Bracket matching
 * - Line numbers with fold indicators
 * - Run gutter icons for detected main functions
 * - File modification tracking with save support (Cmd+S)
 * - Theme integration with BOSS themes
 *
 * @param content The file content to display
 * @param onContentChange Callback when content changes
 * @param language The programming language for syntax highlighting
 * @param filePath The path to the file being edited
 * @param projectPath The project root path
 * @param modifier Modifier for the editor
 * @param onModifiedStateChange Callback when modification state changes
 * @param onSaveRequested Callback when save is requested (Cmd+S or via FileSaveEventBus)
 */
@Composable
fun DesktopCodeEditorUI(
    content: String,
    onContentChange: (String) -> Unit,
    language: String = "kotlin",
    filePath: String = "",
    projectPath: String = "",
    modifier: Modifier = Modifier,
    onModifiedStateChange: (Boolean) -> Unit = {},
    onSaveRequested: suspend () -> Boolean = { false }
) {
    val scope = rememberCoroutineScope()

    // Track cursor position for status bar
    var cursorLine by remember { mutableStateOf(1) }
    var cursorColumn by remember { mutableStateOf(1) }

    // Track modification state
    var isModified by remember { mutableStateOf(false) }

    // Track if save is in progress
    var isSaving by remember { mutableStateOf(false) }

    // Track last save error
    var saveError by remember { mutableStateOf<String?>(null) }

    // Listen for save requests from FileSaveEventBus
    // Use filePath as key so collector restarts on file change and cancels when component unmounts
    LaunchedEffect(filePath) {
        if (filePath.isNotEmpty()) {
            FileSaveEventBus.saveRequests.collectLatest { request ->
                if (request != null) {
                    isSaving = true
                    saveError = null
                    try {
                        val success = onSaveRequested()
                        if (!success) {
                            saveError = "Failed to save file"
                        }
                    } finally {
                        isSaving = false
                        FileSaveEventBus.clearRequest()
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Main editor area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (CodeEditorSettings.useNativeEditor) {
                // Use native BossEditor (Compose Canvas-based)
                BossEditorIntegration(
                    content = content,
                    onContentChange = { newContent ->
                        onContentChange(newContent)
                    },
                    language = language,
                    filePath = filePath,
                    projectPath = projectPath,
                    modifier = Modifier.fillMaxSize(),
                    isReadOnly = false,
                    onCursorPositionChange = { line, column ->
                        cursorLine = line
                        cursorColumn = column
                    },
                    onModifiedStateChange = { modified ->
                        isModified = modified
                        onModifiedStateChange(modified)
                    },
                    onRun = { detected ->
                        scope.launch {
                            executeDetectedMainFunction(detected, projectPath)
                        }
                    },
                    onNavigate = { event ->
                        if (event.filePath.isNotEmpty()) {
                            scope.launch {
                                FileEventBus.openFile(event.filePath, event.line, event.column)
                            }
                        }
                    }
                )
            } else {
                // Use RSyntaxTextArea (Swing-based) - default for maximum compatibility
                RSyntaxEditorWithGutter(
                    content = content,
                    onContentChange = { newContent ->
                        onContentChange(newContent)
                    },
                    language = language,
                    filePath = filePath,
                    projectPath = projectPath,
                    modifier = Modifier.fillMaxSize(),
                    isReadOnly = false,
                    fontSize = CodeEditorSettings.fontSize,
                    fontFamily = CodeEditorSettings.fontFamily,
                    theme = CodeEditorSettings.theme,
                    onCursorPositionChange = { line, column ->
                        cursorLine = line
                        cursorColumn = column
                    },
                    onModifiedStateChange = { modified ->
                        isModified = modified
                        onModifiedStateChange(modified)
                    },
                    onRun = { detected ->
                        scope.launch {
                            executeDetectedMainFunction(detected, projectPath)
                        }
                    },
                    onNavigate = { event ->
                        if (event.filePath.isNotEmpty()) {
                            scope.launch {
                                // Open the target file at the specified position
                                // For same-file navigation, FileEventBus handler will scroll to position
                                // For cross-file, it opens the file and navigates
                                FileEventBus.openFile(event.filePath, event.line, event.column)
                            }
                        }
                    }
                )
            }
        }

        // Status bar
        EditorStatusBar(
            filePath = filePath,
            language = language,
            line = cursorLine,
            column = cursorColumn,
            isModified = isModified,
            isSaving = isSaving,
            error = saveError
        )
    }
}

/**
 * Status bar for the editor showing file info, cursor position, and save status.
 */
@Composable
private fun EditorStatusBar(
    filePath: String,
    language: String,
    line: Int,
    column: Int,
    isModified: Boolean,
    isSaving: Boolean,
    error: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(Color(0xFF_007ACC).copy(alpha = 0.8f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: File info
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File name with modification indicator
            val fileName = filePath.substringAfterLast('/').ifEmpty { "Untitled" }
            Text(
                text = if (isModified) "$fileName *" else fileName,
                color = Color.White,
                fontSize = 12.sp
            )

            // Language
            Text(
                text = language.uppercase(),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
        }

        // Right: Cursor position and status
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Error message
            if (error != null) {
                Text(
                    text = error,
                    color = Color(0xFF_FF6B6B),
                    fontSize = 11.sp
                )
            }

            // Saving indicator
            if (isSaving) {
                Text(
                    text = "Saving...",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }

            // Cursor position
            Text(
                text = "Ln $line, Col $column",
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Creates a DesktopCodeEditorUI with integrated FileModificationTracker.
 *
 * This is the recommended way to use the desktop code editor when you need
 * full file modification tracking and save support.
 */
@Composable
fun DesktopCodeEditorWithFileTracking(
    filePath: String,
    projectPath: String = "",
    modifier: Modifier = Modifier,
    onModifiedStateChange: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    // Load initial content
    var content by remember(filePath) {
        mutableStateOf(readFileContent(filePath) ?: "")
    }

    // Track the file modification tracker
    val tracker = remember(filePath, content) {
        FileModificationTracker(filePath, content, scope)
    }

    // Determine language from file path
    val language = remember(filePath) {
        getLanguageFromFilePath(filePath)
    }

    // Observe tracker state
    val trackerState by tracker.state.collectAsState()

    // Update modified state callback
    LaunchedEffect(trackerState.isModified) {
        onModifiedStateChange(trackerState.isModified)
    }

    DesktopCodeEditorUI(
        content = content,
        onContentChange = { newContent ->
            content = newContent
            tracker.updateContent(newContent)
        },
        language = language,
        filePath = filePath,
        projectPath = projectPath,
        modifier = modifier,
        onModifiedStateChange = onModifiedStateChange,
        onSaveRequested = {
            tracker.save()
        }
    )
}
