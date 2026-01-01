package ai.rever.bosseditor.psi

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.highlight.SemanticTokenProvider
import ai.rever.bosseditor.highlight.Token
import ai.rever.bosseditor.highlight.TokenType
import kotlinx.coroutines.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.util.concurrent.ConcurrentHashMap

/**
 * Semantic element types for highlighting.
 */
enum class SemanticType {
    FUNCTION_CALL,           // Function/method calls -> blue
    PROPERTY_ACCESS,         // Property/field access -> pink
    CLASS_REFERENCE,         // Class/type references
    OBJECT_REFERENCE,        // Object/companion references -> pink
    PARAMETER,               // Function parameters
    LOCAL_VARIABLE,          // Local variables
    ANNOTATION,              // Annotations
    LABEL,                   // Labels (@label)
    TYPE_PARAMETER,          // Generic type parameters
}

/**
 * A semantic element with its location and type.
 */
data class SemanticElement(
    val startOffset: Int,
    val endOffset: Int,
    val type: SemanticType,
    val name: String
)

/**
 * Global cache for semantic analysis results.
 * Keyed by file path, contains list of semantic elements.
 */
object SemanticCache {
    private val cache = ConcurrentHashMap<String, List<SemanticElement>>()

    fun put(filePath: String, elements: List<SemanticElement>) {
        cache[filePath] = elements
    }

    fun get(filePath: String): List<SemanticElement>? = cache[filePath]

    fun clear(filePath: String) {
        cache.remove(filePath)
    }

    fun clearAll() {
        cache.clear()
    }

    /**
     * Get semantic type for a token at the given offset range.
     * Returns null if no semantic element covers this range.
     */
    fun getSemanticType(filePath: String, startOffset: Int, endOffset: Int): SemanticType? {
        val elements = cache[filePath] ?: return null
        return elements.find { element ->
            element.startOffset == startOffset && element.endOffset == endOffset
        }?.type
    }

    /**
     * Get semantic type for an identifier name at the given offset.
     * This is more flexible - finds elements that contain or match the offset range.
     */
    fun findSemanticType(filePath: String, offset: Int, length: Int): SemanticType? {
        val elements = cache[filePath] ?: return null
        val endOffset = offset + length
        return elements.find { element ->
            element.startOffset <= offset && element.endOffset >= endOffset
        }?.type
    }
}

/**
 * PSI-based semantic highlighter for Kotlin code.
 *
 * This highlighter uses the Kotlin PSI to analyze code semantically
 * and apply colors based on element types (function calls, properties, etc.)
 * similar to IntelliJ's semantic highlighting.
 *
 * This version works with BossEditor's EditorDocument instead of RSyntaxTextArea.
 */
