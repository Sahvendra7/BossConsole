package ai.rever.boss.ipc.proto.services

import ai.rever.boss.ipc.proto.services.SettingsServiceGrpc.getServiceDescriptor
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
 * boss.ipc.v1.services.SettingsService.
 */
public object SettingsServiceGrpcKt {
  public const val SERVICE_NAME: String = SettingsServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val getSettingMethod: MethodDescriptor<GetSettingRequest, SettingValue>
    @JvmStatic
    get() = SettingsServiceGrpc.getGetSettingMethod()

  public val setSettingMethod: MethodDescriptor<SetSettingRequest, SettingValue>
    @JvmStatic
    get() = SettingsServiceGrpc.getSetSettingMethod()

  public val watchSettingMethod: MethodDescriptor<GetSettingRequest, SettingValue>
    @JvmStatic
    get() = SettingsServiceGrpc.getWatchSettingMethod()

  public val listSettingsMethod: MethodDescriptor<ListSettingsRequest, SettingsListResponse>
    @JvmStatic
    get() = SettingsServiceGrpc.getListSettingsMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.services.SettingsService service as suspending
   * coroutines.
   */
  @StubFor(SettingsServiceGrpc::class)
  public class SettingsServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<SettingsServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): SettingsServiceCoroutineStub =
        SettingsServiceCoroutineStub(channel, callOptions)

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
    public suspend fun getSetting(request: GetSettingRequest, headers: Metadata = Metadata()):
        SettingValue = unaryRpc(
      channel,
      SettingsServiceGrpc.getGetSettingMethod(),
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
    public suspend fun setSetting(request: SetSettingRequest, headers: Metadata = Metadata()):
        SettingValue = unaryRpc(
      channel,
      SettingsServiceGrpc.getSetSettingMethod(),
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
    public fun watchSetting(request: GetSettingRequest, headers: Metadata = Metadata()):
        Flow<SettingValue> = serverStreamingRpc(
      channel,
      SettingsServiceGrpc.getWatchSettingMethod(),
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
    public suspend fun listSettings(request: ListSettingsRequest, headers: Metadata = Metadata()):
        SettingsListResponse = unaryRpc(
      channel,
      SettingsServiceGrpc.getListSettingsMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.services.SettingsService service based on Kotlin
   * coroutines.
   */
  public abstract class SettingsServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.services.SettingsService.GetSetting.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getSetting(request: GetSettingRequest): SettingValue = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.SettingsService.GetSetting is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.SettingsService.SetSetting.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun setSetting(request: SetSettingRequest): SettingValue = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.SettingsService.SetSetting is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * boss.ipc.v1.services.SettingsService.WatchSetting.
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
    public open fun watchSetting(request: GetSettingRequest): Flow<SettingValue> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.SettingsService.WatchSetting is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.SettingsService.ListSettings.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun listSettings(request: ListSettingsRequest): SettingsListResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.SettingsService.ListSettings is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = SettingsServiceGrpc.getGetSettingMethod(),
      implementation = ::getSetting
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = SettingsServiceGrpc.getSetSettingMethod(),
      implementation = ::setSetting
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = SettingsServiceGrpc.getWatchSettingMethod(),
      implementation = ::watchSetting
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = SettingsServiceGrpc.getListSettingsMethod(),
      implementation = ::listSettings
    )).build()
  }
}
