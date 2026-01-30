package ai.rever.bosseditor.psi

import ai.rever.bosseditor.lsp.logging.LogCategory
import ai.rever.bosseditor.lsp.logging.LspLogger
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.psi.*
import java.io.File

/**
 * Navigation target representing where a symbol is defined.
 *
 * @property filePath Absolute path to the file containing the definition
 * @property offset Character offset within the file
 * @property line Line number (1-based)
 * @property column Column number (1-based)
 * @property name Name of the symbol
 * @property kind Kind of symbol (class, function, property, etc.)
 */
data class NavigationTarget(
    val filePath: String,
    val offset: Int,
    val line: Int,
    val column: Int,
    val name: String,
    val kind: NavigationTargetKind
)

/**
 * Kind of navigation target.
 */
enum class NavigationTargetKind {
    CLASS,
    INTERFACE,
    OBJECT,
    FUNCTION,
    PROPERTY,
    PARAMETER,
    VARIABLE,
    TYPE_ALIAS,
    CONSTRUCTOR,
    UNKNOWN
}

/**
 * Information about a definition (class, function, property, etc.).
 *
 * @property name Name of the symbol
 * @property kind Kind of symbol
 * @property filePath Absolute path to the file containing the definition
 * @property offset Character offset within the file
 * @property line Line number (1-based)
 * @property column Column number (1-based)
 */
data class DefinitionInfo(
    val name: String,
    val kind: NavigationTargetKind,
    val filePath: String,
    val offset: Int,
    val line: Int,
    val column: Int
)

/**
 * Result of a navigation lookup.
 */
sealed class NavigationResult {
    /**
     * Navigation succeeded - target found.
     */
    data class Found(val target: NavigationTarget) : NavigationResult()

    /**
     * Multiple targets found (e.g., overloaded methods).
     */
    data class MultipleTargets(val targets: List<NavigationTarget>) : NavigationResult()

    /**
     * No navigable element at the position.
     */
    object NotNavigable : NavigationResult()

    /**
     * Navigation failed with an error.
     */
    data class Error(val message: String) : NavigationResult()
}

/**
 * Navigation service for go-to-definition and find references.
 *
 * This service provides:
 * - Go-to-definition for Kotlin symbols
 * - Symbol resolution via PSI references
 * - Navigation target extraction
 *
 * Thread Safety: All public methods should be called within a PSI read action.
 * Use PSIThreadBridge.readAction { } to wrap calls.
 */
class NavigationService {
    private val logger = LspLogger.forComponent("NavigationService")

