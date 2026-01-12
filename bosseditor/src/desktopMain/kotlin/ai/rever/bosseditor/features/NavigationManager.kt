package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.psi.DefinitionInfo
import ai.rever.bosseditor.psi.NavigationResult
import ai.rever.bosseditor.psi.NavigationService
import ai.rever.bosseditor.psi.PSIBootstrap
import ai.rever.bosseditor.psi.PSIThreadBridge
import ai.rever.bosseditor.psi.ProjectIndexer
import ai.rever.bosseditor.psi.ReferenceLocation
import ai.rever.bosseditor.psi.ReferenceService
import ai.rever.bosseditor.psi.SemanticHighlighter
import ai.rever.bosseditor.utils.extractFileName
import kotlinx.coroutines.*
import org.jetbrains.kotlin.psi.KtFile

/**
 * Result of a navigation request.
 */
sealed class NavigationOutcome {
    /**
     * Navigation found a target.
     * @param filePath Absolute path to the target file
     * @param line Target line number (1-based, matches NavigationTarget)
     * @param column Target column number (1-based, matches NavigationTarget)
     */
    data class Found(
        val filePath: String,
        val line: Int,
        val column: Int
    ) : NavigationOutcome()

    /**
     * User clicked on a definition - show all usages.
     * @param references List of places where this symbol is used
     * @param definition Information about the definition
     */
    data class ShowUsages(
        val references: List<ReferenceLocation>,
        val definition: DefinitionInfo
    ) : NavigationOutcome()

    /**
     * No navigation target found at this position.
     */
    data object NotFound : NavigationOutcome()

    /**
     * Navigation is not available (PSI not initialized, non-Kotlin file, etc.)
     */
    data object Unavailable : NavigationOutcome()
}

/**
 * Manages code navigation for BossEditor.
 *
 * This class wraps the PSI-based NavigationService and provides
 * a high-level interface for the editor to use for Cmd+Click navigation.
 *
 * Usage:
 * 1. Create NavigationManager with the document and file path
 * 2. Optionally set projectPath to enable cross-file navigation
 * 3. Call resolveNavigation() when user Cmd+Clicks
 * 4. Call dispose() when the editor is closed
 *
 * @param document The editor document
 * @param filePath Path to the current file (for PSI tracking)
 */
