package ai.rever.boss.mastery.orchestrator

import ai.rever.boss.mastery.CapabilityInfo
import ai.rever.boss.mastery.CapabilityResolver
import ai.rever.boss.process.ProcessRegistry
import org.slf4j.LoggerFactory

/**
 * [CapabilityResolver] implementation backed by the kernel's [ProcessRegistry].
 *
 * Aggregates capabilities from all registered process manifests for [MasteryExecutor],
 * and dispatches invocations to the owning plugin process via gRPC IPC.
 *
 * Current state: capability discovery is wired; invocation is stubbed until the
 * plugin-capability gRPC RPC is defined in Phase 7.
 */
class ProcessRegistryCapabilityResolver(
    private val processRegistry: ProcessRegistry,
) : CapabilityResolver {

    private val logger = LoggerFactory.getLogger(ProcessRegistryCapabilityResolver::class.java)

    override suspend fun invoke(
        pluginId: String,
        action: String,
        input: Map<String, String>,
    ): Map<String, String> {
        val capability = processRegistry.findCapability(pluginId, action)
        if (capability == null) {
            logger.warn("Capability not found: pluginId={}, action={}", pluginId, action)
            return emptyMap()
        }

        val process = processRegistry.getProcess(pluginId)
        if (process == null) {
            logger.warn("Process not running for pluginId={}", pluginId)
            return emptyMap()
        }

        // Phase 7 will add a CapabilityInvocationService to each plugin process.
        // For now we log the intent and return empty — the DAG executor handles empty
        // outputs gracefully (nodes that produce nothing simply contribute nothing to
        // the next node's input mapping).
        logger.info(
            "Capability invocation stub: pluginId={}, action={}, inputKeys={}",
            pluginId, action, input.keys,
        )
        return emptyMap()
    }

    override fun getAvailableCapabilities(): List<CapabilityInfo> {
        // PluginCapability proto has: action, input_schema_json, output_schema_json, description
        // The process ID (== pluginId) comes from the manifest key in the registry.
        return processRegistry.getAllProcesses().flatMap { process ->
            val manifest = processRegistry.getManifest(process.config.processId)
                ?: return@flatMap emptyList()
            manifest.capabilitiesList.map { cap ->
                CapabilityInfo(
                    pluginId = process.config.processId,
                    action = cap.action,
                    description = cap.description,
                    inputSchemaJson = cap.inputSchemaJson.ifBlank { "{}" },
                    outputSchemaJson = cap.outputSchemaJson.ifBlank { "{}" },
                )
            }
        }
    }
}
