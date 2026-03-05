package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.api.SemanticTokenProvider
import ai.rever.boss.plugin.api.SemanticElement
import ai.rever.boss.plugin.api.SemanticElementType
import ai.rever.bosseditor.psi.PSIBootstrap
import ai.rever.bosseditor.psi.PSIThreadBridge
import ai.rever.bosseditor.psi.SemanticCache
import ai.rever.bosseditor.psi.SemanticType
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import ai.rever.bosseditor.psi.SemanticElement as HostSemanticElement

private val logger = BossLogger.forComponent("SemanticTokenProvider")

/**
 * Implementation of SemanticTokenProvider that uses the host's PSI infrastructure.
 *
 * This allows dynamic plugins to access semantic highlighting data from the host's
 * SemanticCache, which is populated when files are analyzed.
 */
class SemanticTokenProviderImpl : SemanticTokenProvider {

    init {
        logger.info(LogCategory.EDITOR, "[SEMANTIC-DEBUG] SemanticTokenProviderImpl created, PSI initialized: ${PSIBootstrap.isInitialized}")
    }

    override fun getSemanticElements(filePath: String): List<SemanticElement>? {
        logger.info(LogCategory.EDITOR, "[SEMANTIC-DEBUG] getSemanticElements called for: $filePath")

        // Only support Kotlin files
        if (!filePath.endsWith(".kt") && !filePath.endsWith(".kts")) {
            logger.info(LogCategory.EDITOR, "[SEMANTIC-DEBUG] Not a Kotlin file, returning null")
            return null
        }

        // Get elements from host's SemanticCache
        val hostElements = SemanticCache.get(filePath)
        if (hostElements == null) {
            logger.info(LogCategory.EDITOR, "[SEMANTIC-DEBUG] No cached elements for file")
            return null
        }

        logger.info(LogCategory.EDITOR, "[SEMANTIC-DEBUG] Found ${hostElements.size} semantic elements")

        // Convert host elements to plugin API elements
        return hostElements.map { element ->
            SemanticElement(
                startOffset = element.startOffset,
                endOffset = element.endOffset,
                type = mapSemanticType(element.type),
                name = element.name
            )
        }
    }

    override suspend fun analyzeFile(filePath: String, content: String) {
        logger.info(LogCategory.EDITOR, "[SEMANTIC-DEBUG] analyzeFile called", mapOf(
            "filePath" to filePath,
            "contentLength" to content.length.toString(),
            "psiInitialized" to PSIBootstrap.isInitialized.toString()
        ))

        // Only support Kotlin files
        if (!filePath.endsWith(".kt") && !filePath.endsWith(".kts")) {
            logger.info(LogCategory.EDITOR, "[SEMANTIC-DEBUG] Not a Kotlin file, skipping analysis")
            return
        }

        // Check if PSI is initialized
        if (!PSIBootstrap.isInitialized) {
            logger.warn(LogCategory.EDITOR, "[SEMANTIC-DEBUG] PSI not initialized, skipping analysis")
            return
        }

        if (content.isEmpty()) {
            SemanticCache.clear(filePath)
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val fileName = filePath.substringAfterLast('/')
                logger.info(LogCategory.EDITOR, "[SEMANTIC-DEBUG] Analyzing file: $fileName")

                val elements = PSIThreadBridge.readAction {
                    val ktFile = PSIBootstrap.parseKotlinFile(fileName, content)
                    analyzeKotlinFile(ktFile)
                }

                // Store in host's SemanticCache
                SemanticCache.put(filePath, elements)
                logger.info(LogCategory.EDITOR, "[SEMANTIC-DEBUG] Analysis complete, ${elements.size} elements cached")

            } catch (e: Exception) {
                logger.error(LogCategory.EDITOR, "[SEMANTIC-DEBUG] Analysis error", error = e)
            }
        }
    }

    /**
     * Maps host SemanticType to plugin API SemanticElementType.
     */
    private fun mapSemanticType(type: SemanticType): SemanticElementType = when (type) {
        SemanticType.FUNCTION_CALL -> SemanticElementType.FUNCTION_CALL
        SemanticType.PROPERTY_ACCESS -> SemanticElementType.PROPERTY_ACCESS
        SemanticType.CLASS_REFERENCE -> SemanticElementType.CLASS_REFERENCE
        SemanticType.OBJECT_REFERENCE -> SemanticElementType.OBJECT_REFERENCE
        SemanticType.PARAMETER -> SemanticElementType.PARAMETER
        SemanticType.LOCAL_VARIABLE -> SemanticElementType.LOCAL_VARIABLE
        SemanticType.ANNOTATION -> SemanticElementType.ANNOTATION
        SemanticType.LABEL -> SemanticElementType.LABEL
        SemanticType.TYPE_PARAMETER -> SemanticElementType.TYPE_PARAMETER
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
    private fun analyzeKotlinFile(ktFile: org.jetbrains.kotlin.psi.KtFile): List<HostSemanticElement> {
        val elements = mutableListOf<HostSemanticElement>()

        ktFile.accept(object : KtTreeVisitorVoid() {

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                // Skip import statements
                if (isInsideImport(expression)) return

                // Get the callee expression (function name)
                val callee = expression.calleeExpression
                if (callee != null) {
                    elements.add(HostSemanticElement(
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
                    elements.add(HostSemanticElement(
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
                        elements.add(HostSemanticElement(
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
                    elements.add(HostSemanticElement(
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
                    elements.add(HostSemanticElement(
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
                        elements.add(HostSemanticElement(
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
                    elements.add(HostSemanticElement(
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
                    elements.add(HostSemanticElement(
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
}
