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
import ai.rever.bosseditor.refactoring.SafeDeleteParams
import ai.rever.bosseditor.refactoring.WorkspaceEditApplier
import org.jetbrains.kotlin.psi.*
import java.io.File

/**
 * Handles safe delete refactoring for Kotlin files.
 *
 * This class provides:
 * - Finding the symbol at cursor position
 * - Checking for usages that would be broken by deletion
 * - Generating a WorkspaceEdit to remove the declaration
 * - Optional force delete when usages exist
 *
 * @property navigationService Service for navigating Kotlin code
 */
class SafeDeleteRefactoring(
    private val navigationService: NavigationService
) {
    private val logger = EditorLogger.forComponent("SafeDeleteRefactoring")
    private val referenceService = ReferenceService.instance

    /**
     * Result of checking if a symbol can be safely deleted.
     */
    sealed class SafeDeleteCheckResult {
        /**
         * Symbol can be safely deleted (no usages found).
         */
        data class Safe(
            val definitionInfo: DefinitionInfo,
            val deleteInfo: DeleteInfo
        ) : SafeDeleteCheckResult()

        /**
         * Symbol has usages that would be broken by deletion.
         */
        data class HasUsages(
            val definitionInfo: DefinitionInfo,
            val deleteInfo: DeleteInfo,
            val usages: List<ReferenceLocation>
        ) : SafeDeleteCheckResult()

        /**
         * Cannot delete - symbol not found or unsupported.
         */
        data class CannotDelete(val reason: String) : SafeDeleteCheckResult()
    }

    /**
     * Information about what will be deleted.
     */
    data class DeleteInfo(
        val name: String,
        val kind: String,
        val filePath: String,
        val startOffset: Int,
        val endOffset: Int,
        val startLine: Int,
        val endLine: Int
    )

    /**
     * Checks if the symbol at the cursor can be safely deleted.
     *
     * @param context The refactoring context
     * @return Check result indicating whether deletion is safe
     */
    suspend fun checkSafeDelete(context: RefactorContext): SafeDeleteCheckResult {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return SafeDeleteCheckResult.CannotDelete("File not found: ${context.filePath}")
            }

            val content = file.readText(Charsets.UTF_8)
            val offset = PositionUtils.positionToOffset(content, context.position)

            // Analyze the symbol at cursor
            val deleteInfo = analyzeForDelete(context.filePath, content, offset)
                ?: return SafeDeleteCheckResult.CannotDelete("No deletable symbol at cursor position")

            // Get definition info for reference search
            val definitionInfo = getDefinitionInfoAtOffset(context.filePath, content, offset)
                ?: return SafeDeleteCheckResult.CannotDelete("Cannot resolve symbol definition")

            // Find all usages
            logger.info(EditorLogCategory.EDITOR, "Checking usages for safe delete", mapOf(
                "symbol" to deleteInfo.name,
                "file" to context.filePath
            ))

            val references = referenceService.findReferences(definitionInfo) { searched, total, fileName ->
                logger.debug(EditorLogCategory.EDITOR, "Scanning file $searched/$total: $fileName")
            }

            if (references.isEmpty()) {
                SafeDeleteCheckResult.Safe(definitionInfo, deleteInfo)
            } else {
                SafeDeleteCheckResult.HasUsages(definitionInfo, deleteInfo, references)
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error checking safe delete", error = e)
            SafeDeleteCheckResult.CannotDelete("Error: ${e.message}")
        }
    }

    /**
     * Executes the safe delete refactoring.
     *
     * @param context The refactoring context
     * @param params Parameters including force delete option
     * @return The result of the refactoring
     */
    suspend fun execute(context: RefactorContext, params: SafeDeleteParams): RefactorResult {
        return try {
            val checkResult = checkSafeDelete(context)

            when (checkResult) {
                is SafeDeleteCheckResult.CannotDelete -> {
                    RefactorResult.Error(checkResult.reason)
                }

                is SafeDeleteCheckResult.HasUsages -> {
                    if (!params.forceDelete) {
                        // Return error with details about usages
                        val usageCount = checkResult.usages.size
                        val usageFiles = checkResult.usages.map { it.filePath }.toSet()
                        RefactorResult.Error(
                            message = "Cannot delete '${checkResult.deleteInfo.name}': $usageCount usage(s) found in ${usageFiles.size} file(s)",
                            recoverable = true
                        )
                    } else {
                        // Force delete - proceed anyway
                        executeDelete(context, checkResult.deleteInfo, checkResult.usages.size)
                    }
                }

                is SafeDeleteCheckResult.Safe -> {
                    executeDelete(context, checkResult.deleteInfo, 0)
                }
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error during safe delete refactoring", error = e)
            RefactorResult.Error("Safe delete failed: ${e.message}")
        }
    }

    /**
     * Generates a preview of the safe delete changes.
     *
     * @param context The refactoring context
     * @return List of file changes
     */
    suspend fun preview(context: RefactorContext): List<FileChange> {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) return emptyList()

            val content = file.readText(Charsets.UTF_8)
            val offset = PositionUtils.positionToOffset(content, context.position)

            val deleteInfo = analyzeForDelete(context.filePath, content, offset) ?: return emptyList()

            val edit = buildDeleteEdit(context.filePath, content, deleteInfo)

            edit.changes?.map { (uri, edits) ->
                FileChange(
                    uri = uri,
                    filePath = WorkspaceEditApplier.uriToFilePath(uri),
                    edits = edits,
                    previewBefore = generateDeletePreview(content, deleteInfo),
                    previewAfter = ""
                )
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error generating safe delete preview", error = e)
            emptyList()
        }
    }

    /**
     * Gets the usages for the symbol at cursor without executing delete.
     *
     * @param context The refactoring context
     * @return List of usage locations
     */
    suspend fun getUsages(context: RefactorContext): List<ReferenceLocation> {
        val checkResult = checkSafeDelete(context)
        return when (checkResult) {
            is SafeDeleteCheckResult.HasUsages -> checkResult.usages
            else -> emptyList()
        }
    }

    /**
     * Analyzes the symbol at cursor to determine if it can be deleted.
     *
     * This method handles PSI parsing errors gracefully by returning null
     * and logging the error.
     */
    private suspend fun analyzeForDelete(
        filePath: String,
        content: String,
        offset: Int
    ): DeleteInfo? {
        // Validate input
        if (content.isEmpty() || offset < 0 || offset > content.length) {
            logger.warn(EditorLogCategory.EDITOR, "Invalid input for analyzeForDelete", mapOf(
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
                    logger.error(EditorLogCategory.EDITOR, "Failed to parse Kotlin file for safe delete", mapOf(
                        "filePath" to filePath
                    ), e)
                    return@readAction null
                }
                val element = ktFile.findElementAt(offset) ?: return@readAction null

            // Walk up to find a deletable declaration
            var current: org.jetbrains.kotlin.com.intellij.psi.PsiElement? = element

            while (current != null) {
                val deleteInfo = when (current) {
                    is KtNamedFunction -> {
                        val name = current.name ?: return@readAction null
                        DeleteInfo(
                            name = name,
                            kind = "function",
                            filePath = filePath,
                            startOffset = getDeclarationStartWithAnnotations(current),
                            endOffset = current.textOffset + current.textLength,
                            startLine = PositionUtils.offsetToLine(content, current.textOffset),
                            endLine = PositionUtils.offsetToLine(content, current.textOffset + current.textLength)
                        )
                    }

                    is KtProperty -> {
                        val name = current.name ?: return@readAction null
                        DeleteInfo(
                            name = name,
                            kind = if (current.isLocal) "local variable" else "property",
                            filePath = filePath,
                            startOffset = getDeclarationStartWithAnnotations(current),
                            endOffset = current.textOffset + current.textLength,
                            startLine = PositionUtils.offsetToLine(content, current.textOffset),
                            endLine = PositionUtils.offsetToLine(content, current.textOffset + current.textLength)
                        )
                    }

                    is KtClass -> {
                        val name = current.name ?: return@readAction null
                        DeleteInfo(
                            name = name,
                            kind = when {
                                current.isInterface() -> "interface"
                                current.isEnum() -> "enum"
                                current.isData() -> "data class"
                                else -> "class"
                            },
                            filePath = filePath,
                            startOffset = getDeclarationStartWithAnnotations(current),
                            endOffset = current.textOffset + current.textLength,
                            startLine = PositionUtils.offsetToLine(content, current.textOffset),
                            endLine = PositionUtils.offsetToLine(content, current.textOffset + current.textLength)
                        )
                    }

                    is KtObjectDeclaration -> {
                        val name = current.name ?: return@readAction null
                        DeleteInfo(
                            name = name,
                            kind = "object",
                            filePath = filePath,
                            startOffset = getDeclarationStartWithAnnotations(current),
                            endOffset = current.textOffset + current.textLength,
                            startLine = PositionUtils.offsetToLine(content, current.textOffset),
                            endLine = PositionUtils.offsetToLine(content, current.textOffset + current.textLength)
                        )
                    }

                    is KtTypeAlias -> {
                        val name = current.name ?: return@readAction null
                        DeleteInfo(
                            name = name,
                            kind = "typealias",
                            filePath = filePath,
                            startOffset = getDeclarationStartWithAnnotations(current),
                            endOffset = current.textOffset + current.textLength,
                            startLine = PositionUtils.offsetToLine(content, current.textOffset),
                            endLine = PositionUtils.offsetToLine(content, current.textOffset + current.textLength)
                        )
                    }

                    is KtParameter -> {
                        // Parameters in primary constructor or function
                        val name = current.name ?: return@readAction null
                        DeleteInfo(
                            name = name,
                            kind = "parameter",
                            filePath = filePath,
                            startOffset = current.textOffset,
                            endOffset = current.textOffset + current.textLength,
                            startLine = PositionUtils.offsetToLine(content, current.textOffset),
                            endLine = PositionUtils.offsetToLine(content, current.textOffset + current.textLength)
                        )
                    }

                    else -> null
                }

                if (deleteInfo != null) {
                    return@readAction deleteInfo
                }

                current = current.parent
            }

            null
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error in analyzeForDelete", mapOf(
                "filePath" to filePath,
                "offset" to offset.toString()
            ), e)
            null
        }
    }

    /**
     * Gets the start offset including any annotations before the declaration.
     */
    private fun getDeclarationStartWithAnnotations(declaration: KtDeclaration): Int {
        val annotations = declaration.annotationEntries
        return if (annotations.isNotEmpty()) {
            annotations.first().textOffset
        } else {
            declaration.textOffset
        }
    }

    /**
     * Gets the definition info at the given offset.
     */
    private suspend fun getDefinitionInfoAtOffset(
        filePath: String,
        content: String,
        offset: Int
    ): DefinitionInfo? {
        return PSIThreadBridge.readAction {
            val ktFile = PSIBootstrap.parseKotlinFile(filePath, content)
            navigationService.getDefinitionInfo(ktFile, offset, filePath)
        }
    }

    /**
     * Executes the deletion.
     */
    private fun executeDelete(
        context: RefactorContext,
        deleteInfo: DeleteInfo,
        warningUsageCount: Int
    ): RefactorResult {
        val file = File(context.filePath)
        val content = file.readText(Charsets.UTF_8)

        val workspaceEdit = buildDeleteEdit(context.filePath, content, deleteInfo)

        val description = if (warningUsageCount > 0) {
            "Deleted ${deleteInfo.kind} '${deleteInfo.name}' (WARNING: $warningUsageCount broken usage(s))"
        } else {
            "Deleted ${deleteInfo.kind} '${deleteInfo.name}'"
        }

        logger.info(EditorLogCategory.EDITOR, "Safe delete completed", mapOf(
            "symbol" to deleteInfo.name,
            "kind" to deleteInfo.kind,
            "forcedUsages" to warningUsageCount.toString()
        ))

        return RefactorResult.Success(
            edit = workspaceEdit,
            affectedFiles = 1,
            description = description
        )
    }

    /**
     * Builds a WorkspaceEdit for the delete operation.
     */
    private fun buildDeleteEdit(
        filePath: String,
        content: String,
        deleteInfo: DeleteInfo
    ): WorkspaceEdit {
        // Find the actual line boundaries for a cleaner delete
        val startOfLine = PositionUtils.findLineStart(content, deleteInfo.startOffset)
        val endOfLine = PositionUtils.findLineEnd(content, deleteInfo.endOffset)

        val startPos = PositionUtils.offsetToLspPosition(content, startOfLine)
        val endPos = PositionUtils.offsetToLspPosition(content, endOfLine)

        val uri = WorkspaceEditApplier.filePathToUri(filePath)

        val edit = TextEdit(
            range = Range(start = startPos, end = endPos),
            newText = ""
        )

        return WorkspaceEdit(changes = mapOf(uri to listOf(edit)))
    }

    /**
     * Generates a preview of what will be deleted.
     */
    private fun generateDeletePreview(content: String, deleteInfo: DeleteInfo): String {
        val lines = content.lines()
        return buildString {
            for (lineIndex in deleteInfo.startLine..deleteInfo.endLine.coerceAtMost(lines.size - 1)) {
                appendLine("- ${lines[lineIndex]}")
            }
        }
    }

}
