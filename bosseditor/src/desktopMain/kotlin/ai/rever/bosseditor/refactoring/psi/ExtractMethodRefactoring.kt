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
import ai.rever.bosseditor.refactoring.ExtractMethodParams
import ai.rever.bosseditor.refactoring.FileChange
import ai.rever.bosseditor.refactoring.PositionUtils
import ai.rever.bosseditor.refactoring.RefactorContext
import ai.rever.bosseditor.refactoring.RefactorResult
import ai.rever.bosseditor.refactoring.WorkspaceEditApplier
import org.jetbrains.kotlin.psi.*
import java.io.File

/**
 * Handles extract method refactoring for Kotlin files.
 *
 * This refactoring:
 * 1. Analyzes the selected code block
 * 2. Identifies variables that need to be parameters
 * 3. Identifies variables that are used after the selection (return value)
 * 4. Generates a new method with appropriate signature
 * 5. Replaces the selection with a method call
 */
class ExtractMethodRefactoring {

    private val logger = EditorLogger.forComponent("ExtractMethodRefactoring")

    /**
     * Executes the extract method refactoring.
     *
     * @param context The refactoring context
     * @param params Parameters including method name and options
     * @return The result of the refactoring
     */
    suspend fun execute(context: RefactorContext, params: ExtractMethodParams): RefactorResult {
        val selection = context.selection
            ?: return RefactorResult.Error("No selection for extract method")

        if (selection.isEmpty) {
            return RefactorResult.Error("Please select code to extract as a method")
        }

        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return RefactorResult.Error("File not found: ${context.filePath}")
            }

            val content = file.readText(Charsets.UTF_8)

            // Analyze the selection
            val analysisResult = analyzeSelection(context.filePath, content, selection)
                ?: return RefactorResult.Error("Selected code cannot be extracted as a method")

            // Validate method name
            val nameError = validateMethodName(params.methodName)
            if (nameError != null) {
                return RefactorResult.Error(nameError)
            }

            // Generate the workspace edit
            val workspaceEdit = generateExtractEdit(
                content = content,
                uri = context.fileUri,
                analysis = analysisResult,
                methodName = params.methodName,
                visibility = params.visibility,
                makeStatic = params.makeStatic
            )

            logger.info(EditorLogCategory.EDITOR, "Extract method completed", mapOf(
                "methodName" to params.methodName,
                "paramCount" to analysisResult.parameters.size.toString()
            ))

