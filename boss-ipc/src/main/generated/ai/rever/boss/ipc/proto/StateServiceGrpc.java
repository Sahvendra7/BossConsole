package ai.rever.boss.ipc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * StateService provides cross-process reactive state sharing.
 * Replaces in-process StateFlow singletons when running in kernel mode.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/state.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class StateServiceGrpc {

  private StateServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.StateService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.StateKey,
      ai.rever.boss.ipc.proto.StateValue> getGetStateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetState",
      requestType = ai.rever.boss.ipc.proto.StateKey.class,
      responseType = ai.rever.boss.ipc.proto.StateValue.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.StateKey,
      ai.rever.boss.ipc.proto.StateValue> getGetStateMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.StateKey, ai.rever.boss.ipc.proto.StateValue> getGetStateMethod;
    if ((getGetStateMethod = StateServiceGrpc.getGetStateMethod) == null) {
      synchronized (StateServiceGrpc.class) {
        if ((getGetStateMethod = StateServiceGrpc.getGetStateMethod) == null) {
          StateServiceGrpc.getGetStateMethod = getGetStateMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.StateKey, ai.rever.boss.ipc.proto.StateValue>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetState"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.StateKey.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.StateValue.getDefaultInstance()))
              .setSchemaDescriptor(new StateServiceMethodDescriptorSupplier("GetState"))
              .build();
        }
      }
    }
    return getGetStateMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.StateKey,
      ai.rever.boss.ipc.proto.StateValue> getWatchStateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WatchState",
      requestType = ai.rever.boss.ipc.proto.StateKey.class,
      responseType = ai.rever.boss.ipc.proto.StateValue.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.StateKey,
      ai.rever.boss.ipc.proto.StateValue> getWatchStateMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.StateKey, ai.rever.boss.ipc.proto.StateValue> getWatchStateMethod;
    if ((getWatchStateMethod = StateServiceGrpc.getWatchStateMethod) == null) {
      synchronized (StateServiceGrpc.class) {
        if ((getWatchStateMethod = StateServiceGrpc.getWatchStateMethod) == null) {
          StateServiceGrpc.getWatchStateMethod = getWatchStateMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.StateKey, ai.rever.boss.ipc.proto.StateValue>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WatchState"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.StateKey.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.StateValue.getDefaultInstance()))
              .setSchemaDescriptor(new StateServiceMethodDescriptorSupplier("WatchState"))
              .build();
        }
      }
    }
    return getWatchStateMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.StateUpdate,
      ai.rever.boss.ipc.proto.StateValue> getSetStateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SetState",
      requestType = ai.rever.boss.ipc.proto.StateUpdate.class,
      responseType = ai.rever.boss.ipc.proto.StateValue.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.StateUpdate,
      ai.rever.boss.ipc.proto.StateValue> getSetStateMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.StateUpdate, ai.rever.boss.ipc.proto.StateValue> getSetStateMethod;
    if ((getSetStateMethod = StateServiceGrpc.getSetStateMethod) == null) {
      synchronized (StateServiceGrpc.class) {
        if ((getSetStateMethod = StateServiceGrpc.getSetStateMethod) == null) {
          StateServiceGrpc.getSetStateMethod = getSetStateMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.StateUpdate, ai.rever.boss.ipc.proto.StateValue>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SetState"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.StateUpdate.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.StateValue.getDefaultInstance()))
              .setSchemaDescriptor(new StateServiceMethodDescriptorSupplier("SetState"))
              .build();
        }
      }
    }
    return getSetStateMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.StateKeyList> getListStateKeysMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListStateKeys",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.StateKeyList.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.StateKeyList> getListStateKeysMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.StateKeyList> getListStateKeysMethod;
    if ((getListStateKeysMethod = StateServiceGrpc.getListStateKeysMethod) == null) {
      synchronized (StateServiceGrpc.class) {
        if ((getListStateKeysMethod = StateServiceGrpc.getListStateKeysMethod) == null) {
          StateServiceGrpc.getListStateKeysMethod = getListStateKeysMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.StateKeyList>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListStateKeys"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.StateKeyList.getDefaultInstance()))
              .setSchemaDescriptor(new StateServiceMethodDescriptorSupplier("ListStateKeys"))
              .build();
        }
      }
    }
    return getListStateKeysMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static StateServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<StateServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<StateServiceStub>() {
        @java.lang.Override
        public StateServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new StateServiceStub(channel, callOptions);
        }
      };
    return StateServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static StateServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<StateServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<StateServiceBlockingV2Stub>() {
        @java.lang.Override
        public StateServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new StateServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return StateServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static StateServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<StateServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<StateServiceBlockingStub>() {
        @java.lang.Override
        public StateServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new StateServiceBlockingStub(channel, callOptions);
        }
      };
    return StateServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static StateServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<StateServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<StateServiceFutureStub>() {
        @java.lang.Override
        public StateServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new StateServiceFutureStub(channel, callOptions);
        }
      };
    return StateServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * StateService provides cross-process reactive state sharing.
   * Replaces in-process StateFlow singletons when running in kernel mode.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Get the current value of a state key
     * </pre>
     */
    default void getState(ai.rever.boss.ipc.proto.StateKey request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateValue> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetStateMethod(), responseObserver);
    }

    /**
     * <pre>
     * Watch a state key for changes (like StateFlow.collect across processes)
     * </pre>
     */
    default void watchState(ai.rever.boss.ipc.proto.StateKey request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateValue> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getWatchStateMethod(), responseObserver);
    }

    /**
     * <pre>
     * Set/update a state value
     * </pre>
     */
    default void setState(ai.rever.boss.ipc.proto.StateUpdate request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateValue> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSetStateMethod(), responseObserver);
    }

    /**
     * <pre>
     * List all available state keys
     * </pre>
     */
    default void listStateKeys(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateKeyList> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListStateKeysMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service StateService.
   * <pre>
   * StateService provides cross-process reactive state sharing.
   * Replaces in-process StateFlow singletons when running in kernel mode.
   * </pre>
   */
  public static abstract class StateServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return StateServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service StateService.
   * <pre>
   * StateService provides cross-process reactive state sharing.
   * Replaces in-process StateFlow singletons when running in kernel mode.
   * </pre>
   */
  public static final class StateServiceStub
      extends io.grpc.stub.AbstractAsyncStub<StateServiceStub> {
    private StateServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected StateServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new StateServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get the current value of a state key
     * </pre>
     */
    public void getState(ai.rever.boss.ipc.proto.StateKey request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateValue> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetStateMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Watch a state key for changes (like StateFlow.collect across processes)
     * </pre>
     */
    public void watchState(ai.rever.boss.ipc.proto.StateKey request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateValue> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getWatchStateMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Set/update a state value
     * </pre>
     */
    public void setState(ai.rever.boss.ipc.proto.StateUpdate request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateValue> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSetStateMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * List all available state keys
     * </pre>
     */
    public void listStateKeys(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateKeyList> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListStateKeysMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service StateService.
   * <pre>
   * StateService provides cross-process reactive state sharing.
   * Replaces in-process StateFlow singletons when running in kernel mode.
   * </pre>
   */
  public static final class StateServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<StateServiceBlockingV2Stub> {
    private StateServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected StateServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new StateServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Get the current value of a state key
     * </pre>
     */
    public ai.rever.boss.ipc.proto.StateValue getState(ai.rever.boss.ipc.proto.StateKey request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetStateMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Watch a state key for changes (like StateFlow.collect across processes)
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.StateValue>
        watchState(ai.rever.boss.ipc.proto.StateKey request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getWatchStateMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Set/update a state value
     * </pre>
     */
    public ai.rever.boss.ipc.proto.StateValue setState(ai.rever.boss.ipc.proto.StateUpdate request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSetStateMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * List all available state keys
     * </pre>
     */
    public ai.rever.boss.ipc.proto.StateKeyList listStateKeys(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListStateKeysMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service StateService.
   * <pre>
   * StateService provides cross-process reactive state sharing.
   * Replaces in-process StateFlow singletons when running in kernel mode.
   * </pre>
   */
  public static final class StateServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<StateServiceBlockingStub> {
    private StateServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected StateServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new StateServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get the current value of a state key
     * </pre>
     */
    public ai.rever.boss.ipc.proto.StateValue getState(ai.rever.boss.ipc.proto.StateKey request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetStateMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Watch a state key for changes (like StateFlow.collect across processes)
     * </pre>
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.StateValue> watchState(
        ai.rever.boss.ipc.proto.StateKey request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getWatchStateMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Set/update a state value
     * </pre>
     */
    public ai.rever.boss.ipc.proto.StateValue setState(ai.rever.boss.ipc.proto.StateUpdate request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSetStateMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * List all available state keys
     * </pre>
     */
    public ai.rever.boss.ipc.proto.StateKeyList listStateKeys(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListStateKeysMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service StateService.
   * <pre>
   * StateService provides cross-process reactive state sharing.
   * Replaces in-process StateFlow singletons when running in kernel mode.
   * </pre>
   */
  public static final class StateServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<StateServiceFutureStub> {
    private StateServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected StateServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new StateServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get the current value of a state key
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.StateValue> getState(
        ai.rever.boss.ipc.proto.StateKey request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetStateMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Set/update a state value
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.StateValue> setState(
        ai.rever.boss.ipc.proto.StateUpdate request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSetStateMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * List all available state keys
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.StateKeyList> listStateKeys(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListStateKeysMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_STATE = 0;
  private static final int METHODID_WATCH_STATE = 1;
  private static final int METHODID_SET_STATE = 2;
  private static final int METHODID_LIST_STATE_KEYS = 3;

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
        case METHODID_GET_STATE:
          serviceImpl.getState((ai.rever.boss.ipc.proto.StateKey) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateValue>) responseObserver);
          break;
        case METHODID_WATCH_STATE:
          serviceImpl.watchState((ai.rever.boss.ipc.proto.StateKey) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateValue>) responseObserver);
          break;
        case METHODID_SET_STATE:
          serviceImpl.setState((ai.rever.boss.ipc.proto.StateUpdate) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateValue>) responseObserver);
          break;
        case METHODID_LIST_STATE_KEYS:
          serviceImpl.listStateKeys((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.StateKeyList>) responseObserver);
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
          getGetStateMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.StateKey,
              ai.rever.boss.ipc.proto.StateValue>(
                service, METHODID_GET_STATE)))
        .addMethod(
          getWatchStateMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.StateKey,
              ai.rever.boss.ipc.proto.StateValue>(
                service, METHODID_WATCH_STATE)))
        .addMethod(
          getSetStateMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.StateUpdate,
              ai.rever.boss.ipc.proto.StateValue>(
                service, METHODID_SET_STATE)))
        .addMethod(
          getListStateKeysMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.StateKeyList>(
                service, METHODID_LIST_STATE_KEYS)))
        .build();
  }

  private static abstract class StateServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    StateServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.State.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("StateService");
    }
  }

  private static final class StateServiceFileDescriptorSupplier
      extends StateServiceBaseDescriptorSupplier {
    StateServiceFileDescriptorSupplier() {}
  }

  private static final class StateServiceMethodDescriptorSupplier
      extends StateServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    StateServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (StateServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new StateServiceFileDescriptorSupplier())
              .addMethod(getGetStateMethod())
              .addMethod(getWatchStateMethod())
              .addMethod(getSetStateMethod())
              .addMethod(getListStateKeysMethod())
              .build();
        }
      }
    }
    return result;
  }
}
