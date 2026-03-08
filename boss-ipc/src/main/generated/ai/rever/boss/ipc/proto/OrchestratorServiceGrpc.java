package ai.rever.boss.ipc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * OrchestratorService is the AI-powered self-healing supervisor.
 * It receives failure reports, diagnoses root causes, and executes repair strategies.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/orchestrator.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class OrchestratorServiceGrpc {

  private OrchestratorServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.OrchestratorService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ProcessFailureReport,
      ai.rever.boss.ipc.proto.RepairAction> getReportFailureMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReportFailure",
      requestType = ai.rever.boss.ipc.proto.ProcessFailureReport.class,
      responseType = ai.rever.boss.ipc.proto.RepairAction.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ProcessFailureReport,
      ai.rever.boss.ipc.proto.RepairAction> getReportFailureMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ProcessFailureReport, ai.rever.boss.ipc.proto.RepairAction> getReportFailureMethod;
    if ((getReportFailureMethod = OrchestratorServiceGrpc.getReportFailureMethod) == null) {
      synchronized (OrchestratorServiceGrpc.class) {
        if ((getReportFailureMethod = OrchestratorServiceGrpc.getReportFailureMethod) == null) {
          OrchestratorServiceGrpc.getReportFailureMethod = getReportFailureMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.ProcessFailureReport, ai.rever.boss.ipc.proto.RepairAction>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReportFailure"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ProcessFailureReport.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.RepairAction.getDefaultInstance()))
              .setSchemaDescriptor(new OrchestratorServiceMethodDescriptorSupplier("ReportFailure"))
              .build();
        }
      }
    }
    return getReportFailureMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.HealthDashboard> getGetHealthDashboardMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetHealthDashboard",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.HealthDashboard.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.HealthDashboard> getGetHealthDashboardMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.HealthDashboard> getGetHealthDashboardMethod;
    if ((getGetHealthDashboardMethod = OrchestratorServiceGrpc.getGetHealthDashboardMethod) == null) {
      synchronized (OrchestratorServiceGrpc.class) {
        if ((getGetHealthDashboardMethod = OrchestratorServiceGrpc.getGetHealthDashboardMethod) == null) {
          OrchestratorServiceGrpc.getGetHealthDashboardMethod = getGetHealthDashboardMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.HealthDashboard>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetHealthDashboard"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.HealthDashboard.getDefaultInstance()))
              .setSchemaDescriptor(new OrchestratorServiceMethodDescriptorSupplier("GetHealthDashboard"))
              .build();
        }
      }
    }
    return getGetHealthDashboardMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.RepairHistoryRequest,
      ai.rever.boss.ipc.proto.RepairHistoryResponse> getGetRepairHistoryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetRepairHistory",
      requestType = ai.rever.boss.ipc.proto.RepairHistoryRequest.class,
      responseType = ai.rever.boss.ipc.proto.RepairHistoryResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.RepairHistoryRequest,
      ai.rever.boss.ipc.proto.RepairHistoryResponse> getGetRepairHistoryMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.RepairHistoryRequest, ai.rever.boss.ipc.proto.RepairHistoryResponse> getGetRepairHistoryMethod;
    if ((getGetRepairHistoryMethod = OrchestratorServiceGrpc.getGetRepairHistoryMethod) == null) {
      synchronized (OrchestratorServiceGrpc.class) {
        if ((getGetRepairHistoryMethod = OrchestratorServiceGrpc.getGetRepairHistoryMethod) == null) {
          OrchestratorServiceGrpc.getGetRepairHistoryMethod = getGetRepairHistoryMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.RepairHistoryRequest, ai.rever.boss.ipc.proto.RepairHistoryResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetRepairHistory"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.RepairHistoryRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.RepairHistoryResponse.getDefaultInstance()))
              .setSchemaDescriptor(new OrchestratorServiceMethodDescriptorSupplier("GetRepairHistory"))
              .build();
        }
      }
    }
    return getGetRepairHistoryMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.RepairApproval,
      ai.rever.boss.ipc.proto.RepairApprovalResponse> getApproveRepairMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ApproveRepair",
      requestType = ai.rever.boss.ipc.proto.RepairApproval.class,
      responseType = ai.rever.boss.ipc.proto.RepairApprovalResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.RepairApproval,
      ai.rever.boss.ipc.proto.RepairApprovalResponse> getApproveRepairMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.RepairApproval, ai.rever.boss.ipc.proto.RepairApprovalResponse> getApproveRepairMethod;
    if ((getApproveRepairMethod = OrchestratorServiceGrpc.getApproveRepairMethod) == null) {
      synchronized (OrchestratorServiceGrpc.class) {
        if ((getApproveRepairMethod = OrchestratorServiceGrpc.getApproveRepairMethod) == null) {
          OrchestratorServiceGrpc.getApproveRepairMethod = getApproveRepairMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.RepairApproval, ai.rever.boss.ipc.proto.RepairApprovalResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ApproveRepair"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.RepairApproval.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.RepairApprovalResponse.getDefaultInstance()))
              .setSchemaDescriptor(new OrchestratorServiceMethodDescriptorSupplier("ApproveRepair"))
              .build();
        }
      }
    }
    return getApproveRepairMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.HealthEvent> getWatchHealthMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WatchHealth",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.HealthEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.HealthEvent> getWatchHealthMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.HealthEvent> getWatchHealthMethod;
    if ((getWatchHealthMethod = OrchestratorServiceGrpc.getWatchHealthMethod) == null) {
      synchronized (OrchestratorServiceGrpc.class) {
        if ((getWatchHealthMethod = OrchestratorServiceGrpc.getWatchHealthMethod) == null) {
          OrchestratorServiceGrpc.getWatchHealthMethod = getWatchHealthMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.HealthEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WatchHealth"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.HealthEvent.getDefaultInstance()))
              .setSchemaDescriptor(new OrchestratorServiceMethodDescriptorSupplier("WatchHealth"))
              .build();
        }
      }
    }
    return getWatchHealthMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static OrchestratorServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<OrchestratorServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<OrchestratorServiceStub>() {
        @java.lang.Override
        public OrchestratorServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new OrchestratorServiceStub(channel, callOptions);
        }
      };
    return OrchestratorServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static OrchestratorServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<OrchestratorServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<OrchestratorServiceBlockingV2Stub>() {
        @java.lang.Override
        public OrchestratorServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new OrchestratorServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return OrchestratorServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static OrchestratorServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<OrchestratorServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<OrchestratorServiceBlockingStub>() {
        @java.lang.Override
        public OrchestratorServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new OrchestratorServiceBlockingStub(channel, callOptions);
        }
      };
    return OrchestratorServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static OrchestratorServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<OrchestratorServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<OrchestratorServiceFutureStub>() {
        @java.lang.Override
        public OrchestratorServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new OrchestratorServiceFutureStub(channel, callOptions);
        }
      };
    return OrchestratorServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * OrchestratorService is the AI-powered self-healing supervisor.
   * It receives failure reports, diagnoses root causes, and executes repair strategies.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Report a process failure for diagnosis and repair
     * </pre>
     */
    default void reportFailure(ai.rever.boss.ipc.proto.ProcessFailureReport request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RepairAction> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReportFailureMethod(), responseObserver);
    }

    /**
     * <pre>
     * Get the health dashboard for all processes
     * </pre>
     */
    default void getHealthDashboard(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.HealthDashboard> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetHealthDashboardMethod(), responseObserver);
    }

    /**
     * <pre>
     * Get the history of repair actions taken
     * </pre>
     */
    default void getRepairHistory(ai.rever.boss.ipc.proto.RepairHistoryRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RepairHistoryResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetRepairHistoryMethod(), responseObserver);
    }

    /**
     * <pre>
     * User approves or rejects a proposed repair (e.g., code patch)
     * </pre>
     */
    default void approveRepair(ai.rever.boss.ipc.proto.RepairApproval request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RepairApprovalResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getApproveRepairMethod(), responseObserver);
    }

    /**
     * <pre>
     * Stream real-time health events
     * </pre>
     */
    default void watchHealth(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.HealthEvent> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getWatchHealthMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service OrchestratorService.
   * <pre>
   * OrchestratorService is the AI-powered self-healing supervisor.
   * It receives failure reports, diagnoses root causes, and executes repair strategies.
   * </pre>
   */
  public static abstract class OrchestratorServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return OrchestratorServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service OrchestratorService.
   * <pre>
   * OrchestratorService is the AI-powered self-healing supervisor.
   * It receives failure reports, diagnoses root causes, and executes repair strategies.
   * </pre>
   */
  public static final class OrchestratorServiceStub
      extends io.grpc.stub.AbstractAsyncStub<OrchestratorServiceStub> {
    private OrchestratorServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected OrchestratorServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new OrchestratorServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Report a process failure for diagnosis and repair
     * </pre>
     */
    public void reportFailure(ai.rever.boss.ipc.proto.ProcessFailureReport request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RepairAction> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReportFailureMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Get the health dashboard for all processes
     * </pre>
     */
    public void getHealthDashboard(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.HealthDashboard> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetHealthDashboardMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Get the history of repair actions taken
     * </pre>
     */
    public void getRepairHistory(ai.rever.boss.ipc.proto.RepairHistoryRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RepairHistoryResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetRepairHistoryMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * User approves or rejects a proposed repair (e.g., code patch)
     * </pre>
     */
    public void approveRepair(ai.rever.boss.ipc.proto.RepairApproval request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RepairApprovalResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getApproveRepairMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Stream real-time health events
     * </pre>
     */
    public void watchHealth(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.HealthEvent> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getWatchHealthMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service OrchestratorService.
   * <pre>
   * OrchestratorService is the AI-powered self-healing supervisor.
   * It receives failure reports, diagnoses root causes, and executes repair strategies.
   * </pre>
   */
  public static final class OrchestratorServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<OrchestratorServiceBlockingV2Stub> {
    private OrchestratorServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected OrchestratorServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new OrchestratorServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Report a process failure for diagnosis and repair
     * </pre>
     */
    public ai.rever.boss.ipc.proto.RepairAction reportFailure(ai.rever.boss.ipc.proto.ProcessFailureReport request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReportFailureMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get the health dashboard for all processes
     * </pre>
     */
    public ai.rever.boss.ipc.proto.HealthDashboard getHealthDashboard(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetHealthDashboardMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get the history of repair actions taken
     * </pre>
     */
    public ai.rever.boss.ipc.proto.RepairHistoryResponse getRepairHistory(ai.rever.boss.ipc.proto.RepairHistoryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetRepairHistoryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * User approves or rejects a proposed repair (e.g., code patch)
     * </pre>
     */
    public ai.rever.boss.ipc.proto.RepairApprovalResponse approveRepair(ai.rever.boss.ipc.proto.RepairApproval request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getApproveRepairMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Stream real-time health events
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.HealthEvent>
        watchHealth(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getWatchHealthMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service OrchestratorService.
   * <pre>
   * OrchestratorService is the AI-powered self-healing supervisor.
   * It receives failure reports, diagnoses root causes, and executes repair strategies.
   * </pre>
   */
  public static final class OrchestratorServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<OrchestratorServiceBlockingStub> {
    private OrchestratorServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected OrchestratorServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new OrchestratorServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Report a process failure for diagnosis and repair
     * </pre>
     */
    public ai.rever.boss.ipc.proto.RepairAction reportFailure(ai.rever.boss.ipc.proto.ProcessFailureReport request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReportFailureMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get the health dashboard for all processes
     * </pre>
     */
    public ai.rever.boss.ipc.proto.HealthDashboard getHealthDashboard(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetHealthDashboardMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get the history of repair actions taken
     * </pre>
     */
    public ai.rever.boss.ipc.proto.RepairHistoryResponse getRepairHistory(ai.rever.boss.ipc.proto.RepairHistoryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetRepairHistoryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * User approves or rejects a proposed repair (e.g., code patch)
     * </pre>
     */
    public ai.rever.boss.ipc.proto.RepairApprovalResponse approveRepair(ai.rever.boss.ipc.proto.RepairApproval request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getApproveRepairMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Stream real-time health events
     * </pre>
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.HealthEvent> watchHealth(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getWatchHealthMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service OrchestratorService.
   * <pre>
   * OrchestratorService is the AI-powered self-healing supervisor.
   * It receives failure reports, diagnoses root causes, and executes repair strategies.
   * </pre>
   */
  public static final class OrchestratorServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<OrchestratorServiceFutureStub> {
    private OrchestratorServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected OrchestratorServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new OrchestratorServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Report a process failure for diagnosis and repair
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.RepairAction> reportFailure(
        ai.rever.boss.ipc.proto.ProcessFailureReport request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReportFailureMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Get the health dashboard for all processes
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.HealthDashboard> getHealthDashboard(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetHealthDashboardMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Get the history of repair actions taken
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.RepairHistoryResponse> getRepairHistory(
        ai.rever.boss.ipc.proto.RepairHistoryRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetRepairHistoryMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * User approves or rejects a proposed repair (e.g., code patch)
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.RepairApprovalResponse> approveRepair(
        ai.rever.boss.ipc.proto.RepairApproval request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getApproveRepairMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REPORT_FAILURE = 0;
  private static final int METHODID_GET_HEALTH_DASHBOARD = 1;
  private static final int METHODID_GET_REPAIR_HISTORY = 2;
  private static final int METHODID_APPROVE_REPAIR = 3;
  private static final int METHODID_WATCH_HEALTH = 4;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REPORT_FAILURE:
          serviceImpl.reportFailure((ai.rever.boss.ipc.proto.ProcessFailureReport) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RepairAction>) responseObserver);
          break;
        case METHODID_GET_HEALTH_DASHBOARD:
          serviceImpl.getHealthDashboard((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.HealthDashboard>) responseObserver);
          break;
        case METHODID_GET_REPAIR_HISTORY:
          serviceImpl.getRepairHistory((ai.rever.boss.ipc.proto.RepairHistoryRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RepairHistoryResponse>) responseObserver);
          break;
        case METHODID_APPROVE_REPAIR:
          serviceImpl.approveRepair((ai.rever.boss.ipc.proto.RepairApproval) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RepairApprovalResponse>) responseObserver);
          break;
        case METHODID_WATCH_HEALTH:
          serviceImpl.watchHealth((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.HealthEvent>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getReportFailureMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.ProcessFailureReport,
              ai.rever.boss.ipc.proto.RepairAction>(
                service, METHODID_REPORT_FAILURE)))
        .addMethod(
          getGetHealthDashboardMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.HealthDashboard>(
                service, METHODID_GET_HEALTH_DASHBOARD)))
        .addMethod(
          getGetRepairHistoryMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.RepairHistoryRequest,
              ai.rever.boss.ipc.proto.RepairHistoryResponse>(
                service, METHODID_GET_REPAIR_HISTORY)))
        .addMethod(
          getApproveRepairMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.RepairApproval,
              ai.rever.boss.ipc.proto.RepairApprovalResponse>(
                service, METHODID_APPROVE_REPAIR)))
        .addMethod(
          getWatchHealthMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.HealthEvent>(
                service, METHODID_WATCH_HEALTH)))
        .build();
  }

  private static abstract class OrchestratorServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    OrchestratorServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.Orchestrator.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("OrchestratorService");
    }
  }

  private static final class OrchestratorServiceFileDescriptorSupplier
      extends OrchestratorServiceBaseDescriptorSupplier {
    OrchestratorServiceFileDescriptorSupplier() {}
  }

  private static final class OrchestratorServiceMethodDescriptorSupplier
      extends OrchestratorServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    OrchestratorServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (OrchestratorServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new OrchestratorServiceFileDescriptorSupplier())
              .addMethod(getReportFailureMethod())
              .addMethod(getGetHealthDashboardMethod())
              .addMethod(getGetRepairHistoryMethod())
              .addMethod(getApproveRepairMethod())
              .addMethod(getWatchHealthMethod())
              .build();
        }
      }
    }
    return result;
  }
}
