package ai.rever.bosseditor.refactoring

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.UndoManager
import ai.rever.bosseditor.logging.EditorLogger
import ai.rever.bosseditor.logging.EditorLogCategory
import ai.rever.bosseditor.lsp.protocol.Position
import ai.rever.bosseditor.lsp.protocol.Range
import ai.rever.bosseditor.lsp.protocol.TextEdit
import ai.rever.bosseditor.lsp.protocol.WorkspaceEdit
import java.io.File
import java.net.URI
import kotlin.text.Charsets

/**
 * Applies WorkspaceEdit changes to documents.
 *
 * This class handles:
 * - Applying text edits to open documents
 * - Loading and saving files for closed documents
 * - Grouping all changes in a single undo operation
 * - Converting between LSP positions and document offsets
 *
 * @property documentProvider Function to get an open document by URI, or null if not open
 * @property undoManagerProvider Function to get the undo manager for a document
 * @property onFileModified Callback when a file is modified (for triggering refresh)
 */
class WorkspaceEditApplier(
    private val documentProvider: (String) -> EditorDocument?,
    private val undoManagerProvider: (String) -> UndoManager?,
    private val onFileModified: ((String) -> Unit)? = null
) {
    private val logger = EditorLogger.forComponent("WorkspaceEditApplier")

    /**
     * Applies a workspace edit to all affected files.
     *
     * @param edit The workspace edit to apply
     * @return true if all changes were applied successfully
     */
    fun apply(edit: WorkspaceEdit): Boolean {
        val changes = edit.changes ?: return true

        if (changes.isEmpty()) {
            logger.debug(EditorLogCategory.EDITOR, "No changes to apply")
            return true
        }

        logger.info(EditorLogCategory.EDITOR, "Applying workspace edit", mapOf(
            "fileCount" to changes.size.toString()
        ))

        var success = true

        // Group all changes by collecting undo managers
        val undoManagers = mutableSetOf<UndoManager>()

        try {
            // Begin compound edit on all affected undo managers
            for ((uri, _) in changes) {
                undoManagerProvider(uri)?.let { undoManager ->
                    undoManager.beginCompoundEdit()
                    undoManagers.add(undoManager)
                }
            }

            // Apply changes to each file
            for ((uri, edits) in changes) {
                if (!applyFileEdits(uri, edits)) {
                    success = false
                }
            }
        } finally {
            // End compound edit on all undo managers
            for (undoManager in undoManagers) {
                undoManager.endCompoundEdit()
            }
        }

        return success
    }

    /**
     * Applies edits to a single file.
     *
     * @param uri The file URI
     * @param edits The edits to apply
     * @return true if all edits were applied successfully
     */
    private fun applyFileEdits(uri: String, edits: List<TextEdit>): Boolean {
        if (edits.isEmpty()) return true

        // Try to get an open document first
        val document = documentProvider(uri)

        return if (document != null) {
            applyToDocument(document, edits, uri)
        } else {
            applyToFile(uri, edits)
        }
    }

    /**
     * Applies edits to an open document.
     */
    private fun applyToDocument(document: EditorDocument, edits: List<TextEdit>, uri: String): Boolean {
        return try {
            // Validate that edits don't overlap
            if (!PositionUtils.validateNoOverlap(edits)) {
                logger.error(EditorLogCategory.EDITOR, "Overlapping edits detected", mapOf(
                    "uri" to uri,
                    "editCount" to edits.size.toString()
                ))
                return false
            }

            // Sort edits in reverse order by position to preserve offsets
            val sortedEdits = edits.sortedWith(
                compareByDescending<TextEdit> { it.range.start.line }
                    .thenByDescending { it.range.start.character }
            )

            for (edit in sortedEdits) {
                val startOffset = positionToOffset(document, edit.range.start)
                val endOffset = positionToOffset(document, edit.range.end)

                document.replace(startOffset, endOffset, edit.newText)
            }

            onFileModified?.invoke(uri)

            logger.debug(EditorLogCategory.EDITOR, "Applied edits to document", mapOf(
                "uri" to uri,
                "editCount" to edits.size.toString()
            ))
            true
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error applying edits to document", mapOf(
                "uri" to uri
            ), e)
            false
        }
    }

    /**
     * Applies edits to a file on disk (for files not currently open).
     */
    private fun applyToFile(uri: String, edits: List<TextEdit>): Boolean {
        return try {
            val file = uriToFile(uri)
            if (!file.exists()) {
                logger.error(EditorLogCategory.EDITOR, "File not found", mapOf("uri" to uri))
                return false
            }

            // Validate that edits don't overlap
            if (!PositionUtils.validateNoOverlap(edits)) {
                logger.error(EditorLogCategory.EDITOR, "Overlapping edits detected", mapOf(
                    "uri" to uri,
                    "editCount" to edits.size.toString()
                ))
                return false
            }

            // Read the file content with explicit UTF-8 encoding
            val content = file.readText(Charsets.UTF_8)

            // Apply edits to the content
            val newContent = applyEditsToText(content, edits)

            // Write back with explicit UTF-8 encoding
            file.writeText(newContent, Charsets.UTF_8)

            onFileModified?.invoke(uri)

            logger.debug(EditorLogCategory.EDITOR, "Applied edits to file", mapOf(
                "uri" to uri,
                "editCount" to edits.size.toString()
            ))
            true
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error applying edits to file", mapOf(
                "uri" to uri
            ), e)
            false
        }
    }

    /**
     * Applies edits to text content.
     */
    private fun applyEditsToText(content: String, edits: List<TextEdit>): String {
        // Convert content to a mutable structure
        val result = StringBuilder(content)

        // Sort edits in reverse order to preserve offsets
        val sortedEdits = edits.sortedWith(
            compareByDescending<TextEdit> { it.range.start.line }
                .thenByDescending { it.range.start.character }
        )

        for (edit in sortedEdits) {
            val startOffset = PositionUtils.lspPositionToOffset(content, edit.range.start)
            val endOffset = PositionUtils.lspPositionToOffset(content, edit.range.end)

            result.replace(startOffset, endOffset, edit.newText)
        }

        return result.toString()
    }

    /**
     * Converts an LSP Position to a document offset.
     */
    private fun positionToOffset(document: EditorDocument, position: Position): Int {
        return document.positionToOffset(position.line, position.character)
    }

    /**
     * Converts a URI to a File.
     *
     * This method handles various URI formats and canonicalizes the path
     * to resolve symlinks and normalize the path.
     */
    private fun uriToFile(uri: String): File {
        val file = when {
            uri.startsWith("file://") -> File(URI.create(uri))
            uri.startsWith("file:/") -> File(URI.create(uri))
            else -> File(uri)
        }
        // Canonicalize to resolve symlinks and normalize path
        return file.canonicalFile
    }

    companion object {
        /**
         * Converts a Range to document offsets.
         *
         * @param document The document
         * @param range The LSP range
         * @return Pair of (startOffset, endOffset)
         */
        fun rangeToOffsets(document: EditorDocument, range: Range): Pair<Int, Int> {
            val startOffset = document.positionToOffset(range.start.line, range.start.character)
            val endOffset = document.positionToOffset(range.end.line, range.end.character)
            return startOffset to endOffset
        }

        /**
         * Creates a WorkspaceEdit for a single file with multiple edits.
         *
         * @param uri The file URI
         * @param edits The text edits
         * @return A WorkspaceEdit
         */
        fun createEdit(uri: String, edits: List<TextEdit>): WorkspaceEdit {
            return WorkspaceEdit(changes = mapOf(uri to edits))
        }

        /**
         * Creates a WorkspaceEdit for renaming text at multiple ranges.
         *
         * @param fileRanges Map of file URI to list of ranges to replace
         * @param newName The new text to insert
         * @return A WorkspaceEdit
         */
        fun createRenameEdit(fileRanges: Map<String, List<Range>>, newName: String): WorkspaceEdit {
            val changes = fileRanges.mapValues { (_, ranges) ->
                ranges.map { range -> TextEdit(range = range, newText = newName) }
            }
            return WorkspaceEdit(changes = changes)
        }

        /**
         * Converts a file path to a URI string.
         *
         * @param filePath The file path
         * @return The URI string
         */
        fun filePathToUri(filePath: String): String {
            return File(filePath).toURI().toString()
        }

        /**
         * Converts a URI string to a file path.
         *
         * @param uri The URI string
         * @return The file path
         */
        fun uriToFilePath(uri: String): String {
            return when {
                uri.startsWith("file://") -> File(URI.create(uri)).absolutePath
                uri.startsWith("file:/") -> File(URI.create(uri)).absolutePath
                else -> uri
            }
        }
    }
}
