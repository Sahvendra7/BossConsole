package ai.rever.boss.ipc.proto

import ai.rever.boss.ipc.proto.EventBusServiceGrpc.getServiceDescriptor
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
 * Holder for Kotlin coroutine-based client and server APIs for boss.ipc.v1.EventBusService.
 */
public object EventBusServiceGrpcKt {
  public const val SERVICE_NAME: String = EventBusServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val subscribeMethod: MethodDescriptor<SubscribeRequest, EventEnvelope>
    @JvmStatic
    get() = EventBusServiceGrpc.getSubscribeMethod()

  public val publishMethod: MethodDescriptor<EventEnvelope, PublishResponse>
    @JvmStatic
    get() = EventBusServiceGrpc.getPublishMethod()

  public val publishBatchMethod: MethodDescriptor<PublishBatchRequest, PublishResponse>
    @JvmStatic
    get() = EventBusServiceGrpc.getPublishBatchMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.EventBusService service as suspending coroutines.
   */
  @StubFor(EventBusServiceGrpc::class)
  public class EventBusServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<EventBusServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): EventBusServiceCoroutineStub =
        EventBusServiceCoroutineStub(channel, callOptions)

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
    public fun subscribe(request: SubscribeRequest, headers: Metadata = Metadata()):
        Flow<EventEnvelope> = serverStreamingRpc(
      channel,
      EventBusServiceGrpc.getSubscribeMethod(),
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
    public suspend fun publish(request: EventEnvelope, headers: Metadata = Metadata()):
        PublishResponse = unaryRpc(
      channel,
      EventBusServiceGrpc.getPublishMethod(),
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
    public suspend fun publishBatch(request: PublishBatchRequest, headers: Metadata = Metadata()):
        PublishResponse = unaryRpc(
      channel,
      EventBusServiceGrpc.getPublishBatchMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.EventBusService service based on Kotlin coroutines.
   */
  public abstract class EventBusServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns a [Flow] of responses to an RPC for boss.ipc.v1.EventBusService.Subscribe.
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
    public open fun subscribe(request: SubscribeRequest): Flow<EventEnvelope> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.EventBusService.Subscribe is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.EventBusService.Publish.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun publish(request: EventEnvelope): PublishResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.EventBusService.Publish is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.EventBusService.PublishBatch.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun publishBatch(request: PublishBatchRequest): PublishResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.EventBusService.PublishBatch is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = EventBusServiceGrpc.getSubscribeMethod(),
      implementation = ::subscribe
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = EventBusServiceGrpc.getPublishMethod(),
      implementation = ::publish
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = EventBusServiceGrpc.getPublishBatchMethod(),
      implementation = ::publishBatch
    )).build()
  }
}
