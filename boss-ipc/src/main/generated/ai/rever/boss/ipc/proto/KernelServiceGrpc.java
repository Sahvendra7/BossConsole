package ai.rever.boss.ipc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * KernelService is the central hub that all child processes connect to on startup.
 * It handles process registration, heartbeat monitoring, and lifecycle management.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/kernel.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class KernelServiceGrpc {

  private KernelServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.KernelService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.RegisterProcessRequest,
      ai.rever.boss.ipc.proto.RegisterProcessResponse> getRegisterProcessMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RegisterProcess",
      requestType = ai.rever.boss.ipc.proto.RegisterProcessRequest.class,
      responseType = ai.rever.boss.ipc.proto.RegisterProcessResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.RegisterProcessRequest,
      ai.rever.boss.ipc.proto.RegisterProcessResponse> getRegisterProcessMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.RegisterProcessRequest, ai.rever.boss.ipc.proto.RegisterProcessResponse> getRegisterProcessMethod;
    if ((getRegisterProcessMethod = KernelServiceGrpc.getRegisterProcessMethod) == null) {
      synchronized (KernelServiceGrpc.class) {
        if ((getRegisterProcessMethod = KernelServiceGrpc.getRegisterProcessMethod) == null) {
          KernelServiceGrpc.getRegisterProcessMethod = getRegisterProcessMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.RegisterProcessRequest, ai.rever.boss.ipc.proto.RegisterProcessResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RegisterProcess"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.RegisterProcessRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.RegisterProcessResponse.getDefaultInstance()))
              .setSchemaDescriptor(new KernelServiceMethodDescriptorSupplier("RegisterProcess"))
              .build();
        }
      }
    }
    return getRegisterProcessMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.HeartbeatPing,
      ai.rever.boss.ipc.proto.HeartbeatPong> getHeartbeatMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Heartbeat",
      requestType = ai.rever.boss.ipc.proto.HeartbeatPing.class,
      responseType = ai.rever.boss.ipc.proto.HeartbeatPong.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.HeartbeatPing,
      ai.rever.boss.ipc.proto.HeartbeatPong> getHeartbeatMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.HeartbeatPing, ai.rever.boss.ipc.proto.HeartbeatPong> getHeartbeatMethod;
    if ((getHeartbeatMethod = KernelServiceGrpc.getHeartbeatMethod) == null) {
      synchronized (KernelServiceGrpc.class) {
        if ((getHeartbeatMethod = KernelServiceGrpc.getHeartbeatMethod) == null) {
          KernelServiceGrpc.getHeartbeatMethod = getHeartbeatMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.HeartbeatPing, ai.rever.boss.ipc.proto.HeartbeatPong>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Heartbeat"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.HeartbeatPing.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.HeartbeatPong.getDefaultInstance()))
              .setSchemaDescriptor(new KernelServiceMethodDescriptorSupplier("Heartbeat"))
              .build();
        }
      }
    }
    return getHeartbeatMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ShutdownRequest,
      ai.rever.boss.ipc.proto.ShutdownResponse> getRequestShutdownMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RequestShutdown",
      requestType = ai.rever.boss.ipc.proto.ShutdownRequest.class,
      responseType = ai.rever.boss.ipc.proto.ShutdownResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ShutdownRequest,
      ai.rever.boss.ipc.proto.ShutdownResponse> getRequestShutdownMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ShutdownRequest, ai.rever.boss.ipc.proto.ShutdownResponse> getRequestShutdownMethod;
    if ((getRequestShutdownMethod = KernelServiceGrpc.getRequestShutdownMethod) == null) {
      synchronized (KernelServiceGrpc.class) {
        if ((getRequestShutdownMethod = KernelServiceGrpc.getRequestShutdownMethod) == null) {
          KernelServiceGrpc.getRequestShutdownMethod = getRequestShutdownMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.ShutdownRequest, ai.rever.boss.ipc.proto.ShutdownResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RequestShutdown"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ShutdownRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ShutdownResponse.getDefaultInstance()))
              .setSchemaDescriptor(new KernelServiceMethodDescriptorSupplier("RequestShutdown"))
              .build();
        }
      }
    }
    return getRequestShutdownMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ProcessStatusRequest,
      ai.rever.boss.ipc.proto.ProcessStatusResponse> getGetProcessStatusMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetProcessStatus",
      requestType = ai.rever.boss.ipc.proto.ProcessStatusRequest.class,
      responseType = ai.rever.boss.ipc.proto.ProcessStatusResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ProcessStatusRequest,
      ai.rever.boss.ipc.proto.ProcessStatusResponse> getGetProcessStatusMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ProcessStatusRequest, ai.rever.boss.ipc.proto.ProcessStatusResponse> getGetProcessStatusMethod;
    if ((getGetProcessStatusMethod = KernelServiceGrpc.getGetProcessStatusMethod) == null) {
      synchronized (KernelServiceGrpc.class) {
        if ((getGetProcessStatusMethod = KernelServiceGrpc.getGetProcessStatusMethod) == null) {
          KernelServiceGrpc.getGetProcessStatusMethod = getGetProcessStatusMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.ProcessStatusRequest, ai.rever.boss.ipc.proto.ProcessStatusResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetProcessStatus"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ProcessStatusRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ProcessStatusResponse.getDefaultInstance()))
              .setSchemaDescriptor(new KernelServiceMethodDescriptorSupplier("GetProcessStatus"))
              .build();
        }
      }
    }
    return getGetProcessStatusMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.ListProcessesResponse> getListProcessesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListProcesses",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.ListProcessesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.ListProcessesResponse> getListProcessesMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.ListProcessesResponse> getListProcessesMethod;
    if ((getListProcessesMethod = KernelServiceGrpc.getListProcessesMethod) == null) {
      synchronized (KernelServiceGrpc.class) {
        if ((getListProcessesMethod = KernelServiceGrpc.getListProcessesMethod) == null) {
          KernelServiceGrpc.getListProcessesMethod = getListProcessesMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.ListProcessesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListProcesses"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ListProcessesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new KernelServiceMethodDescriptorSupplier("ListProcesses"))
              .build();
        }
      }
    }
    return getListProcessesMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static KernelServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<KernelServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<KernelServiceStub>() {
        @java.lang.Override
        public KernelServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new KernelServiceStub(channel, callOptions);
        }
      };
    return KernelServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static KernelServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<KernelServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<KernelServiceBlockingV2Stub>() {
        @java.lang.Override
        public KernelServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new KernelServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return KernelServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static KernelServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<KernelServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<KernelServiceBlockingStub>() {
        @java.lang.Override
        public KernelServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new KernelServiceBlockingStub(channel, callOptions);
        }
      };
    return KernelServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static KernelServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<KernelServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<KernelServiceFutureStub>() {
        @java.lang.Override
        public KernelServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new KernelServiceFutureStub(channel, callOptions);
        }
      };
    return KernelServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * KernelService is the central hub that all child processes connect to on startup.
   * It handles process registration, heartbeat monitoring, and lifecycle management.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Register a newly started process with the kernel
     * </pre>
     */
    default void registerProcess(ai.rever.boss.ipc.proto.RegisterProcessRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RegisterProcessResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterProcessMethod(), responseObserver);
    }

    /**
     * <pre>
     * Bidirectional heartbeat stream — kernel sends pings, process responds with pongs
     * </pre>
     */
    default io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.HeartbeatPing> heartbeat(
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.HeartbeatPong> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getHeartbeatMethod(), responseObserver);
    }

    /**
     * <pre>
     * Request graceful shutdown of a process
     * </pre>
     */
    default void requestShutdown(ai.rever.boss.ipc.proto.ShutdownRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ShutdownResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRequestShutdownMethod(), responseObserver);
    }

    /**
     * <pre>
     * Query the status of a specific process
     * </pre>
     */
    default void getProcessStatus(ai.rever.boss.ipc.proto.ProcessStatusRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ProcessStatusResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetProcessStatusMethod(), responseObserver);
    }

    /**
     * <pre>
     * List all registered processes
     * </pre>
     */
    default void listProcesses(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListProcessesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListProcessesMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service KernelService.
   * <pre>
   * KernelService is the central hub that all child processes connect to on startup.
   * It handles process registration, heartbeat monitoring, and lifecycle management.
   * </pre>
   */
  public static abstract class KernelServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return KernelServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service KernelService.
   * <pre>
   * KernelService is the central hub that all child processes connect to on startup.
   * It handles process registration, heartbeat monitoring, and lifecycle management.
   * </pre>
   */
  public static final class KernelServiceStub
      extends io.grpc.stub.AbstractAsyncStub<KernelServiceStub> {
    private KernelServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected KernelServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new KernelServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Register a newly started process with the kernel
     * </pre>
     */
    public void registerProcess(ai.rever.boss.ipc.proto.RegisterProcessRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RegisterProcessResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterProcessMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Bidirectional heartbeat stream — kernel sends pings, process responds with pongs
     * </pre>
     */
    public io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.HeartbeatPing> heartbeat(
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.HeartbeatPong> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), responseObserver);
    }

    /**
     * <pre>
     * Request graceful shutdown of a process
     * </pre>
     */
    public void requestShutdown(ai.rever.boss.ipc.proto.ShutdownRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ShutdownResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRequestShutdownMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Query the status of a specific process
     * </pre>
     */
    public void getProcessStatus(ai.rever.boss.ipc.proto.ProcessStatusRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ProcessStatusResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetProcessStatusMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * List all registered processes
     * </pre>
     */
    public void listProcesses(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListProcessesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListProcessesMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service KernelService.
   * <pre>
   * KernelService is the central hub that all child processes connect to on startup.
   * It handles process registration, heartbeat monitoring, and lifecycle management.
   * </pre>
   */
  public static final class KernelServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<KernelServiceBlockingV2Stub> {
    private KernelServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected KernelServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new KernelServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Register a newly started process with the kernel
     * </pre>
     */
    public ai.rever.boss.ipc.proto.RegisterProcessResponse registerProcess(ai.rever.boss.ipc.proto.RegisterProcessRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterProcessMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Bidirectional heartbeat stream — kernel sends pings, process responds with pongs
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<ai.rever.boss.ipc.proto.HeartbeatPing, ai.rever.boss.ipc.proto.HeartbeatPong>
        heartbeat() {
      return io.grpc.stub.ClientCalls.blockingBidiStreamingCall(
          getChannel(), getHeartbeatMethod(), getCallOptions());
    }

    /**
     * <pre>
     * Request graceful shutdown of a process
     * </pre>
     */
    public ai.rever.boss.ipc.proto.ShutdownResponse requestShutdown(ai.rever.boss.ipc.proto.ShutdownRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRequestShutdownMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Query the status of a specific process
     * </pre>
     */
    public ai.rever.boss.ipc.proto.ProcessStatusResponse getProcessStatus(ai.rever.boss.ipc.proto.ProcessStatusRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetProcessStatusMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * List all registered processes
     * </pre>
     */
    public ai.rever.boss.ipc.proto.ListProcessesResponse listProcesses(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListProcessesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service KernelService.
   * <pre>
   * KernelService is the central hub that all child processes connect to on startup.
   * It handles process registration, heartbeat monitoring, and lifecycle management.
   * </pre>
   */
  public static final class KernelServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<KernelServiceBlockingStub> {
    private KernelServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected KernelServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new KernelServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Register a newly started process with the kernel
     * </pre>
     */
    public ai.rever.boss.ipc.proto.RegisterProcessResponse registerProcess(ai.rever.boss.ipc.proto.RegisterProcessRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterProcessMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Request graceful shutdown of a process
     * </pre>
     */
    public ai.rever.boss.ipc.proto.ShutdownResponse requestShutdown(ai.rever.boss.ipc.proto.ShutdownRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRequestShutdownMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Query the status of a specific process
     * </pre>
     */
    public ai.rever.boss.ipc.proto.ProcessStatusResponse getProcessStatus(ai.rever.boss.ipc.proto.ProcessStatusRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetProcessStatusMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * List all registered processes
     * </pre>
     */
    public ai.rever.boss.ipc.proto.ListProcessesResponse listProcesses(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListProcessesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service KernelService.
   * <pre>
   * KernelService is the central hub that all child processes connect to on startup.
   * It handles process registration, heartbeat monitoring, and lifecycle management.
   * </pre>
   */
  public static final class KernelServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<KernelServiceFutureStub> {
    private KernelServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected KernelServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new KernelServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Register a newly started process with the kernel
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.RegisterProcessResponse> registerProcess(
        ai.rever.boss.ipc.proto.RegisterProcessRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterProcessMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Request graceful shutdown of a process
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.ShutdownResponse> requestShutdown(
        ai.rever.boss.ipc.proto.ShutdownRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRequestShutdownMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Query the status of a specific process
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.ProcessStatusResponse> getProcessStatus(
        ai.rever.boss.ipc.proto.ProcessStatusRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetProcessStatusMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * List all registered processes
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.ListProcessesResponse> listProcesses(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListProcessesMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER_PROCESS = 0;
  private static final int METHODID_REQUEST_SHUTDOWN = 1;
  private static final int METHODID_GET_PROCESS_STATUS = 2;
  private static final int METHODID_LIST_PROCESSES = 3;
  private static final int METHODID_HEARTBEAT = 4;

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
        case METHODID_REGISTER_PROCESS:
          serviceImpl.registerProcess((ai.rever.boss.ipc.proto.RegisterProcessRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.RegisterProcessResponse>) responseObserver);
          break;
        case METHODID_REQUEST_SHUTDOWN:
          serviceImpl.requestShutdown((ai.rever.boss.ipc.proto.ShutdownRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ShutdownResponse>) responseObserver);
          break;
        case METHODID_GET_PROCESS_STATUS:
          serviceImpl.getProcessStatus((ai.rever.boss.ipc.proto.ProcessStatusRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ProcessStatusResponse>) responseObserver);
          break;
        case METHODID_LIST_PROCESSES:
          serviceImpl.listProcesses((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListProcessesResponse>) responseObserver);
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
        case METHODID_HEARTBEAT:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.heartbeat(
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.HeartbeatPong>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRegisterProcessMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.RegisterProcessRequest,
              ai.rever.boss.ipc.proto.RegisterProcessResponse>(
                service, METHODID_REGISTER_PROCESS)))
        .addMethod(
          getHeartbeatMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.HeartbeatPing,
              ai.rever.boss.ipc.proto.HeartbeatPong>(
                service, METHODID_HEARTBEAT)))
        .addMethod(
          getRequestShutdownMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.ShutdownRequest,
              ai.rever.boss.ipc.proto.ShutdownResponse>(
                service, METHODID_REQUEST_SHUTDOWN)))
        .addMethod(
          getGetProcessStatusMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.ProcessStatusRequest,
              ai.rever.boss.ipc.proto.ProcessStatusResponse>(
                service, METHODID_GET_PROCESS_STATUS)))
        .addMethod(
          getListProcessesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.ListProcessesResponse>(
                service, METHODID_LIST_PROCESSES)))
        .build();
  }

  private static abstract class KernelServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    KernelServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.Kernel.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("KernelService");
    }
  }

  private static final class KernelServiceFileDescriptorSupplier
      extends KernelServiceBaseDescriptorSupplier {
    KernelServiceFileDescriptorSupplier() {}
  }

  private static final class KernelServiceMethodDescriptorSupplier
      extends KernelServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    KernelServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (KernelServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new KernelServiceFileDescriptorSupplier())
              .addMethod(getRegisterProcessMethod())
              .addMethod(getHeartbeatMethod())
              .addMethod(getRequestShutdownMethod())
              .addMethod(getGetProcessStatusMethod())
              .addMethod(getListProcessesMethod())
              .build();
        }
      }
    }
    return result;
  }
}