class NavigationManager(
    private val document: EditorDocument,
    private var filePath: String?
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val navigationService = NavigationService()
    private val referenceService = ReferenceService()
    private var semanticHighlighter: SemanticHighlighter? = null
    private var projectPath: String? = null

    /**
     * Callback for when semantic analysis is complete.
     * This can be used to trigger a repaint to show updated highlighting.
     */
    var onSemanticAnalysisComplete: (() -> Unit)? = null

    init {
        // Initialize semantic highlighter for Kotlin files
        initializeHighlighter()
    }

    private fun initializeHighlighter() {
        val path = filePath ?: return
        if (path.endsWith(".kt") || path.endsWith(".kts")) {
            semanticHighlighter = SemanticHighlighter(document, path).also {
                it.onAnalysisComplete = {
                    onSemanticAnalysisComplete?.invoke()
                }
            }
        }
    }

    /**
     * Sets the project path for cross-file navigation.
     * This enables indexing of the entire project for go-to-definition.
     *
     * @param path Absolute path to the project root
     */
    fun setProjectPath(path: String?) {
        this.projectPath = path

        // Start project indexing if PSI is available
        if (path != null) {
            scope.launch {
                ensurePSIInitialized()
                if (PSIBootstrap.isInitialized) {
                    // Initialize ProjectIndexer for the project
                    val indexer = ProjectIndexer.initialize(path)

                    // Start project source indexing
                    indexer.startIndexing()

                    // Also index library sources for cross-file navigation to Compose, stdlib, etc.
                    if (!indexer.isLibraryIndexingComplete) {
                        indexer.indexLibrarySources()
                    }
                }
            }
        }
    }

    /**
     * Ensures PSI infrastructure is initialized.
     * This is called lazily when navigation is first needed.
     */
    private fun ensurePSIInitialized() {
        if (!PSIBootstrap.isInitialized) {
            try {
                PSIBootstrap.initialize()
                println("[NavigationManager] PSI initialized successfully")
            } catch (e: Exception) {
                println("[NavigationManager] Failed to initialize PSI: ${e.message}")
            }
        }
    }

    /**
     * Updates the file path for the current document.
     * Call this when the file is saved with a new name.
     *
     * @param newFilePath The new file path
     */
    fun updateFilePath(newFilePath: String) {
        val oldPath = filePath
        filePath = newFilePath

        // Dispose old highlighter and create new one if file type changed
        val wasKotlin = oldPath?.let { it.endsWith(".kt") || it.endsWith(".kts") } ?: false
        val isKotlin = newFilePath.endsWith(".kt") || newFilePath.endsWith(".kts")

        if (wasKotlin != isKotlin || oldPath != newFilePath) {
            semanticHighlighter?.dispose()
            semanticHighlighter = null
            initializeHighlighter()
        } else {
            semanticHighlighter?.setFilePath(newFilePath)
        }
    }

    /**
     * Triggers semantic analysis after content changes.
     * This is debounced internally to avoid excessive re-analysis.
     */
    fun analyzeContent() {
        semanticHighlighter?.analyzeAndHighlight()
    }

    /**
     * Parses the document content into a KtFile for PSI operations.
     */
    private suspend fun parseKotlinFile(): KtFile? {
        val path = filePath ?: return null
        val content = document.getText()
        val fileName = path.extractFileName()

        return try {
            PSIThreadBridge.readAction {
                PSIBootstrap.parseKotlinFile(fileName, content)
            }
        } catch (e: Exception) {
            println("[NavigationManager] Error parsing file: ${e.message}")
            null
        }
    }

    /**
     * Resolves navigation for a position in the document.
     *
     * If clicking on a definition (class, function, property), returns ShowUsages
     * with all references to that symbol.
     * If clicking on a reference, returns Found with the definition location.
     *
     * @param position The editor position where user Cmd+Clicked
     * @return NavigationOutcome indicating where to navigate or why navigation failed
     */
    suspend fun resolveNavigation(position: EditorPosition): NavigationOutcome {
        val path = filePath ?: return NavigationOutcome.Unavailable

        // Only support Kotlin files for now
        if (!path.endsWith(".kt") && !path.endsWith(".kts")) {
            return NavigationOutcome.Unavailable
        }

        // Ensure PSI is initialized
        ensurePSIInitialized()
        if (!PSIBootstrap.isInitialized) {
            return NavigationOutcome.Unavailable
        }

        // Ensure the current file's project is indexed for cross-file navigation
        // This handles the case where user opens a file from a different project
        ProjectIndexer.current?.let { indexer ->
            val indexingJob = indexer.ensureFileProjectIndexed(path)
            indexingJob?.join() // Wait for indexing to complete
        }

        // Parse the file
        val ktFile = parseKotlinFile() ?: return NavigationOutcome.Unavailable

        // Convert position to offset
        val offset = document.positionToOffset(position)

        // First check if the clicked element is a definition
        val isDefinition = PSIThreadBridge.readAction {
            navigationService.isDefinition(ktFile, offset)
        }

        val definitionInfo = if (isDefinition) {
            PSIThreadBridge.readAction {
                navigationService.getDefinitionInfo(ktFile, offset, path)
            }
        } else {
            null
        }

        // If it's a definition, find all references
        if (definitionInfo != null) {
            val references = referenceService.findReferences(definitionInfo)
            return NavigationOutcome.ShowUsages(references, definitionInfo)
        }

        // Otherwise, resolve as a reference to go to definition
        val result = PSIThreadBridge.readAction {
            navigationService.goToDefinition(ktFile, offset, path)
        }

        return when (result) {
            is NavigationResult.Found -> {
                NavigationOutcome.Found(
                    filePath = result.target.filePath,
                    line = result.target.line,
                    column = result.target.column
                )
            }
            is NavigationResult.MultipleTargets -> {
                // Use first target for now
                result.targets.firstOrNull()?.let { target ->
                    NavigationOutcome.Found(
                        filePath = target.filePath,
                        line = target.line,
                        column = target.column
                    )
                } ?: NavigationOutcome.NotFound
            }
            is NavigationResult.NotNavigable -> NavigationOutcome.NotFound
            is NavigationResult.Error -> NavigationOutcome.Unavailable
        }
    }

    /**
     * Checks if a position is navigable (for hover highlighting).
     *
     * @param position The editor position to check
     * @return True if the position has a navigation target
     */
    suspend fun isNavigable(position: EditorPosition): Boolean {
        val path = filePath ?: return false

        if (!path.endsWith(".kt") && !path.endsWith(".kts")) {
            return false
        }

        ensurePSIInitialized()
        if (!PSIBootstrap.isInitialized) {
            return false
        }

        val ktFile = parseKotlinFile() ?: return false
        val offset = document.positionToOffset(position)

        return PSIThreadBridge.readAction {
            navigationService.isNavigable(ktFile, offset)
        }
    }

    /**
     * Gets the range of the navigable symbol at a position (for underline highlighting).
     *
     * @param position The editor position
     * @return EditorRange of the navigable symbol, or null if not navigable
     */
    suspend fun getNavigableRange(position: EditorPosition): EditorRange? {
        val path = filePath ?: return null

        if (!path.endsWith(".kt") && !path.endsWith(".kts")) {
            return null
        }

        ensurePSIInitialized()
        if (!PSIBootstrap.isInitialized) {
            return null
        }

        val ktFile = parseKotlinFile() ?: return null
        val offset = document.positionToOffset(position)

        val range = PSIThreadBridge.readAction {
            navigationService.getNavigableRange(ktFile, offset)
        } ?: return null

        // Convert offsets to positions (range is Pair<Int, Int>)
        val startPos = document.offsetToPosition(range.first)
        val endPos = document.offsetToPosition(range.second)

        return EditorRange(startPos, endPos)
    }

    /**
     * Disposes resources used by this manager.
     * Call when the editor is closed.
     */
    fun dispose() {
        semanticHighlighter?.dispose()
        semanticHighlighter = null
        scope.cancel()
    }
}
