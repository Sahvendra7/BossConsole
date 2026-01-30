package ai.rever.bosseditor.refactoring.psi

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.logging.EditorLogger
import ai.rever.bosseditor.logging.EditorLogCategory
import ai.rever.bosseditor.lsp.protocol.Position
import ai.rever.bosseditor.lsp.protocol.Range
import ai.rever.bosseditor.lsp.protocol.TextEdit
import ai.rever.bosseditor.lsp.protocol.WorkspaceEdit
import ai.rever.bosseditor.psi.DefinitionInfo
import ai.rever.bosseditor.psi.NavigationService
import ai.rever.bosseditor.psi.PSIBootstrap
import ai.rever.bosseditor.psi.PSIThreadBridge
import ai.rever.bosseditor.psi.ReferenceLocation
import ai.rever.bosseditor.psi.ReferenceService
import ai.rever.bosseditor.refactoring.FileChange
import ai.rever.bosseditor.refactoring.PositionUtils
import ai.rever.bosseditor.refactoring.RefactorContext
import ai.rever.bosseditor.refactoring.RefactorResult
import ai.rever.bosseditor.refactoring.WorkspaceEditApplier
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import java.io.File

/**
 * Handles rename refactoring for Kotlin files.
 *
 * This class provides:
 * - Finding the symbol at cursor position
 * - Finding all references to the symbol across the project
 * - Validating new names
 * - Generating a WorkspaceEdit for all changes
 *
 * @property navigationService Service for navigating Kotlin code
 */
