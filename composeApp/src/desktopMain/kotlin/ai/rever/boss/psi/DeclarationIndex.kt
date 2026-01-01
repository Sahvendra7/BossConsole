package ai.rever.boss.psi

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.kotlin.psi.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Kind of declaration.
 */
enum class DeclarationKind {
    CLASS,
    INTERFACE,
    OBJECT,
    FUNCTION,
    PROPERTY,
    TYPE_ALIAS,
    CONSTRUCTOR,
    ENUM_CLASS,
    ENUM_ENTRY,
    ANNOTATION_CLASS
}

/**
 * Information about a declaration in the index.
 *
 * @property name Simple name of the declaration
 * @property kind Type of declaration
 * @property filePath Absolute path to the file containing the declaration
 * @property offset Character offset within the file
 * @property fqName Fully qualified name (if available)
 * @property containingClass Name of the containing class (for members)
 * @property isLibrary True if this declaration is from an external library
 */
data class DeclarationInfo(
    val name: String,
    val kind: DeclarationKind,
    val filePath: String,
    val offset: Int,
    val fqName: String? = null,
    val containingClass: String? = null,
    val isLibrary: Boolean = false
)

/**
 * Index of declarations for cross-file symbol resolution.
 *
 * This index maintains a mapping from symbol names to their declarations,
 * enabling fast lookup for go-to-definition across files.
 *
 * The index is built incrementally as files are parsed and can be updated
 * when files change.
 *
 * Thread Safety: Uses ConcurrentHashMap for thread-safe access.
 */
class DeclarationIndex {

    /**
     * Index from simple name to list of declarations with that name.
     * Multiple declarations may have the same name (overloading, different files).
     */
    private val nameIndex = ConcurrentHashMap<String, MutableList<DeclarationInfo>>()

    /**
     * Index from fully qualified name to declaration.
     * FQN should be unique within a project.
     */
    private val fqNameIndex = ConcurrentHashMap<String, DeclarationInfo>()

    /**
     * Index from file path to list of declarations in that file.
     * Used for efficient removal when a file changes.
     */
    private val fileIndex = ConcurrentHashMap<String, MutableList<DeclarationInfo>>()

    /**
     * Total number of indexed declarations.
     */
    val size: Int
        get() = fqNameIndex.size

