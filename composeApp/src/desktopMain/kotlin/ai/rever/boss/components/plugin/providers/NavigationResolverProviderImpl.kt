package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.api.NavigationResolverProvider
import ai.rever.boss.plugin.api.NavigationResolveResult
import ai.rever.boss.plugin.api.DefinitionInfoData
import ai.rever.boss.plugin.api.ReferenceLocationData
import ai.rever.boss.plugin.api.NavigationTargetKind as ApiNavigationTargetKind
import ai.rever.bosseditor.psi.NavigationResult
import ai.rever.bosseditor.psi.NavigationService
import ai.rever.bosseditor.psi.NavigationTargetKind
import ai.rever.bosseditor.psi.PSIBootstrap
import ai.rever.bosseditor.psi.PSIThreadBridge
import ai.rever.bosseditor.psi.ProjectIndexer
import ai.rever.bosseditor.psi.ReferenceService
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = BossLogger.forComponent("NavigationResolverProvider")

/**
 * Implementation of NavigationResolverProvider that uses the host's PSI infrastructure.
 *
 * This allows dynamic plugins to use the same navigation capabilities as the bundled editor.
 * When clicking on a definition, it returns ShowUsages with all references.
 * When clicking on a reference, it returns Found with the definition location.
 */
class NavigationResolverProviderImpl : NavigationResolverProvider {
    private val navigationService = NavigationService()
    private val referenceService = ReferenceService()

    init {
        logger.info(LogCategory.EDITOR, "[NAV-DEBUG] NavigationResolverProviderImpl created, PSI initialized: ${PSIBootstrap.isInitialized}")
    }

    override val isInitialized: Boolean
        get() = PSIBootstrap.isInitialized

