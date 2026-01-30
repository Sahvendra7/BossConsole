package ai.rever.bosseditor.refactoring.psi

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.logging.EditorLogger
import ai.rever.bosseditor.logging.EditorLogCategory
import ai.rever.bosseditor.lsp.protocol.Position
import ai.rever.bosseditor.lsp.protocol.Range
import ai.rever.bosseditor.lsp.protocol.TextEdit
import ai.rever.bosseditor.lsp.protocol.WorkspaceEdit
import ai.rever.bosseditor.psi.PSIBootstrap
import ai.rever.bosseditor.psi.PSIThreadBridge
import ai.rever.bosseditor.refactoring.ExtractVariableParams
import ai.rever.bosseditor.refactoring.FileChange
import ai.rever.bosseditor.refactoring.PositionUtils
import ai.rever.bosseditor.refactoring.RefactorContext
import ai.rever.bosseditor.refactoring.RefactorResult
import ai.rever.bosseditor.refactoring.WorkspaceEditApplier
import org.jetbrains.kotlin.psi.*
import java.io.File

/**
 * Handles extract variable refactoring for Kotlin files.
 *
 * This refactoring:
 * 1. Analyzes the selected expression
 * 2. Determines variable type (inferred or explicit)
 * 3. Finds the best insertion point (start of containing statement)
 * 4. Generates a variable declaration
 * 5. Replaces the expression with the variable name
 */
class ExtractVariableRefactoring {

    private val logger = EditorLogger.forComponent("ExtractVariableRefactoring")

    /**
     * Executes the extract variable refactoring.
     *
     * @param context The refactoring context
     * @param params Parameters including variable name and options
     * @return The result of the refactoring
     */
    suspend fun execute(context: RefactorContext, params: ExtractVariableParams): RefactorResult {
        val selection = context.selection
            ?: return RefactorResult.Error("No selection for extract variable")

        if (selection.isEmpty) {
            return RefactorResult.Error("Please select an expression to extract")
        }

        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return RefactorResult.Error("File not found: ${context.filePath}")
            }

            val content = file.readText(Charsets.UTF_8)

            // Analyze the selection and generate edits
            val analysisResult = analyzeSelection(context.filePath, content, selection)
                ?: return RefactorResult.Error("Selected text is not a valid expression")

            // Validate variable name
            val nameError = validateVariableName(params.variableName)
            if (nameError != null) {
                return RefactorResult.Error(nameError)
            }

            // Generate the workspace edit
            val workspaceEdit = generateExtractEdit(
                content = content,
                uri = context.fileUri,
                analysis = analysisResult,
                variableName = params.variableName,
                isVal = params.isVal,
                replaceAll = params.replaceAll
            )

            logger.info(EditorLogCategory.EDITOR, "Extract variable completed", mapOf(
                "variableName" to params.variableName,
                "expression" to analysisResult.expressionText.take(50)
            ))