            RefactorResult.Success(
                edit = workspaceEdit,
                affectedFiles = 1,
                description = "Extracted method '${params.methodName}' with ${analysisResult.parameters.size} parameters"
            )
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error during extract method", error = e)
            RefactorResult.Error("Extract method failed: ${e.message}")
        }
    }

    /**
     * Generates a preview of the extract method changes.
     */
    suspend fun preview(context: RefactorContext, params: ExtractMethodParams): List<FileChange> {
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
                methodName = params.methodName,
                visibility = params.visibility,
                makeStatic = params.makeStatic
            )

            workspaceEdit.changes?.map { (uri, edits) ->
                FileChange(
                    uri = uri,
                    filePath = WorkspaceEditApplier.uriToFilePath(uri),
                    edits = edits
                )
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error generating extract method preview", error = e)
            emptyList()
        }
    }

    /**
     * Suggests a method name based on the selected code.
     */
    suspend fun suggestMethodName(context: RefactorContext): String {
        val selection = context.selection ?: return "extractedMethod"
        if (selection.isEmpty) return "extractedMethod"

        return try {
            val file = File(context.filePath)
            if (!file.exists()) return "extractedMethod"

            val content = file.readText(Charsets.UTF_8)
            val startOffset = PositionUtils.positionToOffset(content, selection.start)
            val endOffset = PositionUtils.positionToOffset(content, selection.end)
            val selectedText = content.substring(startOffset, endOffset)

            generateSuggestedName(selectedText)
        } catch (e: Exception) {
            "extractedMethod"
        }
    }

    /**
     * Analyzes the selected code to prepare for extraction.
     *
     * This method handles PSI parsing errors gracefully by returning null
     * and logging the error.
     */
    private suspend fun analyzeSelection(
        filePath: String,
        content: String,
        selection: EditorRange
    ): MethodAnalysis? {
        // Validate input
        if (content.isEmpty()) {
            logger.warn(EditorLogCategory.EDITOR, "Empty content for analyzeSelection")
            return null
        }

        val startOffset = PositionUtils.positionToOffset(content, selection.start)
        val endOffset = PositionUtils.positionToOffset(content, selection.end)

        // Validate offsets
        if (startOffset >= endOffset || endOffset > content.length) {
            logger.warn(EditorLogCategory.EDITOR, "Invalid selection range", mapOf(
                "startOffset" to startOffset.toString(),
                "endOffset" to endOffset.toString(),
                "contentLength" to content.length.toString()
            ))
            return null
        }

        val selectedText = content.substring(startOffset, endOffset)

        return try {
            PSIThreadBridge.readAction {
                val ktFile = try {
                    PSIBootstrap.parseKotlinFile(filePath, content)
                } catch (e: Exception) {
                    logger.error(EditorLogCategory.EDITOR, "Failed to parse Kotlin file for extract method", mapOf(
                        "filePath" to filePath
                    ), e)
                    return@readAction null
                }

            // Find the containing function
            val containingFunction = findContainingFunction(ktFile, startOffset)
                ?: return@readAction null

            // Find all statements in the selection
            val selectedStatements = findStatementsInRange(containingFunction, startOffset, endOffset)
            if (selectedStatements.isEmpty()) {
                return@readAction null
            }

            // Analyze variables
            val variableAnalysis = analyzeVariables(
                containingFunction = containingFunction,
                selectedStatements = selectedStatements,
                selectionStart = startOffset,
                selectionEnd = endOffset
            )

            // Get insertion point (after the containing function)
            val insertionOffset = containingFunction.textOffset + containingFunction.textLength

            // Get indentation
            val indentation = getIndentation(content, containingFunction.textOffset)

            MethodAnalysis(
                selectedText = selectedText,
                selectionStart = startOffset,
                selectionEnd = endOffset,
                parameters = variableAnalysis.parameters,
                returnVariables = variableAnalysis.returnVariables,
                localVariables = variableAnalysis.localVariables,
                insertionOffset = insertionOffset,
                indentation = indentation,
                containingFunctionName = containingFunction.name,
                isInClass = containingFunction.parent is KtClassBody
            )
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error in analyzeSelection", mapOf(
                "filePath" to filePath
            ), e)
            null
        }
    }

    /**
     * Finds the function containing the given offset.
     */
    private fun findContainingFunction(ktFile: KtFile, offset: Int): KtNamedFunction? {
        var result: KtNamedFunction? = null

        ktFile.accept(object : KtTreeVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)

                val start = function.textOffset
                val end = start + function.textLength

                if (offset in start..end) {
                    // Prefer the innermost function
                    result = function
                }
            }
        })

        return result
    }

    /**
     * Finds statements that fall within the selection range.
     */
    private fun findStatementsInRange(
        function: KtNamedFunction,
        startOffset: Int,
        endOffset: Int
    ): List<KtExpression> {
        val body = function.bodyBlockExpression ?: return emptyList()
        val statements = mutableListOf<KtExpression>()

        for (statement in body.statements) {
            val stmtStart = statement.textOffset
            val stmtEnd = stmtStart + statement.textLength

            // Include if the statement overlaps with selection
            if (stmtStart < endOffset && stmtEnd > startOffset) {
                statements.add(statement)
            }
        }

        return statements
    }

    /**
     * Analyzes variables used in the selection.
     */
    private fun analyzeVariables(
        containingFunction: KtNamedFunction,
        selectedStatements: List<KtExpression>,
        selectionStart: Int,
        selectionEnd: Int
    ): VariableAnalysis {
        val referencedNames = mutableSetOf<String>()
        val definedInSelection = mutableSetOf<String>()
        val usedAfterSelection = mutableSetOf<String>()

        // Find all referenced names in the selection
        for (statement in selectedStatements) {
            statement.accept(object : KtTreeVisitorVoid() {
                override fun visitReferenceExpression(expression: KtReferenceExpression) {
                    super.visitReferenceExpression(expression)
                    if (expression !is KtNameReferenceExpression) return
                    referencedNames.add(expression.getReferencedName())
                }

                override fun visitProperty(property: KtProperty) {
                    super.visitProperty(property)
                    property.name?.let { definedInSelection.add(it) }
                }
            })
        }

        // Find variables defined before selection and used in selection (parameters)
        val parameters = mutableListOf<ParameterInfo>()
        containingFunction.bodyBlockExpression?.statements?.forEach { statement ->
            if (statement.textOffset < selectionStart) {
                statement.accept(object : KtTreeVisitorVoid() {
                    override fun visitProperty(property: KtProperty) {
                        super.visitProperty(property)
                        val name = property.name ?: return
                        if (name in referencedNames && name !in definedInSelection) {
                            val type = property.typeReference?.text ?: "Any"
                            parameters.add(ParameterInfo(name, type))
                        }
                    }
                })
            }
        }

        // Also check function parameters
        containingFunction.valueParameters.forEach { param ->
            val name = param.name ?: return@forEach
            if (name in referencedNames) {
                val type = param.typeReference?.text ?: "Any"
                parameters.add(ParameterInfo(name, type))
            }
        }

        // Find variables defined in selection and used after (return values)
        containingFunction.bodyBlockExpression?.statements?.forEach { statement ->
            val stmtEnd = statement.textOffset + statement.textLength
            if (stmtEnd > selectionEnd) {
                statement.accept(object : KtTreeVisitorVoid() {
                    override fun visitReferenceExpression(expression: KtReferenceExpression) {
                        super.visitReferenceExpression(expression)
                        if (expression is KtNameReferenceExpression) {
                            val name = expression.getReferencedName()
                            if (name in definedInSelection) {
                                usedAfterSelection.add(name)
                            }
                        }
                    }
                })
            }
        }

        return VariableAnalysis(
            parameters = parameters.distinctBy { it.name },
            returnVariables = usedAfterSelection.toList(),
            localVariables = (definedInSelection - usedAfterSelection).toList()
        )
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
        return content.substring(lineStart, indentEnd)
    }

    /**
     * Generates a suggested method name.
     */
    private fun generateSuggestedName(selectedText: String): String {
        // Look for the main action in the code
        val actionVerbs = listOf(
            "calculate", "compute", "get", "find", "create", "build",
            "process", "handle", "validate", "check", "update", "load"
        )

        for (verb in actionVerbs) {
            if (selectedText.contains(verb, ignoreCase = true)) {
                return verb
            }
        }

        return "extractedMethod"
    }

    /**
     * Validates a method name.
     */
    private fun validateMethodName(name: String): String? {
        if (name.isBlank()) {
            return "Method name cannot be empty"
        }

        if (!name.first().isLetter() && name.first() != '_') {
            return "Method name must start with a letter or underscore"
        }

        if (!name.all { it.isLetterOrDigit() || it == '_' }) {
            return "Method name contains invalid characters"
        }

        val keywords = setOf(
            "val", "var", "fun", "class", "interface", "object", "if", "else",
            "when", "for", "while", "do", "return", "break", "continue", "true",
            "false", "null", "this", "super"
        )

        if (name in keywords) {
            return "'$name' is a reserved keyword"
        }

        return null
    }

    /**
     * Generates the workspace edit for extracting the method.
     */
    private fun generateExtractEdit(
        content: String,
        uri: String,
        analysis: MethodAnalysis,
        methodName: String,
        visibility: String,
        makeStatic: Boolean
    ): WorkspaceEdit {
        val edits = mutableListOf<TextEdit>()

        // Generate method signature
        val visibilityPrefix = if (visibility != "public") "$visibility " else ""
        val paramList = analysis.parameters.joinToString(", ") { "${it.name}: ${it.type}" }

        val returnType = when (analysis.returnVariables.size) {
            0 -> ""
            1 -> ": ${guessTypeFromName(analysis.returnVariables.first())}"
            else -> ": Pair<${analysis.returnVariables.joinToString(", ") { guessTypeFromName(it) }}>"
        }

        val returnStatement = when (analysis.returnVariables.size) {
            0 -> ""
            1 -> "\n${analysis.indentation}    return ${analysis.returnVariables.first()}"
            else -> "\n${analysis.indentation}    return Pair(${analysis.returnVariables.joinToString(", ")})"
        }

        // Build the method body
        val methodBody = buildString {
            append("\n\n")
            append(analysis.indentation)
            append(visibilityPrefix)
            append("fun ")
            append(methodName)
            append("(")
            append(paramList)
            append(")")
            append(returnType)
            append(" {\n")
            // Add the selected code with proper indentation
            val selectedLines = analysis.selectedText.lines()
            for (line in selectedLines) {
                append(analysis.indentation)
                append("    ")
                append(line.trimStart())
                append("\n")
            }
            if (returnStatement.isNotEmpty()) {
                append(returnStatement)
                append("\n")
            }
            append(analysis.indentation)
            append("}")
        }

        // Insert the new method
        val insertionPosition = PositionUtils.offsetToLspPosition(content, analysis.insertionOffset)
        edits.add(TextEdit(
            range = Range(start = insertionPosition, end = insertionPosition),
            newText = methodBody
        ))

        // Build the method call
        val methodCall = buildString {
            when (analysis.returnVariables.size) {
                0 -> {}
                1 -> append("val ${analysis.returnVariables.first()} = ")
                else -> append("val (${analysis.returnVariables.joinToString(", ")}) = ")
            }
            append(methodName)
            append("(")
            append(analysis.parameters.joinToString(", ") { it.name })
            append(")")
        }

        // Replace the selection with the method call
        val selectionStartPos = PositionUtils.offsetToLspPosition(content, analysis.selectionStart)
        val selectionEndPos = PositionUtils.offsetToLspPosition(content, analysis.selectionEnd)
        edits.add(TextEdit(
            range = Range(start = selectionStartPos, end = selectionEndPos),
            newText = methodCall
        ))

        return WorkspaceEdit(changes = mapOf(uri to edits))
    }

    /**
     * Guesses a type from a variable name.
     */
    private fun guessTypeFromName(name: String): String {
        return when {
            name.endsWith("s") -> "List<Any>"
            name.contains("count", ignoreCase = true) -> "Int"
            name.contains("number", ignoreCase = true) -> "Int"
            name.contains("text", ignoreCase = true) -> "String"
            name.contains("name", ignoreCase = true) -> "String"
            name.contains("flag", ignoreCase = true) -> "Boolean"
            name.contains("is", ignoreCase = true) -> "Boolean"
            name.contains("has", ignoreCase = true) -> "Boolean"
            else -> "Any"
        }
    }

    /**
     * Result of method extraction analysis.
     */
    private data class MethodAnalysis(
        val selectedText: String,
        val selectionStart: Int,
        val selectionEnd: Int,
        val parameters: List<ParameterInfo>,
        val returnVariables: List<String>,
        val localVariables: List<String>,
        val insertionOffset: Int,
        val indentation: String,
        val containingFunctionName: String?,
        val isInClass: Boolean
    )

    /**
     * Information about a parameter.
     */
    private data class ParameterInfo(
        val name: String,
        val type: String
    )

    /**
     * Result of variable analysis.
     */
    private data class VariableAnalysis(
        val parameters: List<ParameterInfo>,
        val returnVariables: List<String>,
        val localVariables: List<String>
    )
}
