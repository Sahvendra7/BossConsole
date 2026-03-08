package ai.rever.boss.app.editor

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.*
import org.slf4j.LoggerFactory

/**
 * gRPC implementation of EditorService.
 *
 * Phase 5 skeleton — logs method calls.
 * Full BossEditor + LSP + PSI integration added when this process moves to production.
 */
class EditorServiceImpl : EditorServiceGrpcKt.EditorServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(EditorServiceImpl::class.java)

    override suspend fun openFile(request: OpenFileRequest): OpenFileResponse {
        logger.info("openFile: path={}, line={}", request.path, request.line)
        return OpenFileResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("BossEditor integration pending (Phase 5 skeleton)")
            .build()
    }

    override suspend fun saveFile(request: SaveFileRequest): Empty {
        logger.info("saveFile: path={}", request.path)
        return Empty.getDefaultInstance()
    }

    override suspend fun getTokens(request: GetTokensRequest): GetTokensResponse {
        logger.debug("getTokens: path={}", request.path)
        return GetTokensResponse.newBuilder().build()
    }

    override suspend fun navigateToDefinition(request: NavigateRequest): NavigateResponse {
        logger.info("navigateToDefinition: path={}, line={}, col={}", request.path, request.line, request.column)
        return NavigateResponse.newBuilder()
            .setFound(false)
            .build()
    }

    override suspend fun detectMainFunctions(request: DetectMainRequest): DetectMainResponse {
        logger.info("detectMainFunctions: path={}", request.path)
        return DetectMainResponse.newBuilder().build()
    }

    override suspend fun listOpenFiles(request: Empty): ListOpenFilesResponse {
        return ListOpenFilesResponse.newBuilder().build()
    }
}
