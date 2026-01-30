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
import ai.rever.bosseditor.refactoring.ChangeSignatureParams
import ai.rever.bosseditor.refactoring.FileChange
import ai.rever.bosseditor.refactoring.ParameterInfo
import ai.rever.bosseditor.refactoring.PositionUtils
import ai.rever.bosseditor.refactoring.RefactorContext
import ai.rever.bosseditor.refactoring.RefactorResult
import ai.rever.bosseditor.refactoring.WorkspaceEditApplier
import org.jetbrains.kotlin.psi.*
import java.io.File

/**
 * Handles change signature refactoring for Kotlin functions.
 *
 * This class provides:
 * - Extracting current function signature
 * - Updating function signature (name, parameters, return type)
 * - Updating all call sites to match new signature
 *
 * @property navigationService Service for navigating Kotlin code
 */
class ChangeSignatureRefactoring(
    private val navigationService: NavigationService
) {
    private val logger = EditorLogger.forComponent("ChangeSignatureRefactoring")
    private val referenceService = ReferenceService.instance

    /**
     * Information about a function's current signature.
     */
    data class SignatureInfo(
        val name: String,
        val parameters: List<ParameterInfo>,
        val returnType: String?,
        val visibility: String?,
        val isInfix: Boolean,
        val isOperator: Boolean,
        val isSuspend: Boolean,
        val filePath: String,
        val signatureStart: Int,
        val signatureEnd: Int,
        val bodyStart: Int?,
        val textOffset: Int,
        val line: Int,
        val column: Int
    )

    /**
     * Extracts the current signature of the function at cursor.
     *
     * @param context The refactoring context
     * @return The signature info or null if not on a function
     */
    suspend fun extractSignature(context: RefactorContext): SignatureInfo? {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) return null

            val content = file.readText(Charsets.UTF_8)
            val offset = PositionUtils.positionToOffset(content, context.position)

            analyzeSignature(context.filePath, content, offset)
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error extracting signature", error = e)
            null
        }
    }

    /**
     * Executes the change signature refactoring.
     *
     * @param context The refactoring context
     * @param params The new signature parameters
     * @return The result of the refactoring
     */
    suspend fun execute(context: RefactorContext, params: ChangeSignatureParams): RefactorResult {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return RefactorResult.Error("File not found: ${context.filePath}")
            }

            val content = file.readText(Charsets.UTF_8)
            val offset = PositionUtils.positionToOffset(content, context.position)

            // Get current signature
            val currentSignature = analyzeSignature(context.filePath, content, offset)
                ?: return RefactorResult.Error("No function at cursor position")

            // Validate new signature
            val validationError = validateNewSignature(params, currentSignature)
            if (validationError != null) {
                return RefactorResult.Error(validationError, recoverable = true)
            }

            // Build definition info for finding references
            val definitionInfo = DefinitionInfo(
                name = currentSignature.name,
                kind = ai.rever.bosseditor.psi.NavigationTargetKind.FUNCTION,
                filePath = context.filePath,
                offset = currentSignature.textOffset,
                line = currentSignature.line,
                column = currentSignature.column
            )

            // Find all call sites
            logger.info(EditorLogCategory.EDITOR, "Finding call sites for signature change", mapOf(
                "function" to currentSignature.name,
                "file" to context.filePath
            ))

            val references = referenceService.findReferences(definitionInfo) { searched, total, fileName ->
                logger.debug(EditorLogCategory.EDITOR, "Scanning file $searched/$total: $fileName")
            }

            // Build workspace edit
            val workspaceEdit = buildSignatureChangeEdit(
                context.filePath,
                content,
                currentSignature,
                params,
                references
            )

            val affectedFiles = workspaceEdit.changes?.size ?: 0
            val callSiteCount = references.size

            logger.info(EditorLogCategory.EDITOR, "Change signature completed", mapOf(
                "function" to currentSignature.name,
                "newName" to (params.newName ?: currentSignature.name),
                "callSites" to callSiteCount.toString(),
                "affectedFiles" to affectedFiles.toString()
            ))

            RefactorResult.Success(
                edit = workspaceEdit,
                affectedFiles = affectedFiles,
                description = buildDescription(currentSignature, params, callSiteCount)
            )
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error during change signature", error = e)
            RefactorResult.Error("Change signature failed: ${e.message}")
        }
    }

    /**
     * Generates a preview of the signature change.
     *
     * @param context The refactoring context
     * @param params The new signature parameters
     * @return List of file changes
     */
    suspend fun preview(context: RefactorContext, params: ChangeSignatureParams): List<FileChange> {
        return try {
            val result = execute(context, params)
            if (result is RefactorResult.Success) {
                result.edit.changes?.map { (uri, edits) ->
                    val filePath = WorkspaceEditApplier.uriToFilePath(uri)
                    val fileContent = File(filePath).readText(Charsets.UTF_8)

                    FileChange(
                        uri = uri,
                        filePath = filePath,
                        edits = edits
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error generating change signature preview", error = e)
            emptyList()
        }
    }

    /**
     * Analyzes the function at the given offset.
     *
     * This method handles PSI parsing errors gracefully by returning null
     * and logging the error.
     */
    private suspend fun analyzeSignature(
        filePath: String,
        content: String,
        offset: Int
    ): SignatureInfo? {
        // Validate input
        if (content.isEmpty() || offset < 0 || offset > content.length) {
            logger.warn(EditorLogCategory.EDITOR, "Invalid input for analyzeSignature", mapOf(
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
                    logger.error(EditorLogCategory.EDITOR, "Failed to parse Kotlin file for change signature", mapOf(
                        "filePath" to filePath
                    ), e)
                    return@readAction null
                }
                val element = ktFile.findElementAt(offset) ?: return@readAction null

            // Walk up to find a function
            var current: org.jetbrains.kotlin.com.intellij.psi.PsiElement? = element

            while (current != null) {
                if (current is KtNamedFunction) {
                    val name = current.name ?: return@readAction null
                    val parameters = current.valueParameters.map { param ->
                        ParameterInfo(
                            name = param.name ?: "",
                            type = param.typeReference?.text ?: "Any",
                            defaultValue = param.defaultValue?.text,
                            isVararg = param.isVarArg
                        )
                    }

                    val returnType = current.typeReference?.text
                    val visibility = current.modifierList?.let { modifiers ->
                        when {
                            modifiers.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.PUBLIC_KEYWORD) -> "public"
                            modifiers.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.PRIVATE_KEYWORD) -> "private"
                            modifiers.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.PROTECTED_KEYWORD) -> "protected"
                            modifiers.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.INTERNAL_KEYWORD) -> "internal"
                            else -> null
                        }
                    }

                    // Calculate positions
                    val funKeyword = current.funKeyword
                    val signatureStart = funKeyword?.textOffset ?: current.textOffset

                    // Signature ends at the body start or at the end of return type
                    val body = current.bodyExpression
                    val signatureEnd = when {
                        body != null -> body.textOffset - 1
                        current.typeReference != null -> current.typeReference!!.textOffset + current.typeReference!!.textLength
                        current.valueParameterList != null -> {
                            val paramList = current.valueParameterList!!
                            paramList.textOffset + paramList.textLength
                        }
                        else -> current.textOffset + current.textLength
                    }

                    val line = content.substring(0, current.textOffset.coerceAtMost(content.length))
                        .count { it == '\n' } + 1
                    val lastNewline = content.lastIndexOf('\n', current.textOffset - 1)
                    val column = if (lastNewline < 0) current.textOffset + 1 else current.textOffset - lastNewline

                    return@readAction SignatureInfo(
                        name = name,
                        parameters = parameters,
                        returnType = returnType,
                        visibility = visibility,
                        isInfix = current.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.INFIX_KEYWORD),
                        isOperator = current.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.OPERATOR_KEYWORD),
                        isSuspend = current.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.SUSPEND_KEYWORD),
                        filePath = filePath,
                        signatureStart = signatureStart,
                        signatureEnd = signatureEnd,
                        bodyStart = body?.textOffset,
                        textOffset = current.textOffset,
                        line = line,
                        column = column
                    )
                }
                current = current.parent
            }

            null
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error in analyzeSignature", mapOf(
                "filePath" to filePath,
                "offset" to offset.toString()
            ), e)
            null
        }
    }

    /**
     * Validates the new signature parameters.
     */
    private fun validateNewSignature(params: ChangeSignatureParams, current: SignatureInfo): String? {
        // Validate new name if provided
        if (params.newName != null) {
            if (params.newName.isBlank()) {
                return "Function name cannot be empty"
            }
            if (!isValidKotlinIdentifier(params.newName)) {
                return "Invalid function name: '${params.newName}'"
            }
        }

        // Validate parameters
        for (param in params.newParameters) {
            if (param.name.isBlank()) {
                return "Parameter name cannot be empty"
            }
            if (!isValidKotlinIdentifier(param.name)) {
                return "Invalid parameter name: '${param.name}'"
            }
            if (param.type.isBlank()) {
                return "Parameter type cannot be empty for '${param.name}'"
            }
        }

        // Check for duplicate parameter names
        val paramNames = params.newParameters.map { it.name }
        val duplicates = paramNames.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            return "Duplicate parameter name: '${duplicates.first()}'"
        }

        return null
    }

    /**
     * Builds the workspace edit for signature change.
     */
    private suspend fun buildSignatureChangeEdit(
        filePath: String,
        content: String,
        currentSignature: SignatureInfo,
        params: ChangeSignatureParams,
        references: List<ReferenceLocation>
    ): WorkspaceEdit {
        val changes = mutableMapOf<String, MutableList<TextEdit>>()

        // 1. Update the function declaration
        val newSignature = buildNewSignature(currentSignature, params)
        val declUri = WorkspaceEditApplier.filePathToUri(filePath)

        val signatureStartPos = PositionUtils.offsetToLspPosition(content, currentSignature.signatureStart)
        val signatureEndPos = if (currentSignature.bodyStart != null) {
            // Find the position just before the body (including any whitespace)
            val bodyStartOffset = currentSignature.bodyStart
            var endOffset = bodyStartOffset - 1
            // Skip whitespace before body
            while (endOffset > currentSignature.signatureStart && content[endOffset].isWhitespace()) {
                endOffset--
            }
            PositionUtils.offsetToLspPosition(content, endOffset + 1)
        } else {
            PositionUtils.offsetToLspPosition(content, currentSignature.signatureEnd)
        }

        changes.getOrPut(declUri) { mutableListOf() }.add(
            TextEdit(
                range = Range(start = signatureStartPos, end = signatureEndPos),
                newText = newSignature
            )
        )

        // 2. Update all call sites if parameters changed or renamed
        if (params.newName != null || hasParameterChanges(currentSignature.parameters, params.newParameters)) {
            for (ref in references) {
                val refUri = WorkspaceEditApplier.filePathToUri(ref.filePath)
                val refContent = if (ref.filePath == filePath) {
                    content
                } else {
                    File(ref.filePath).readText()
                }

                val callEdit = buildCallSiteEdit(refContent, ref, currentSignature, params)
                if (callEdit != null) {
                    changes.getOrPut(refUri) { mutableListOf() }.add(callEdit)
                }
            }
        }

        return WorkspaceEdit(changes = changes)
    }

    /**
     * Builds the new signature string.
     */
    private fun buildNewSignature(current: SignatureInfo, params: ChangeSignatureParams): String {
        return buildString {
            // Visibility
            val visibility = params.newVisibility ?: current.visibility
            if (visibility != null && visibility != "public") {
                append(visibility)
                append(" ")
            }

            // Modifiers
            if (current.isSuspend) append("suspend ")
            if (current.isInfix) append("infix ")
            if (current.isOperator) append("operator ")

            // fun keyword and name
            append("fun ")
            append(params.newName ?: current.name)

            // Parameters
            append("(")
            append(params.newParameters.joinToString(", ") { param ->
                buildString {
                    if (param.isVararg) append("vararg ")
                    append(param.name)
                    append(": ")
                    append(param.type)
                    if (param.defaultValue != null) {
                        append(" = ")
                        append(param.defaultValue)
                    }
                }
            })
            append(")")

            // Return type
            val returnType = params.newReturnType ?: current.returnType
            if (returnType != null && returnType != "Unit") {
                append(": ")
                append(returnType)
            }
        }
    }

    /**
     * Checks if parameters have changed.
     */
    private fun hasParameterChanges(old: List<ParameterInfo>, new: List<ParameterInfo>): Boolean {
        if (old.size != new.size) return true

        for (i in old.indices) {
            if (old[i].name != new[i].name) return true
            // Note: We don't check type changes for call sites, only name/order
        }

        return false
    }

    /**
     * Builds a text edit for a call site.
     */
    private suspend fun buildCallSiteEdit(
        content: String,
        ref: ReferenceLocation,
        currentSignature: SignatureInfo,
        params: ChangeSignatureParams
    ): TextEdit? {
        // Convert 1-based to 0-based
        val line = ref.line - 1
        val column = ref.column - 1

        val offset = PositionUtils.positionToOffset(content, line, column)
        if (offset < 0 || offset >= content.length) return null

        // Find the call expression
        val callInfo = findCallExpression(content, offset, currentSignature.name)
            ?: return null

        // Build the new call
        val newCall = buildNewCall(currentSignature, params, callInfo)

        val startPos = PositionUtils.offsetToLspPosition(content, callInfo.start)
        val endPos = PositionUtils.offsetToLspPosition(content, callInfo.end)

        return TextEdit(
            range = Range(start = startPos, end = endPos),
            newText = newCall
        )
    }

    /**
     * Information about a function call.
     */
    private data class CallInfo(
        val start: Int,
        val end: Int,
        val arguments: List<String>
    )

    /**
     * Finds a function call expression at the given offset.
     *
     * Uses word boundary checking to avoid matching partial names
     * (e.g., "getValue" shouldn't match when looking for "get").
     */
    private fun findCallExpression(content: String, offset: Int, functionName: String): CallInfo? {
        // Use regex with word boundaries to find the function name
        val pattern = "\\b${Regex.escape(functionName)}\\s*\\(".toRegex()
        
        // Search forward from offset
        val forwardMatch = pattern.find(content, offset.coerceAtLeast(0))
        if (forwardMatch != null && forwardMatch.range.first <= offset + functionName.length) {
            return parseCallAt(content, forwardMatch.range.first, functionName)
        }
        
        // Search backwards from offset
        val searchStart = (offset - functionName.length * 2).coerceAtLeast(0)
        val searchRegion = content.substring(searchStart, (offset + functionName.length + 10).coerceAtMost(content.length))
        val backwardMatch = pattern.find(searchRegion)
        if (backwardMatch != null) {
            val absoluteStart = searchStart + backwardMatch.range.first
            if (absoluteStart <= offset && absoluteStart + functionName.length >= offset) {
                return parseCallAt(content, absoluteStart, functionName)
            }
        }
        
        return null
    }

    /**
     * Parses a function call at the given position.
     */
    private fun parseCallAt(content: String, nameStart: Int, functionName: String): CallInfo? {
        val afterName = nameStart + functionName.length

        // Skip whitespace
        var pos = afterName
        while (pos < content.length && content[pos].isWhitespace()) pos++

        // Check for opening parenthesis
        if (pos >= content.length || content[pos] != '(') {
            // Might be just a reference, not a call
            return CallInfo(
                start = nameStart,
                end = nameStart + functionName.length,
                arguments = emptyList()
            )
        }

        // Find matching closing parenthesis
        val argsStart = pos + 1
        var depth = 1
        pos++

        while (pos < content.length && depth > 0) {
            when (content[pos]) {
                '(' -> depth++
                ')' -> depth--
                '"' -> {
                    // Skip string
                    pos++
                    while (pos < content.length && content[pos] != '"') {
                        if (content[pos] == '\\') pos++
                        pos++
                    }
                }
            }
            pos++
        }

        val argsEnd = pos - 1
        val argsText = content.substring(argsStart, argsEnd)
        val arguments = parseArguments(argsText)

        return CallInfo(
            start = nameStart,
            end = pos,
            arguments = arguments
        )
    }

    /**
     * Parses comma-separated arguments.
     */
    private fun parseArguments(argsText: String): List<String> {
        if (argsText.isBlank()) return emptyList()

        val arguments = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0
        var inString = false

        for (char in argsText) {
            when {
                char == '"' && !inString -> inString = true
                char == '"' && inString -> inString = false
                char == '(' && !inString -> depth++
                char == ')' && !inString -> depth--
                char == ',' && depth == 0 && !inString -> {
                    arguments.add(current.toString().trim())
                    current = StringBuilder()
                    continue
                }
            }
            current.append(char)
        }

        if (current.isNotBlank()) {
            arguments.add(current.toString().trim())
        }

        return arguments
    }

    /**
     * Builds the new function call with reordered/renamed arguments.
     */
    private fun buildNewCall(
        currentSignature: SignatureInfo,
        params: ChangeSignatureParams,
        callInfo: CallInfo
    ): String {
        val newName = params.newName ?: currentSignature.name

        if (callInfo.arguments.isEmpty()) {
            // No arguments - just update name if needed
            return if (params.newParameters.isEmpty()) {
                "$newName()"
            } else {
                // Need to add default values for new required parameters
                val args = params.newParameters.mapNotNull { param ->
                    param.defaultValue ?: "TODO()"
                }
                "$newName(${args.joinToString(", ")})"
            }
        }

        // Map old arguments to new parameter positions
        val newArgs = mutableListOf<String>()

        for (newParam in params.newParameters) {
            // Find the old parameter index
            val oldIndex = currentSignature.parameters.indexOfFirst { it.name == newParam.name }

            if (oldIndex >= 0 && oldIndex < callInfo.arguments.size) {
                // Reuse the old argument
                newArgs.add(callInfo.arguments[oldIndex])
            } else if (newParam.defaultValue != null) {
                // Use default value (omit from call)
                continue
            } else {
                // New required parameter - add placeholder
                newArgs.add("TODO()")
            }
        }

        return "$newName(${newArgs.joinToString(", ")})"
    }

    /**
     * Builds a description of the changes.
     */
    private fun buildDescription(
        current: SignatureInfo,
        params: ChangeSignatureParams,
        callSiteCount: Int
    ): String {
        val changes = mutableListOf<String>()

        if (params.newName != null && params.newName != current.name) {
            changes.add("renamed to '${params.newName}'")
        }

        val paramCountDiff = params.newParameters.size - current.parameters.size
        when {
            paramCountDiff > 0 -> changes.add("added $paramCountDiff parameter(s)")
            paramCountDiff < 0 -> changes.add("removed ${-paramCountDiff} parameter(s)")
        }

        if (params.newReturnType != null && params.newReturnType != current.returnType) {
            changes.add("return type changed to '${params.newReturnType}'")
        }

        val changesDesc = if (changes.isEmpty()) "signature updated" else changes.joinToString(", ")

        return "Changed signature of '${current.name}': $changesDesc ($callSiteCount call site(s) updated)"
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
}
