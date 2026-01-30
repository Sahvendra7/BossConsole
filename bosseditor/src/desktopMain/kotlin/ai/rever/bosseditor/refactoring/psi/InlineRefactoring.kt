package ai.rever.bosseditor.refactoring.psi

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.logging.EditorLogger
import ai.rever.bosseditor.logging.EditorLogCategory
import ai.rever.bosseditor.lsp.protocol.Position
import ai.rever.bosseditor.lsp.protocol.Range
import ai.rever.bosseditor.lsp.protocol.TextEdit
import ai.rever.bosseditor.lsp.protocol.WorkspaceEdit
import ai.rever.bosseditor.psi.PSIBootstrap
import ai.rever.bosseditor.psi.PSIThreadBridge
import ai.rever.bosseditor.refactoring.FileChange
import ai.rever.bosseditor.refactoring.PositionUtils
import ai.rever.bosseditor.refactoring.RefactorContext
import ai.rever.bosseditor.refactoring.RefactorResult
import ai.rever.bosseditor.refactoring.WorkspaceEditApplier
import org.jetbrains.kotlin.psi.*
import java.io.File

/**
 * Handles inline refactoring for Kotlin files.
 *
 * Supports:
 * - Inline variable: Replace all usages with the value, remove declaration
 * - Inline method: Replace all calls with method body (future)
 */
class InlineRefactoring {

    private val logger = EditorLogger.forComponent("InlineRefactoring")

    /**
     * Executes the inline refactoring.
     *
     * @param context The refactoring context
     * @return The result of the refactoring
     */
    suspend fun execute(context: RefactorContext): RefactorResult {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return RefactorResult.Error("File not found: ${context.filePath}")
            }

            val content = file.readText(Charsets.UTF_8)
            val offset = PositionUtils.positionToOffset(content, context.position)

            // Analyze what can be inlined at this position
            val inlineInfo = analyzeForInline(context.filePath, content, offset)
                ?: return RefactorResult.Error("No inlinable element at cursor position")

