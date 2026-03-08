package ai.rever.boss.ipc.proto

import ai.rever.boss.ipc.proto.OrchestratorServiceGrpc.getServiceDescriptor
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
 * Holder for Kotlin coroutine-based client and server APIs for boss.ipc.v1.OrchestratorService.
 */
public object OrchestratorServiceGrpcKt {
  public const val SERVICE_NAME: String = OrchestratorServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val reportFailureMethod: MethodDescriptor<ProcessFailureReport, RepairAction>
    @JvmStatic
    get() = OrchestratorServiceGrpc.getReportFailureMethod()

  public val getHealthDashboardMethod: MethodDescriptor<Empty, HealthDashboard>
    @JvmStatic
    get() = OrchestratorServiceGrpc.getGetHealthDashboardMethod()

  public val getRepairHistoryMethod: MethodDescriptor<RepairHistoryRequest, RepairHistoryResponse>
    @JvmStatic
    get() = OrchestratorServiceGrpc.getGetRepairHistoryMethod()

  public val approveRepairMethod: MethodDescriptor<RepairApproval, RepairApprovalResponse>
    @JvmStatic
    get() = OrchestratorServiceGrpc.getApproveRepairMethod()

  public val watchHealthMethod: MethodDescriptor<Empty, HealthEvent>
    @JvmStatic
    get() = OrchestratorServiceGrpc.getWatchHealthMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.OrchestratorService service as suspending
   * coroutines.
   */
  @StubFor(OrchestratorServiceGrpc::class)
  public class OrchestratorServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<OrchestratorServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): OrchestratorServiceCoroutineStub
        = OrchestratorServiceCoroutineStub(channel, callOptions)

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
    public suspend fun reportFailure(request: ProcessFailureReport, headers: Metadata = Metadata()):
        RepairAction = unaryRpc(
      channel,
      OrchestratorServiceGrpc.getReportFailureMethod(),
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
    public suspend fun getHealthDashboard(request: Empty, headers: Metadata = Metadata()):
        HealthDashboard = unaryRpc(
      channel,
      OrchestratorServiceGrpc.getGetHealthDashboardMethod(),
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
    public suspend fun getRepairHistory(request: RepairHistoryRequest, headers: Metadata =
        Metadata()): RepairHistoryResponse = unaryRpc(
      channel,
      OrchestratorServiceGrpc.getGetRepairHistoryMethod(),
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
    public suspend fun approveRepair(request: RepairApproval, headers: Metadata = Metadata()):
        RepairApprovalResponse = unaryRpc(
      channel,
      OrchestratorServiceGrpc.getApproveRepairMethod(),
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
    public fun watchHealth(request: Empty, headers: Metadata = Metadata()): Flow<HealthEvent> =
        serverStreamingRpc(
      channel,
      OrchestratorServiceGrpc.getWatchHealthMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.OrchestratorService service based on Kotlin
   * coroutines.
   */
  public abstract class OrchestratorServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.OrchestratorService.ReportFailure.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun reportFailure(request: ProcessFailureReport): RepairAction = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.OrchestratorService.ReportFailure is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.OrchestratorService.GetHealthDashboard.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getHealthDashboard(request: Empty): HealthDashboard = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.OrchestratorService.GetHealthDashboard is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.OrchestratorService.GetRepairHistory.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getRepairHistory(request: RepairHistoryRequest): RepairHistoryResponse =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.OrchestratorService.GetRepairHistory is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.OrchestratorService.ApproveRepair.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun approveRepair(request: RepairApproval): RepairApprovalResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.OrchestratorService.ApproveRepair is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for boss.ipc.v1.OrchestratorService.WatchHealth.
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
    public open fun watchHealth(request: Empty): Flow<HealthEvent> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.OrchestratorService.WatchHealth is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = OrchestratorServiceGrpc.getReportFailureMethod(),
      implementation = ::reportFailure
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = OrchestratorServiceGrpc.getGetHealthDashboardMethod(),
      implementation = ::getHealthDashboard
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = OrchestratorServiceGrpc.getGetRepairHistoryMethod(),
      implementation = ::getRepairHistory
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = OrchestratorServiceGrpc.getApproveRepairMethod(),
      implementation = ::approveRepair
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = OrchestratorServiceGrpc.getWatchHealthMethod(),
      implementation = ::watchHealth
    )).build()
  }
}
