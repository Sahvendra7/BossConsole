package ai.rever.boss.ipc.proto

import ai.rever.boss.ipc.proto.KernelServiceGrpc.getServiceDescriptor
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
import io.grpc.kotlin.ClientCalls.bidiStreamingRpc
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls.bidiStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.String
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow

/**
 * Holder for Kotlin coroutine-based client and server APIs for boss.ipc.v1.KernelService.
 */
public object KernelServiceGrpcKt {
  public const val SERVICE_NAME: String = KernelServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val registerProcessMethod:
      MethodDescriptor<RegisterProcessRequest, RegisterProcessResponse>
    @JvmStatic
    get() = KernelServiceGrpc.getRegisterProcessMethod()

  public val heartbeatMethod: MethodDescriptor<HeartbeatPing, HeartbeatPong>
    @JvmStatic
    get() = KernelServiceGrpc.getHeartbeatMethod()

  public val requestShutdownMethod: MethodDescriptor<ShutdownRequest, ShutdownResponse>
    @JvmStatic
    get() = KernelServiceGrpc.getRequestShutdownMethod()

  public val getProcessStatusMethod: MethodDescriptor<ProcessStatusRequest, ProcessStatusResponse>
    @JvmStatic
    get() = KernelServiceGrpc.getGetProcessStatusMethod()

  public val listProcessesMethod: MethodDescriptor<Empty, ListProcessesResponse>
    @JvmStatic
    get() = KernelServiceGrpc.getListProcessesMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.KernelService service as suspending coroutines.
   */
  @StubFor(KernelServiceGrpc::class)
  public class KernelServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<KernelServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): KernelServiceCoroutineStub =
        KernelServiceCoroutineStub(channel, callOptions)

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
    public suspend fun registerProcess(request: RegisterProcessRequest, headers: Metadata =
        Metadata()): RegisterProcessResponse = unaryRpc(
      channel,
      KernelServiceGrpc.getRegisterProcessMethod(),
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
     * The [Flow] of requests is collected once each time the [Flow] of responses is
     * collected. If collection of the [Flow] of responses completes normally or
     * exceptionally before collection of `requests` completes, the collection of
     * `requests` is cancelled.  If the collection of `requests` completes
     * exceptionally for any other reason, then the collection of the [Flow] of responses
     * completes exceptionally for the same reason and the RPC is cancelled with that reason.
     *
     * @param requests A [Flow] of request messages.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    public fun heartbeat(requests: Flow<HeartbeatPing>, headers: Metadata = Metadata()):
        Flow<HeartbeatPong> = bidiStreamingRpc(
      channel,
      KernelServiceGrpc.getHeartbeatMethod(),
      requests,
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
    public suspend fun requestShutdown(request: ShutdownRequest, headers: Metadata = Metadata()):
        ShutdownResponse = unaryRpc(
      channel,
      KernelServiceGrpc.getRequestShutdownMethod(),
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
    public suspend fun getProcessStatus(request: ProcessStatusRequest, headers: Metadata =
        Metadata()): ProcessStatusResponse = unaryRpc(
      channel,
      KernelServiceGrpc.getGetProcessStatusMethod(),
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
    public suspend fun listProcesses(request: Empty, headers: Metadata = Metadata()):
        ListProcessesResponse = unaryRpc(
      channel,
      KernelServiceGrpc.getListProcessesMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.KernelService service based on Kotlin coroutines.
   */
  public abstract class KernelServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.KernelService.RegisterProcess.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun registerProcess(request: RegisterProcessRequest):
        RegisterProcessResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.KernelService.RegisterProcess is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for boss.ipc.v1.KernelService.Heartbeat.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [io.grpc.Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param requests A [Flow] of requests from the client.  This flow can be
     *        collected only once and throws [java.lang.IllegalStateException] on attempts to
     * collect
     *        it more than once.
     */
    public open fun heartbeat(requests: Flow<HeartbeatPing>): Flow<HeartbeatPong> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.KernelService.Heartbeat is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.KernelService.RequestShutdown.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun requestShutdown(request: ShutdownRequest): ShutdownResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.KernelService.RequestShutdown is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.KernelService.GetProcessStatus.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getProcessStatus(request: ProcessStatusRequest): ProcessStatusResponse =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.KernelService.GetProcessStatus is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.KernelService.ListProcesses.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun listProcesses(request: Empty): ListProcessesResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.KernelService.ListProcesses is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = KernelServiceGrpc.getRegisterProcessMethod(),
      implementation = ::registerProcess
    ))
      .addMethod(bidiStreamingServerMethodDefinition(
      context = this.context,
      descriptor = KernelServiceGrpc.getHeartbeatMethod(),
      implementation = ::heartbeat
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = KernelServiceGrpc.getRequestShutdownMethod(),
      implementation = ::requestShutdown
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = KernelServiceGrpc.getGetProcessStatusMethod(),
      implementation = ::getProcessStatus
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = KernelServiceGrpc.getListProcessesMethod(),
      implementation = ::listProcesses
    )).build()
  }
}