    override suspend fun resolveNavigation(
        content: String,
        filePath: String,
        offset: Int
    ): NavigationResolveResult {
        logger.info(LogCategory.EDITOR, "[NAV-DEBUG] resolveNavigation called", mapOf(
            "filePath" to filePath,
            "offset" to offset.toString(),
            "contentLength" to content.length.toString(),
            "psiInitialized" to PSIBootstrap.isInitialized.toString()
        ))

        // Only support Kotlin files
        if (!filePath.endsWith(".kt") && !filePath.endsWith(".kts")) {
            logger.info(LogCategory.EDITOR, "[NAV-DEBUG] Not a Kotlin file, returning Unavailable")
            return NavigationResolveResult.Unavailable
        }

        // Check if PSI is initialized
        if (!PSIBootstrap.isInitialized) {
            logger.warn(LogCategory.EDITOR, "[NAV-DEBUG] PSI not initialized, returning Unavailable")
            return NavigationResolveResult.Unavailable
        }

        return withContext(Dispatchers.IO) {
            try {
                val fileName = filePath.substringAfterLast('/')
                logger.info(LogCategory.EDITOR, "[NAV-DEBUG] Parsing file: $fileName")

                val ktFile = PSIThreadBridge.readAction {
                    PSIBootstrap.parseKotlinFile(fileName, content)
                }
                logger.info(LogCategory.EDITOR, "[NAV-DEBUG] File parsed successfully")

                // Check if clicking on a definition
                val isDefinition = PSIThreadBridge.readAction {
                    navigationService.isDefinition(ktFile, offset)
                }
                logger.info(LogCategory.EDITOR, "[NAV-DEBUG] isDefinition at offset $offset: $isDefinition")

                if (isDefinition) {
                    // Get definition info and find usages
                    val definitionInfo = PSIThreadBridge.readAction {
                        navigationService.getDefinitionInfo(ktFile, offset, filePath)
                    }

                    if (definitionInfo != null) {
                        logger.info(LogCategory.EDITOR, "[NAV-DEBUG] Finding references for: ${definitionInfo.name}")

                        // Find all references to this definition
                        val references = referenceService.findReferences(definitionInfo)
                        logger.info(LogCategory.EDITOR, "[NAV-DEBUG] Found ${references.size} references")

                        // Convert to plugin API types
                        val apiReferences = references.map { ref ->
                            ReferenceLocationData(
                                filePath = ref.filePath,
                                line = ref.line,
                                column = ref.column,
                                offset = ref.offset,
                                context = ref.context,
                                symbolName = ref.symbolName
                            )
                        }

                        val apiDefinition = DefinitionInfoData(
                            name = definitionInfo.name,
                            kind = mapKindToApi(definitionInfo.kind),
                            filePath = definitionInfo.filePath,
                            offset = definitionInfo.offset,
                            line = definitionInfo.line,
                            column = definitionInfo.column
                        )

                        return@withContext NavigationResolveResult.ShowUsages(
                            references = apiReferences,
                            definition = apiDefinition
                        )
                    }
                }

                // Not a definition - perform normal go-to-definition
                val result = PSIThreadBridge.readAction {
                    navigationService.goToDefinition(ktFile, offset, filePath)
                }
                logger.info(LogCategory.EDITOR, "[NAV-DEBUG] Navigation result: $result")

                when (result) {
                    is NavigationResult.Found -> {
                        logger.info(LogCategory.EDITOR, "[NAV-DEBUG] Found target", mapOf(
                            "targetFile" to result.target.filePath,
                            "line" to result.target.line.toString(),
                            "column" to result.target.column.toString()
                        ))
                        NavigationResolveResult.Found(
                            filePath = result.target.filePath,
                            line = result.target.line,
                            column = result.target.column
                        )
                    }
                    is NavigationResult.MultipleTargets -> {
                        logger.info(LogCategory.EDITOR, "[NAV-DEBUG] Multiple targets found: ${result.targets.size}")
                        // Return the first target
                        result.targets.firstOrNull()?.let { target ->
                            NavigationResolveResult.Found(
                                filePath = target.filePath,
                                line = target.line,
                                column = target.column
                            )
                        } ?: NavigationResolveResult.NotFound
                    }
                    is NavigationResult.NotNavigable -> {
                        logger.info(LogCategory.EDITOR, "[NAV-DEBUG] Not navigable at offset $offset")
                        NavigationResolveResult.NotFound
                    }
                    is NavigationResult.Error -> {
                        logger.warn(LogCategory.EDITOR, "[NAV-DEBUG] Navigation error: ${(result as NavigationResult.Error).message}")
                        NavigationResolveResult.NotFound
                    }
                }
            } catch (e: Exception) {
                logger.error(LogCategory.EDITOR, "[NAV-DEBUG] Exception during navigation", error = e)
                NavigationResolveResult.NotFound
            }
        }
    }

    /**
     * Maps host NavigationTargetKind to plugin API NavigationTargetKind.
     */
    private fun mapKindToApi(kind: NavigationTargetKind): ApiNavigationTargetKind = when (kind) {
        NavigationTargetKind.CLASS -> ApiNavigationTargetKind.CLASS
        NavigationTargetKind.INTERFACE -> ApiNavigationTargetKind.INTERFACE
        NavigationTargetKind.OBJECT -> ApiNavigationTargetKind.OBJECT
        NavigationTargetKind.FUNCTION -> ApiNavigationTargetKind.FUNCTION
        NavigationTargetKind.PROPERTY -> ApiNavigationTargetKind.PROPERTY
        NavigationTargetKind.PARAMETER -> ApiNavigationTargetKind.PARAMETER
        NavigationTargetKind.VARIABLE -> ApiNavigationTargetKind.VARIABLE
        NavigationTargetKind.TYPE_ALIAS -> ApiNavigationTargetKind.TYPE_PARAMETER
        NavigationTargetKind.CONSTRUCTOR -> ApiNavigationTargetKind.FUNCTION
        NavigationTargetKind.UNKNOWN -> ApiNavigationTargetKind.UNKNOWN
    }

    override suspend fun ensureProjectIndexed(filePath: String) {
        if (filePath.isEmpty()) return

        logger.info(LogCategory.EDITOR, "[NAV-DEBUG] ensureProjectIndexed called", mapOf(
            "filePath" to filePath,
            "hasIndexer" to (ProjectIndexer.current != null).toString()
        ))

        withContext(Dispatchers.IO) {
            ProjectIndexer.current?.ensureFileProjectIndexed(filePath)
        }
    }
}
