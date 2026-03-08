package ai.rever.boss.ipc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * MasteryService orchestrates DAG workflows that compose plugin capabilities.
 * A Mastery is a directed acyclic graph of plugin invocations that automates multi-step tasks.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/mastery.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class MasteryServiceGrpc {

  private MasteryServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.MasteryService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryDefinition,
      ai.rever.boss.ipc.proto.MasteryId> getCreateMasteryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreateMastery",
      requestType = ai.rever.boss.ipc.proto.MasteryDefinition.class,
      responseType = ai.rever.boss.ipc.proto.MasteryId.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryDefinition,
      ai.rever.boss.ipc.proto.MasteryId> getCreateMasteryMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryDefinition, ai.rever.boss.ipc.proto.MasteryId> getCreateMasteryMethod;
    if ((getCreateMasteryMethod = MasteryServiceGrpc.getCreateMasteryMethod) == null) {
      synchronized (MasteryServiceGrpc.class) {
        if ((getCreateMasteryMethod = MasteryServiceGrpc.getCreateMasteryMethod) == null) {
          MasteryServiceGrpc.getCreateMasteryMethod = getCreateMasteryMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.MasteryDefinition, ai.rever.boss.ipc.proto.MasteryId>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateMastery"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.MasteryDefinition.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.MasteryId.getDefaultInstance()))
              .setSchemaDescriptor(new MasteryServiceMethodDescriptorSupplier("CreateMastery"))
              .build();
        }
      }
    }
    return getCreateMasteryMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ExecuteMasteryRequest,
      ai.rever.boss.ipc.proto.MasteryProgress> getExecuteMasteryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ExecuteMastery",
      requestType = ai.rever.boss.ipc.proto.ExecuteMasteryRequest.class,
      responseType = ai.rever.boss.ipc.proto.MasteryProgress.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ExecuteMasteryRequest,
      ai.rever.boss.ipc.proto.MasteryProgress> getExecuteMasteryMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ExecuteMasteryRequest, ai.rever.boss.ipc.proto.MasteryProgress> getExecuteMasteryMethod;
    if ((getExecuteMasteryMethod = MasteryServiceGrpc.getExecuteMasteryMethod) == null) {
      synchronized (MasteryServiceGrpc.class) {
        if ((getExecuteMasteryMethod = MasteryServiceGrpc.getExecuteMasteryMethod) == null) {
          MasteryServiceGrpc.getExecuteMasteryMethod = getExecuteMasteryMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.ExecuteMasteryRequest, ai.rever.boss.ipc.proto.MasteryProgress>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ExecuteMastery"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ExecuteMasteryRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.MasteryProgress.getDefaultInstance()))
              .setSchemaDescriptor(new MasteryServiceMethodDescriptorSupplier("ExecuteMastery"))
              .build();
        }
      }
    }
    return getExecuteMasteryMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryExecutionId,
      ai.rever.boss.ipc.proto.CancelMasteryResponse> getCancelMasteryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CancelMastery",
      requestType = ai.rever.boss.ipc.proto.MasteryExecutionId.class,
      responseType = ai.rever.boss.ipc.proto.CancelMasteryResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryExecutionId,
      ai.rever.boss.ipc.proto.CancelMasteryResponse> getCancelMasteryMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryExecutionId, ai.rever.boss.ipc.proto.CancelMasteryResponse> getCancelMasteryMethod;
    if ((getCancelMasteryMethod = MasteryServiceGrpc.getCancelMasteryMethod) == null) {
      synchronized (MasteryServiceGrpc.class) {
        if ((getCancelMasteryMethod = MasteryServiceGrpc.getCancelMasteryMethod) == null) {
          MasteryServiceGrpc.getCancelMasteryMethod = getCancelMasteryMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.MasteryExecutionId, ai.rever.boss.ipc.proto.CancelMasteryResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CancelMastery"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.MasteryExecutionId.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.CancelMasteryResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasteryServiceMethodDescriptorSupplier("CancelMastery"))
              .build();
        }
      }
    }
    return getCancelMasteryMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryExecutionId,
      ai.rever.boss.ipc.proto.MasteryStatus> getGetMasteryStatusMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetMasteryStatus",
      requestType = ai.rever.boss.ipc.proto.MasteryExecutionId.class,
      responseType = ai.rever.boss.ipc.proto.MasteryStatus.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryExecutionId,
      ai.rever.boss.ipc.proto.MasteryStatus> getGetMasteryStatusMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryExecutionId, ai.rever.boss.ipc.proto.MasteryStatus> getGetMasteryStatusMethod;
    if ((getGetMasteryStatusMethod = MasteryServiceGrpc.getGetMasteryStatusMethod) == null) {
      synchronized (MasteryServiceGrpc.class) {
        if ((getGetMasteryStatusMethod = MasteryServiceGrpc.getGetMasteryStatusMethod) == null) {
          MasteryServiceGrpc.getGetMasteryStatusMethod = getGetMasteryStatusMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.MasteryExecutionId, ai.rever.boss.ipc.proto.MasteryStatus>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetMasteryStatus"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.MasteryExecutionId.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.MasteryStatus.getDefaultInstance()))
              .setSchemaDescriptor(new MasteryServiceMethodDescriptorSupplier("GetMasteryStatus"))
              .build();
        }
      }
    }
    return getGetMasteryStatusMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.GenerateMasteryRequest,
      ai.rever.boss.ipc.proto.MasteryDefinition> getGenerateMasteryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GenerateMastery",
      requestType = ai.rever.boss.ipc.proto.GenerateMasteryRequest.class,
      responseType = ai.rever.boss.ipc.proto.MasteryDefinition.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.GenerateMasteryRequest,
      ai.rever.boss.ipc.proto.MasteryDefinition> getGenerateMasteryMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.GenerateMasteryRequest, ai.rever.boss.ipc.proto.MasteryDefinition> getGenerateMasteryMethod;
    if ((getGenerateMasteryMethod = MasteryServiceGrpc.getGenerateMasteryMethod) == null) {
      synchronized (MasteryServiceGrpc.class) {
        if ((getGenerateMasteryMethod = MasteryServiceGrpc.getGenerateMasteryMethod) == null) {
          MasteryServiceGrpc.getGenerateMasteryMethod = getGenerateMasteryMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.GenerateMasteryRequest, ai.rever.boss.ipc.proto.MasteryDefinition>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GenerateMastery"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.GenerateMasteryRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.MasteryDefinition.getDefaultInstance()))
              .setSchemaDescriptor(new MasteryServiceMethodDescriptorSupplier("GenerateMastery"))
              .build();
        }
      }
    }
    return getGenerateMasteryMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ListMasteriesRequest,
      ai.rever.boss.ipc.proto.ListMasteriesResponse> getListMasteriesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListMasteries",
      requestType = ai.rever.boss.ipc.proto.ListMasteriesRequest.class,
      responseType = ai.rever.boss.ipc.proto.ListMasteriesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ListMasteriesRequest,
      ai.rever.boss.ipc.proto.ListMasteriesResponse> getListMasteriesMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ListMasteriesRequest, ai.rever.boss.ipc.proto.ListMasteriesResponse> getListMasteriesMethod;
    if ((getListMasteriesMethod = MasteryServiceGrpc.getListMasteriesMethod) == null) {
      synchronized (MasteryServiceGrpc.class) {
        if ((getListMasteriesMethod = MasteryServiceGrpc.getListMasteriesMethod) == null) {
          MasteryServiceGrpc.getListMasteriesMethod = getListMasteriesMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.ListMasteriesRequest, ai.rever.boss.ipc.proto.ListMasteriesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListMasteries"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ListMasteriesRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ListMasteriesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasteryServiceMethodDescriptorSupplier("ListMasteries"))
              .build();
        }
      }
    }
    return getListMasteriesMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryId,
      ai.rever.boss.ipc.proto.Empty> getDeleteMasteryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DeleteMastery",
      requestType = ai.rever.boss.ipc.proto.MasteryId.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryId,
      ai.rever.boss.ipc.proto.Empty> getDeleteMasteryMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.MasteryId, ai.rever.boss.ipc.proto.Empty> getDeleteMasteryMethod;
    if ((getDeleteMasteryMethod = MasteryServiceGrpc.getDeleteMasteryMethod) == null) {
      synchronized (MasteryServiceGrpc.class) {
        if ((getDeleteMasteryMethod = MasteryServiceGrpc.getDeleteMasteryMethod) == null) {
          MasteryServiceGrpc.getDeleteMasteryMethod = getDeleteMasteryMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.MasteryId, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DeleteMastery"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.MasteryId.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new MasteryServiceMethodDescriptorSupplier("DeleteMastery"))
              .build();
        }
      }
    }
    return getDeleteMasteryMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static MasteryServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MasteryServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MasteryServiceStub>() {
        @java.lang.Override
        public MasteryServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MasteryServiceStub(channel, callOptions);
        }
      };
    return MasteryServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static MasteryServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MasteryServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MasteryServiceBlockingV2Stub>() {
        @java.lang.Override
        public MasteryServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MasteryServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return MasteryServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static MasteryServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MasteryServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MasteryServiceBlockingStub>() {
        @java.lang.Override
        public MasteryServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MasteryServiceBlockingStub(channel, callOptions);
        }
      };
    return MasteryServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static MasteryServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MasteryServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MasteryServiceFutureStub>() {
        @java.lang.Override
        public MasteryServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MasteryServiceFutureStub(channel, callOptions);
        }
      };
    return MasteryServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * MasteryService orchestrates DAG workflows that compose plugin capabilities.
   * A Mastery is a directed acyclic graph of plugin invocations that automates multi-step tasks.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Create and persist a mastery definition
     * </pre>
     */
    default void createMastery(ai.rever.boss.ipc.proto.MasteryDefinition request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryId> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreateMasteryMethod(), responseObserver);
    }

    /**
     * <pre>
     * Execute a mastery with input, streaming progress events
     * </pre>
     */
    default void executeMastery(ai.rever.boss.ipc.proto.ExecuteMasteryRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryProgress> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getExecuteMasteryMethod(), responseObserver);
    }

    /**
     * <pre>
     * Cancel a running mastery execution
     * </pre>
     */
    default void cancelMastery(ai.rever.boss.ipc.proto.MasteryExecutionId request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.CancelMasteryResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCancelMasteryMethod(), responseObserver);
    }

    /**
     * <pre>
     * Get the status of a running or completed mastery execution
     * </pre>
     */
    default void getMasteryStatus(ai.rever.boss.ipc.proto.MasteryExecutionId request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryStatus> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetMasteryStatusMethod(), responseObserver);
    }

    /**
     * <pre>
     * AI-assisted: generate a mastery definition from a natural language task description
     * </pre>
     */
    default void generateMastery(ai.rever.boss.ipc.proto.GenerateMasteryRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryDefinition> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGenerateMasteryMethod(), responseObserver);
    }

    /**
     * <pre>
     * List all saved mastery definitions
     * </pre>
     */
    default void listMasteries(ai.rever.boss.ipc.proto.ListMasteriesRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListMasteriesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListMasteriesMethod(), responseObserver);
    }

    /**
     * <pre>
     * Delete a saved mastery
     * </pre>
     */
    default void deleteMastery(ai.rever.boss.ipc.proto.MasteryId request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDeleteMasteryMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service MasteryService.
   * <pre>
   * MasteryService orchestrates DAG workflows that compose plugin capabilities.
   * A Mastery is a directed acyclic graph of plugin invocations that automates multi-step tasks.
   * </pre>
   */
  public static abstract class MasteryServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return MasteryServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service MasteryService.
   * <pre>
   * MasteryService orchestrates DAG workflows that compose plugin capabilities.
   * A Mastery is a directed acyclic graph of plugin invocations that automates multi-step tasks.
   * </pre>
   */
  public static final class MasteryServiceStub
      extends io.grpc.stub.AbstractAsyncStub<MasteryServiceStub> {
    private MasteryServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MasteryServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MasteryServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Create and persist a mastery definition
     * </pre>
     */
    public void createMastery(ai.rever.boss.ipc.proto.MasteryDefinition request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryId> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreateMasteryMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Execute a mastery with input, streaming progress events
     * </pre>
     */
    public void executeMastery(ai.rever.boss.ipc.proto.ExecuteMasteryRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryProgress> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getExecuteMasteryMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Cancel a running mastery execution
     * </pre>
     */
    public void cancelMastery(ai.rever.boss.ipc.proto.MasteryExecutionId request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.CancelMasteryResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCancelMasteryMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Get the status of a running or completed mastery execution
     * </pre>
     */
    public void getMasteryStatus(ai.rever.boss.ipc.proto.MasteryExecutionId request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryStatus> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetMasteryStatusMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * AI-assisted: generate a mastery definition from a natural language task description
     * </pre>
     */
    public void generateMastery(ai.rever.boss.ipc.proto.GenerateMasteryRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryDefinition> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGenerateMasteryMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * List all saved mastery definitions
     * </pre>
     */
    public void listMasteries(ai.rever.boss.ipc.proto.ListMasteriesRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListMasteriesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListMasteriesMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Delete a saved mastery
     * </pre>
     */
    public void deleteMastery(ai.rever.boss.ipc.proto.MasteryId request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDeleteMasteryMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service MasteryService.
   * <pre>
   * MasteryService orchestrates DAG workflows that compose plugin capabilities.
   * A Mastery is a directed acyclic graph of plugin invocations that automates multi-step tasks.
   * </pre>
   */
  public static final class MasteryServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<MasteryServiceBlockingV2Stub> {
    private MasteryServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MasteryServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MasteryServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Create and persist a mastery definition
     * </pre>
     */
    public ai.rever.boss.ipc.proto.MasteryId createMastery(ai.rever.boss.ipc.proto.MasteryDefinition request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateMasteryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Execute a mastery with input, streaming progress events
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.MasteryProgress>
        executeMastery(ai.rever.boss.ipc.proto.ExecuteMasteryRequest request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getExecuteMasteryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Cancel a running mastery execution
     * </pre>
     */
    public ai.rever.boss.ipc.proto.CancelMasteryResponse cancelMastery(ai.rever.boss.ipc.proto.MasteryExecutionId request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCancelMasteryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get the status of a running or completed mastery execution
     * </pre>
     */
    public ai.rever.boss.ipc.proto.MasteryStatus getMasteryStatus(ai.rever.boss.ipc.proto.MasteryExecutionId request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetMasteryStatusMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * AI-assisted: generate a mastery definition from a natural language task description
     * </pre>
     */
    public ai.rever.boss.ipc.proto.MasteryDefinition generateMastery(ai.rever.boss.ipc.proto.GenerateMasteryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGenerateMasteryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * List all saved mastery definitions
     * </pre>
     */
    public ai.rever.boss.ipc.proto.ListMasteriesResponse listMasteries(ai.rever.boss.ipc.proto.ListMasteriesRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListMasteriesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Delete a saved mastery
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty deleteMastery(ai.rever.boss.ipc.proto.MasteryId request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeleteMasteryMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service MasteryService.
   * <pre>
   * MasteryService orchestrates DAG workflows that compose plugin capabilities.
   * A Mastery is a directed acyclic graph of plugin invocations that automates multi-step tasks.
   * </pre>
   */
  public static final class MasteryServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<MasteryServiceBlockingStub> {
    private MasteryServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MasteryServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MasteryServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Create and persist a mastery definition
     * </pre>
     */
    public ai.rever.boss.ipc.proto.MasteryId createMastery(ai.rever.boss.ipc.proto.MasteryDefinition request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateMasteryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Execute a mastery with input, streaming progress events
     * </pre>
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.MasteryProgress> executeMastery(
        ai.rever.boss.ipc.proto.ExecuteMasteryRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getExecuteMasteryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Cancel a running mastery execution
     * </pre>
     */
    public ai.rever.boss.ipc.proto.CancelMasteryResponse cancelMastery(ai.rever.boss.ipc.proto.MasteryExecutionId request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCancelMasteryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get the status of a running or completed mastery execution
     * </pre>
     */
    public ai.rever.boss.ipc.proto.MasteryStatus getMasteryStatus(ai.rever.boss.ipc.proto.MasteryExecutionId request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetMasteryStatusMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * AI-assisted: generate a mastery definition from a natural language task description
     * </pre>
     */
    public ai.rever.boss.ipc.proto.MasteryDefinition generateMastery(ai.rever.boss.ipc.proto.GenerateMasteryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGenerateMasteryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * List all saved mastery definitions
     * </pre>
     */
    public ai.rever.boss.ipc.proto.ListMasteriesResponse listMasteries(ai.rever.boss.ipc.proto.ListMasteriesRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListMasteriesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Delete a saved mastery
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty deleteMastery(ai.rever.boss.ipc.proto.MasteryId request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeleteMasteryMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service MasteryService.
   * <pre>
   * MasteryService orchestrates DAG workflows that compose plugin capabilities.
   * A Mastery is a directed acyclic graph of plugin invocations that automates multi-step tasks.
   * </pre>
   */
  public static final class MasteryServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<MasteryServiceFutureStub> {
    private MasteryServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MasteryServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MasteryServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Create and persist a mastery definition
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.MasteryId> createMastery(
        ai.rever.boss.ipc.proto.MasteryDefinition request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreateMasteryMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Cancel a running mastery execution
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.CancelMasteryResponse> cancelMastery(
        ai.rever.boss.ipc.proto.MasteryExecutionId request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCancelMasteryMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Get the status of a running or completed mastery execution
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.MasteryStatus> getMasteryStatus(
        ai.rever.boss.ipc.proto.MasteryExecutionId request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetMasteryStatusMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * AI-assisted: generate a mastery definition from a natural language task description
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.MasteryDefinition> generateMastery(
        ai.rever.boss.ipc.proto.GenerateMasteryRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGenerateMasteryMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * List all saved mastery definitions
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.ListMasteriesResponse> listMasteries(
        ai.rever.boss.ipc.proto.ListMasteriesRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListMasteriesMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Delete a saved mastery
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> deleteMastery(
        ai.rever.boss.ipc.proto.MasteryId request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDeleteMasteryMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_MASTERY = 0;
  private static final int METHODID_EXECUTE_MASTERY = 1;
  private static final int METHODID_CANCEL_MASTERY = 2;
  private static final int METHODID_GET_MASTERY_STATUS = 3;
  private static final int METHODID_GENERATE_MASTERY = 4;
  private static final int METHODID_LIST_MASTERIES = 5;
  private static final int METHODID_DELETE_MASTERY = 6;

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
        case METHODID_CREATE_MASTERY:
          serviceImpl.createMastery((ai.rever.boss.ipc.proto.MasteryDefinition) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryId>) responseObserver);
          break;
        case METHODID_EXECUTE_MASTERY:
          serviceImpl.executeMastery((ai.rever.boss.ipc.proto.ExecuteMasteryRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryProgress>) responseObserver);
          break;
        case METHODID_CANCEL_MASTERY:
          serviceImpl.cancelMastery((ai.rever.boss.ipc.proto.MasteryExecutionId) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.CancelMasteryResponse>) responseObserver);
          break;
        case METHODID_GET_MASTERY_STATUS:
          serviceImpl.getMasteryStatus((ai.rever.boss.ipc.proto.MasteryExecutionId) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryStatus>) responseObserver);
          break;
        case METHODID_GENERATE_MASTERY:
          serviceImpl.generateMastery((ai.rever.boss.ipc.proto.GenerateMasteryRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.MasteryDefinition>) responseObserver);
          break;
        case METHODID_LIST_MASTERIES:
          serviceImpl.listMasteries((ai.rever.boss.ipc.proto.ListMasteriesRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListMasteriesResponse>) responseObserver);
          break;
        case METHODID_DELETE_MASTERY:
          serviceImpl.deleteMastery((ai.rever.boss.ipc.proto.MasteryId) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty>) responseObserver);
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
          getCreateMasteryMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.MasteryDefinition,
              ai.rever.boss.ipc.proto.MasteryId>(
                service, METHODID_CREATE_MASTERY)))
        .addMethod(
          getExecuteMasteryMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.ExecuteMasteryRequest,
              ai.rever.boss.ipc.proto.MasteryProgress>(
                service, METHODID_EXECUTE_MASTERY)))
        .addMethod(
          getCancelMasteryMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.MasteryExecutionId,
              ai.rever.boss.ipc.proto.CancelMasteryResponse>(
                service, METHODID_CANCEL_MASTERY)))
        .addMethod(
          getGetMasteryStatusMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.MasteryExecutionId,
              ai.rever.boss.ipc.proto.MasteryStatus>(
                service, METHODID_GET_MASTERY_STATUS)))
        .addMethod(
          getGenerateMasteryMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.GenerateMasteryRequest,
              ai.rever.boss.ipc.proto.MasteryDefinition>(
                service, METHODID_GENERATE_MASTERY)))
        .addMethod(
          getListMasteriesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.ListMasteriesRequest,
              ai.rever.boss.ipc.proto.ListMasteriesResponse>(
                service, METHODID_LIST_MASTERIES)))
        .addMethod(
          getDeleteMasteryMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.MasteryId,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_DELETE_MASTERY)))
        .build();
  }

  private static abstract class MasteryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    MasteryServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.Mastery.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("MasteryService");
    }
  }

  private static final class MasteryServiceFileDescriptorSupplier
      extends MasteryServiceBaseDescriptorSupplier {
    MasteryServiceFileDescriptorSupplier() {}
  }

  private static final class MasteryServiceMethodDescriptorSupplier
      extends MasteryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    MasteryServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (MasteryServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new MasteryServiceFileDescriptorSupplier())
              .addMethod(getCreateMasteryMethod())
              .addMethod(getExecuteMasteryMethod())
              .addMethod(getCancelMasteryMethod())
              .addMethod(getGetMasteryStatusMethod())
              .addMethod(getGenerateMasteryMethod())
              .addMethod(getListMasteriesMethod())
              .addMethod(getDeleteMasteryMethod())
              .build();
        }
      }
    }
    return result;
  }
}