            RefactorResult.Success(
                edit = workspaceEdit,
                affectedFiles = 1,
                description = "Extracted '${params.variableName}' from expression"
            )
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error during extract variable", error = e)
            RefactorResult.Error("Extract variable failed: ${e.message}")
        }
    }

    /**
     * Generates a preview of the extract variable changes.
     */
    suspend fun preview(context: RefactorContext, params: ExtractVariableParams): List<FileChange> {
        val selection = context.selection ?: return emptyList()
        if (selection.isEmpty) return emptyList()

        return try {
            val file = File(context.filePath)
            if (!file.exists()) return emptyList()

            val content = file.readText(Charsets.UTF_8)
            val analysisResult = analyzeSelection(context.filePath, content, selection) ?: return emptyList()

            val workspaceEdit = generateExtractEdit(
                content = content,
                uri = context.fileUri,
                analysis = analysisResult,
                variableName = params.variableName,
                isVal = params.isVal,
                replaceAll = params.replaceAll
            )

            workspaceEdit.changes?.map { (uri, edits) ->
                FileChange(
                    uri = uri,
                    filePath = WorkspaceEditApplier.uriToFilePath(uri),
                    edits = edits
                )
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error generating extract variable preview", error = e)
            emptyList()
        }
    }

    /**
     * Suggests a variable name based on the selected expression.
     */
    suspend fun suggestVariableName(context: RefactorContext): String {
        val selection = context.selection ?: return "value"
        if (selection.isEmpty) return "value"

        return try {
            val file = File(context.filePath)
            if (!file.exists()) return "value"

            val content = file.readText(Charsets.UTF_8)
            val analysisResult = analyzeSelection(context.filePath, content, selection) ?: return "value"

            // Generate a name based on the expression
            generateSuggestedName(analysisResult)
        } catch (e: Exception) {
            "value"
        }
    }

    /**
     * Analyzes the selected expression to prepare for extraction.
     *
     * This method handles PSI parsing errors gracefully by returning null
     * and logging the error.
     */
    private suspend fun analyzeSelection(
        filePath: String,
        content: String,
        selection: EditorRange
    ): ExpressionAnalysis? {
        // Validate input
        if (content.isEmpty()) {
            logger.warn(EditorLogCategory.EDITOR, "Empty content for analyzeSelection")
            return null
        }

        val startOffset = PositionUtils.positionToOffset(content, selection.start)
        val endOffset = PositionUtils.positionToOffset(content, selection.end)

        // Validate offsets
        if (startOffset >= endOffset || endOffset > content.length) {
            return null
        }

        return try {
            PSIThreadBridge.readAction {
                val ktFile = try {
                    PSIBootstrap.parseKotlinFile(filePath, content)
                } catch (e: Exception) {
                    logger.error(EditorLogCategory.EDITOR, "Failed to parse Kotlin file for extract variable", mapOf(
                        "filePath" to filePath
                    ), e)
                    return@readAction null
                }

            // Find the expression at the selection
            val expression = findExpressionInRange(ktFile, startOffset, endOffset)
                ?: return@readAction null

            // Get the containing statement/block
            val containingStatement = findContainingStatement(expression)
                ?: return@readAction null

            // Calculate insertion point (before the containing statement)
            val insertionOffset = containingStatement.textOffset

            // Get indentation of the containing statement
            val indentation = getIndentation(content, insertionOffset)

            ExpressionAnalysis(
                expressionText = expression.text,
                expressionStart = expression.textOffset,
                expressionEnd = expression.textOffset + expression.textLength,
                insertionOffset = insertionOffset,
                indentation = indentation,
                inferredType = inferExpressionType(expression),
                containingFunction = findContainingFunction(expression)?.name
            )
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error in analyzeSelection for extract variable", mapOf(
                "filePath" to filePath
            ), e)
            null
        }
    }

    /**
     * Finds an expression that matches the selection range.
     */
    private fun findExpressionInRange(ktFile: KtFile, startOffset: Int, endOffset: Int): KtExpression? {
        // Find all elements that overlap with the selection
        var bestMatch: KtExpression? = null
        var bestMatchLength = Int.MAX_VALUE

        ktFile.accept(object : KtTreeVisitorVoid() {
            override fun visitExpression(expression: KtExpression) {
                super.visitExpression(expression)

                val exprStart = expression.textOffset
                val exprEnd = exprStart + expression.textLength

                // Check if this expression matches the selection
                if (exprStart >= startOffset && exprEnd <= endOffset) {
                    // This expression is within the selection
                    val length = exprEnd - exprStart
                    if (length < bestMatchLength) {
                        // Skip certain types that don't make sense to extract
                        if (isExtractableExpression(expression)) {
                            bestMatch = expression
                            bestMatchLength = length
                        }
                    }
                } else if (exprStart == startOffset && exprEnd == endOffset) {
                    // Exact match
                    if (isExtractableExpression(expression)) {
                        bestMatch = expression
                        bestMatchLength = 0
                    }
                }
            }
        })

        // If no exact match, try to find the smallest expression that contains the selection
        if (bestMatch == null) {
            ktFile.accept(object : KtTreeVisitorVoid() {
                override fun visitExpression(expression: KtExpression) {
                    super.visitExpression(expression)

                    val exprStart = expression.textOffset
                    val exprEnd = exprStart + expression.textLength

                    // Check if this expression contains the selection
                    if (exprStart <= startOffset && exprEnd >= endOffset) {
                        val length = exprEnd - exprStart
                        if (length < bestMatchLength && isExtractableExpression(expression)) {
                            bestMatch = expression
                            bestMatchLength = length
                        }
                    }
                }
            })
        }

        return bestMatch
    }

    /**
     * Checks if an expression can be extracted to a variable.
     */
    private fun isExtractableExpression(expression: KtExpression): Boolean {
        return when (expression) {
            is KtConstantExpression,
            is KtStringTemplateExpression,
            is KtCallExpression,
            is KtDotQualifiedExpression,
            is KtBinaryExpression,
            is KtParenthesizedExpression,
            is KtLambdaExpression,
            is KtArrayAccessExpression,
            is KtPrefixExpression,
            is KtPostfixExpression,
            is KtIfExpression,
            is KtWhenExpression,
            is KtThisExpression,
            is KtSuperExpression,
            is KtCollectionLiteralExpression,
            is KtObjectLiteralExpression -> true
            is KtNameReferenceExpression -> {
                // Only extract if it's more than a simple name reference
                // (e.g., referencing a property or calling a method)
                true
            }
            else -> false
        }
    }

    /**
     * Finds the containing statement (the top-level expression in a block).
     */
    private fun findContainingStatement(expression: KtExpression): KtExpression? {
        var current: org.jetbrains.kotlin.com.intellij.psi.PsiElement? = expression

        while (current != null) {
            val parent = current.parent

            // If parent is a block or function body, current is the statement
            if (parent is KtBlockExpression || parent is KtDeclarationWithBody) {
                return current as? KtExpression
            }

            // If parent is the file, current is the top-level statement
            if (parent is KtFile) {
                return current as? KtExpression
            }

            current = parent
        }

        return expression
    }

    /**
     * Finds the containing function for the expression.
     */
    private fun findContainingFunction(expression: KtExpression): KtNamedFunction? {
        var current: org.jetbrains.kotlin.com.intellij.psi.PsiElement? = expression.parent

        while (current != null) {
            if (current is KtNamedFunction) {
                return current
            }
            current = current.parent
        }

        return null
    }

    /**
     * Attempts to infer the type of an expression.
     */
    private fun inferExpressionType(expression: KtExpression): String? {
        // This is a simplified type inference - for accurate types,
        // we would need the full Kotlin analysis API
        return when (expression) {
            is KtConstantExpression -> {
                val text = expression.text
                when {
                    text == "true" || text == "false" -> "Boolean"
                    text.contains(".") -> "Double"
                    text.endsWith("L") || text.endsWith("l") -> "Long"
                    text.endsWith("F") || text.endsWith("f") -> "Float"
                    text.all { it.isDigit() || it == '-' } -> "Int"
                    else -> null
                }
            }
            is KtStringTemplateExpression -> "String"
            else -> null
        }
    }

    /**
     * Gets the indentation at a given offset.
     */
    private fun getIndentation(content: String, offset: Int): String {
        val lineStart = content.lastIndexOf('\n', offset - 1) + 1
        var indentEnd = lineStart
        while (indentEnd < content.length && content[indentEnd].isWhitespace() && content[indentEnd] != '\n') {
            indentEnd++
        }
        return content.substring(lineStart, indentEnd.coerceAtMost(offset))
    }

    /**
     * Generates a suggested variable name based on the expression.
     */
    private fun generateSuggestedName(analysis: ExpressionAnalysis): String {
        val expr = analysis.expressionText

        // If it's a method call, use the method name
        val methodMatch = Regex("""\.(\w+)\s*\(""").find(expr)
        if (methodMatch != null) {
            val methodName = methodMatch.groupValues[1]
            return toCamelCase(methodName)
        }

        // If it's a property access, use the property name
        val propertyMatch = Regex("""\.(\w+)$""").find(expr)
        if (propertyMatch != null) {
            return propertyMatch.groupValues[1]
        }

        // If it's a simple function call
        val funcMatch = Regex("""^(\w+)\s*\(""").find(expr)
        if (funcMatch != null) {
            val funcName = funcMatch.groupValues[1]
            return toCamelCase(funcName)
        }

        // Based on inferred type
        return when (analysis.inferredType) {
            "String" -> "text"
            "Int", "Long" -> "number"
            "Double", "Float" -> "value"
            "Boolean" -> "flag"
            else -> "result"
        }
    }

    /**
     * Converts a string to camelCase.
     */
    private fun toCamelCase(name: String): String {
        // If already camelCase, return as is
        if (name.first().isLowerCase()) return name

        // Convert to camelCase
        return name.replaceFirstChar { it.lowercase() }
    }

    /**
     * Validates a variable name.
     */
    private fun validateVariableName(name: String): String? {
        if (name.isBlank()) {
            return "Variable name cannot be empty"
        }

        if (!name.first().isLetter() && name.first() != '_') {
            return "Variable name must start with a letter or underscore"
        }

        if (!name.all { it.isLetterOrDigit() || it == '_' }) {
            return "Variable name contains invalid characters"
        }

        val keywords = setOf(
            "val", "var", "fun", "class", "interface", "object", "if", "else",
            "when", "for", "while", "do", "return", "break", "continue", "true",
            "false", "null", "this", "super", "is", "in", "as", "try", "catch",
            "throw", "finally", "import", "package"
        )

        if (name in keywords) {
            return "'$name' is a reserved keyword"
        }

        return null
    }

    /**
     * Generates the workspace edit for extracting the variable.
     */
    private fun generateExtractEdit(
        content: String,
        uri: String,
        analysis: ExpressionAnalysis,
        variableName: String,
        isVal: Boolean,
        replaceAll: Boolean
    ): WorkspaceEdit {
        val edits = mutableListOf<TextEdit>()

        // Calculate positions
        val insertionPosition = PositionUtils.offsetToLspPosition(content, analysis.insertionOffset)
        val exprStartPosition = PositionUtils.offsetToLspPosition(content, analysis.expressionStart)
        val exprEndPosition = PositionUtils.offsetToLspPosition(content, analysis.expressionEnd)

        // Generate variable declaration
        val keyword = if (isVal) "val" else "var"
        val declaration = "$keyword $variableName = ${analysis.expressionText}\n${analysis.indentation}"

        // Add variable declaration
        edits.add(TextEdit(
            range = Range(start = insertionPosition, end = insertionPosition),
            newText = declaration
        ))

        // Replace original expression with variable name
        edits.add(TextEdit(
            range = Range(start = exprStartPosition, end = exprEndPosition),
            newText = variableName
        ))

        // If replaceAll, find and replace other occurrences
        if (replaceAll) {
            val otherOccurrences = findOtherOccurrences(content, analysis)
            for (occurrence in otherOccurrences) {
                val startPos = PositionUtils.offsetToLspPosition(content, occurrence.first)
                val endPos = PositionUtils.offsetToLspPosition(content, occurrence.second)
                edits.add(TextEdit(
                    range = Range(start = startPos, end = endPos),
                    newText = variableName
                ))
            }
        }

        return WorkspaceEdit(changes = mapOf(uri to edits))
    }

    /**
     * Finds other occurrences of the same expression in the same scope.
     */
    private fun findOtherOccurrences(
        content: String,
        analysis: ExpressionAnalysis
    ): List<Pair<Int, Int>> {
        val occurrences = mutableListOf<Pair<Int, Int>>()
        val expressionText = analysis.expressionText

        var searchStart = 0
        while (true) {
            val idx = content.indexOf(expressionText, searchStart)
            if (idx < 0) break

            // Don't include the original expression
            if (idx != analysis.expressionStart) {
                occurrences.add(idx to idx + expressionText.length)
            }

            searchStart = idx + 1
        }

        return occurrences
    }

    /**
     * Result of expression analysis.
     */
    private data class ExpressionAnalysis(
        val expressionText: String,
        val expressionStart: Int,
        val expressionEnd: Int,
        val insertionOffset: Int,
        val indentation: String,
        val inferredType: String?,
        val containingFunction: String?
    )
}
