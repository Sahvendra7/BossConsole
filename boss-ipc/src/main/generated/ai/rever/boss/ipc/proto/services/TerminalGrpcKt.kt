package ai.rever.boss.ipc.proto.services

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.TerminalServiceGrpc.getServiceDescriptor
import io.grpc.CallOptions
import io.grpc.CallOptions.DEFAULT
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.ServerServiceDefinition.builder
import io.grpc.ServiceDescriptor
import io.grpc.Status.UNIMPLEMENTED
import io.grpc.StatusException
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.AbstractCoroutineStub
import io.grpc.kotlin.ClientCalls.serverStreamingRpc
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls.serverStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.String
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow

/**
 * Holder for Kotlin coroutine-based client and server APIs for
 * boss.ipc.v1.services.TerminalService.
 */
public object TerminalServiceGrpcKt {
  public const val SERVICE_NAME: String = TerminalServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val createSessionMethod: MethodDescriptor<CreateSessionRequest, CreateSessionResponse>
    @JvmStatic
    get() = TerminalServiceGrpc.getCreateSessionMethod()

  public val sendInputMethod: MethodDescriptor<SendInputRequest, Empty>
    @JvmStatic
    get() = TerminalServiceGrpc.getSendInputMethod()

  public val streamOutputMethod: MethodDescriptor<StreamOutputRequest, TerminalOutputChunk>
    @JvmStatic
    get() = TerminalServiceGrpc.getStreamOutputMethod()

  public val resizeMethod: MethodDescriptor<ResizeRequest, Empty>
    @JvmStatic
    get() = TerminalServiceGrpc.getResizeMethod()

  public val closeSessionMethod: MethodDescriptor<CloseSessionRequest, Empty>
    @JvmStatic
    get() = TerminalServiceGrpc.getCloseSessionMethod()

  public val listSessionsMethod: MethodDescriptor<Empty, ListSessionsResponse>
    @JvmStatic
    get() = TerminalServiceGrpc.getListSessionsMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.services.TerminalService service as suspending
   * coroutines.
   */
  @StubFor(TerminalServiceGrpc::class)
  public class TerminalServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<TerminalServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): TerminalServiceCoroutineStub =
        TerminalServiceCoroutineStub(channel, callOptions)

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun createSession(request: CreateSessionRequest, headers: Metadata = Metadata()):
        CreateSessionResponse = unaryRpc(
      channel,
      TerminalServiceGrpc.getCreateSessionMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun sendInput(request: SendInputRequest, headers: Metadata = Metadata()): Empty =
        unaryRpc(
      channel,
      TerminalServiceGrpc.getSendInputMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][io.grpc.Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    public fun streamOutput(request: StreamOutputRequest, headers: Metadata = Metadata()):
        Flow<TerminalOutputChunk> = serverStreamingRpc(
      channel,
      TerminalServiceGrpc.getStreamOutputMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun resize(request: ResizeRequest, headers: Metadata = Metadata()): Empty =
        unaryRpc(
      channel,
      TerminalServiceGrpc.getResizeMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun closeSession(request: CloseSessionRequest, headers: Metadata = Metadata()):
        Empty = unaryRpc(
      channel,
      TerminalServiceGrpc.getCloseSessionMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun listSessions(request: Empty, headers: Metadata = Metadata()):
        ListSessionsResponse = unaryRpc(
      channel,
      TerminalServiceGrpc.getListSessionsMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.services.TerminalService service based on Kotlin
   * coroutines.
   */
  public abstract class TerminalServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.services.TerminalService.CreateSession.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun createSession(request: CreateSessionRequest): CreateSessionResponse =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.TerminalService.CreateSession is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.TerminalService.SendInput.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun sendInput(request: SendInputRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.TerminalService.SendInput is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * boss.ipc.v1.services.TerminalService.StreamOutput.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [io.grpc.Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open fun streamOutput(request: StreamOutputRequest): Flow<TerminalOutputChunk> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.TerminalService.StreamOutput is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.TerminalService.Resize.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun resize(request: ResizeRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.TerminalService.Resize is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.TerminalService.CloseSession.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun closeSession(request: CloseSessionRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.TerminalService.CloseSession is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.TerminalService.ListSessions.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun listSessions(request: Empty): ListSessionsResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.TerminalService.ListSessions is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = TerminalServiceGrpc.getCreateSessionMethod(),
      implementation = ::createSession
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = TerminalServiceGrpc.getSendInputMethod(),
      implementation = ::sendInput
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = TerminalServiceGrpc.getStreamOutputMethod(),
      implementation = ::streamOutput
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = TerminalServiceGrpc.getResizeMethod(),
      implementation = ::resize
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = TerminalServiceGrpc.getCloseSessionMethod(),
      implementation = ::closeSession
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = TerminalServiceGrpc.getListSessionsMethod(),
      implementation = ::listSessions
    )).build()
  }
}
