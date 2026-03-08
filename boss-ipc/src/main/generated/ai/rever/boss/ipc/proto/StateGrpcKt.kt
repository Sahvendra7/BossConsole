package ai.rever.boss.ipc.proto

import ai.rever.boss.ipc.proto.StateServiceGrpc.getServiceDescriptor
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
 * Holder for Kotlin coroutine-based client and server APIs for boss.ipc.v1.StateService.
 */
public object StateServiceGrpcKt {
  public const val SERVICE_NAME: String = StateServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val getStateMethod: MethodDescriptor<StateKey, StateValue>
    @JvmStatic
    get() = StateServiceGrpc.getGetStateMethod()

  public val watchStateMethod: MethodDescriptor<StateKey, StateValue>
    @JvmStatic
    get() = StateServiceGrpc.getWatchStateMethod()

  public val setStateMethod: MethodDescriptor<StateUpdate, StateValue>
    @JvmStatic
    get() = StateServiceGrpc.getSetStateMethod()

  public val listStateKeysMethod: MethodDescriptor<Empty, StateKeyList>
    @JvmStatic
    get() = StateServiceGrpc.getListStateKeysMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.StateService service as suspending coroutines.
   */
  @StubFor(StateServiceGrpc::class)
  public class StateServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<StateServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): StateServiceCoroutineStub =
        StateServiceCoroutineStub(channel, callOptions)

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
    public suspend fun getState(request: StateKey, headers: Metadata = Metadata()): StateValue =
        unaryRpc(
      channel,
      StateServiceGrpc.getGetStateMethod(),
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
    public fun watchState(request: StateKey, headers: Metadata = Metadata()): Flow<StateValue> =
        serverStreamingRpc(
      channel,
      StateServiceGrpc.getWatchStateMethod(),
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
    public suspend fun setState(request: StateUpdate, headers: Metadata = Metadata()): StateValue =
        unaryRpc(
      channel,
      StateServiceGrpc.getSetStateMethod(),
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
    public suspend fun listStateKeys(request: Empty, headers: Metadata = Metadata()): StateKeyList =
        unaryRpc(
      channel,
      StateServiceGrpc.getListStateKeysMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.StateService service based on Kotlin coroutines.
   */
  public abstract class StateServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.StateService.GetState.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getState(request: StateKey): StateValue = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.StateService.GetState is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for boss.ipc.v1.StateService.WatchState.
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
    public open fun watchState(request: StateKey): Flow<StateValue> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.StateService.WatchState is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.StateService.SetState.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun setState(request: StateUpdate): StateValue = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.StateService.SetState is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.StateService.ListStateKeys.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun listStateKeys(request: Empty): StateKeyList = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.StateService.ListStateKeys is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = StateServiceGrpc.getGetStateMethod(),
      implementation = ::getState
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = StateServiceGrpc.getWatchStateMethod(),
      implementation = ::watchState
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = StateServiceGrpc.getSetStateMethod(),
      implementation = ::setState
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = StateServiceGrpc.getListStateKeysMethod(),
      implementation = ::listStateKeys
    )).build()
  }
}
