package ai.rever.boss.ipc.proto.services

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.WorkspaceServiceGrpc.getServiceDescriptor
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
 * boss.ipc.v1.services.WorkspaceService.
 */
public object WorkspaceServiceGrpcKt {
  public const val SERVICE_NAME: String = WorkspaceServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val getWorkspacesMethod: MethodDescriptor<Empty, WorkspacesResponse>
    @JvmStatic
    get() = WorkspaceServiceGrpc.getGetWorkspacesMethod()

  public val watchWorkspacesMethod: MethodDescriptor<Empty, WorkspacesResponse>
    @JvmStatic
    get() = WorkspaceServiceGrpc.getWatchWorkspacesMethod()

  public val getCurrentWorkspaceMethod: MethodDescriptor<Empty, WorkspaceResponse>
    @JvmStatic
    get() = WorkspaceServiceGrpc.getGetCurrentWorkspaceMethod()

  public val watchCurrentWorkspaceMethod: MethodDescriptor<Empty, WorkspaceResponse>
    @JvmStatic
    get() = WorkspaceServiceGrpc.getWatchCurrentWorkspaceMethod()

  public val loadWorkspaceMethod: MethodDescriptor<LoadWorkspaceRequest, WorkspaceResponse>
    @JvmStatic
    get() = WorkspaceServiceGrpc.getLoadWorkspaceMethod()

  public val saveWorkspaceMethod: MethodDescriptor<SaveWorkspaceRequest, WorkspaceResponse>
    @JvmStatic
    get() = WorkspaceServiceGrpc.getSaveWorkspaceMethod()

  public val deleteWorkspaceMethod: MethodDescriptor<DeleteWorkspaceRequest, Empty>
    @JvmStatic
    get() = WorkspaceServiceGrpc.getDeleteWorkspaceMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.services.WorkspaceService service as suspending
   * coroutines.
   */
  @StubFor(WorkspaceServiceGrpc::class)
  public class WorkspaceServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<WorkspaceServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): WorkspaceServiceCoroutineStub =
        WorkspaceServiceCoroutineStub(channel, callOptions)

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
    public suspend fun getWorkspaces(request: Empty, headers: Metadata = Metadata()):
        WorkspacesResponse = unaryRpc(
      channel,
      WorkspaceServiceGrpc.getGetWorkspacesMethod(),
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
    public fun watchWorkspaces(request: Empty, headers: Metadata = Metadata()):
        Flow<WorkspacesResponse> = serverStreamingRpc(
      channel,
      WorkspaceServiceGrpc.getWatchWorkspacesMethod(),
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
    public suspend fun getCurrentWorkspace(request: Empty, headers: Metadata = Metadata()):
        WorkspaceResponse = unaryRpc(
      channel,
      WorkspaceServiceGrpc.getGetCurrentWorkspaceMethod(),
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
    public fun watchCurrentWorkspace(request: Empty, headers: Metadata = Metadata()):
        Flow<WorkspaceResponse> = serverStreamingRpc(
      channel,
      WorkspaceServiceGrpc.getWatchCurrentWorkspaceMethod(),
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
    public suspend fun loadWorkspace(request: LoadWorkspaceRequest, headers: Metadata = Metadata()):
        WorkspaceResponse = unaryRpc(
      channel,
      WorkspaceServiceGrpc.getLoadWorkspaceMethod(),
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
    public suspend fun saveWorkspace(request: SaveWorkspaceRequest, headers: Metadata = Metadata()):
        WorkspaceResponse = unaryRpc(
      channel,
      WorkspaceServiceGrpc.getSaveWorkspaceMethod(),
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
    public suspend fun deleteWorkspace(request: DeleteWorkspaceRequest, headers: Metadata =
        Metadata()): Empty = unaryRpc(
      channel,
      WorkspaceServiceGrpc.getDeleteWorkspaceMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.services.WorkspaceService service based on Kotlin
   * coroutines.
   */
  public abstract class WorkspaceServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.services.WorkspaceService.GetWorkspaces.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getWorkspaces(request: Empty): WorkspacesResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.WorkspaceService.GetWorkspaces is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * boss.ipc.v1.services.WorkspaceService.WatchWorkspaces.
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
    public open fun watchWorkspaces(request: Empty): Flow<WorkspacesResponse> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.WorkspaceService.WatchWorkspaces is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.WorkspaceService.GetCurrentWorkspace.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getCurrentWorkspace(request: Empty): WorkspaceResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.WorkspaceService.GetCurrentWorkspace is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * boss.ipc.v1.services.WorkspaceService.WatchCurrentWorkspace.
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
    public open fun watchCurrentWorkspace(request: Empty): Flow<WorkspaceResponse> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.WorkspaceService.WatchCurrentWorkspace is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.WorkspaceService.LoadWorkspace.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun loadWorkspace(request: LoadWorkspaceRequest): WorkspaceResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.WorkspaceService.LoadWorkspace is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.WorkspaceService.SaveWorkspace.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun saveWorkspace(request: SaveWorkspaceRequest): WorkspaceResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.WorkspaceService.SaveWorkspace is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.WorkspaceService.DeleteWorkspace.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun deleteWorkspace(request: DeleteWorkspaceRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.WorkspaceService.DeleteWorkspace is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = WorkspaceServiceGrpc.getGetWorkspacesMethod(),
      implementation = ::getWorkspaces
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = WorkspaceServiceGrpc.getWatchWorkspacesMethod(),
      implementation = ::watchWorkspaces
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = WorkspaceServiceGrpc.getGetCurrentWorkspaceMethod(),
      implementation = ::getCurrentWorkspace
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = WorkspaceServiceGrpc.getWatchCurrentWorkspaceMethod(),
      implementation = ::watchCurrentWorkspace
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = WorkspaceServiceGrpc.getLoadWorkspaceMethod(),
      implementation = ::loadWorkspace
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = WorkspaceServiceGrpc.getSaveWorkspaceMethod(),
      implementation = ::saveWorkspace
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = WorkspaceServiceGrpc.getDeleteWorkspaceMethod(),
      implementation = ::deleteWorkspace
    )).build()
  }
}
