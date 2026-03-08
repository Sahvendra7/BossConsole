package ai.rever.boss.app.terminal

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.slf4j.LoggerFactory

/**
 * gRPC implementation of TerminalService.
 *
 * Phase 5 skeleton — logs method calls for now.
 * Full PTY4J integration is added when this process moves to production.
 */
class TerminalServiceImpl : TerminalServiceGrpcKt.TerminalServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(TerminalServiceImpl::class.java)

    override suspend fun createSession(request: CreateSessionRequest): CreateSessionResponse {
        logger.info("createSession: workdir={}, command={}", request.workingDirectory, request.commandList)
        return CreateSessionResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("PTY4J integration pending (Phase 5 skeleton)")
            .build()
    }

    override suspend fun sendInput(request: SendInputRequest): Empty {
        logger.debug("sendInput: session={}, bytes={}", request.sessionId, request.data.size())
        return Empty.getDefaultInstance()
    }

    override fun streamOutput(request: StreamOutputRequest): Flow<TerminalOutputChunk> {
        logger.debug("streamOutput: session={}", request.sessionId)
        return emptyFlow()
    }

    override suspend fun resize(request: ResizeRequest): Empty {
        logger.debug("resize: session={}, cols={}, rows={}", request.sessionId, request.cols, request.rows)
        return Empty.getDefaultInstance()
    }

    override suspend fun closeSession(request: CloseSessionRequest): Empty {
        logger.info("closeSession: session={}", request.sessionId)
        return Empty.getDefaultInstance()
    }

    override suspend fun listSessions(request: Empty): ListSessionsResponse {
        return ListSessionsResponse.newBuilder().build()
    }
}