class RenameRefactoring(
    private val navigationService: NavigationService
) {
    private val logger = EditorLogger.forComponent("RenameRefactoring")
    private val referenceService = ReferenceService.instance

    /**
     * Executes the rename refactoring.
     *
     * @param context The refactoring context
     * @param newName The new name for the symbol
     * @return The result of the refactoring
     */
    suspend fun execute(context: RefactorContext, newName: String): RefactorResult {
        // Validate the new name first
        val validationError = validateNewName(newName, context)
        if (validationError != null) {
            return RefactorResult.Error(validationError, recoverable = true)
        }

        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return RefactorResult.Error("File not found: ${context.filePath}")
            }

            val content = file.readText(Charsets.UTF_8)
            val offset = PositionUtils.positionToOffset(content, context.position)

            // Get the definition info for the symbol
            val definitionInfo = getDefinitionInfoAtOffset(context.filePath, content, offset)
            if (definitionInfo == null) {
                return RefactorResult.Error("No symbol found at cursor position")
            }

            // Find all references to the symbol
            logger.info(EditorLogCategory.EDITOR, "Finding references for rename", mapOf(
                "symbol" to definitionInfo.name,
                "file" to context.filePath
            ))

            val references = referenceService.findReferences(definitionInfo) { searched, total, fileName ->
                logger.debug(EditorLogCategory.EDITOR, "Scanning file $searched/$total: $fileName")
            }

            // Build the workspace edit
            // Use context.symbolName as the actual current name (from in-memory document)
            // instead of definitionInfo.name (from disk) to handle unsaved changes correctly
            // If symbolName is not provided, we must return an error rather than silently
            // falling back to the disk name which may be stale
            val actualCurrentName = context.symbolName
                ?: return RefactorResult.Error(
                    "Symbol name not provided - cannot safely rename with unsaved changes",
                    recoverable = true
                )
            val workspaceEdit = buildRenameEdit(definitionInfo, references, actualCurrentName, newName)

            // Count affected files
            val affectedFiles = workspaceEdit.changes?.size ?: 0

            logger.info(EditorLogCategory.EDITOR, "Rename refactoring completed", mapOf(
                "oldName" to definitionInfo.name,
                "newName" to newName,
                "referenceCount" to references.size.toString(),
                "affectedFiles" to affectedFiles.toString()
            ))

            RefactorResult.Success(
                edit = workspaceEdit,
                affectedFiles = affectedFiles,
                description = "Renamed '${definitionInfo.name}' to '$newName' (${references.size + 1} occurrences)"
            )
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error during rename refactoring", error = e)
            RefactorResult.Error("Rename failed: ${e.message}")
        }
    }

    /**
     * Generates a preview of the rename changes.
     *
     * @param context The refactoring context
     * @param newName The new name for the symbol
     * @return List of file changes
     */
    suspend fun preview(context: RefactorContext, newName: String): List<FileChange> {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return emptyList()
            }

            val content = file.readText(Charsets.UTF_8)
            val offset = PositionUtils.positionToOffset(content, context.position)

            val definitionInfo = getDefinitionInfoAtOffset(context.filePath, content, offset)
                ?: return emptyList()

            val references = referenceService.findReferences(definitionInfo)

            // Use context.symbolName as the actual current name (from in-memory document)
            // For preview, we can fall back to disk name since it's non-destructive
            val actualCurrentName = context.symbolName ?: definitionInfo.name
            val workspaceEdit = buildRenameEdit(definitionInfo, references, actualCurrentName, newName)

            // Convert to FileChange list with previews
            workspaceEdit.changes?.map { (uri, edits) ->
                val filePath = WorkspaceEditApplier.uriToFilePath(uri)
                val fileContent = File(filePath).readText(Charsets.UTF_8)

                FileChange(
                    uri = uri,
                    filePath = filePath,
                    edits = edits,
                    previewBefore = generatePreview(fileContent, edits, definitionInfo.name),
                    previewAfter = generatePreview(fileContent, edits, newName, applied = true)
                )
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error generating rename preview", error = e)
            emptyList()
        }
    }

    /**
     * Validates a new name for the rename.
     *
     * @param newName The proposed new name
     * @param context The refactoring context
     * @return null if valid, or an error message
     */
    suspend fun validateNewName(newName: String, context: RefactorContext): String? {
        // Check if name is empty
        if (newName.isBlank()) {
            return "Name cannot be empty"
        }

        // Check if it's a valid Kotlin identifier
        if (!isValidKotlinIdentifier(newName)) {
            return "Invalid identifier: '$newName'"
        }

        // Check for reserved keywords
        if (isKotlinKeyword(newName)) {
            return "'$newName' is a reserved keyword"
        }

        // Check if the new name is different from the current name
        // Use context.symbolName which comes from the in-memory document (not disk)
        // This ensures validation works correctly even before the file is saved
        if (context.symbolName != null && context.symbolName == newName) {
            return "New name is the same as current name"
        }

        // TODO: Check for conflicts with existing symbols in scope

        return null
    }

    /**
     * Gets the definition info at the given offset.
     *
     * This method handles PSI parsing errors gracefully by returning null
     * and logging the error.
     */
    private suspend fun getDefinitionInfoAtOffset(
        filePath: String,
        content: String,
        offset: Int
    ): DefinitionInfo? {
        // Validate input
        if (content.isEmpty() || offset < 0 || offset > content.length) {
            logger.warn(EditorLogCategory.EDITOR, "Invalid input for getDefinitionInfoAtOffset", mapOf(
                "contentLength" to content.length.toString(),
                "offset" to offset.toString()
            ))
            return null
        }

        return try {
            PSIThreadBridge.readAction {
                val ktFile = try {
                    PSIBootstrap.parseKotlinFile(filePath, content)
                } catch (e: Exception) {
                    logger.error(EditorLogCategory.EDITOR, "Failed to parse Kotlin file", mapOf(
                        "filePath" to filePath
                    ), e)
                    return@readAction null
                }

            // First check if we're directly on a definition
            val definitionInfo = navigationService.getDefinitionInfo(ktFile, offset, filePath)
            if (definitionInfo != null) {
                return@readAction definitionInfo
            }

            // Check if we're on a reference and get its definition
            val element = ktFile.findElementAt(offset)
            if (element != null) {
                val parent = element.parent
                if (parent is KtNameReferenceExpression) {
                    // Try to resolve the reference
                    val resolved = parent.references.firstNotNullOfOrNull { it.resolve() }
                    if (resolved != null) {
                        val resolvedFile = resolved.containingFile
                        val resolvedFilePath = resolvedFile?.virtualFile?.path ?: filePath
                        val resolvedOffset = resolved.textOffset

                        // Get the resolved text for line/column calculation
                        val resolvedContent = if (resolvedFilePath == filePath) {
                            content
                        } else {
                            File(resolvedFilePath).takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: content
                        }

                        val line = resolvedContent.substring(0, resolvedOffset.coerceAtMost(resolvedContent.length))
                            .count { it == '\n' } + 1
                        val lastNewline = resolvedContent.lastIndexOf('\n', resolvedOffset - 1)
                        val column = if (lastNewline < 0) resolvedOffset + 1 else resolvedOffset - lastNewline

                        return@readAction DefinitionInfo(
                            name = parent.getReferencedName(),
                            kind = ai.rever.bosseditor.psi.NavigationTargetKind.UNKNOWN,
                            filePath = resolvedFilePath,
                            offset = resolvedOffset,
                            line = line,
                            column = column
                        )
                    }
                }
            }

            null
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error in getDefinitionInfoAtOffset", mapOf(
                "filePath" to filePath,
                "offset" to offset.toString()
            ), e)
            null
        }
    }

    /**
     * Builds a WorkspaceEdit for the rename operation.
     *
     * @param definitionInfo The definition info (used for position, not name)
     * @param references The references to the symbol
     * @param actualCurrentName The actual current name from in-memory document (handles unsaved changes)
     * @param newName The new name to rename to
     */
    private fun buildRenameEdit(
        definitionInfo: DefinitionInfo,
        references: List<ReferenceLocation>,
        actualCurrentName: String,
        newName: String
    ): WorkspaceEdit {
        val changes = mutableMapOf<String, MutableList<TextEdit>>()

        // Add the definition itself
        // Use actualCurrentName (from in-memory) instead of definitionInfo.name (from disk)
        val defUri = WorkspaceEditApplier.filePathToUri(definitionInfo.filePath)
        changes.getOrPut(defUri) { mutableListOf() }.add(
            createTextEdit(
                line = definitionInfo.line - 1, // Convert to 0-based
                column = definitionInfo.column - 1,
                oldName = actualCurrentName,
                newName = newName
            )
        )

        // Add all references
        // References also use actualCurrentName since they refer to the same symbol
        for (ref in references) {
            val refUri = WorkspaceEditApplier.filePathToUri(ref.filePath)
            changes.getOrPut(refUri) { mutableListOf() }.add(
                createTextEdit(
                    line = ref.line - 1, // Convert to 0-based
                    column = ref.column - 1,
                    oldName = actualCurrentName,
                    newName = newName
                )
            )
        }

        return WorkspaceEdit(changes = changes)
    }

    /**
     * Creates a TextEdit for replacing a symbol name.
     */
    private fun createTextEdit(line: Int, column: Int, oldName: String, newName: String): TextEdit {
        return TextEdit(
            range = Range(
                start = Position(line = line, character = column),
                end = Position(line = line, character = column + oldName.length)
            ),
            newText = newName
        )
    }

    /**
     * Generates a preview of changes for a file.
     */
    private fun generatePreview(
        content: String,
        edits: List<TextEdit>,
        name: String,
        applied: Boolean = false
    ): String {
        val lines = content.lines()
        val affectedLines = edits.map { it.range.start.line }.toSet()

        return buildString {
            for ((index, line) in lines.withIndex()) {
                if (index in affectedLines) {
                    if (applied) {
                        // Apply the edits to this line
                        var modifiedLine = line
                        val lineEdits = edits.filter { it.range.start.line == index }
                            .sortedByDescending { it.range.start.character }
                        for (edit in lineEdits) {
                            val startCol = edit.range.start.character
                            val endCol = edit.range.end.character
                            modifiedLine = modifiedLine.substring(0, startCol) +
                                edit.newText +
                                modifiedLine.substring(endCol.coerceAtMost(modifiedLine.length))
                        }
                        appendLine("+ $modifiedLine")
                    } else {
                        appendLine("- $line")
                    }
                }
            }
        }
    }

    /**
     * Checks if a name is a valid Kotlin identifier.
     */
    private fun isValidKotlinIdentifier(name: String): Boolean {
        if (name.isEmpty()) return false

        // Backtick-escaped identifiers
        if (name.startsWith('`') && name.endsWith('`') && name.length > 2) {
            val inner = name.substring(1, name.length - 1)
            return inner.isNotEmpty() && !inner.contains('`')
        }

        // Regular identifiers
        val firstChar = name.first()
        if (!firstChar.isLetter() && firstChar != '_') {
            return false
        }

        return name.all { it.isLetterOrDigit() || it == '_' }
    }

    /**
     * Checks if a name is a Kotlin reserved keyword.
     */
    private fun isKotlinKeyword(name: String): Boolean {
        val keywords = setOf(
            // Hard keywords
            "as", "as?", "break", "class", "continue", "do", "else", "false", "for",
            "fun", "if", "in", "!in", "interface", "is", "!is", "null", "object",
            "package", "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while",
            // Soft keywords (context-dependent, but better to avoid)
            "by", "catch", "constructor", "delegate", "dynamic", "field", "file",
            "finally", "get", "import", "init", "param", "property", "receiver",
            "set", "setparam", "value", "where"
        )
        return name in keywords
    }
}
