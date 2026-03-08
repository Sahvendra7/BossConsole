package ai.rever.boss.ipc.proto

import ai.rever.boss.ipc.proto.SnapshotServiceGrpc.getServiceDescriptor
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
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.String
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Holder for Kotlin coroutine-based client and server APIs for boss.ipc.v1.SnapshotService.
 */
public object SnapshotServiceGrpcKt {
  public const val SERVICE_NAME: String = SnapshotServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val saveSnapshotMethod: MethodDescriptor<SaveSnapshotRequest, SaveSnapshotResponse>
    @JvmStatic
    get() = SnapshotServiceGrpc.getSaveSnapshotMethod()

  public val loadSnapshotMethod: MethodDescriptor<LoadSnapshotRequest, LoadSnapshotResponse>
    @JvmStatic
    get() = SnapshotServiceGrpc.getLoadSnapshotMethod()

  public val listSnapshotsMethod: MethodDescriptor<ListSnapshotsRequest, ListSnapshotsResponse>
    @JvmStatic
    get() = SnapshotServiceGrpc.getListSnapshotsMethod()

  public val deleteSnapshotMethod: MethodDescriptor<DeleteSnapshotRequest, Empty>
    @JvmStatic
    get() = SnapshotServiceGrpc.getDeleteSnapshotMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.SnapshotService service as suspending coroutines.
   */
  @StubFor(SnapshotServiceGrpc::class)
  public class SnapshotServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<SnapshotServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): SnapshotServiceCoroutineStub =
        SnapshotServiceCoroutineStub(channel, callOptions)

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
    public suspend fun saveSnapshot(request: SaveSnapshotRequest, headers: Metadata = Metadata()):
        SaveSnapshotResponse = unaryRpc(
      channel,
      SnapshotServiceGrpc.getSaveSnapshotMethod(),
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
    public suspend fun loadSnapshot(request: LoadSnapshotRequest, headers: Metadata = Metadata()):
        LoadSnapshotResponse = unaryRpc(
      channel,
      SnapshotServiceGrpc.getLoadSnapshotMethod(),
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
    public suspend fun listSnapshots(request: ListSnapshotsRequest, headers: Metadata = Metadata()):
        ListSnapshotsResponse = unaryRpc(
      channel,
      SnapshotServiceGrpc.getListSnapshotsMethod(),
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
    public suspend fun deleteSnapshot(request: DeleteSnapshotRequest, headers: Metadata =
        Metadata()): Empty = unaryRpc(
      channel,
      SnapshotServiceGrpc.getDeleteSnapshotMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.SnapshotService service based on Kotlin coroutines.
   */
  public abstract class SnapshotServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.SnapshotService.SaveSnapshot.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun saveSnapshot(request: SaveSnapshotRequest): SaveSnapshotResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.SnapshotService.SaveSnapshot is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.SnapshotService.LoadSnapshot.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun loadSnapshot(request: LoadSnapshotRequest): LoadSnapshotResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.SnapshotService.LoadSnapshot is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.SnapshotService.ListSnapshots.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun listSnapshots(request: ListSnapshotsRequest): ListSnapshotsResponse =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.SnapshotService.ListSnapshots is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.SnapshotService.DeleteSnapshot.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun deleteSnapshot(request: DeleteSnapshotRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.SnapshotService.DeleteSnapshot is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = SnapshotServiceGrpc.getSaveSnapshotMethod(),
      implementation = ::saveSnapshot
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = SnapshotServiceGrpc.getLoadSnapshotMethod(),
      implementation = ::loadSnapshot
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = SnapshotServiceGrpc.getListSnapshotsMethod(),
      implementation = ::listSnapshots
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = SnapshotServiceGrpc.getDeleteSnapshotMethod(),
      implementation = ::deleteSnapshot
    )).build()
  }
}
