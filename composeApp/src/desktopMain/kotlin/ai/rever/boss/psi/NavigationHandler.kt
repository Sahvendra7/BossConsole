package ai.rever.boss.psi

import kotlinx.coroutines.*
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.jetbrains.kotlin.psi.KtFile
import java.awt.event.*

/**
 * Callback for navigation events.
 *
 * @property filePath Path to the target file
 * @property offset Character offset in the target file
 * @property line Line number (1-based)
 * @property column Column number (1-based)
 */
data class NavigationEvent(
    val filePath: String,
    val offset: Int,
    val line: Int,
    val column: Int
)

/**
 * Callback types for navigation.
 */
typealias NavigationCallback = (NavigationEvent) -> Unit
typealias NavigableCheckCallback = (isNavigable: Boolean) -> Unit

/**
 * Handles navigation interactions for RSyntaxTextArea.
 *
 * This handler provides:
 * - Cmd+Click for go-to-definition
 * - Cmd+hover for highlighting navigable symbols
 * - Integration with PSI navigation service
 *
 * Currently supports Kotlin files only (using kotlin-compiler-embeddable).
 * Java file support can be added later with additional dependencies.
 *
 * @property textArea The RSyntaxTextArea to add navigation to
 * @property currentFilePath Path of the current file being edited
 * @property onNavigate Callback when navigation is triggered
 */
