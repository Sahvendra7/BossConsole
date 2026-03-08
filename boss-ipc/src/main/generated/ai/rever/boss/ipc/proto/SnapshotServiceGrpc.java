package ai.rever.boss.ipc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * SnapshotService enables processes to checkpoint their state for rollback.
 * The orchestrator uses this to restore last-known-good state before restarting.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/snapshot.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class SnapshotServiceGrpc {

  private SnapshotServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.SnapshotService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.SaveSnapshotRequest,
      ai.rever.boss.ipc.proto.SaveSnapshotResponse> getSaveSnapshotMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SaveSnapshot",
      requestType = ai.rever.boss.ipc.proto.SaveSnapshotRequest.class,
      responseType = ai.rever.boss.ipc.proto.SaveSnapshotResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.SaveSnapshotRequest,
      ai.rever.boss.ipc.proto.SaveSnapshotResponse> getSaveSnapshotMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.SaveSnapshotRequest, ai.rever.boss.ipc.proto.SaveSnapshotResponse> getSaveSnapshotMethod;
    if ((getSaveSnapshotMethod = SnapshotServiceGrpc.getSaveSnapshotMethod) == null) {
      synchronized (SnapshotServiceGrpc.class) {
        if ((getSaveSnapshotMethod = SnapshotServiceGrpc.getSaveSnapshotMethod) == null) {
          SnapshotServiceGrpc.getSaveSnapshotMethod = getSaveSnapshotMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.SaveSnapshotRequest, ai.rever.boss.ipc.proto.SaveSnapshotResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SaveSnapshot"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.SaveSnapshotRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.SaveSnapshotResponse.getDefaultInstance()))
              .setSchemaDescriptor(new SnapshotServiceMethodDescriptorSupplier("SaveSnapshot"))
              .build();
        }
      }
    }
    return getSaveSnapshotMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.LoadSnapshotRequest,
      ai.rever.boss.ipc.proto.LoadSnapshotResponse> getLoadSnapshotMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "LoadSnapshot",
      requestType = ai.rever.boss.ipc.proto.LoadSnapshotRequest.class,
      responseType = ai.rever.boss.ipc.proto.LoadSnapshotResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.LoadSnapshotRequest,
      ai.rever.boss.ipc.proto.LoadSnapshotResponse> getLoadSnapshotMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.LoadSnapshotRequest, ai.rever.boss.ipc.proto.LoadSnapshotResponse> getLoadSnapshotMethod;
    if ((getLoadSnapshotMethod = SnapshotServiceGrpc.getLoadSnapshotMethod) == null) {
      synchronized (SnapshotServiceGrpc.class) {
        if ((getLoadSnapshotMethod = SnapshotServiceGrpc.getLoadSnapshotMethod) == null) {
          SnapshotServiceGrpc.getLoadSnapshotMethod = getLoadSnapshotMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.LoadSnapshotRequest, ai.rever.boss.ipc.proto.LoadSnapshotResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "LoadSnapshot"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.LoadSnapshotRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.LoadSnapshotResponse.getDefaultInstance()))
              .setSchemaDescriptor(new SnapshotServiceMethodDescriptorSupplier("LoadSnapshot"))
              .build();
        }
      }
    }
    return getLoadSnapshotMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ListSnapshotsRequest,
      ai.rever.boss.ipc.proto.ListSnapshotsResponse> getListSnapshotsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListSnapshots",
      requestType = ai.rever.boss.ipc.proto.ListSnapshotsRequest.class,
      responseType = ai.rever.boss.ipc.proto.ListSnapshotsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ListSnapshotsRequest,
      ai.rever.boss.ipc.proto.ListSnapshotsResponse> getListSnapshotsMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.ListSnapshotsRequest, ai.rever.boss.ipc.proto.ListSnapshotsResponse> getListSnapshotsMethod;
    if ((getListSnapshotsMethod = SnapshotServiceGrpc.getListSnapshotsMethod) == null) {
      synchronized (SnapshotServiceGrpc.class) {
        if ((getListSnapshotsMethod = SnapshotServiceGrpc.getListSnapshotsMethod) == null) {
          SnapshotServiceGrpc.getListSnapshotsMethod = getListSnapshotsMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.ListSnapshotsRequest, ai.rever.boss.ipc.proto.ListSnapshotsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListSnapshots"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ListSnapshotsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ListSnapshotsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new SnapshotServiceMethodDescriptorSupplier("ListSnapshots"))
              .build();
        }
      }
    }
    return getListSnapshotsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.DeleteSnapshotRequest,
      ai.rever.boss.ipc.proto.Empty> getDeleteSnapshotMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DeleteSnapshot",
      requestType = ai.rever.boss.ipc.proto.DeleteSnapshotRequest.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.DeleteSnapshotRequest,
      ai.rever.boss.ipc.proto.Empty> getDeleteSnapshotMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.DeleteSnapshotRequest, ai.rever.boss.ipc.proto.Empty> getDeleteSnapshotMethod;
    if ((getDeleteSnapshotMethod = SnapshotServiceGrpc.getDeleteSnapshotMethod) == null) {
      synchronized (SnapshotServiceGrpc.class) {
        if ((getDeleteSnapshotMethod = SnapshotServiceGrpc.getDeleteSnapshotMethod) == null) {
          SnapshotServiceGrpc.getDeleteSnapshotMethod = getDeleteSnapshotMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.DeleteSnapshotRequest, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DeleteSnapshot"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.DeleteSnapshotRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new SnapshotServiceMethodDescriptorSupplier("DeleteSnapshot"))
              .build();
        }
      }
    }
    return getDeleteSnapshotMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static SnapshotServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SnapshotServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SnapshotServiceStub>() {
        @java.lang.Override
        public SnapshotServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SnapshotServiceStub(channel, callOptions);
        }
      };
    return SnapshotServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static SnapshotServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SnapshotServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SnapshotServiceBlockingV2Stub>() {
        @java.lang.Override
        public SnapshotServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SnapshotServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return SnapshotServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static SnapshotServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SnapshotServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SnapshotServiceBlockingStub>() {
        @java.lang.Override
        public SnapshotServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SnapshotServiceBlockingStub(channel, callOptions);
        }
      };
    return SnapshotServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static SnapshotServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SnapshotServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SnapshotServiceFutureStub>() {
        @java.lang.Override
        public SnapshotServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SnapshotServiceFutureStub(channel, callOptions);
        }
      };
    return SnapshotServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * SnapshotService enables processes to checkpoint their state for rollback.
   * The orchestrator uses this to restore last-known-good state before restarting.
   * </pre>
   */
  public interface AsyncService {

    /**
     */
    default void saveSnapshot(ai.rever.boss.ipc.proto.SaveSnapshotRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.SaveSnapshotResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSaveSnapshotMethod(), responseObserver);
    }

    /**
     */
    default void loadSnapshot(ai.rever.boss.ipc.proto.LoadSnapshotRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.LoadSnapshotResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getLoadSnapshotMethod(), responseObserver);
    }

    /**
     */
    default void listSnapshots(ai.rever.boss.ipc.proto.ListSnapshotsRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListSnapshotsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListSnapshotsMethod(), responseObserver);
    }

    /**
     */
    default void deleteSnapshot(ai.rever.boss.ipc.proto.DeleteSnapshotRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDeleteSnapshotMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service SnapshotService.
   * <pre>
   * SnapshotService enables processes to checkpoint their state for rollback.
   * The orchestrator uses this to restore last-known-good state before restarting.
   * </pre>
   */
  public static abstract class SnapshotServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return SnapshotServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service SnapshotService.
   * <pre>
   * SnapshotService enables processes to checkpoint their state for rollback.
   * The orchestrator uses this to restore last-known-good state before restarting.
   * </pre>
   */
  public static final class SnapshotServiceStub
      extends io.grpc.stub.AbstractAsyncStub<SnapshotServiceStub> {
    private SnapshotServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SnapshotServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SnapshotServiceStub(channel, callOptions);
    }

    /**
     */
    public void saveSnapshot(ai.rever.boss.ipc.proto.SaveSnapshotRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.SaveSnapshotResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSaveSnapshotMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void loadSnapshot(ai.rever.boss.ipc.proto.LoadSnapshotRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.LoadSnapshotResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getLoadSnapshotMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void listSnapshots(ai.rever.boss.ipc.proto.ListSnapshotsRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListSnapshotsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListSnapshotsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void deleteSnapshot(ai.rever.boss.ipc.proto.DeleteSnapshotRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDeleteSnapshotMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service SnapshotService.
   * <pre>
   * SnapshotService enables processes to checkpoint their state for rollback.
   * The orchestrator uses this to restore last-known-good state before restarting.
   * </pre>
   */
  public static final class SnapshotServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<SnapshotServiceBlockingV2Stub> {
    private SnapshotServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SnapshotServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SnapshotServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.SaveSnapshotResponse saveSnapshot(ai.rever.boss.ipc.proto.SaveSnapshotRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSaveSnapshotMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.LoadSnapshotResponse loadSnapshot(ai.rever.boss.ipc.proto.LoadSnapshotRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getLoadSnapshotMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.ListSnapshotsResponse listSnapshots(ai.rever.boss.ipc.proto.ListSnapshotsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListSnapshotsMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty deleteSnapshot(ai.rever.boss.ipc.proto.DeleteSnapshotRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeleteSnapshotMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service SnapshotService.
   * <pre>
   * SnapshotService enables processes to checkpoint their state for rollback.
   * The orchestrator uses this to restore last-known-good state before restarting.
   * </pre>
   */
  public static final class SnapshotServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<SnapshotServiceBlockingStub> {
    private SnapshotServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SnapshotServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SnapshotServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.SaveSnapshotResponse saveSnapshot(ai.rever.boss.ipc.proto.SaveSnapshotRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSaveSnapshotMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.LoadSnapshotResponse loadSnapshot(ai.rever.boss.ipc.proto.LoadSnapshotRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getLoadSnapshotMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.ListSnapshotsResponse listSnapshots(ai.rever.boss.ipc.proto.ListSnapshotsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListSnapshotsMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty deleteSnapshot(ai.rever.boss.ipc.proto.DeleteSnapshotRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeleteSnapshotMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service SnapshotService.
   * <pre>
   * SnapshotService enables processes to checkpoint their state for rollback.
   * The orchestrator uses this to restore last-known-good state before restarting.
   * </pre>
   */
  public static final class SnapshotServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<SnapshotServiceFutureStub> {
    private SnapshotServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SnapshotServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SnapshotServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.SaveSnapshotResponse> saveSnapshot(
        ai.rever.boss.ipc.proto.SaveSnapshotRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSaveSnapshotMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.LoadSnapshotResponse> loadSnapshot(
        ai.rever.boss.ipc.proto.LoadSnapshotRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getLoadSnapshotMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.ListSnapshotsResponse> listSnapshots(
        ai.rever.boss.ipc.proto.ListSnapshotsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListSnapshotsMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> deleteSnapshot(
        ai.rever.boss.ipc.proto.DeleteSnapshotRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDeleteSnapshotMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SAVE_SNAPSHOT = 0;
  private static final int METHODID_LOAD_SNAPSHOT = 1;
  private static final int METHODID_LIST_SNAPSHOTS = 2;
  private static final int METHODID_DELETE_SNAPSHOT = 3;

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
        case METHODID_SAVE_SNAPSHOT:
          serviceImpl.saveSnapshot((ai.rever.boss.ipc.proto.SaveSnapshotRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.SaveSnapshotResponse>) responseObserver);
          break;
        case METHODID_LOAD_SNAPSHOT:
          serviceImpl.loadSnapshot((ai.rever.boss.ipc.proto.LoadSnapshotRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.LoadSnapshotResponse>) responseObserver);
          break;
        case METHODID_LIST_SNAPSHOTS:
          serviceImpl.listSnapshots((ai.rever.boss.ipc.proto.ListSnapshotsRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListSnapshotsResponse>) responseObserver);
          break;
        case METHODID_DELETE_SNAPSHOT:
          serviceImpl.deleteSnapshot((ai.rever.boss.ipc.proto.DeleteSnapshotRequest) request,
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
          getSaveSnapshotMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.SaveSnapshotRequest,
              ai.rever.boss.ipc.proto.SaveSnapshotResponse>(
                service, METHODID_SAVE_SNAPSHOT)))
        .addMethod(
          getLoadSnapshotMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.LoadSnapshotRequest,
              ai.rever.boss.ipc.proto.LoadSnapshotResponse>(
                service, METHODID_LOAD_SNAPSHOT)))
        .addMethod(
          getListSnapshotsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.ListSnapshotsRequest,
              ai.rever.boss.ipc.proto.ListSnapshotsResponse>(
                service, METHODID_LIST_SNAPSHOTS)))
        .addMethod(
          getDeleteSnapshotMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.DeleteSnapshotRequest,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_DELETE_SNAPSHOT)))
        .build();
  }

  private static abstract class SnapshotServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    SnapshotServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.Snapshot.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("SnapshotService");
    }
  }

  private static final class SnapshotServiceFileDescriptorSupplier
      extends SnapshotServiceBaseDescriptorSupplier {
    SnapshotServiceFileDescriptorSupplier() {}
  }

  private static final class SnapshotServiceMethodDescriptorSupplier
      extends SnapshotServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    SnapshotServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (SnapshotServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new SnapshotServiceFileDescriptorSupplier())
              .addMethod(getSaveSnapshotMethod())
              .addMethod(getLoadSnapshotMethod())
              .addMethod(getListSnapshotsMethod())
              .addMethod(getDeleteSnapshotMethod())
              .build();
        }
      }
    }
    return result;
  }
}
