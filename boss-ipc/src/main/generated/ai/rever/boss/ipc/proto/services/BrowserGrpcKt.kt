package ai.rever.boss.ipc.proto.services

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.BrowserServiceGrpc.getServiceDescriptor
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
 * Holder for Kotlin coroutine-based client and server APIs for boss.ipc.v1.services.BrowserService.
 */
public object BrowserServiceGrpcKt {
  public const val SERVICE_NAME: String = BrowserServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val navigateMethod: MethodDescriptor<NavigateBrowserRequest, NavigateBrowserResponse>
    @JvmStatic
    get() = BrowserServiceGrpc.getNavigateMethod()

  public val executeJSMethod: MethodDescriptor<ExecuteJSRequest, ExecuteJSResponse>
    @JvmStatic
    get() = BrowserServiceGrpc.getExecuteJSMethod()

  public val onNavigationEventMethod: MethodDescriptor<Empty, BrowserNavigationEvent>
    @JvmStatic
    get() = BrowserServiceGrpc.getOnNavigationEventMethod()

  public val getFaviconMethod: MethodDescriptor<GetFaviconRequest, GetFaviconResponse>
    @JvmStatic
    get() = BrowserServiceGrpc.getGetFaviconMethod()

  public val getPageInfoMethod: MethodDescriptor<Empty, PageInfoResponse>
    @JvmStatic
    get() = BrowserServiceGrpc.getGetPageInfoMethod()

  public val goBackMethod: MethodDescriptor<Empty, Empty>
    @JvmStatic
    get() = BrowserServiceGrpc.getGoBackMethod()

  public val goForwardMethod: MethodDescriptor<Empty, Empty>
    @JvmStatic
    get() = BrowserServiceGrpc.getGoForwardMethod()

  public val reloadMethod: MethodDescriptor<Empty, Empty>
    @JvmStatic
    get() = BrowserServiceGrpc.getReloadMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.services.BrowserService service as suspending
   * coroutines.
   */
  @StubFor(BrowserServiceGrpc::class)
  public class BrowserServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<BrowserServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): BrowserServiceCoroutineStub =
        BrowserServiceCoroutineStub(channel, callOptions)

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
    public suspend fun navigate(request: NavigateBrowserRequest, headers: Metadata = Metadata()):
        NavigateBrowserResponse = unaryRpc(
      channel,
      BrowserServiceGrpc.getNavigateMethod(),
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
    public suspend fun executeJS(request: ExecuteJSRequest, headers: Metadata = Metadata()):
        ExecuteJSResponse = unaryRpc(
      channel,
      BrowserServiceGrpc.getExecuteJSMethod(),
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
    public fun onNavigationEvent(request: Empty, headers: Metadata = Metadata()):
        Flow<BrowserNavigationEvent> = serverStreamingRpc(
      channel,
      BrowserServiceGrpc.getOnNavigationEventMethod(),
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
    public suspend fun getFavicon(request: GetFaviconRequest, headers: Metadata = Metadata()):
        GetFaviconResponse = unaryRpc(
      channel,
      BrowserServiceGrpc.getGetFaviconMethod(),
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
    public suspend fun getPageInfo(request: Empty, headers: Metadata = Metadata()): PageInfoResponse
        = unaryRpc(
      channel,
      BrowserServiceGrpc.getGetPageInfoMethod(),
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
    public suspend fun goBack(request: Empty, headers: Metadata = Metadata()): Empty = unaryRpc(
      channel,
      BrowserServiceGrpc.getGoBackMethod(),
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
    public suspend fun goForward(request: Empty, headers: Metadata = Metadata()): Empty = unaryRpc(
      channel,
      BrowserServiceGrpc.getGoForwardMethod(),
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
    public suspend fun reload(request: Empty, headers: Metadata = Metadata()): Empty = unaryRpc(
      channel,
      BrowserServiceGrpc.getReloadMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.services.BrowserService service based on Kotlin
   * coroutines.
   */
  public abstract class BrowserServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.services.BrowserService.Navigate.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun navigate(request: NavigateBrowserRequest): NavigateBrowserResponse =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.BrowserService.Navigate is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.BrowserService.ExecuteJS.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun executeJS(request: ExecuteJSRequest): ExecuteJSResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.BrowserService.ExecuteJS is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * boss.ipc.v1.services.BrowserService.OnNavigationEvent.
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
    public open fun onNavigationEvent(request: Empty): Flow<BrowserNavigationEvent> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.BrowserService.OnNavigationEvent is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.BrowserService.GetFavicon.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getFavicon(request: GetFaviconRequest): GetFaviconResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.BrowserService.GetFavicon is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.BrowserService.GetPageInfo.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getPageInfo(request: Empty): PageInfoResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.BrowserService.GetPageInfo is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.BrowserService.GoBack.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun goBack(request: Empty): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.BrowserService.GoBack is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.BrowserService.GoForward.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun goForward(request: Empty): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.BrowserService.GoForward is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.BrowserService.Reload.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun reload(request: Empty): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.BrowserService.Reload is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = BrowserServiceGrpc.getNavigateMethod(),
      implementation = ::navigate
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = BrowserServiceGrpc.getExecuteJSMethod(),
      implementation = ::executeJS
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = BrowserServiceGrpc.getOnNavigationEventMethod(),
      implementation = ::onNavigationEvent
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = BrowserServiceGrpc.getGetFaviconMethod(),
      implementation = ::getFavicon
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = BrowserServiceGrpc.getGetPageInfoMethod(),
      implementation = ::getPageInfo
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = BrowserServiceGrpc.getGoBackMethod(),
      implementation = ::goBack
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = BrowserServiceGrpc.getGoForwardMethod(),
      implementation = ::goForward
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = BrowserServiceGrpc.getReloadMethod(),
      implementation = ::reload
    )).build()
  }
}