    /**
     * Index declarations from a Kotlin PSI file.
     *
     * @param file The KtFile to index
     * @param filePath The file path (used as key for updates)
     * @param isLibrary Whether this file is from an external library
     */
    fun indexFile(file: KtFile, filePath: String, isLibrary: Boolean = false) {
        // Remove old declarations from this file first
        removeFile(filePath)

        val declarations = mutableListOf<DeclarationInfo>()

        // Visit all elements in the file
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                extractDeclaration(element, filePath, isLibrary)?.let { info ->
                    declarations.add(info)
                    addToIndex(info)
                }
                super.visitElement(element)
            }
        })

        // Store file index for efficient removal
        fileIndex[filePath] = declarations
    }

    /**
     * Remove all declarations from a file.
     *
     * @param filePath The file path to remove
     */
    fun removeFile(filePath: String) {
        val declarations = fileIndex.remove(filePath) ?: return

        for (info in declarations) {
            // Remove from name index
            nameIndex[info.name]?.removeIf { it.filePath == filePath }

            // Remove from FQN index
            info.fqName?.let { fqNameIndex.remove(it) }
        }

        // Clean up empty name entries
        nameIndex.entries.removeIf { it.value.isEmpty() }
    }

    /**
     * Find declarations by simple name.
     *
     * @param name Simple name to search for
     * @return List of matching declarations
     */
    fun findByName(name: String): List<DeclarationInfo> {
        return nameIndex[name]?.toList() ?: emptyList()
    }

    /**
     * Find declaration by fully qualified name.
     *
     * @param fqName Fully qualified name
     * @return Declaration info or null
     */
    fun findByFqName(fqName: String): DeclarationInfo? {
        return fqNameIndex[fqName]
    }

    /**
     * Find declarations matching a predicate.
     *
     * @param predicate Filter function
     * @return List of matching declarations
     */
    fun findAll(predicate: (DeclarationInfo) -> Boolean): List<DeclarationInfo> {
        return fqNameIndex.values.filter(predicate)
    }

    /**
     * Get all declarations in a file.
     *
     * @param filePath The file path
     * @return List of declarations in the file
     */
    fun getDeclarationsInFile(filePath: String): List<DeclarationInfo> {
        return fileIndex[filePath]?.toList() ?: emptyList()
    }

    /**
     * Clear all indexed data.
     */
    fun clear() {
        nameIndex.clear()
        fqNameIndex.clear()
        fileIndex.clear()
    }

    /**
     * Add a declaration to the indices.
     */
    private fun addToIndex(info: DeclarationInfo) {
        // Add to name index
        nameIndex.computeIfAbsent(info.name) { mutableListOf() }.add(info)

        // Add to FQN index
        info.fqName?.let { fqNameIndex[it] = info }
    }

    /**
     * Extract declaration info from a PSI element.
     */
    private fun extractDeclaration(element: PsiElement, filePath: String, isLibrary: Boolean = false): DeclarationInfo? {
        return when (element) {
            // Kotlin declarations
            is KtClass -> {
                val kind = when {
                    element.isInterface() -> DeclarationKind.INTERFACE
                    element.isEnum() -> DeclarationKind.ENUM_CLASS
                    element.isAnnotation() -> DeclarationKind.ANNOTATION_CLASS
                    else -> DeclarationKind.CLASS
                }
                element.name?.let { name ->
                    DeclarationInfo(
                        name = name,
                        kind = kind,
                        filePath = filePath,
                        offset = element.textOffset,
                        fqName = element.fqName?.asString(),
                        containingClass = element.containingClass()?.name,
                        isLibrary = isLibrary
                    )
                }
            }

            is KtObjectDeclaration -> {
                element.name?.let { name ->
                    DeclarationInfo(
                        name = name,
                        kind = DeclarationKind.OBJECT,
                        filePath = filePath,
                        offset = element.textOffset,
                        fqName = element.fqName?.asString(),
                        containingClass = element.containingClass()?.name,
                        isLibrary = isLibrary
                    )
                }
            }

            is KtNamedFunction -> {
                element.name?.let { name ->
                    val containingClass = element.containingClass()
                    val fqn = if (containingClass != null) {
                        "${containingClass.fqName?.asString()}.${name}"
                    } else {
                        // Top-level function
                        val packageFqn = (element.containingKtFile).packageFqName.asString()
                        if (packageFqn.isNotEmpty()) "$packageFqn.$name" else name
                    }
                    DeclarationInfo(
                        name = name,
                        kind = DeclarationKind.FUNCTION,
                        filePath = filePath,
                        offset = element.textOffset,
                        fqName = fqn,
                        containingClass = containingClass?.name,
                        isLibrary = isLibrary
                    )
                }
            }

            is KtProperty -> {
                // Only index top-level and class-level properties, not local variables
                if (element.isLocal) return null

                element.name?.let { name ->
                    val containingClass = element.containingClass()
                    val fqn = if (containingClass != null) {
                        "${containingClass.fqName?.asString()}.${name}"
                    } else {
                        val packageFqn = (element.containingKtFile).packageFqName.asString()
                        if (packageFqn.isNotEmpty()) "$packageFqn.$name" else name
                    }
                    DeclarationInfo(
                        name = name,
                        kind = DeclarationKind.PROPERTY,
                        filePath = filePath,
                        offset = element.textOffset,
                        fqName = fqn,
                        containingClass = containingClass?.name,
                        isLibrary = isLibrary
                    )
                }
            }

            is KtTypeAlias -> {
                element.name?.let { name ->
                    val packageFqn = (element.containingKtFile).packageFqName.asString()
                    val fqn = if (packageFqn.isNotEmpty()) "$packageFqn.$name" else name
                    DeclarationInfo(
                        name = name,
                        kind = DeclarationKind.TYPE_ALIAS,
                        filePath = filePath,
                        offset = element.textOffset,
                        fqName = fqn,
                        isLibrary = isLibrary
                    )
                }
            }

            is KtEnumEntry -> {
                element.name?.let { name ->
                    val containingClass = element.containingClass()
                    DeclarationInfo(
                        name = name,
                        kind = DeclarationKind.ENUM_ENTRY,
                        filePath = filePath,
                        offset = element.textOffset,
                        fqName = "${containingClass?.fqName?.asString()}.$name",
                        containingClass = containingClass?.name,
                        isLibrary = isLibrary
                    )
                }
            }

            // Skip other elements
            else -> null
        }
    }

    /**
     * Get the containing class of an element, if any.
     */
    private fun PsiElement.containingClass(): KtClassOrObject? {
        var parent = this.parent
        while (parent != null) {
            if (parent is KtClassOrObject) return parent
            parent = parent.parent
        }
        return null
    }
}