            when (inlineInfo) {
                is InlineInfo.Variable -> executeVariableInline(context, content, inlineInfo)
                is InlineInfo.Method -> RefactorResult.Error("Method inlining not yet implemented")
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error during inline refactoring", error = e)
            RefactorResult.Error("Inline failed: ${e.message}")
        }
    }

    /**
     * Generates a preview of the inline changes.
     */
    suspend fun preview(context: RefactorContext): List<FileChange> {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) return emptyList()

            val content = file.readText(Charsets.UTF_8)
            val offset = PositionUtils.positionToOffset(content, context.position)

            val inlineInfo = analyzeForInline(context.filePath, content, offset) ?: return emptyList()

            when (inlineInfo) {
                is InlineInfo.Variable -> {
                    val result = executeVariableInline(context, content, inlineInfo)
                    if (result is RefactorResult.Success) {
                        result.edit.changes?.map { (uri, edits) ->
                            FileChange(
                                uri = uri,
                                filePath = WorkspaceEditApplier.uriToFilePath(uri),
                                edits = edits
                            )
                        } ?: emptyList()
                    } else {
                        emptyList()
                    }
                }
                is InlineInfo.Method -> emptyList()
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error generating inline preview", error = e)
            emptyList()
        }
    }

    /**
     * Analyzes the element at the cursor to determine what can be inlined.
     *
     * This method handles PSI parsing errors gracefully by returning null
     * and logging the error.
     */
    private suspend fun analyzeForInline(
        filePath: String,
        content: String,
        offset: Int
    ): InlineInfo? {
        // Validate input
        if (content.isEmpty() || offset < 0 || offset > content.length) {
            logger.warn(EditorLogCategory.EDITOR, "Invalid input for analyzeForInline", mapOf(
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
                    logger.error(EditorLogCategory.EDITOR, "Failed to parse Kotlin file for inline", mapOf(
                        "filePath" to filePath
                    ), e)
                    return@readAction null
                }
                val element = ktFile.findElementAt(offset) ?: return@readAction null

            // Check if we're on a variable declaration or reference
            var current: org.jetbrains.kotlin.com.intellij.psi.PsiElement? = element

            while (current != null) {
                when (current) {
                    is KtProperty -> {
                        // Check if this is a local variable with an initializer
                        val initializer = current.initializer
                        if (initializer != null && current.isLocal) {
                            val name = current.name ?: return@readAction null

                            // Find all usages
                            val usages = findVariableUsages(ktFile, name, current.textOffset)

                            return@readAction InlineInfo.Variable(
                                name = name,
                                value = initializer.text,
                                declarationStart = current.textOffset,
                                declarationEnd = current.textOffset + current.textLength,
                                usages = usages
                            )
                        }
                        return@readAction null
                    }
                    is KtNameReferenceExpression -> {
                        // We're on a reference - try to find the declaration
                        val name = current.getReferencedName()

                        // Find the local declaration
                        val declaration = findLocalDeclaration(ktFile, name, current.textOffset)
                        if (declaration != null) {
                            val initializer = declaration.initializer
                            if (initializer != null) {
                                val usages = findVariableUsages(ktFile, name, declaration.textOffset)

                                return@readAction InlineInfo.Variable(
                                    name = name,
                                    value = initializer.text,
                                    declarationStart = declaration.textOffset,
                                    declarationEnd = declaration.textOffset + declaration.textLength,
                                    usages = usages
                                )
                            }
                        }
                        return@readAction null
                    }
                    is KtNamedFunction -> {
                        // Method inlining (future)
                        return@readAction null
                    }
                }
                current = current.parent
            }

            null
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error in analyzeForInline", mapOf(
                "filePath" to filePath,
                "offset" to offset.toString()
            ), e)
            null
        }
    }

    /**
     * Finds all usages of a local variable.
     */
    private fun findVariableUsages(ktFile: KtFile, name: String, declarationOffset: Int): List<UsageInfo> {
        val usages = mutableListOf<UsageInfo>()

        ktFile.accept(object : KtTreeVisitorVoid() {
            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                super.visitReferenceExpression(expression)
                if (expression !is KtNameReferenceExpression) return

                if (expression.getReferencedName() == name && expression.textOffset != declarationOffset) {
                    usages.add(UsageInfo(
                        start = expression.textOffset,
                        end = expression.textOffset + expression.textLength
                    ))
                }
            }
        })

        return usages.sortedBy { it.start }
    }

    /**
     * Finds a local property declaration by name.
     */
    private fun findLocalDeclaration(ktFile: KtFile, name: String, beforeOffset: Int): KtProperty? {
        var result: KtProperty? = null

        ktFile.accept(object : KtTreeVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)

                if (property.name == name && property.isLocal && property.textOffset < beforeOffset) {
                    result = property
                }
            }
        })

        return result
    }

    /**
     * Executes variable inlining.
     */
    private fun executeVariableInline(
        context: RefactorContext,
        content: String,
        info: InlineInfo.Variable
    ): RefactorResult {
        if (info.usages.isEmpty()) {
            return RefactorResult.Error("Variable '${info.name}' has no usages to inline")
        }

        val edits = mutableListOf<TextEdit>()

        // Replace each usage with the value
        // Process in reverse order to maintain correct offsets
        for (usage in info.usages.sortedByDescending { it.start }) {
            val startPos = PositionUtils.offsetToLspPosition(content, usage.start)
            val endPos = PositionUtils.offsetToLspPosition(content, usage.end)

            // Wrap value in parentheses if it might need them
            val needsParens = info.value.contains(' ') ||
                info.value.contains('+') ||
                info.value.contains('-')
            val replacement = if (needsParens) "(${info.value})" else info.value

            edits.add(TextEdit(
                range = Range(start = startPos, end = endPos),
                newText = replacement
            ))
        }

        // Delete the declaration (including any trailing newline)
        val declarationEndWithNewline = PositionUtils.findLineEnd(content, info.declarationEnd)
        val declarationStartOfLine = PositionUtils.findLineStart(content, info.declarationStart)

        val declStartPos = PositionUtils.offsetToLspPosition(content, declarationStartOfLine)
        val declEndPos = PositionUtils.offsetToLspPosition(content, declarationEndWithNewline)

        edits.add(TextEdit(
            range = Range(start = declStartPos, end = declEndPos),
            newText = ""
        ))

        val workspaceEdit = WorkspaceEdit(changes = mapOf(context.fileUri to edits))

        logger.info(EditorLogCategory.EDITOR, "Inline variable completed", mapOf(
            "variable" to info.name,
            "usages" to info.usages.size.toString()
        ))

        return RefactorResult.Success(
            edit = workspaceEdit,
            affectedFiles = 1,
            description = "Inlined variable '${info.name}' (${info.usages.size} usages)"
        )
    }

    /**
     * Information about what can be inlined.
     */
    private sealed class InlineInfo {
        data class Variable(
            val name: String,
            val value: String,
            val declarationStart: Int,
            val declarationEnd: Int,
            val usages: List<UsageInfo>
        ) : InlineInfo()

        data class Method(
            val name: String,
            val body: String,
            val parameters: List<String>
        ) : InlineInfo()
    }

    /**
     * Information about a usage location.
     */
    private data class UsageInfo(
        val start: Int,
        val end: Int
    )
}
