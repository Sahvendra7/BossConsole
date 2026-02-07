package ai.rever.boss.psi

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
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
 * Information about a definition (class, function, property, etc.).
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
 * Location of a reference to a symbol.
 */
data class ReferenceLocation(
    val filePath: String,
    val line: Int,
    val column: Int,
    val offset: Int,
    val context: String,
    val symbolName: String
)

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
    private val logger = BossLogger.forComponent("NavigationService")

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
            logger.warn(LogCategory.EDITOR, "Error during go-to-definition", error = e)
            NavigationResult.Error(e.message ?: "Unknown error")
        }
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
            logger.debug(LogCategory.EDITOR, "Symbol name too short or empty", mapOf("symbolName" to symbolName))
            return null
        }

        logger.debug(LogCategory.EDITOR, "Looking up symbol in index", mapOf("symbolName" to symbolName))

        // Look up symbol in the project index
        val indexer = ProjectIndexer.current ?: run {
            logger.debug(LogCategory.EDITOR, "ProjectIndexer.current is null")
            return null
        }
        val declarations = indexer.index.findByName(symbolName)

        logger.debug(LogCategory.EDITOR, "Found declarations for symbol", mapOf("count" to declarations.size, "symbolName" to symbolName))

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

        logger.debug(LogCategory.EDITOR, "Best match found", mapOf("name" to bestMatch.name, "kind" to bestMatch.kind.toString(), "filePath" to bestMatch.filePath, "isLibrary" to bestMatch.isLibrary))

        // Handle JAR paths for library sources
        // JAR paths look like: jar:///path/to/sources.jar!/package/File.kt
        val (fileContent, effectivePath) = if (bestMatch.filePath.startsWith("jar://")) {
            val content = readJarEntry(bestMatch.filePath)
            if (content == null) {
                logger.warn(LogCategory.EDITOR, "Failed to read JAR entry", mapOf("filePath" to bestMatch.filePath))
                return null
            }
            content to bestMatch.filePath
        } else {
            // Regular file path
            val targetFile = java.io.File(bestMatch.filePath)
            if (!targetFile.exists()) {
                logger.warn(LogCategory.EDITOR, "Target file does not exist", mapOf("filePath" to bestMatch.filePath))
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
     * Check if the element at the given offset is a definition (class, function, property, etc.).
     *
     * @param file The PSI file
     * @param offset Character offset to check
     * @return true if the element at offset is a definition
     */
    fun isDefinition(file: KtFile, offset: Int): Boolean {
        val element = file.findElementAt(offset) ?: return false
        val parent = element.parent

        // Check if parent is a named declaration
        return when (parent) {
            is KtClass,
            is KtObjectDeclaration,
            is KtNamedFunction,
            is KtProperty,
            is KtParameter,
            is KtTypeAlias,
            is KtPrimaryConstructor,
            is KtSecondaryConstructor -> true
            else -> false
        }
    }

    /**
     * Get definition info for the element at the given offset.
     *
     * @param file The PSI file
     * @param offset Character offset
     * @param filePath Absolute path to the file
     * @return DefinitionInfo if the element at offset is a definition, null otherwise
     */
    fun getDefinitionInfo(file: KtFile, offset: Int, filePath: String): DefinitionInfo? {
        val element = file.findElementAt(offset) ?: return null

        // Try to find a named declaration by traversing up the tree
        var current: PsiElement? = element
        var declaration: KtNamedDeclaration? = null

        while (current != null && declaration == null) {
            if (current is KtNamedDeclaration) {
                // Check if the original element is part of the name identifier
                val nameIdentifier = current.nameIdentifier
                if (nameIdentifier != null && element.textOffset >= nameIdentifier.textOffset &&
                    element.textOffset < nameIdentifier.textOffset + nameIdentifier.textLength) {
                    declaration = current
                }
            }
            current = current.parent
        }

        if (declaration == null) return null

        val text = file.text
        val declOffset = declaration.textOffset
        val line = text.substring(0, declOffset.coerceAtMost(text.length)).count { it == '\n' } + 1
        val lastNewline = text.lastIndexOf('\n', declOffset - 1)
        val column = if (lastNewline < 0) declOffset + 1 else declOffset - lastNewline

        val kind = when (declaration) {
            is KtClass -> if (declaration.isInterface()) NavigationTargetKind.INTERFACE else NavigationTargetKind.CLASS
            is KtObjectDeclaration -> NavigationTargetKind.OBJECT
            is KtNamedFunction -> NavigationTargetKind.FUNCTION
            is KtProperty -> NavigationTargetKind.PROPERTY
            is KtParameter -> NavigationTargetKind.PARAMETER
            is KtTypeAlias -> NavigationTargetKind.TYPE_ALIAS
            is KtPrimaryConstructor, is KtSecondaryConstructor -> NavigationTargetKind.CONSTRUCTOR
            else -> NavigationTargetKind.UNKNOWN
        }

        return DefinitionInfo(
            name = declaration.name ?: "<anonymous>",
            kind = kind,
            filePath = filePath,
            offset = declOffset,
            line = line,
            column = column
        )
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
                        logger.debug(LogCategory.EDITOR, "isNavigable: symbol found in index", mapOf("symbolName" to symbolName, "matchCount" to matches.size))
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
                logger.warn(LogCategory.EDITOR, "Invalid JAR path format", mapOf("jarPath" to jarPath))
                return null
            }

            val jarFilePath = withoutPrefix.substring(0, separatorIndex)
            val entryPath = withoutPrefix.substring(separatorIndex + 2)

            val jarFile = java.util.jar.JarFile(java.io.File(jarFilePath))
            jarFile.use { jar ->
                val entry = jar.getJarEntry(entryPath)
                if (entry == null) {
                    logger.warn(LogCategory.EDITOR, "JAR entry not found", mapOf("entryPath" to entryPath, "jarFilePath" to jarFilePath))
                    return null
                }

                jar.getInputStream(entry).bufferedReader().readText()
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.EDITOR, "Error reading JAR entry", error = e)
            null
        }
    }
}
