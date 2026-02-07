package ai.rever.boss.psi

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.yield
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.io.File

private val logger = BossLogger.forComponent("ReferenceService")

/**
 * Service for finding all references to a symbol.
 *
 * This service provides:
 * - Finding all usages of a class, function, property, etc.
 * - Cross-file reference search using the project index
 * - Accurate reference resolution using PSI
 */
class ReferenceService {

    /**
     * Find all references to a definition.
     *
     * @param definitionInfo Information about the definition to find references for
     * @param progressCallback Optional callback for progress updates (filesSearched, totalFiles, currentFile)
     * @return List of reference locations, sorted by file and line
     */
    suspend fun findReferences(
        definitionInfo: DefinitionInfo,
        progressCallback: ((Int, Int, String) -> Unit)? = null
    ): List<ReferenceLocation> {
        val references = mutableListOf<ReferenceLocation>()

        val indexer = ProjectIndexer.current
        if (indexer == null) {
            logger.warn(LogCategory.EDITOR, "[REF-DEBUG] ProjectIndexer.current is null")
            return emptyList()
        }

        // Get all indexed Kotlin files
        val kotlinFiles = getIndexedKotlinFiles(indexer)
        logger.info(LogCategory.EDITOR, "[REF-DEBUG] Searching ${kotlinFiles.size} files for references to ${definitionInfo.name}")

        var filesSearched = 0
        for (filePath in kotlinFiles) {
            try {
                val fileReferences = findReferencesInFile(filePath, definitionInfo)
                references.addAll(fileReferences)

                filesSearched++
                progressCallback?.invoke(filesSearched, kotlinFiles.size, File(filePath).name)

                // Yield periodically to avoid blocking
                if (filesSearched % 10 == 0) {
                    yield()
                }
            } catch (e: Exception) {
                // Skip files that can't be parsed
            }
        }

        logger.info(LogCategory.EDITOR, "[REF-DEBUG] Found ${references.size} references")

        // Deduplicate and sort by file path, then by line number
        return references
            .distinctBy { "${it.filePath}:${it.line}:${it.column}" }
            .sortedWith(compareBy({ it.filePath }, { it.line }))
    }

    /**
     * Find references to a symbol in a single file.
     *
     * @param filePath Path to the file to search
     * @param definitionInfo Information about the definition to find references for
     * @return List of reference locations in this file
     */
    private suspend fun findReferencesInFile(
        filePath: String,
        definitionInfo: DefinitionInfo
    ): List<ReferenceLocation> {
        val references = mutableListOf<ReferenceLocation>()

        // Skip JAR files for now
        if (filePath.startsWith("jar://")) {
            return emptyList()
        }

        val file = File(filePath)
        if (!file.exists()) {
            return emptyList()
        }

        val content = file.readText()

        PSIThreadBridge.readAction {
            val ktFile = PSIBootstrap.parseKotlinFile(filePath, content)
            findReferencesInPsi(ktFile, content, filePath, definitionInfo, references)
        }

        return references
    }

    /**
     * Find references in a parsed PSI file.
     */
    private fun findReferencesInPsi(
        ktFile: KtFile,
        content: String,
        filePath: String,
        definitionInfo: DefinitionInfo,
        references: MutableList<ReferenceLocation>
    ) {
        ktFile.accept(object : KtTreeVisitorVoid() {
            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                super.visitReferenceExpression(expression)

                // Only handle name reference expressions
                if (expression !is KtNameReferenceExpression) {
                    return
                }

                val referencedName = expression.getReferencedName()

                // Quick check: name must match
                if (referencedName != definitionInfo.name) {
                    return
                }

                // Skip if this is the definition itself
                val refOffset = expression.textOffset
                if (filePath == definitionInfo.filePath && refOffset == definitionInfo.offset) {
                    return
                }

                // Try to resolve the reference to verify it points to our definition
                val resolved = expression.references.firstNotNullOfOrNull { it.resolve() }

                // If resolution works, check if it matches our definition
                val isMatch = if (resolved != null) {
                    val resolvedFile = resolved.containingFile
                    val resolvedOffset = resolved.textOffset
                    val resolvedFilePath = resolvedFile?.virtualFile?.path ?: resolvedFile?.name

                    (definitionInfo.filePath.endsWith(resolvedFile?.name ?: "") ||
                        resolvedFilePath == definitionInfo.filePath) &&
                        kotlin.math.abs(resolvedOffset - definitionInfo.offset) < 10
                } else {
                    // Resolution failed, fall back to name matching
                    true
                }

                if (isMatch) {
                    // Calculate line and column
                    val line = content.substring(0, refOffset.coerceAtMost(content.length))
                        .count { it == '\n' } + 1
                    val lastNewline = content.lastIndexOf('\n', refOffset - 1)
                    val column = if (lastNewline < 0) refOffset + 1 else refOffset - lastNewline

                    // Get context line
                    val lineStart = if (lastNewline < 0) 0 else lastNewline + 1
                    val lineEnd = content.indexOf('\n', refOffset).takeIf { it >= 0 } ?: content.length
                    val contextLine = content.substring(lineStart, lineEnd).trim()

                    references.add(
                        ReferenceLocation(
                            filePath = filePath,
                            line = line,
                            column = column,
                            offset = refOffset,
                            context = contextLine,
                            symbolName = referencedName
                        )
                    )
                }
            }
        })
    }

    /**
     * Get all indexed Kotlin file paths from all indexed directories.
     */
    private fun getIndexedKotlinFiles(indexer: ProjectIndexer): List<String> {
        val allFiles = mutableListOf<String>()

        // Get all indexed directories
        val indexedDirs = indexer.getIndexedDirectories()

        for (dirPath in indexedDirs) {
            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) {
                continue
            }

            dir.walkTopDown()
                .filter { file ->
                    file.isFile && file.extension.lowercase() == "kt"
                }
                .filter { file ->
                    // Skip build directories and hidden files
                    val relativePath = file.relativeTo(dir).path
                    !relativePath.contains("/build/") &&
                        !relativePath.contains("/.") &&
                        !relativePath.startsWith("build/") &&
                        !relativePath.startsWith(".")
                }
                .forEach { allFiles.add(it.absolutePath) }
        }

        return allFiles
    }

    companion object {
        val instance = ReferenceService()
    }
}