class SemanticHighlighter(
    private val document: EditorDocument,
    private var filePath: String
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Debounce job for analysis
    private var analysisJob: Job? = null

    // Callback for when analysis is complete
    var onAnalysisComplete: (() -> Unit)? = null

    /**
     * Analyze the document and update semantic cache.
     * This is debounced to avoid excessive re-analysis.
     */
    fun analyzeAndHighlight() {
        // Cancel any pending analysis
        analysisJob?.cancel()

        // Debounce analysis
        analysisJob = scope.launch {
            delay(150) // Wait for typing to settle

            if (!isActive) return@launch

            try {
                val content = document.getText()
                if (content.isEmpty()) {
                    SemanticCache.clear(filePath)
                    return@launch
                }

                // Only analyze Kotlin files
                if (!filePath.endsWith(".kt") && !filePath.endsWith(".kts")) {
                    return@launch
                }

                // Check if PSI is initialized
                if (!PSIBootstrap.isInitialized) return@launch

                val fileName = filePath.substringAfterLast('/')

                // Parse and analyze in PSI thread
                val elements = PSIThreadBridge.readAction {
                    val ktFile = PSIBootstrap.parseKotlinFile(fileName, content)
                    analyzeKotlinFile(ktFile)
                }

                // Store in global cache
                SemanticCache.put(filePath, elements)

                // Notify that analysis is complete
                withContext(Dispatchers.Main) {
                    onAnalysisComplete?.invoke()
                }

            } catch (e: Exception) {
                // Silently ignore analysis errors
                if (e !is CancellationException) {
                    println("[SemanticHighlighter] Analysis error: ${e.message}")
                }
            }
        }
    }

    /**
     * Update the current file path.
     */
    fun setFilePath(newFilePath: String) {
        filePath = newFilePath
    }

    /**
     * Check if an element is inside an import statement.
     */
    private fun isInsideImport(element: KtElement): Boolean {
        return element.getParentOfType<KtImportDirective>(strict = false) != null
    }

    /**
     * Analyze a Kotlin PSI file and extract semantic elements.
     */
    private fun analyzeKotlinFile(ktFile: KtFile): List<SemanticElement> {
        val elements = mutableListOf<SemanticElement>()

        ktFile.accept(object : KtTreeVisitorVoid() {

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                // Skip import statements
                if (isInsideImport(expression)) return

                // Get the callee expression (function name)
                val callee = expression.calleeExpression
                if (callee != null) {
                    elements.add(SemanticElement(
                        startOffset = callee.startOffset,
                        endOffset = callee.endOffset,
                        type = SemanticType.FUNCTION_CALL,
                        name = callee.text
                    ))
                }
            }

            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                super.visitDotQualifiedExpression(expression)

                // Skip import statements
                if (isInsideImport(expression)) return

                // Check if the selector is a property access (not a function call)
                val selector = expression.selectorExpression
                if (selector != null && selector !is KtCallExpression) {
                    // This is a property access like obj.property
                    elements.add(SemanticElement(
                        startOffset = selector.startOffset,
                        endOffset = selector.endOffset,
                        type = SemanticType.PROPERTY_ACCESS,
                        name = selector.text
                    ))
                }
            }

            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                super.visitReferenceExpression(expression)

                // Skip import statements
                if (isInsideImport(expression)) return

                // Skip if already handled as part of other expressions
                val parent = expression.parent
                if (parent is KtCallExpression && parent.calleeExpression == expression) {
                    return // Already handled as function call
                }
                if (parent is KtDotQualifiedExpression && parent.selectorExpression == expression) {
                    return // Already handled as property access
                }

                // Check if this is a class/type reference (starts with uppercase)
                val name = expression.text
                if (name.isNotEmpty() && name[0].isUpperCase()) {
                    // Could be a class reference, object reference, or enum
                    val grandParent = parent?.parent
                    if (grandParent is KtDotQualifiedExpression && grandParent.receiverExpression == parent) {
                        // This is the receiver in a qualified expression (e.g., MyClass.something)
                        elements.add(SemanticElement(
                            startOffset = expression.startOffset,
                            endOffset = expression.endOffset,
                            type = SemanticType.OBJECT_REFERENCE,
                            name = name
                        ))
                    }
                }
            }

            override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
                super.visitAnnotationEntry(annotationEntry)

                // Get annotation name
                val typeRef = annotationEntry.typeReference
                if (typeRef != null) {
                    elements.add(SemanticElement(
                        startOffset = typeRef.startOffset,
                        endOffset = typeRef.endOffset,
                        type = SemanticType.ANNOTATION,
                        name = typeRef.text
                    ))
                }
            }

            override fun visitParameter(parameter: KtParameter) {
                super.visitParameter(parameter)

                // Function parameters
                val nameId = parameter.nameIdentifier
                if (nameId != null) {
                    elements.add(SemanticElement(
                        startOffset = nameId.startOffset,
                        endOffset = nameId.endOffset,
                        type = SemanticType.PARAMETER,
                        name = nameId.text
                    ))
                }
            }

            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)

                // Local variables (properties inside functions)
                if (property.isLocal) {
                    val nameId = property.nameIdentifier
                    if (nameId != null) {
                        elements.add(SemanticElement(
                            startOffset = nameId.startOffset,
                            endOffset = nameId.endOffset,
                            type = SemanticType.LOCAL_VARIABLE,
                            name = nameId.text
                        ))
                    }
                }
            }

            override fun visitLabeledExpression(expression: KtLabeledExpression) {
                super.visitLabeledExpression(expression)

                // Labels like @label
                val labelRef = expression.getTargetLabel()
                if (labelRef != null) {
                    elements.add(SemanticElement(
                        startOffset = labelRef.startOffset,
                        endOffset = labelRef.endOffset,
                        type = SemanticType.LABEL,
                        name = labelRef.text
                    ))
                }
            }

            override fun visitTypeParameter(parameter: KtTypeParameter) {
                super.visitTypeParameter(parameter)

                val nameId = parameter.nameIdentifier
                if (nameId != null) {
                    elements.add(SemanticElement(
                        startOffset = nameId.startOffset,
                        endOffset = nameId.endOffset,
                        type = SemanticType.TYPE_PARAMETER,
                        name = nameId.text
                    ))
                }
            }
        })

        return elements
    }

    /**
     * Dispose resources.
     */
    fun dispose() {
        analysisJob?.cancel()
        scope.cancel()
        SemanticCache.clear(filePath)
    }
}