    /**
     * Go to the definition of the symbol at the given offset.
     *
     * @param file The Kotlin PSI file containing the reference
     * @param offset Character offset where the user clicked
     * @param sourceFilePath Absolute path of the source file (where user clicked)
     * @return Navigation result
     */
    fun goToDefinition(file: KtFile, offset: Int, sourceFilePath: String = ""): NavigationResult {
        return try {
            // Find element at position
            val element = file.findElementAt(offset)
                ?: return NavigationResult.NotNavigable

            // Try to find a reference at this position
            val target = findNavigationTarget(element, file, sourceFilePath)
                ?: return NavigationResult.NotNavigable

            NavigationResult.Found(target)

        } catch (e: Exception) {
            NavigationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Check if the element at the given offset is a definition (not a reference).
     *
     * A definition is a named declaration like a class, function, property, etc.
     * This is used to distinguish between clicking on a definition (show usages)
     * vs clicking on a reference (go to definition).
     *
     * @param file The Kotlin PSI file
     * @param offset Character offset where the user clicked
     * @return true if the element at offset is a definition
     */
    fun isDefinition(file: KtFile, offset: Int): Boolean {
        val element = file.findElementAt(offset) ?: return false
        val parent = element.parent

        // Check if parent is a named declaration
        return when (parent) {
            is KtClass -> true
            is KtObjectDeclaration -> true
            is KtNamedFunction -> true
            is KtProperty -> {
                // Only top-level or class member properties, not local variables
                parent.parent is KtFile || parent.parent is KtClassBody
            }
            is KtParameter -> {
                // Only function parameters with val/var (they become properties)
                parent.hasValOrVar()
            }
            is KtTypeAlias -> true
            else -> false
        }
    }

    /**
     * Get information about the definition at the given offset.
     *
     * @param file The Kotlin PSI file
     * @param offset Character offset where the user clicked
     * @param filePath Absolute path of the file
     * @return DefinitionInfo if the element at offset is a definition, null otherwise
     */
    fun getDefinitionInfo(file: KtFile, offset: Int, filePath: String): DefinitionInfo? {
        val element = file.findElementAt(offset) ?: return null

        // Try to find a named declaration by traversing up the tree
        var current: PsiElement? = element
        var declaration: KtNamedDeclaration? = null

        // First, check if we're on a reference (e.g., usage of a variable/function)
        // If so, try to resolve it to get the target declaration's name
        val referenceExpression = element.parent as? KtNameReferenceExpression
        if (referenceExpression != null) {
            // We're on a reference - get the name being referenced
            val referencedName = referenceExpression.getReferencedName()
            if (referencedName.isNotEmpty()) {
                // For rename purposes, we need to find if this references a local declaration
                // Traverse up to find the containing declaration scope
                var scope: PsiElement? = referenceExpression.parent
                while (scope != null && scope !is KtFile) {
                    when (scope) {
                        is KtNamedFunction, is KtPropertyAccessor, is KtClassBody, is KtBlockExpression -> {
                            // Search for local variable/parameter declarations with this name
                            val localDecl = findLocalDeclaration(scope, referencedName) as? KtNamedDeclaration
                            if (localDecl != null) {
                                declaration = localDecl
                                break
                            }
                        }
                    }
                    scope = scope.parent
                }

                // If not found locally, it might be a top-level or class member reference
                // In that case, return info based on the reference name
                if (declaration == null) {
                    return DefinitionInfo(
                        name = referencedName,
                        kind = NavigationTargetKind.UNKNOWN,
                        filePath = filePath,
                        offset = referenceExpression.textOffset,
                        line = calculateLine(file.text, referenceExpression.textOffset),
                        column = calculateColumn(file.text, referenceExpression.textOffset)
                    )
                }
            }
        }

        // Traverse up the tree to find the nearest named declaration
        if (declaration == null) {
            while (current != null && current !is KtFile) {
                when (current) {
                    is KtClass,
                    is KtObjectDeclaration,
                    is KtNamedFunction,
                    is KtTypeAlias -> {
                        declaration = current as KtNamedDeclaration
                        break
                    }
                    is KtProperty -> {
                        // Accept properties at any level (including local variables)
                        declaration = current
                        break
                    }
                    is KtParameter -> {
                        // Accept all parameters (not just val/var)
                        declaration = current
                        break
                    }
                    is KtDestructuringDeclarationEntry -> {
                        // Destructuring declaration entries (val (a, b) = pair)
                        declaration = current
                        break
                    }
                }
                current = current.parent
            }
        }

        if (declaration == null) return null

        val name = declaration.name ?: return null
        val declOffset = declaration.nameIdentifier?.textOffset ?: declaration.textOffset

        // Calculate line and column
        val text = file.text
        val line = calculateLine(text, declOffset)
        val column = calculateColumn(text, declOffset)

        // Determine kind
        val kind = when (declaration) {
            is KtClass -> if (declaration.isInterface()) NavigationTargetKind.INTERFACE else NavigationTargetKind.CLASS
            is KtObjectDeclaration -> NavigationTargetKind.OBJECT
            is KtNamedFunction -> NavigationTargetKind.FUNCTION
            is KtProperty -> NavigationTargetKind.PROPERTY
            is KtParameter -> NavigationTargetKind.PARAMETER
            is KtTypeAlias -> NavigationTargetKind.TYPE_ALIAS
            is KtDestructuringDeclarationEntry -> NavigationTargetKind.VARIABLE
            else -> NavigationTargetKind.UNKNOWN
        }

        return DefinitionInfo(
            name = name,
            kind = kind,
            filePath = filePath,
            offset = declOffset,
            line = line,
            column = column
        )
    }

    private fun calculateLine(text: String, offset: Int): Int {
        return text.substring(0, offset.coerceAtMost(text.length)).count { it == '\n' } + 1
    }

    private fun calculateColumn(text: String, offset: Int): Int {
        val lastNewline = text.lastIndexOf('\n', offset - 1)
        return if (lastNewline < 0) offset + 1 else offset - lastNewline
    }

    /**
     * Find navigation target for an element.
     *
     * @param element The PSI element at the cursor
     * @param sourceFile The source KtFile (where user clicked)
     * @param sourceFilePath Absolute path of the source file
     */
    private fun findNavigationTarget(element: PsiElement, sourceFile: KtFile, sourceFilePath: String): NavigationTarget? {
        // Try to resolve reference via PSI (works for same-file navigation)
        val reference = element.reference
            ?: element.parent?.reference

        if (reference != null) {
            val resolved = reference.resolve()
            if (resolved != null) {
                return extractNavigationTarget(resolved, sourceFile, sourceFilePath)
            }
        }

        // For Kotlin, try to find the declaration directly via PSI
        val parent = element.parent
        if (parent is KtNameReferenceExpression) {
            val resolved = resolveKotlinReference(parent)
            if (resolved != null) {
                return extractNavigationTarget(resolved, sourceFile, sourceFilePath)
            }
        }

        // PSI resolution failed - try index-based cross-file navigation
        // Get the name of the symbol the user clicked on
        val symbolName = when {
            parent is KtNameReferenceExpression -> parent.getReferencedName()
            element is PsiNamedElement -> element.name
            element.parent is PsiNamedElement -> (element.parent as PsiNamedElement).name
            else -> element.text.trim()
        }

        if (symbolName.isNullOrEmpty() || symbolName.length < 2) {
            return null
        }

        // Look up symbol in the project index
        val indexer = ProjectIndexer.current ?: return null
        val declarations = indexer.index.findByName(symbolName)

        if (declarations.isEmpty()) {
            return null
        }

        // Filter to find the best match
        // Prefer: project files > library files
        // Within project: same directory > same project > any match
        val sourceDir = if (sourceFilePath.isNotEmpty()) {
            java.io.File(sourceFilePath).parentFile?.absolutePath ?: ""
        } else ""

        val bestMatch = declarations
            .sortedBy { decl ->
                // Score: prefer project files over library files
                val libraryPenalty = if (decl.isLibrary) 100 else 0
                val locationScore = when {
                    decl.filePath.startsWith(sourceDir) -> 0
                    sourceDir.isNotEmpty() && decl.filePath.contains(sourceDir.substringBeforeLast("/src/")) -> 1
                    !decl.isLibrary -> 2  // Other project files
                    else -> 3  // Library files
                }
                libraryPenalty + locationScore
            }
            .firstOrNull() ?: return null

        // Handle JAR paths for library sources
        // JAR paths look like: jar:///path/to/sources.jar!/package/File.kt
        val (fileContent, effectivePath) = if (bestMatch.filePath.startsWith("jar://")) {
            val content = readJarEntry(bestMatch.filePath)
            if (content == null) {
                logger.warn(LogCategory.NAVIGATION, "Failed to read JAR entry", data = mapOf("path" to bestMatch.filePath))
                return null
            }
            content to bestMatch.filePath
        } else {
            // Regular file path
            val targetFile = java.io.File(bestMatch.filePath)
            if (!targetFile.exists()) {
                logger.warn(LogCategory.NAVIGATION, "Target file does not exist", data = mapOf("path" to bestMatch.filePath))
                return null
            }
            targetFile.readText() to bestMatch.filePath
        }

        val offset = bestMatch.offset.coerceIn(0, fileContent.length)
        val line = fileContent.substring(0, offset).count { it == '\n' } + 1
        val lastNewline = fileContent.lastIndexOf('\n', offset - 1)
        val column = if (lastNewline < 0) offset + 1 else offset - lastNewline

        // Map DeclarationKind to NavigationTargetKind
        val kind = when (bestMatch.kind) {
            DeclarationKind.CLASS -> NavigationTargetKind.CLASS
            DeclarationKind.INTERFACE -> NavigationTargetKind.INTERFACE
            DeclarationKind.OBJECT -> NavigationTargetKind.OBJECT
            DeclarationKind.FUNCTION -> NavigationTargetKind.FUNCTION
            DeclarationKind.PROPERTY -> NavigationTargetKind.PROPERTY
            DeclarationKind.TYPE_ALIAS -> NavigationTargetKind.TYPE_ALIAS
            DeclarationKind.CONSTRUCTOR -> NavigationTargetKind.CONSTRUCTOR
            DeclarationKind.ENUM_CLASS -> NavigationTargetKind.CLASS
            DeclarationKind.ENUM_ENTRY -> NavigationTargetKind.PROPERTY
            DeclarationKind.ANNOTATION_CLASS -> NavigationTargetKind.CLASS
        }

        return NavigationTarget(
            filePath = bestMatch.filePath,
            offset = bestMatch.offset,
            line = line,
            column = column,
            name = bestMatch.name,
            kind = kind
        )
    }

    /**
     * Resolve a Kotlin reference to its target.
     */
    private fun resolveKotlinReference(reference: KtNameReferenceExpression): PsiElement? {
        // Try reference resolution first
        val resolved = reference.references.firstNotNullOfOrNull { it.resolve() }
        if (resolved != null) {
            return resolved
        }

        // Fallback: Look for local declarations in scope
        val name = reference.getReferencedName()
        return findLocalDeclaration(reference, name)
    }

    /**
     * Find a local declaration with the given name in the enclosing scope.
     */
    private fun findLocalDeclaration(element: PsiElement, name: String): PsiElement? {
        // Search upward through scopes
        var scope: PsiElement? = element.parent

        while (scope != null) {
            when (scope) {
                is KtBlockExpression -> {
                    // Search statements before the current element
                    for (statement in scope.statements) {
                        if (statement.textOffset >= element.textOffset) break

                        when (statement) {
                            is KtProperty -> {
                                if (statement.name == name) return statement as PsiElement
                            }
                            is KtDestructuringDeclaration -> {
                                statement.entries.find { it.name == name }?.let { return it as PsiElement }
                            }
                        }
                    }
                }
                is KtNamedFunction -> {
                    // Check parameters
                    scope.valueParameters.find { it.name == name }?.let { return it as PsiElement }
                    if (scope.name == name) return scope as PsiElement
                }
                is KtClass -> {
                    // Check class members
                    scope.declarations.filterIsInstance<KtProperty>()
                        .find { it.name == name }?.let { return it as PsiElement }
                    scope.declarations.filterIsInstance<KtNamedFunction>()
                        .find { it.name == name }?.let { return it as PsiElement }
                    if (scope.name == name) return scope as PsiElement
                }
                is KtObjectDeclaration -> {
                    if (scope.name == name) return scope as PsiElement
                    scope.declarations.filterIsInstance<KtProperty>()
                        .find { it.name == name }?.let { return it as PsiElement }
                    scope.declarations.filterIsInstance<KtNamedFunction>()
                        .find { it.name == name }?.let { return it as PsiElement }
                }
                is KtFile -> {
                    // Check top-level declarations
                    scope.declarations.forEach { decl ->
                        when (decl) {
                            is KtNamedFunction -> if (decl.name == name) return decl as PsiElement
                            is KtProperty -> if (decl.name == name) return decl as PsiElement
                            is KtClass -> if (decl.name == name) return decl as PsiElement
                            is KtObjectDeclaration -> if (decl.name == name) return decl as PsiElement
                            is KtTypeAlias -> if (decl.name == name) return decl as PsiElement
                        }
                    }
                }
            }
            scope = scope.parent
        }

        return null
    }

    /**
     * Extract navigation target from a resolved PSI element.
     *
     * @param element The resolved PSI element (definition)
     * @param sourceFile The source KtFile (where user clicked)
     * @param sourceFilePath Absolute path of the source file
     */
    private fun extractNavigationTarget(element: PsiElement, sourceFile: KtFile, sourceFilePath: String): NavigationTarget? {
        val file = element.containingFile ?: return null

        // Determine file path:
        // - If target is in the same file as source, use the known absolute path
        // - If target is in a different file, try to get path from VirtualFile first,
        //   then fall back to index lookup, then file.name as last resort
        val filePath = if (file === sourceFile || file.name == sourceFile.name) {
            // Same file - use the known absolute path
            sourceFilePath.ifEmpty { file.name }
        } else {
            // Different file - try multiple approaches to get the absolute path:
            // 1. First try to get path directly from PsiFile's VirtualFile (most reliable)
            val virtualFilePath = file.virtualFile?.path

            if (!virtualFilePath.isNullOrEmpty()) {
                // Got absolute path from VirtualFile - use it directly
                virtualFilePath
            } else {
                // 2. Fallback to index lookup if VirtualFile not available
                val targetName = (element as? PsiNamedElement)?.name
                val indexPath = targetName?.let { name ->
                    ProjectIndexer.current?.index?.findByName(name)
                        ?.firstOrNull { it.filePath.endsWith(file.name) }
                        ?.filePath
                }

                // 3. Last resort: try to construct path relative to source file's directory
                if (indexPath != null) {
                    indexPath
                } else if (sourceFilePath.isNotEmpty()) {
                    // Try to find the file in the same directory structure as source
                    val sourceDir = java.io.File(sourceFilePath).parentFile
                    val potentialPath = java.io.File(sourceDir, file.name)
                    if (potentialPath.exists()) {
                        potentialPath.absolutePath
                    } else {
                        // Can't resolve - return filename (navigation will fail gracefully)
                        file.name
                    }
                } else {
                    file.name
                }
            }
        }

        // Calculate position
        val offset = element.textOffset
        val text = file.text
        val line = text.substring(0, offset.coerceAtMost(text.length)).count { it == '\n' } + 1
        val lastNewline = text.lastIndexOf('\n', offset - 1)
        val column = if (lastNewline < 0) offset + 1 else offset - lastNewline

        // Get name and kind
        val (name, kind) = getNameAndKind(element)

        return NavigationTarget(
            filePath = filePath,
            offset = offset,
            line = line,
            column = column,
            name = name,
            kind = kind
        )
    }

    /**
     * Get name and kind from a PSI element.
     */
    private fun getNameAndKind(element: PsiElement): Pair<String, NavigationTargetKind> {
        return when (element) {
            // Kotlin elements
            is KtClass -> {
                val kind = when {
                    element.isInterface() -> NavigationTargetKind.INTERFACE
                    else -> NavigationTargetKind.CLASS
                }
                (element.name ?: "<anonymous>") to kind
            }
            is KtObjectDeclaration -> (element.name ?: "<anonymous>") to NavigationTargetKind.OBJECT
            is KtNamedFunction -> (element.name ?: "<anonymous>") to NavigationTargetKind.FUNCTION
            is KtProperty -> (element.name ?: "<anonymous>") to NavigationTargetKind.PROPERTY
            is KtParameter -> (element.name ?: "<anonymous>") to NavigationTargetKind.PARAMETER
            is KtTypeAlias -> (element.name ?: "<anonymous>") to NavigationTargetKind.TYPE_ALIAS
            is KtPrimaryConstructor -> (element.getContainingClassOrObject().name ?: "<constructor>") to NavigationTargetKind.CONSTRUCTOR
            is KtSecondaryConstructor -> (element.getContainingClassOrObject().name ?: "<constructor>") to NavigationTargetKind.CONSTRUCTOR

            // Generic named element
            is PsiNamedElement -> (element.name ?: "<unknown>") to NavigationTargetKind.UNKNOWN

            // Fallback
            else -> (element.text.take(30)) to NavigationTargetKind.UNKNOWN
        }
    }

    /**
     * Check if an offset position is navigable (has a symbol we can go to).
     *
     * @param file The PSI file
     * @param offset Character offset to check
     * @return true if navigation is available at this position
     */
    fun isNavigable(file: KtFile, offset: Int): Boolean {
        val element = file.findElementAt(offset) ?: return false

        // Check if there's a reference via PSI
        if (element.reference?.resolve() != null) return true
        if (element.parent?.reference?.resolve() != null) return true

        // For Kotlin references
        val parent = element.parent
        if (parent is KtNameReferenceExpression) {
            val symbolName = parent.getReferencedName()

            // Check PSI resolution first
            if (parent.references.any { it.resolve() != null }) return true
            if (findLocalDeclaration(parent, symbolName) != null) return true

            // Check index for library symbols
            if (symbolName.length >= 2) {
                val indexer = ProjectIndexer.current
                if (indexer != null) {
                    val matches = indexer.index.findByName(symbolName)
                    if (matches.isNotEmpty()) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * Get the range of the navigable symbol at the offset.
     *
     * @param file The PSI file
     * @param offset Character offset
     * @return Pair of (startOffset, endOffset) or null if not navigable
     */
    fun getNavigableRange(file: KtFile, offset: Int): Pair<Int, Int>? {
        val element = file.findElementAt(offset) ?: return null

        // Find the reference element
        val parent = element.parent
        val referenceElement: PsiElement? = when {
            parent is KtNameReferenceExpression -> {
                val symbolName = parent.getReferencedName()

                // Check PSI resolution or index lookup
                val hasReference = parent.references.any { it.resolve() != null } ||
                    findLocalDeclaration(parent, symbolName) != null

                // Also check index for library symbols
                val inIndex = if (!hasReference) {
                    symbolName.length >= 2 && ProjectIndexer.current?.index?.findByName(symbolName)?.isNotEmpty() == true
                } else false

                if (hasReference || inIndex) parent else null
            }
            element.reference != null -> element
            element.parent?.reference != null -> element.parent
            else -> null
        }

        return referenceElement?.let {
            it.textRange.startOffset to it.textRange.endOffset
        }
    }

    /**
     * Read content from a JAR entry.
     * Handles paths like: jar:///path/to/sources.jar!/package/File.kt
     *
     * @param jarPath The virtual JAR path
     * @return The file content or null if not found
     */
    private fun readJarEntry(jarPath: String): String? {
        return try {
            // Parse: jar:///path/to/sources.jar!/package/File.kt
            val withoutPrefix = jarPath.removePrefix("jar://")
            val separatorIndex = withoutPrefix.indexOf("!/")
            if (separatorIndex < 0) {
                logger.warn(LogCategory.NAVIGATION, "Invalid JAR path format", data = mapOf("path" to jarPath))
                return null
            }

            val jarFilePath = withoutPrefix.substring(0, separatorIndex)
            val entryPath = withoutPrefix.substring(separatorIndex + 2)

            val jarFile = java.util.jar.JarFile(java.io.File(jarFilePath))
            jarFile.use { jar ->
                val entry = jar.getJarEntry(entryPath)
                if (entry == null) {
                    logger.warn(LogCategory.NAVIGATION, "JAR entry not found", data = mapOf("entry" to entryPath, "jar" to jarFilePath))
                    return null
                }

                jar.getInputStream(entry).bufferedReader().readText()
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.NAVIGATION, "Error reading JAR entry", error = e)
            null
        }
    }
}
