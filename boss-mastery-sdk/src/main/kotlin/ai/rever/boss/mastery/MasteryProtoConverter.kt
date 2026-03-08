package ai.rever.boss.mastery

import ai.rever.boss.ipc.proto.MasteryCompleted
import ai.rever.boss.ipc.proto.MasteryDefinition as ProtoMasteryDefinition
import ai.rever.boss.ipc.proto.MasteryEdge as ProtoMasteryEdge
import ai.rever.boss.ipc.proto.MasteryFailed
import ai.rever.boss.ipc.proto.MasteryNode as ProtoMasteryNode
import ai.rever.boss.ipc.proto.MasteryProgress as ProtoMasteryProgress
import ai.rever.boss.ipc.proto.MasteryStarted
import ai.rever.boss.ipc.proto.NodeCompleted as ProtoNodeCompleted
import ai.rever.boss.ipc.proto.NodeFailed as ProtoNodeFailed
import ai.rever.boss.ipc.proto.NodeStarted as ProtoNodeStarted

/**
 * Converts between the Kotlin [MasteryDefinition] model and protobuf messages from mastery.proto.
 */
object MasteryProtoConverter {

    fun MasteryDefinition.toProto(): ProtoMasteryDefinition =
        ProtoMasteryDefinition.newBuilder()
            .setId(id)
            .setName(name)
            .setDescription(description)
            .setInputSchemaJson(inputSchemaJson)
            .setOutputSchemaJson(outputSchemaJson)
            .addAllNodes(nodes.map { it.toProto() })
            .addAllEdges(edges.map { it.toProto() })
            .setAuthor(author)
            .setCreatedAt(createdAt)
            .setUpdatedAt(updatedAt)
            .build()

    fun ProtoMasteryDefinition.toKotlin(): MasteryDefinition =
        MasteryDefinition(
            id = id,
            name = name,
            description = description,
            inputSchemaJson = inputSchemaJson,
            outputSchemaJson = outputSchemaJson,
            nodes = nodesList.map { it.toKotlin() },
            edges = edgesList.map { it.toKotlin() },
            author = author,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun MasteryNode.toProto(): ProtoMasteryNode =
        ProtoMasteryNode.newBuilder()
            .setId(id)
            .setPluginId(pluginId)
            .setAction(action)
            .putAllInputMapping(inputMapping)
            .putAllStaticConfig(staticConfig)
            .setIsAgentCall(isAgentCall)
            .apply { agentPrompt?.let { setAgentPrompt(it) } }
            .setMaxRetries(maxRetries)
            .setTimeoutMs(timeoutMs)
            .setDisplayName(displayName)
            .build()

    private fun ProtoMasteryNode.toKotlin(): MasteryNode =
        MasteryNode(
            id = id,
            pluginId = pluginId,
            action = action,
            inputMapping = inputMappingMap,
            staticConfig = staticConfigMap,
            isAgentCall = isAgentCall,
            agentPrompt = agentPrompt.ifEmpty { null },
            maxRetries = maxRetries,
            timeoutMs = timeoutMs,
            displayName = displayName,
        )

    private fun MasteryEdge.toProto(): ProtoMasteryEdge =
        ProtoMasteryEdge.newBuilder()
            .setFromNode(fromNode)
            .setToNode(toNode)
            .setOutputKey(outputKey)
            .setInputKey(inputKey)
            .apply { condition?.let { setCondition(it) } }
            .build()

    private fun ProtoMasteryEdge.toKotlin(): MasteryEdge =
        MasteryEdge(
            fromNode = fromNode,
            toNode = toNode,
            outputKey = outputKey,
            inputKey = inputKey,
            condition = condition.ifEmpty { null },
        )

    /**
     * Convert a Kotlin [MasteryProgress] event to its protobuf representation.
     *
     * @param executionId The running execution ID to embed in the proto message
     */
    fun MasteryProgress.toProto(executionId: String): ProtoMasteryProgress {
        val builder = ProtoMasteryProgress.newBuilder()
            .setExecutionId(executionId)
            .setTimestamp(System.currentTimeMillis())

        when (this) {
            is MasteryProgress.Started -> builder.setStarted(
                MasteryStarted.newBuilder()
                    .setMasteryId(masteryId)
                    .setTotalNodes(totalNodes)
                    .build()
            )
            is MasteryProgress.NodeStarted -> builder.setNodeStarted(
                ProtoNodeStarted.newBuilder()
                    .setNodeId(nodeId)
                    .setDisplayName(displayName)
                    .build()
            )
            is MasteryProgress.NodeCompleted -> builder.setNodeCompleted(
                ProtoNodeCompleted.newBuilder()
                    .setNodeId(nodeId)
                    .putAllOutput(output)
                    .setDurationMs(durationMs)
                    .build()
            )
            is MasteryProgress.NodeFailed -> builder.setNodeFailed(
                ProtoNodeFailed.newBuilder()
                    .setNodeId(nodeId)
                    .setErrorMessage(error)
                    .setWillRetry(willRetry)
                    .build()
            )
            is MasteryProgress.Completed -> builder.setCompleted(
                MasteryCompleted.newBuilder()
                    .putAllOutput(output)
                    .setTotalDurationMs(totalDurationMs)
                    .build()
            )
            is MasteryProgress.Failed -> builder.setFailed(
                MasteryFailed.newBuilder()
                    .setErrorMessage(error)
                    .setFailedNodeId(failedNodeId)
                    .build()
            )
        }

        return builder.build()
    }
}