/**
 * Adapter that bridges the PSI-based semantic highlighting
 * to the BossEditor's SemanticTokenProvider interface.
 *
 * The adapter reads from SemanticCache, which is populated by
 * SemanticHighlighter when it analyzes Kotlin files.
 */
class SemanticAdapter(
    private val document: EditorDocument,
    private val filePath: String
) : SemanticTokenProvider {

    /**
     * Gets semantic tokens for a line by querying the SemanticCache.
     *
     * Returns null if no semantic analysis is available, allowing
     * the lexer-based highlighting to be used instead.
     */
    override fun getLineTokens(lineNumber: Int): List<Token>? {
        if (lineNumber < 0 || lineNumber >= document.lineCount) {
            return null
        }

        // Get all semantic elements for this file
        val allElements = SemanticCache.get(filePath) ?: return null
        if (allElements.isEmpty()) return null

        // Get the line range in the document
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)

        // Filter elements that fall within this line
        val lineElements = allElements.filter { element ->
            element.startOffset >= lineStart && element.endOffset <= lineEnd
        }

        if (lineElements.isEmpty()) return null

        // Convert SemanticElements to BossEditor Tokens
        return lineElements.map { element ->
            Token(
                startOffset = element.startOffset - lineStart,
                endOffset = element.endOffset - lineStart,
                type = mapSemanticType(element.type)
            )
        }.sortedBy { it.startOffset }
    }

    override fun isAvailable(): Boolean {
        // Check if there's any cached data for this file
        return SemanticCache.get(filePath)?.isNotEmpty() == true
    }

    /**
     * Maps PSI SemanticType to BossEditor TokenType.
     */
    private fun mapSemanticType(type: SemanticType): TokenType = when (type) {
        SemanticType.FUNCTION_CALL -> TokenType.FUNCTION_CALL
        SemanticType.PROPERTY_ACCESS -> TokenType.PROPERTY
        SemanticType.CLASS_REFERENCE -> TokenType.TYPE
        SemanticType.OBJECT_REFERENCE -> TokenType.VARIABLE
        SemanticType.PARAMETER -> TokenType.PARAMETER
        SemanticType.LOCAL_VARIABLE -> TokenType.LOCAL_VARIABLE
        SemanticType.ANNOTATION -> TokenType.ANNOTATION
        SemanticType.LABEL -> TokenType.LABEL
        SemanticType.TYPE_PARAMETER -> TokenType.TYPE_PARAMETER
    }
}

/**
 * Factory for creating SemanticAdapters for different file types.
 */
object SemanticAdapterFactory {
    /**
     * Creates a SemanticAdapter if the file type supports semantic analysis.
     */
    fun create(document: EditorDocument, filePath: String): SemanticAdapter? {
        // Only Kotlin files currently support semantic analysis
        return if (filePath.endsWith(".kt") || filePath.endsWith(".kts")) {
            SemanticAdapter(document, filePath)
        } else {
            null
        }
    }

    /**
     * Creates a SemanticHighlighter for the given document.
     */
    fun createHighlighter(document: EditorDocument, filePath: String): SemanticHighlighter? {
        // Only Kotlin files currently support semantic analysis
        return if (filePath.endsWith(".kt") || filePath.endsWith(".kts")) {
            SemanticHighlighter(document, filePath)
        } else {
            null
        }
    }
}
