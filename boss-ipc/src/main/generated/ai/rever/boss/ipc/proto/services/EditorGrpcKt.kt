package ai.rever.boss.ipc.proto.services

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.EditorServiceGrpc.getServiceDescriptor
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
 * Holder for Kotlin coroutine-based client and server APIs for boss.ipc.v1.services.EditorService.
 */
public object EditorServiceGrpcKt {
  public const val SERVICE_NAME: String = EditorServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val openFileMethod: MethodDescriptor<OpenFileRequest, OpenFileResponse>
    @JvmStatic
    get() = EditorServiceGrpc.getOpenFileMethod()

  public val saveFileMethod: MethodDescriptor<SaveFileRequest, Empty>
    @JvmStatic
    get() = EditorServiceGrpc.getSaveFileMethod()

  public val getTokensMethod: MethodDescriptor<GetTokensRequest, GetTokensResponse>
    @JvmStatic
    get() = EditorServiceGrpc.getGetTokensMethod()

  public val navigateToDefinitionMethod: MethodDescriptor<NavigateRequest, NavigateResponse>
    @JvmStatic
    get() = EditorServiceGrpc.getNavigateToDefinitionMethod()

  public val detectMainFunctionsMethod: MethodDescriptor<DetectMainRequest, DetectMainResponse>
    @JvmStatic
    get() = EditorServiceGrpc.getDetectMainFunctionsMethod()

  public val listOpenFilesMethod: MethodDescriptor<Empty, ListOpenFilesResponse>
    @JvmStatic
    get() = EditorServiceGrpc.getListOpenFilesMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.services.EditorService service as suspending
   * coroutines.
   */
  @StubFor(EditorServiceGrpc::class)
  public class EditorServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<EditorServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): EditorServiceCoroutineStub =
        EditorServiceCoroutineStub(channel, callOptions)

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
    public suspend fun openFile(request: OpenFileRequest, headers: Metadata = Metadata()):
        OpenFileResponse = unaryRpc(
      channel,
      EditorServiceGrpc.getOpenFileMethod(),
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
    public suspend fun saveFile(request: SaveFileRequest, headers: Metadata = Metadata()): Empty =
        unaryRpc(
      channel,
      EditorServiceGrpc.getSaveFileMethod(),
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
    public suspend fun getTokens(request: GetTokensRequest, headers: Metadata = Metadata()):
        GetTokensResponse = unaryRpc(
      channel,
      EditorServiceGrpc.getGetTokensMethod(),
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
    public suspend fun navigateToDefinition(request: NavigateRequest, headers: Metadata =
        Metadata()): NavigateResponse = unaryRpc(
      channel,
      EditorServiceGrpc.getNavigateToDefinitionMethod(),
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
    public suspend fun detectMainFunctions(request: DetectMainRequest, headers: Metadata =
        Metadata()): DetectMainResponse = unaryRpc(
      channel,
      EditorServiceGrpc.getDetectMainFunctionsMethod(),
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
    public suspend fun listOpenFiles(request: Empty, headers: Metadata = Metadata()):
        ListOpenFilesResponse = unaryRpc(
      channel,
      EditorServiceGrpc.getListOpenFilesMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.services.EditorService service based on Kotlin
   * coroutines.
   */
  public abstract class EditorServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.services.EditorService.OpenFile.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun openFile(request: OpenFileRequest): OpenFileResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.EditorService.OpenFile is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.EditorService.SaveFile.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun saveFile(request: SaveFileRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.EditorService.SaveFile is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.EditorService.GetTokens.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getTokens(request: GetTokensRequest): GetTokensResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.EditorService.GetTokens is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.EditorService.NavigateToDefinition.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun navigateToDefinition(request: NavigateRequest): NavigateResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.EditorService.NavigateToDefinition is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.EditorService.DetectMainFunctions.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun detectMainFunctions(request: DetectMainRequest): DetectMainResponse =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.EditorService.DetectMainFunctions is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.EditorService.ListOpenFiles.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun listOpenFiles(request: Empty): ListOpenFilesResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.EditorService.ListOpenFiles is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = EditorServiceGrpc.getOpenFileMethod(),
      implementation = ::openFile
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = EditorServiceGrpc.getSaveFileMethod(),
      implementation = ::saveFile
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = EditorServiceGrpc.getGetTokensMethod(),
      implementation = ::getTokens
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = EditorServiceGrpc.getNavigateToDefinitionMethod(),
      implementation = ::navigateToDefinition
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = EditorServiceGrpc.getDetectMainFunctionsMethod(),
      implementation = ::detectMainFunctions
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = EditorServiceGrpc.getListOpenFilesMethod(),
      implementation = ::listOpenFiles
    )).build()
  }
}
