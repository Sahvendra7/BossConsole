package ai.rever.boss.ipc.proto

import ai.rever.boss.ipc.proto.MasteryServiceGrpc.getServiceDescriptor
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
 * Holder for Kotlin coroutine-based client and server APIs for boss.ipc.v1.MasteryService.
 */
public object MasteryServiceGrpcKt {
  public const val SERVICE_NAME: String = MasteryServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val createMasteryMethod: MethodDescriptor<MasteryDefinition, MasteryId>
    @JvmStatic
    get() = MasteryServiceGrpc.getCreateMasteryMethod()

  public val executeMasteryMethod: MethodDescriptor<ExecuteMasteryRequest, MasteryProgress>
    @JvmStatic
    get() = MasteryServiceGrpc.getExecuteMasteryMethod()

  public val cancelMasteryMethod: MethodDescriptor<MasteryExecutionId, CancelMasteryResponse>
    @JvmStatic
    get() = MasteryServiceGrpc.getCancelMasteryMethod()

  public val getMasteryStatusMethod: MethodDescriptor<MasteryExecutionId, MasteryStatus>
    @JvmStatic
    get() = MasteryServiceGrpc.getGetMasteryStatusMethod()

  public val generateMasteryMethod: MethodDescriptor<GenerateMasteryRequest, MasteryDefinition>
    @JvmStatic
    get() = MasteryServiceGrpc.getGenerateMasteryMethod()

  public val listMasteriesMethod: MethodDescriptor<ListMasteriesRequest, ListMasteriesResponse>
    @JvmStatic
    get() = MasteryServiceGrpc.getListMasteriesMethod()

  public val deleteMasteryMethod: MethodDescriptor<MasteryId, Empty>
    @JvmStatic
    get() = MasteryServiceGrpc.getDeleteMasteryMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.MasteryService service as suspending coroutines.
   */
  @StubFor(MasteryServiceGrpc::class)
  public class MasteryServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<MasteryServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): MasteryServiceCoroutineStub =
        MasteryServiceCoroutineStub(channel, callOptions)

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
    public suspend fun createMastery(request: MasteryDefinition, headers: Metadata = Metadata()):
        MasteryId = unaryRpc(
      channel,
      MasteryServiceGrpc.getCreateMasteryMethod(),
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
    public fun executeMastery(request: ExecuteMasteryRequest, headers: Metadata = Metadata()):
        Flow<MasteryProgress> = serverStreamingRpc(
      channel,
      MasteryServiceGrpc.getExecuteMasteryMethod(),
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
    public suspend fun cancelMastery(request: MasteryExecutionId, headers: Metadata = Metadata()):
        CancelMasteryResponse = unaryRpc(
      channel,
      MasteryServiceGrpc.getCancelMasteryMethod(),
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
    public suspend fun getMasteryStatus(request: MasteryExecutionId, headers: Metadata =
        Metadata()): MasteryStatus = unaryRpc(
      channel,
      MasteryServiceGrpc.getGetMasteryStatusMethod(),
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
    public suspend fun generateMastery(request: GenerateMasteryRequest, headers: Metadata =
        Metadata()): MasteryDefinition = unaryRpc(
      channel,
      MasteryServiceGrpc.getGenerateMasteryMethod(),
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
    public suspend fun listMasteries(request: ListMasteriesRequest, headers: Metadata = Metadata()):
        ListMasteriesResponse = unaryRpc(
      channel,
      MasteryServiceGrpc.getListMasteriesMethod(),
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
    public suspend fun deleteMastery(request: MasteryId, headers: Metadata = Metadata()): Empty =
        unaryRpc(
      channel,
      MasteryServiceGrpc.getDeleteMasteryMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.MasteryService service based on Kotlin coroutines.
   */
  public abstract class MasteryServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.MasteryService.CreateMastery.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun createMastery(request: MasteryDefinition): MasteryId = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.MasteryService.CreateMastery is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for boss.ipc.v1.MasteryService.ExecuteMastery.
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
    public open fun executeMastery(request: ExecuteMasteryRequest): Flow<MasteryProgress> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.MasteryService.ExecuteMastery is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.MasteryService.CancelMastery.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun cancelMastery(request: MasteryExecutionId): CancelMasteryResponse =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.MasteryService.CancelMastery is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.MasteryService.GetMasteryStatus.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getMasteryStatus(request: MasteryExecutionId): MasteryStatus = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.MasteryService.GetMasteryStatus is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.MasteryService.GenerateMastery.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun generateMastery(request: GenerateMasteryRequest): MasteryDefinition =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.MasteryService.GenerateMastery is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.MasteryService.ListMasteries.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun listMasteries(request: ListMasteriesRequest): ListMasteriesResponse =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.MasteryService.ListMasteries is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.MasteryService.DeleteMastery.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun deleteMastery(request: MasteryId): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.MasteryService.DeleteMastery is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = MasteryServiceGrpc.getCreateMasteryMethod(),
      implementation = ::createMastery
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = MasteryServiceGrpc.getExecuteMasteryMethod(),
      implementation = ::executeMastery
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = MasteryServiceGrpc.getCancelMasteryMethod(),
      implementation = ::cancelMastery
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = MasteryServiceGrpc.getGetMasteryStatusMethod(),
      implementation = ::getMasteryStatus
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = MasteryServiceGrpc.getGenerateMasteryMethod(),
      implementation = ::generateMastery
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = MasteryServiceGrpc.getListMasteriesMethod(),
      implementation = ::listMasteries
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = MasteryServiceGrpc.getDeleteMasteryMethod(),
      implementation = ::deleteMastery
    )).build()
  }
}