class NavigationHandler(
    private val textArea: RSyntaxTextArea,
    private var currentFilePath: String,
    private val onNavigate: NavigationCallback
) {
    /**
     * Navigation service for PSI operations.
     */
    private val navigationService = NavigationService()

    /**
     * Highlighter for visual feedback.
     */
    private val highlighter = NavigationHighlighter(textArea)

    /**
     * Coroutine scope for async operations.
     */
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Current PSI file (cached for performance).
     * Currently only KtFile is supported.
     * Marked volatile for thread-safe access from coroutines.
     */
    @Volatile
    private var currentPsiFile: KtFile? = null

    /**
     * Job for debounced hover updates.
     */
    private var hoverJob: Job? = null

    /**
     * Whether Cmd key is currently pressed.
     */
    private var isCmdPressed = false

    /**
     * Mouse listeners.
     */
    private val mouseListener: MouseListener
    private val mouseMotionListener: MouseMotionListener
    private val keyListener: KeyListener

    init {
        // Ensure the initial file's project is indexed for cross-file navigation
        if (currentFilePath.isNotEmpty()) {
            ProjectIndexer.current?.ensureFileProjectIndexed(currentFilePath)
        }

        // Create mouse listener for Cmd+Click
        mouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (isCmdModifier(e) && e.clickCount == 1 && e.button == MouseEvent.BUTTON1) {
                    handleNavigation(e)
                }
            }
        }

        // Create mouse motion listener for Cmd+hover
        mouseMotionListener = object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                if (isCmdModifier(e)) {
                    handleHover(e)
                } else if (highlighter.hasHighlight()) {
                    highlighter.clearHighlight()
                }
            }
        }

        // Create key listener for Cmd key state
        keyListener = object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (isCmdKey(e)) {
                    isCmdPressed = true
                }
            }

            override fun keyReleased(e: KeyEvent) {
                if (isCmdKey(e)) {
                    isCmdPressed = false
                    highlighter.clearHighlight()
                }
            }
        }

        // Add listeners
        textArea.addMouseListener(mouseListener)
        textArea.addMouseMotionListener(mouseMotionListener)
        textArea.addKeyListener(keyListener)
    }

    /**
     * Update the current file path (when switching files).
     */
    fun updateFilePath(filePath: String) {
        if (currentFilePath != filePath) {
            currentFilePath = filePath
            currentPsiFile = null // Force re-parse
            highlighter.clearHighlight()

            // Ensure the file's project is indexed for cross-file navigation
            ProjectIndexer.current?.ensureFileProjectIndexed(filePath)
        }
    }

    /**
     * Update the content (when file content changes).
     */
    fun updateContent(content: String) {
        // Invalidate cached PSI file
        currentPsiFile = null
    }

    /**
     * Handle navigation on Cmd+Click.
     */
    private fun handleNavigation(e: MouseEvent) {
        val offset = textArea.viewToModel2D(e.point).toInt()
        if (offset < 0) return

        println("[Navigation] Cmd+Click at offset $offset in file: $currentFilePath")

        scope.launch {
            try {
                val psiFile = getPsiFile() ?: run {
                    println("[Navigation] Failed to get PSI file")
                    return@launch
                }

                val result = PSIThreadBridge.readAction {
                    navigationService.goToDefinition(psiFile, offset, currentFilePath)
                }

                when (result) {
                    is NavigationResult.Found -> {
                        val target = result.target
                        println("[Navigation] Found: ${target.name} (${target.kind}) at ${target.filePath}:${target.line}:${target.column}")
                        onNavigate(NavigationEvent(
                            filePath = target.filePath,
                            offset = target.offset,
                            line = target.line,
                            column = target.column
                        ))
                    }
                    is NavigationResult.MultipleTargets -> {
                        println("[Navigation] Multiple targets found: ${result.targets.size}")
                        // For now, navigate to first target
                        result.targets.firstOrNull()?.let { target ->
                            println("[Navigation] Using first: ${target.name} at ${target.filePath}:${target.line}")
                            onNavigate(NavigationEvent(
                                filePath = target.filePath,
                                offset = target.offset,
                                line = target.line,
                                column = target.column
                            ))
                        }
                    }
                    is NavigationResult.NotNavigable -> {
                        println("[Navigation] Not navigable (e.g., keyword, literal)")
                    }
                    is NavigationResult.Error -> {
                        println("[Navigation] Error: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                println("[Navigation] Exception: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Handle hover for highlighting.
     */
    private fun handleHover(e: MouseEvent) {
        val offset = textArea.viewToModel2D(e.point).toInt()
        if (offset < 0) {
            highlighter.clearHighlight()
            return
        }

        // Debounce hover updates
        hoverJob?.cancel()
        hoverJob = scope.launch {
            delay(50) // Small delay to avoid excessive updates

            // Check if cancelled during delay
            if (!isActive) return@launch

            try {
                // Add timeout for large files to prevent UI freeze
                val result = withTimeoutOrNull(500) {
                    val psiFile = getPsiFile() ?: return@withTimeoutOrNull null

                    PSIThreadBridge.readAction {
                        val navigable = navigationService.isNavigable(psiFile, offset)
                        val range = if (navigable) {
                            navigationService.getNavigableRange(psiFile, offset)
                        } else null
                        println("[Navigation] hover: navigable=$navigable, range=$range")
                        if (navigable && range != null) range else null
                    }
                }

                println("[Navigation] hover result: $result")
                if (result != null) {
                    highlighter.highlightRange(result.first, result.second)
                } else {
                    highlighter.clearHighlight()
                }

            } catch (e: Exception) {
                println("[Navigation] hover exception: ${e.message}")
                highlighter.clearHighlight()
            }
        }
    }

    /**
     * Get the PSI file for the current content.
     * Currently only supports Kotlin files.
     */
    private suspend fun getPsiFile(): KtFile? {
        // Return cached file if available
        currentPsiFile?.let { return it }

        // Check if PSI is initialized
        if (!PSIBootstrap.isInitialized) {
            return null
        }

        // Only support Kotlin files for now
        if (!currentFilePath.endsWith(".kt") && !currentFilePath.endsWith(".kts")) {
            return null
        }

        // Parse the current file
        return try {
            val content = textArea.text ?: return null
            val fileName = currentFilePath.substringAfterLast('/')

            PSIThreadBridge.readAction {
                PSIBootstrap.parseKotlinFile(fileName, content)
            }.also { currentPsiFile = it }

        } catch (e: Exception) {
            println("[Navigation] Error parsing file: ${e.message}")
            null
        }
    }

    /**
     * Check if the Cmd/Ctrl modifier is pressed.
     */
    private fun isCmdModifier(e: MouseEvent): Boolean {
        return if (System.getProperty("os.name").lowercase().contains("mac")) {
            e.isMetaDown
        } else {
            e.isControlDown
        }
    }

    /**
     * Check if this is the Cmd/Ctrl key.
     */
    private fun isCmdKey(e: KeyEvent): Boolean {
        return if (System.getProperty("os.name").lowercase().contains("mac")) {
            e.keyCode == KeyEvent.VK_META
        } else {
            e.keyCode == KeyEvent.VK_CONTROL
        }
    }

    /**
     * Dispose of resources.
     */
    fun dispose() {
        textArea.removeMouseListener(mouseListener)
        textArea.removeMouseMotionListener(mouseMotionListener)
        textArea.removeKeyListener(keyListener)

        hoverJob?.cancel()
        scope.cancel()
        highlighter.dispose()
    }
}
