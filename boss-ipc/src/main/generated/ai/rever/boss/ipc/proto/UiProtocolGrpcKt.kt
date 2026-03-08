package ai.rever.boss.ipc.proto

import ai.rever.boss.ipc.proto.PluginUIServiceGrpc.getServiceDescriptor
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
 * Holder for Kotlin coroutine-based client and server APIs for boss.ipc.v1.PluginUIService.
 */
public object PluginUIServiceGrpcKt {
  public const val SERVICE_NAME: String = PluginUIServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val registerUIMethod: MethodDescriptor<UIRegistration, UIRegistrationResponse>
    @JvmStatic
    get() = PluginUIServiceGrpc.getRegisterUIMethod()

  public val streamUIMethod: MethodDescriptor<WidgetUpdate, UIEvent>
    @JvmStatic
    get() = PluginUIServiceGrpc.getStreamUIMethod()

  public val unregisterUIMethod: MethodDescriptor<UIUnregistration, Empty>
    @JvmStatic
    get() = PluginUIServiceGrpc.getUnregisterUIMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.PluginUIService service as suspending coroutines.
   */
  @StubFor(PluginUIServiceGrpc::class)
  public class PluginUIServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<PluginUIServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): PluginUIServiceCoroutineStub =
        PluginUIServiceCoroutineStub(channel, callOptions)

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
    public suspend fun registerUI(request: UIRegistration, headers: Metadata = Metadata()):
        UIRegistrationResponse = unaryRpc(
      channel,
      PluginUIServiceGrpc.getRegisterUIMethod(),
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
    public fun streamUI(requests: Flow<WidgetUpdate>, headers: Metadata = Metadata()): Flow<UIEvent>
        = bidiStreamingRpc(
      channel,
      PluginUIServiceGrpc.getStreamUIMethod(),
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
    public suspend fun unregisterUI(request: UIUnregistration, headers: Metadata = Metadata()):
        Empty = unaryRpc(
      channel,
      PluginUIServiceGrpc.getUnregisterUIMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.PluginUIService service based on Kotlin coroutines.
   */
  public abstract class PluginUIServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.PluginUIService.RegisterUI.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun registerUI(request: UIRegistration): UIRegistrationResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.PluginUIService.RegisterUI is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for boss.ipc.v1.PluginUIService.StreamUI.
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
    public open fun streamUI(requests: Flow<WidgetUpdate>): Flow<UIEvent> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.PluginUIService.StreamUI is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.PluginUIService.UnregisterUI.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun unregisterUI(request: UIUnregistration): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.PluginUIService.UnregisterUI is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = PluginUIServiceGrpc.getRegisterUIMethod(),
      implementation = ::registerUI
    ))
      .addMethod(bidiStreamingServerMethodDefinition(
      context = this.context,
      descriptor = PluginUIServiceGrpc.getStreamUIMethod(),
      implementation = ::streamUI
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = PluginUIServiceGrpc.getUnregisterUIMethod(),
      implementation = ::unregisterUI
    )).build()
  }
}
