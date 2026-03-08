package ai.rever.boss.ipc.proto

import ai.rever.boss.ipc.proto.CapabilityServiceGrpc.getServiceDescriptor
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
 * Holder for Kotlin coroutine-based client and server APIs for boss.ipc.v1.CapabilityService.
 */
public object CapabilityServiceGrpcKt {
  public const val SERVICE_NAME: String = CapabilityServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val invokeCapabilityMethod:
      MethodDescriptor<InvokeCapabilityRequest, InvokeCapabilityResponse>
    @JvmStatic
    get() = CapabilityServiceGrpc.getInvokeCapabilityMethod()

  public val listCapabilitiesMethod: MethodDescriptor<Empty, ListCapabilitiesResponse>
    @JvmStatic
    get() = CapabilityServiceGrpc.getListCapabilitiesMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.CapabilityService service as suspending coroutines.
   */
  @StubFor(CapabilityServiceGrpc::class)
  public class CapabilityServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<CapabilityServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): CapabilityServiceCoroutineStub =
        CapabilityServiceCoroutineStub(channel, callOptions)

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
    public suspend fun invokeCapability(request: InvokeCapabilityRequest, headers: Metadata =
        Metadata()): InvokeCapabilityResponse = unaryRpc(
      channel,
      CapabilityServiceGrpc.getInvokeCapabilityMethod(),
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
    public suspend fun listCapabilities(request: Empty, headers: Metadata = Metadata()):
        ListCapabilitiesResponse = unaryRpc(
      channel,
      CapabilityServiceGrpc.getListCapabilitiesMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.CapabilityService service based on Kotlin
   * coroutines.
   */
  public abstract class CapabilityServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.CapabilityService.InvokeCapability.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun invokeCapability(request: InvokeCapabilityRequest):
        InvokeCapabilityResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.CapabilityService.InvokeCapability is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.CapabilityService.ListCapabilities.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun listCapabilities(request: Empty): ListCapabilitiesResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.CapabilityService.ListCapabilities is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = CapabilityServiceGrpc.getInvokeCapabilityMethod(),
      implementation = ::invokeCapability
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = CapabilityServiceGrpc.getListCapabilitiesMethod(),
      implementation = ::listCapabilities
    )).build()
  }
}
