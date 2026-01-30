package ai.rever.bosseditor.refactoring.psi

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.logging.EditorLogger
import ai.rever.bosseditor.logging.EditorLogCategory
import ai.rever.bosseditor.psi.DefinitionInfo
import ai.rever.bosseditor.psi.NavigationService
import ai.rever.bosseditor.psi.NavigationTargetKind
import ai.rever.bosseditor.psi.PSIBootstrap
import ai.rever.bosseditor.psi.PSIThreadBridge
import ai.rever.bosseditor.refactoring.*
import java.io.File

/**
 * PSI-based refactoring provider for Kotlin files.
 *
 * Uses the JetBrains Kotlin compiler's PSI (Program Structure Interface)
 * to analyze and refactor Kotlin code with full semantic understanding.
 *
 * @property navigationService Service for navigating and analyzing Kotlin code
 */
class PSIRefactoringProvider(
    private val navigationService: NavigationService = NavigationService()
) : RefactoringProvider {

    private val logger = EditorLogger.forComponent("PSIRefactoringProvider")

    private val renameRefactoring = RenameRefactoring(navigationService)
    private val inlineRefactoring = InlineRefactoring()
    private val safeDeleteRefactoring = SafeDeleteRefactoring(navigationService)
    private val changeSignatureRefactoring = ChangeSignatureRefactoring(navigationService)

    override val supportedExtensions: Set<String> = setOf("kt", "kts")

    override suspend fun getAvailableRefactorings(context: RefactorContext): List<RefactorAvailability> {
        val results = mutableListOf<RefactorAvailability>()

        // Check rename availability
        val renameAvailable = checkRenameAvailable(context)
        results.add(RefactorAvailability(
            kind = RefactorKind.RENAME,
            available = renameAvailable.first,
            reason = renameAvailable.second
        ))

        // Check extract variable (requires selection)
        val extractVarAvailable = checkExtractVariableAvailable(context)
        results.add(RefactorAvailability(
            kind = RefactorKind.EXTRACT_VARIABLE,
            available = extractVarAvailable.first,
            reason = extractVarAvailable.second
        ))

        // Check extract method (requires selection)
        val extractMethodAvailable = checkExtractMethodAvailable(context)
        results.add(RefactorAvailability(
            kind = RefactorKind.EXTRACT_METHOD,
            available = extractMethodAvailable.first,
            reason = extractMethodAvailable.second
        ))

        // Extract constant (requires selection of literal)
        results.add(RefactorAvailability(
            kind = RefactorKind.EXTRACT_CONSTANT,
            available = false,
            reason = "Not yet implemented"
        ))

        // Inline (requires cursor on variable/method)
        val inlineAvailable = checkInlineAvailable(context)
        results.add(RefactorAvailability(
            kind = RefactorKind.INLINE,
            available = inlineAvailable.first,
            reason = inlineAvailable.second
        ))

        // Move
        results.add(RefactorAvailability(
            kind = RefactorKind.MOVE,
            available = false,
            reason = "Not yet implemented"
        ))

        // Change signature (requires cursor on function)
        val changeSignatureAvailable = checkChangeSignatureAvailable(context)
        results.add(RefactorAvailability(
            kind = RefactorKind.CHANGE_SIGNATURE,
            available = changeSignatureAvailable.first,
            reason = changeSignatureAvailable.second
        ))

        // Safe delete (requires cursor on declaration)
        val safeDeleteAvailable = checkSafeDeleteAvailable(context)
        results.add(RefactorAvailability(
            kind = RefactorKind.SAFE_DELETE,
            available = safeDeleteAvailable.first,
            reason = safeDeleteAvailable.second
        ))

        // Introduce parameter
        results.add(RefactorAvailability(
            kind = RefactorKind.INTRODUCE_PARAMETER,
            available = false,
            reason = "Not yet implemented"
        ))

        return results
    }

    override suspend fun prepare(kind: RefactorKind, context: RefactorContext): PrepareResult {
        return when (kind) {
            RefactorKind.RENAME -> prepareRename(context)
            RefactorKind.EXTRACT_VARIABLE -> prepareExtractVariable(context)
            RefactorKind.EXTRACT_METHOD -> prepareExtractMethod(context)
            RefactorKind.INLINE -> prepareInline(context)
            RefactorKind.SAFE_DELETE -> prepareSafeDelete(context)
            RefactorKind.CHANGE_SIGNATURE -> prepareChangeSignature(context)
            else -> PrepareResult.NotAvailable("Refactoring not yet implemented: $kind")
        }
    }

    override suspend fun execute(kind: RefactorKind, context: RefactorContext, params: Any?): RefactorResult {
        return when (kind) {
            RefactorKind.RENAME -> {
                val renameParams = params as? RenameParams
                    ?: return RefactorResult.Error("Invalid rename parameters")
                renameRefactoring.execute(context, renameParams.newName)
            }
            RefactorKind.EXTRACT_VARIABLE -> {
                RefactorResult.Error("Extract variable not yet implemented")
            }
            RefactorKind.EXTRACT_METHOD -> {
                RefactorResult.Error("Extract method not yet implemented")
            }
            RefactorKind.INLINE -> {
                inlineRefactoring.execute(context)
            }
            RefactorKind.SAFE_DELETE -> {
                val safeDeleteParams = params as? SafeDeleteParams
                    ?: SafeDeleteParams(forceDelete = false)
                safeDeleteRefactoring.execute(context, safeDeleteParams)
            }
            RefactorKind.CHANGE_SIGNATURE -> {
                val changeParams = params as? ChangeSignatureParams
                    ?: return RefactorResult.Error("Invalid change signature parameters")
                changeSignatureRefactoring.execute(context, changeParams)
            }
            else -> RefactorResult.Error("Refactoring not yet implemented: $kind")
        }
    }

    override suspend fun preview(kind: RefactorKind, context: RefactorContext, params: Any?): List<FileChange> {
        return when (kind) {
            RefactorKind.RENAME -> {
                val renameParams = params as? RenameParams ?: return emptyList()
                renameRefactoring.preview(context, renameParams.newName)
            }
            RefactorKind.INLINE -> {
                inlineRefactoring.preview(context)
            }
            RefactorKind.SAFE_DELETE -> {
                safeDeleteRefactoring.preview(context)
            }
            RefactorKind.CHANGE_SIGNATURE -> {
                val changeParams = params as? ChangeSignatureParams ?: return emptyList()
                changeSignatureRefactoring.preview(context, changeParams)
            }
            else -> emptyList()
        }
    }

    override suspend fun validateRename(newName: String, context: RefactorContext): String? {
        return renameRefactoring.validateNewName(newName, context)
    }

    /**
     * Checks if rename is available at the current context.
     */
    private suspend fun checkRenameAvailable(context: RefactorContext): Pair<Boolean, String?> {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return false to "File not found"
            }

            val content = file.readText()
            val offset = positionToOffset(content, context.position)

            val definitionInfo = PSIThreadBridge.readAction {
                val ktFile = PSIBootstrap.parseKotlinFile(context.filePath, content)
                navigationService.getDefinitionInfo(ktFile, offset, context.filePath)
            }

            if (definitionInfo != null) {
                true to null
            } else {
                // Check if we're on a reference that can be renamed
                val symbolInfo = getSymbolInfoAtOffset(context.filePath, content, offset)
                if (symbolInfo != null) {
                    true to null
                } else {
                    false to "Cursor must be on a symbol name"
                }
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error checking rename availability", error = e)
            false to "Error analyzing code: ${e.message}"
        }
    }

    /**
     * Checks if extract variable is available at the current context.
     */
    private fun checkExtractVariableAvailable(context: RefactorContext): Pair<Boolean, String?> {
        val selection = context.selection
        if (selection == null || selection.isEmpty) {
            return false to "Select an expression to extract"
        }
        // TODO: More sophisticated check to verify selection is a valid expression
        return false to "Not yet implemented"
    }

    /**
     * Checks if extract method is available at the current context.
     */
    private fun checkExtractMethodAvailable(context: RefactorContext): Pair<Boolean, String?> {
        val selection = context.selection
        if (selection == null || selection.isEmpty) {
            return false to "Select code to extract as method"
        }
        // TODO: More sophisticated check to verify selection is valid for extraction
        return false to "Not yet implemented"
    }

    /**
     * Prepares a rename refactoring.
     */
    private suspend fun prepareRename(context: RefactorContext): PrepareResult {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return PrepareResult.NotAvailable("File not found")
            }

            val content = file.readText()
            val offset = positionToOffset(content, context.position)

            val symbolInfo = getSymbolInfoAtOffset(context.filePath, content, offset)
            if (symbolInfo != null) {
                PrepareResult.Ready(
                    currentName = symbolInfo.name,
                    symbolKind = mapNavigationKindToSymbolKind(symbolInfo.kind),
                    symbolRange = symbolInfo.range
                )
            } else {
                PrepareResult.NotAvailable("No symbol found at cursor position")
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error preparing rename", error = e)
            PrepareResult.Error("Error analyzing code: ${e.message}")
        }
    }

    /**
     * Prepares an extract variable refactoring.
     */
    private fun prepareExtractVariable(context: RefactorContext): PrepareResult {
        return PrepareResult.NotAvailable("Extract variable not yet implemented")
    }

    /**
     * Prepares an extract method refactoring.
     */
    private fun prepareExtractMethod(context: RefactorContext): PrepareResult {
        return PrepareResult.NotAvailable("Extract method not yet implemented")
    }

    /**
     * Checks if inline is available at the current context.
     */
    private suspend fun checkInlineAvailable(context: RefactorContext): Pair<Boolean, String?> {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return false to "File not found"
            }

            val content = file.readText()
            val offset = positionToOffset(content, context.position)

            // Check if cursor is on a local variable
            val canInline = PSIThreadBridge.readAction {
                val ktFile = PSIBootstrap.parseKotlinFile(context.filePath, content)
                val element = ktFile.findElementAt(offset) ?: return@readAction false

                var current: org.jetbrains.kotlin.com.intellij.psi.PsiElement? = element
                while (current != null) {
                    when (current) {
                        is org.jetbrains.kotlin.psi.KtProperty -> {
                            // Local variable with initializer
                            if (current.isLocal && current.initializer != null) {
                                return@readAction true
                            }
                        }
                        is org.jetbrains.kotlin.psi.KtNameReferenceExpression -> {
                            // Could be a reference to a local variable
                            return@readAction true
                        }
                    }
                    current = current.parent
                }
                false
            }

            if (canInline) {
                true to null
            } else {
                false to "Place cursor on a local variable or reference"
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error checking inline availability", error = e)
            false to "Error analyzing code: ${e.message}"
        }
    }

    /**
     * Prepares an inline refactoring.
     */
    private suspend fun prepareInline(context: RefactorContext): PrepareResult {
        val available = checkInlineAvailable(context)
        return if (available.first) {
            PrepareResult.Ready(
                currentName = "variable",
                symbolKind = SymbolKind.VARIABLE,
                symbolRange = EditorRange(context.position, context.position)
            )
        } else {
            PrepareResult.NotAvailable(available.second ?: "Inline not available")
        }
    }

    /**
     * Checks if safe delete is available at the current context.
     */
    private suspend fun checkSafeDeleteAvailable(context: RefactorContext): Pair<Boolean, String?> {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return false to "File not found"
            }

            val content = file.readText()
            val offset = positionToOffset(content, context.position)

            val canDelete = PSIThreadBridge.readAction {
                val ktFile = PSIBootstrap.parseKotlinFile(context.filePath, content)
                val element = ktFile.findElementAt(offset) ?: return@readAction false

                var current: org.jetbrains.kotlin.com.intellij.psi.PsiElement? = element
                while (current != null) {
                    when (current) {
                        is org.jetbrains.kotlin.psi.KtNamedFunction,
                        is org.jetbrains.kotlin.psi.KtProperty,
                        is org.jetbrains.kotlin.psi.KtClass,
                        is org.jetbrains.kotlin.psi.KtObjectDeclaration,
                        is org.jetbrains.kotlin.psi.KtTypeAlias -> {
                            return@readAction true
                        }
                    }
                    current = current.parent
                }
                false
            }

            if (canDelete) {
                true to null
            } else {
                false to "Place cursor on a deletable declaration"
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error checking safe delete availability", error = e)
            false to "Error analyzing code: ${e.message}"
        }
    }

    /**
     * Prepares a safe delete refactoring.
     */
    private suspend fun prepareSafeDelete(context: RefactorContext): PrepareResult {
        return try {
            val checkResult = safeDeleteRefactoring.checkSafeDelete(context)
            when (checkResult) {
                is SafeDeleteRefactoring.SafeDeleteCheckResult.Safe -> {
                    PrepareResult.Ready(
                        currentName = checkResult.deleteInfo.name,
                        symbolKind = mapKindString(checkResult.deleteInfo.kind),
                        symbolRange = EditorRange(context.position, context.position)
                    )
                }
                is SafeDeleteRefactoring.SafeDeleteCheckResult.HasUsages -> {
                    PrepareResult.Ready(
                        currentName = checkResult.deleteInfo.name,
                        symbolKind = mapKindString(checkResult.deleteInfo.kind),
                        symbolRange = EditorRange(context.position, context.position)
                    )
                }
                is SafeDeleteRefactoring.SafeDeleteCheckResult.CannotDelete -> {
                    PrepareResult.NotAvailable(checkResult.reason)
                }
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error preparing safe delete", error = e)
            PrepareResult.Error("Error: ${e.message}")
        }
    }

    /**
     * Checks if change signature is available at the current context.
     */
    private suspend fun checkChangeSignatureAvailable(context: RefactorContext): Pair<Boolean, String?> {
        return try {
            val file = File(context.filePath)
            if (!file.exists()) {
                return false to "File not found"
            }

            val content = file.readText()
            val offset = positionToOffset(content, context.position)

            val isFunction = PSIThreadBridge.readAction {
                val ktFile = PSIBootstrap.parseKotlinFile(context.filePath, content)
                val element = ktFile.findElementAt(offset) ?: return@readAction false

                var current: org.jetbrains.kotlin.com.intellij.psi.PsiElement? = element
                while (current != null) {
                    if (current is org.jetbrains.kotlin.psi.KtNamedFunction) {
                        return@readAction true
                    }
                    current = current.parent
                }
                false
            }

            if (isFunction) {
                true to null
            } else {
                false to "Place cursor on a function"
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error checking change signature availability", error = e)
            false to "Error analyzing code: ${e.message}"
        }
    }

    /**
     * Prepares a change signature refactoring.
     */
    private suspend fun prepareChangeSignature(context: RefactorContext): PrepareResult {
        return try {
            val signatureInfo = changeSignatureRefactoring.extractSignature(context)
            if (signatureInfo != null) {
                PrepareResult.Ready(
                    currentName = signatureInfo.name,
                    symbolKind = SymbolKind.FUNCTION,
                    symbolRange = EditorRange(context.position, context.position)
                )
            } else {
                PrepareResult.NotAvailable("Place cursor on a function")
            }
        } catch (e: Exception) {
            logger.error(EditorLogCategory.EDITOR, "Error preparing change signature", error = e)
            PrepareResult.Error("Error: ${e.message}")
        }
    }

    /**
     * Maps a kind string to SymbolKind.
     */
    private fun mapKindString(kind: String): SymbolKind {
        return when (kind.lowercase()) {
            "function" -> SymbolKind.FUNCTION
            "property", "local variable" -> SymbolKind.PROPERTY
            "class", "data class" -> SymbolKind.CLASS
            "interface" -> SymbolKind.INTERFACE
            "object" -> SymbolKind.OBJECT
            "enum" -> SymbolKind.ENUM
            "typealias" -> SymbolKind.TYPE_ALIAS
            "parameter" -> SymbolKind.PARAMETER
            else -> SymbolKind.UNKNOWN
        }
    }

    /**
     * Gets symbol information at the given offset.
     */
    private suspend fun getSymbolInfoAtOffset(
        filePath: String,
        content: String,
        offset: Int
    ): SymbolInfo? {
        return PSIThreadBridge.readAction {
            val ktFile = PSIBootstrap.parseKotlinFile(filePath, content)

            // First check if we're on a definition
            val definitionInfo = navigationService.getDefinitionInfo(ktFile, offset, filePath)
            if (definitionInfo != null) {
                return@readAction SymbolInfo(
                    name = definitionInfo.name,
                    kind = definitionInfo.kind,
                    range = calculateSymbolRange(content, definitionInfo.offset, definitionInfo.name)
                )
            }

            // Check if we're on a reference
            val element = ktFile.findElementAt(offset)
            if (element != null) {
                val parent = element.parent
                if (parent is org.jetbrains.kotlin.psi.KtNameReferenceExpression) {
                    val name = parent.getReferencedName()
                    val textOffset = parent.textOffset
                    return@readAction SymbolInfo(
                        name = name,
                        kind = NavigationTargetKind.UNKNOWN,
                        range = calculateSymbolRange(content, textOffset, name)
                    )
                }
            }

            null
        }
    }

    /**
     * Calculates the editor range for a symbol.
     */
    private fun calculateSymbolRange(content: String, offset: Int, name: String): EditorRange {
        val lines = content.lines()
        var lineStart = 0
        var lineNumber = 0

        for (line in lines) {
            val lineEnd = lineStart + line.length
            if (offset in lineStart..lineEnd) {
                val column = offset - lineStart
                return EditorRange(
                    EditorPosition(lineNumber, column),
                    EditorPosition(lineNumber, column + name.length)
                )
            }
            lineStart = lineEnd + 1 // +1 for newline
            lineNumber++
        }

        // Fallback
        return EditorRange(
            EditorPosition(0, 0),
            EditorPosition(0, name.length)
        )
    }

    /**
     * Converts an EditorPosition to a character offset.
     */
    private fun positionToOffset(content: String, position: EditorPosition): Int {
        val lines = content.lines()
        var offset = 0
        for (i in 0 until position.line.coerceAtMost(lines.size)) {
            offset += lines[i].length + 1 // +1 for newline
        }
        if (position.line < lines.size) {
            offset += position.column.coerceAtMost(lines[position.line].length)
        }
        return offset
    }

    /**
     * Maps NavigationTargetKind to SymbolKind.
     */
    private fun mapNavigationKindToSymbolKind(kind: NavigationTargetKind): SymbolKind {
        return when (kind) {
            NavigationTargetKind.CLASS -> SymbolKind.CLASS
            NavigationTargetKind.INTERFACE -> SymbolKind.INTERFACE
            NavigationTargetKind.OBJECT -> SymbolKind.OBJECT
            NavigationTargetKind.FUNCTION -> SymbolKind.FUNCTION
            NavigationTargetKind.PROPERTY -> SymbolKind.PROPERTY
            NavigationTargetKind.PARAMETER -> SymbolKind.PARAMETER
            NavigationTargetKind.VARIABLE -> SymbolKind.VARIABLE
            NavigationTargetKind.TYPE_ALIAS -> SymbolKind.TYPE_ALIAS
            NavigationTargetKind.CONSTRUCTOR -> SymbolKind.METHOD
            NavigationTargetKind.UNKNOWN -> SymbolKind.UNKNOWN
        }
    }

    /**
     * Symbol information at cursor.
     */
    private data class SymbolInfo(
        val name: String,
        val kind: NavigationTargetKind,
        val range: EditorRange
    )
}
