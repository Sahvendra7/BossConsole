package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.run.DetectedMainFunction
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.run.Language
import ai.rever.boss.run.MainFunctionDetectorProvider
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

private val gutterLogger = BossLogger.forComponent("RSyntaxGutterOverlay")

/**
 * Data class to hold gutter state synchronized with RSyntaxTextArea.
 */
data class RSyntaxGutterState(
    val scrollOffset: Int = 0,
    val lineHeight: Int = 0,
    val totalLines: Int = 0,
    val firstVisibleLine: Int = 0,
    val visibleLineCount: Int = 0,
    val gutterWidth: Int = 0
)

/**
 * Composable overlay that displays run icons alongside RSyntaxTextArea.
 * Synchronizes scroll position with the Swing editor component.
 *
 * This overlay sits to the left of RSyntaxTextArea's built-in gutter (line numbers)
 * and shows run icons for lines containing main functions.
 *
 * @param textArea The RSyntaxTextArea to synchronize with
 * @param scrollPane The RTextScrollPane containing the text area
 * @param content The current file content (for main function detection)
 * @param filePath The path to the current file
 * @param projectPath The project root path
 * @param onRun Callback when a run icon is clicked
 * @param modifier Modifier for the overlay
 */
@Composable
fun RSyntaxGutterOverlay(
    textArea: RSyntaxTextArea,
    scrollPane: RTextScrollPane,
    content: String,
    filePath: String,
    projectPath: String,
    onRun: (DetectedMainFunction) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // State for detected main functions
    var detectedMainFunctions by remember { mutableStateOf<List<DetectedMainFunction>>(emptyList()) }

    // Gutter state synchronized with RSyntaxTextArea
    var gutterState by remember { mutableStateOf(RSyntaxGutterState()) }

    // Calculate line height in dp
    val lineHeightDp = remember(gutterState.lineHeight) {
        with(density) { gutterState.lineHeight.toDp() }
    }

    // Detect main functions when content changes
    LaunchedEffect(content, filePath) {
        if (filePath.isNotEmpty() && content.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val detector = MainFunctionDetectorProvider.get()
                    val langEnum = Language.fromFileName(filePath)
                    val detected = detector.detectInFile(filePath, content, langEnum)
                    withContext(Dispatchers.Main) {
                        detectedMainFunctions = detected
                    }
                } catch (e: Exception) {
                    gutterLogger.warn(LogCategory.EDITOR, "Error detecting main functions", error = e)
                    withContext(Dispatchers.Main) {
                        detectedMainFunctions = emptyList()
                    }
                }
            }
        } else {
            detectedMainFunctions = emptyList()
        }
    }

    // Set up scroll and document listeners
    DisposableEffect(textArea, scrollPane) {
        // Update gutter state from text area
        fun updateGutterState() {
            SwingUtilities.invokeLater {
                try {
                    val fontMetrics = textArea.getFontMetrics(textArea.font)
                    val lineHeight = fontMetrics.height
                    val viewRect = scrollPane.viewport.viewRect
                    val scrollY = viewRect.y
                    val viewHeight = viewRect.height

                    // Calculate visible lines
                    val firstVisible = if (lineHeight > 0) scrollY / lineHeight else 0
                    val visibleCount = if (lineHeight > 0) (viewHeight / lineHeight) + 2 else 0

                    // Get gutter width (line number column width)
                    val gutter = scrollPane.gutter
                    val gutterWidth = gutter?.preferredSize?.width ?: 0

                    scope.launch(Dispatchers.Main) {
                        gutterState = RSyntaxGutterState(
                            scrollOffset = scrollY,
                            lineHeight = lineHeight,
                            totalLines = textArea.lineCount,
                            firstVisibleLine = firstVisible,
                            visibleLineCount = visibleCount,
                            gutterWidth = gutterWidth
                        )
                    }
                } catch (e: Exception) {
                    gutterLogger.warn(LogCategory.EDITOR, "Error updating state", error = e)
                }
            }
        }

        // Initial state update
        updateGutterState()

        // Scroll listener for vertical scrollbar
        val scrollListener = AdjustmentListener { _: AdjustmentEvent ->
            updateGutterState()
        }
        scrollPane.verticalScrollBar?.addAdjustmentListener(scrollListener)

        // Document listener to update on content changes
        val documentListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateGutterState()
            override fun removeUpdate(e: DocumentEvent) = updateGutterState()
            override fun changedUpdate(e: DocumentEvent) = updateGutterState()
        }
        textArea.document.addDocumentListener(documentListener)

        onDispose {
            scrollPane.verticalScrollBar?.removeAdjustmentListener(scrollListener)
            textArea.document.removeDocumentListener(documentListener)
        }
    }

    // Only render if we have valid state
    if (gutterState.lineHeight > 0) {
        RunIconGutter(
            detectedMainFunctions = detectedMainFunctions,
            gutterState = gutterState,
            lineHeightDp = lineHeightDp,
            onRun = onRun,
            modifier = modifier
        )
    }
}

/**
 * Renders the run icon gutter with proper positioning.
 */
@Composable
private fun RunIconGutter(
    detectedMainFunctions: List<DetectedMainFunction>,
    gutterState: RSyntaxGutterState,
    lineHeightDp: Dp,
    onRun: (DetectedMainFunction) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Calculate scroll offset in dp
    val scrollOffsetDp = with(density) { gutterState.scrollOffset.toDp() }

    // Create a map of line numbers to detected functions for fast lookup
    val runnableLines = remember(detectedMainFunctions) {
        detectedMainFunctions.associateBy { it.lineNumber }
    }

    // Only show icons for visible lines (with some buffer)
    val visibleRange = remember(gutterState.firstVisibleLine, gutterState.visibleLineCount) {
        val start = (gutterState.firstVisibleLine - 2).coerceAtLeast(0)
        val end = (gutterState.firstVisibleLine + gutterState.visibleLineCount + 2)
            .coerceAtMost(gutterState.totalLines)
        start until end
    }

    Box(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
    ) {
        // Position each run icon at the correct line
        detectedMainFunctions
            .filter { it.lineNumber in visibleRange }
            .forEach { detected ->
                val lineY = (detected.lineNumber * gutterState.lineHeight) - gutterState.scrollOffset

                // Only render if visible
                if (lineY >= -gutterState.lineHeight && lineY < gutterState.scrollOffset + 2000) {
                    val yOffsetDp = with(density) { lineY.toDp() }

                    Box(
                        modifier = Modifier
                            .offset(y = yOffsetDp)
                            .height(lineHeightDp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        GutterRunIcon(
                            detected = detected,
                            onRun = onRun,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
    }
}

/**
 * Helper function to get line height from RSyntaxTextArea.
 */
fun RSyntaxTextArea.getLineHeightPx(): Int {
    val fontMetrics = getFontMetrics(font)
    return fontMetrics.height
}

/**
 * Helper function to get scroll offset from RTextScrollPane.
 */
fun RTextScrollPane.getVerticalScrollOffset(): Int {
    return viewport.viewRect.y
}
