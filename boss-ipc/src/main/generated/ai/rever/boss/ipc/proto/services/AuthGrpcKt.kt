package ai.rever.boss.ipc.proto.services

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.AuthServiceGrpc.getServiceDescriptor
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
 * Holder for Kotlin coroutine-based client and server APIs for boss.ipc.v1.services.AuthService.
 */
public object AuthServiceGrpcKt {
  public const val SERVICE_NAME: String = AuthServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val getAuthStateMethod: MethodDescriptor<Empty, AuthStateResponse>
    @JvmStatic
    get() = AuthServiceGrpc.getGetAuthStateMethod()

  public val watchAuthStateMethod: MethodDescriptor<Empty, AuthStateResponse>
    @JvmStatic
    get() = AuthServiceGrpc.getWatchAuthStateMethod()

  public val signInMethod: MethodDescriptor<SignInRequest, SignInResponse>
    @JvmStatic
    get() = AuthServiceGrpc.getSignInMethod()

  public val signOutMethod: MethodDescriptor<Empty, SignOutResponse>
    @JvmStatic
    get() = AuthServiceGrpc.getSignOutMethod()

  public val getCurrentUserMethod: MethodDescriptor<Empty, UserInfoResponse>
    @JvmStatic
    get() = AuthServiceGrpc.getGetCurrentUserMethod()

  public val watchCurrentUserMethod: MethodDescriptor<Empty, UserInfoResponse>
    @JvmStatic
    get() = AuthServiceGrpc.getWatchCurrentUserMethod()

  public val hasPermissionMethod: MethodDescriptor<PermissionRequest, PermissionResponse>
    @JvmStatic
    get() = AuthServiceGrpc.getHasPermissionMethod()

  public val hasAnyPermissionMethod: MethodDescriptor<HasAnyPermissionRequest, PermissionResponse>
    @JvmStatic
    get() = AuthServiceGrpc.getHasAnyPermissionMethod()

  public val getUserPermissionsMethod: MethodDescriptor<Empty, UserPermissionsResponse>
    @JvmStatic
    get() = AuthServiceGrpc.getGetUserPermissionsMethod()

  public val isAdminMethod: MethodDescriptor<Empty, IsAdminResponse>
    @JvmStatic
    get() = AuthServiceGrpc.getIsAdminMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.services.AuthService service as suspending
   * coroutines.
   */
  @StubFor(AuthServiceGrpc::class)
  public class AuthServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<AuthServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): AuthServiceCoroutineStub =
        AuthServiceCoroutineStub(channel, callOptions)

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
    public suspend fun getAuthState(request: Empty, headers: Metadata = Metadata()):
        AuthStateResponse = unaryRpc(
      channel,
      AuthServiceGrpc.getGetAuthStateMethod(),
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
    public fun watchAuthState(request: Empty, headers: Metadata = Metadata()):
        Flow<AuthStateResponse> = serverStreamingRpc(
      channel,
      AuthServiceGrpc.getWatchAuthStateMethod(),
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
    public suspend fun signIn(request: SignInRequest, headers: Metadata = Metadata()):
        SignInResponse = unaryRpc(
      channel,
      AuthServiceGrpc.getSignInMethod(),
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
    public suspend fun signOut(request: Empty, headers: Metadata = Metadata()): SignOutResponse =
        unaryRpc(
      channel,
      AuthServiceGrpc.getSignOutMethod(),
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
    public suspend fun getCurrentUser(request: Empty, headers: Metadata = Metadata()):
        UserInfoResponse = unaryRpc(
      channel,
      AuthServiceGrpc.getGetCurrentUserMethod(),
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
    public fun watchCurrentUser(request: Empty, headers: Metadata = Metadata()):
        Flow<UserInfoResponse> = serverStreamingRpc(
      channel,
      AuthServiceGrpc.getWatchCurrentUserMethod(),
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
    public suspend fun hasPermission(request: PermissionRequest, headers: Metadata = Metadata()):
        PermissionResponse = unaryRpc(
      channel,
      AuthServiceGrpc.getHasPermissionMethod(),
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
    public suspend fun hasAnyPermission(request: HasAnyPermissionRequest, headers: Metadata =
        Metadata()): PermissionResponse = unaryRpc(
      channel,
      AuthServiceGrpc.getHasAnyPermissionMethod(),
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
    public suspend fun getUserPermissions(request: Empty, headers: Metadata = Metadata()):
        UserPermissionsResponse = unaryRpc(
      channel,
      AuthServiceGrpc.getGetUserPermissionsMethod(),
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
    public suspend fun isAdmin(request: Empty, headers: Metadata = Metadata()): IsAdminResponse =
        unaryRpc(
      channel,
      AuthServiceGrpc.getIsAdminMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.services.AuthService service based on Kotlin
   * coroutines.
   */
  public abstract class AuthServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.services.AuthService.GetAuthState.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getAuthState(request: Empty): AuthStateResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.AuthService.GetAuthState is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for boss.ipc.v1.services.AuthService.WatchAuthState.
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
    public open fun watchAuthState(request: Empty): Flow<AuthStateResponse> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.AuthService.WatchAuthState is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.AuthService.SignIn.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun signIn(request: SignInRequest): SignInResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.AuthService.SignIn is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.AuthService.SignOut.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun signOut(request: Empty): SignOutResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.AuthService.SignOut is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.AuthService.GetCurrentUser.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getCurrentUser(request: Empty): UserInfoResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.AuthService.GetCurrentUser is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * boss.ipc.v1.services.AuthService.WatchCurrentUser.
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
    public open fun watchCurrentUser(request: Empty): Flow<UserInfoResponse> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.AuthService.WatchCurrentUser is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.AuthService.HasPermission.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun hasPermission(request: PermissionRequest): PermissionResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.AuthService.HasPermission is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.AuthService.HasAnyPermission.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun hasAnyPermission(request: HasAnyPermissionRequest): PermissionResponse =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.AuthService.HasAnyPermission is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.AuthService.GetUserPermissions.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getUserPermissions(request: Empty): UserPermissionsResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.AuthService.GetUserPermissions is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.AuthService.IsAdmin.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun isAdmin(request: Empty): IsAdminResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.AuthService.IsAdmin is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = AuthServiceGrpc.getGetAuthStateMethod(),
      implementation = ::getAuthState
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = AuthServiceGrpc.getWatchAuthStateMethod(),
      implementation = ::watchAuthState
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = AuthServiceGrpc.getSignInMethod(),
      implementation = ::signIn
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = AuthServiceGrpc.getSignOutMethod(),
      implementation = ::signOut
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = AuthServiceGrpc.getGetCurrentUserMethod(),
      implementation = ::getCurrentUser
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = AuthServiceGrpc.getWatchCurrentUserMethod(),
      implementation = ::watchCurrentUser
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = AuthServiceGrpc.getHasPermissionMethod(),
      implementation = ::hasPermission
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = AuthServiceGrpc.getHasAnyPermissionMethod(),
      implementation = ::hasAnyPermission
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = AuthServiceGrpc.getGetUserPermissionsMethod(),
      implementation = ::getUserPermissions
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = AuthServiceGrpc.getIsAdminMethod(),
      implementation = ::isAdmin
    )).build()
  }
}
