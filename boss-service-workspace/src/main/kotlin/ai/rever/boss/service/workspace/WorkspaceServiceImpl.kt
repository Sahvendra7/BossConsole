package ai.rever.boss.service.workspace

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * gRPC implementation of WorkspaceService.
 * Phase 6 skeleton — in-memory workspace store with reactive streaming.
 */
class WorkspaceServiceImpl : WorkspaceServiceGrpcKt.WorkspaceServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(WorkspaceServiceImpl::class.java)
    private val workspaces = ConcurrentHashMap<String, WorkspaceInfo>()
    private val currentWorkspaceFlow = MutableStateFlow<WorkspaceInfo?>(null)

    override suspend fun getWorkspaces(request: Empty): WorkspacesResponse {
        return WorkspacesResponse.newBuilder()
            .addAllWorkspaces(workspaces.values.toList())
            .build()
    }

    override fun watchWorkspaces(request: Empty): Flow<WorkspacesResponse> = flow {
        emit(WorkspacesResponse.newBuilder().addAllWorkspaces(workspaces.values.toList()).build())
    }

    override suspend fun getCurrentWorkspace(request: Empty): WorkspaceResponse {
        val ws = currentWorkspaceFlow.value
        return WorkspaceResponse.newBuilder()
            .setFound(ws != null)
            .apply { ws?.let { setWorkspace(it) } }
            .build()
    }

    override fun watchCurrentWorkspace(request: Empty): Flow<WorkspaceResponse> = flow {
        currentWorkspaceFlow.collect { ws ->
            emit(
                WorkspaceResponse.newBuilder()
                    .setFound(ws != null)
                    .apply { ws?.let { setWorkspace(it) } }
                    .build()
            )
        }
    }

    override suspend fun loadWorkspace(request: LoadWorkspaceRequest): WorkspaceResponse {
        logger.info("loadWorkspace: id={}", request.workspaceId)
        val ws = workspaces[request.workspaceId]
        if (ws != null) {
            currentWorkspaceFlow.value = ws
        }
        return WorkspaceResponse.newBuilder()
            .setFound(ws != null)
            .apply { ws?.let { setWorkspace(it) } }
            .build()
    }

    override suspend fun saveWorkspace(request: SaveWorkspaceRequest): WorkspaceResponse {
        logger.info("saveWorkspace: id={}", request.workspaceId)
        val ws = WorkspaceInfo.newBuilder()
            .setId(request.workspaceId)
            .setName(request.name)
            .setProjectPath(request.projectPath)
            .putAllMetadata(request.metadataMap)
            .build()
        workspaces[ws.id] = ws
        return WorkspaceResponse.newBuilder().setFound(true).setWorkspace(ws).build()
    }

    override suspend fun deleteWorkspace(request: DeleteWorkspaceRequest): Empty {
        logger.info("deleteWorkspace: id={}", request.workspaceId)
        workspaces.remove(request.workspaceId)
        if (currentWorkspaceFlow.value?.id == request.workspaceId) {
            currentWorkspaceFlow.value = null
        }
        return Empty.getDefaultInstance()
    }
}
