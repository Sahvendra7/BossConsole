package ai.rever.boss.ipc.proto.services;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * SettingsService provides persistent key-value settings storage across processes.
 * Out-of-process plugins use this to read, write, and watch application settings
 * without direct access to the host's storage layer.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/services/settings.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class SettingsServiceGrpc {

  private SettingsServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.services.SettingsService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetSettingRequest,
      ai.rever.boss.ipc.proto.services.SettingValue> getGetSettingMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetSetting",
      requestType = ai.rever.boss.ipc.proto.services.GetSettingRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.SettingValue.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetSettingRequest,
      ai.rever.boss.ipc.proto.services.SettingValue> getGetSettingMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetSettingRequest, ai.rever.boss.ipc.proto.services.SettingValue> getGetSettingMethod;
    if ((getGetSettingMethod = SettingsServiceGrpc.getGetSettingMethod) == null) {
      synchronized (SettingsServiceGrpc.class) {
        if ((getGetSettingMethod = SettingsServiceGrpc.getGetSettingMethod) == null) {
          SettingsServiceGrpc.getGetSettingMethod = getGetSettingMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.GetSettingRequest, ai.rever.boss.ipc.proto.services.SettingValue>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetSetting"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.GetSettingRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.SettingValue.getDefaultInstance()))
              .setSchemaDescriptor(new SettingsServiceMethodDescriptorSupplier("GetSetting"))
              .build();
        }
      }
    }
    return getGetSettingMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SetSettingRequest,
      ai.rever.boss.ipc.proto.services.SettingValue> getSetSettingMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SetSetting",
      requestType = ai.rever.boss.ipc.proto.services.SetSettingRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.SettingValue.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SetSettingRequest,
      ai.rever.boss.ipc.proto.services.SettingValue> getSetSettingMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SetSettingRequest, ai.rever.boss.ipc.proto.services.SettingValue> getSetSettingMethod;
    if ((getSetSettingMethod = SettingsServiceGrpc.getSetSettingMethod) == null) {
      synchronized (SettingsServiceGrpc.class) {
        if ((getSetSettingMethod = SettingsServiceGrpc.getSetSettingMethod) == null) {
          SettingsServiceGrpc.getSetSettingMethod = getSetSettingMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.SetSettingRequest, ai.rever.boss.ipc.proto.services.SettingValue>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SetSetting"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.SetSettingRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.SettingValue.getDefaultInstance()))
              .setSchemaDescriptor(new SettingsServiceMethodDescriptorSupplier("SetSetting"))
              .build();
        }
      }
    }
    return getSetSettingMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetSettingRequest,
      ai.rever.boss.ipc.proto.services.SettingValue> getWatchSettingMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WatchSetting",
      requestType = ai.rever.boss.ipc.proto.services.GetSettingRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.SettingValue.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetSettingRequest,
      ai.rever.boss.ipc.proto.services.SettingValue> getWatchSettingMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetSettingRequest, ai.rever.boss.ipc.proto.services.SettingValue> getWatchSettingMethod;
    if ((getWatchSettingMethod = SettingsServiceGrpc.getWatchSettingMethod) == null) {
      synchronized (SettingsServiceGrpc.class) {
        if ((getWatchSettingMethod = SettingsServiceGrpc.getWatchSettingMethod) == null) {
          SettingsServiceGrpc.getWatchSettingMethod = getWatchSettingMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.GetSettingRequest, ai.rever.boss.ipc.proto.services.SettingValue>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WatchSetting"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.GetSettingRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.SettingValue.getDefaultInstance()))
              .setSchemaDescriptor(new SettingsServiceMethodDescriptorSupplier("WatchSetting"))
              .build();
        }
      }
    }
    return getWatchSettingMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ListSettingsRequest,
      ai.rever.boss.ipc.proto.services.SettingsListResponse> getListSettingsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListSettings",
      requestType = ai.rever.boss.ipc.proto.services.ListSettingsRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.SettingsListResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ListSettingsRequest,
      ai.rever.boss.ipc.proto.services.SettingsListResponse> getListSettingsMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ListSettingsRequest, ai.rever.boss.ipc.proto.services.SettingsListResponse> getListSettingsMethod;
    if ((getListSettingsMethod = SettingsServiceGrpc.getListSettingsMethod) == null) {
      synchronized (SettingsServiceGrpc.class) {
        if ((getListSettingsMethod = SettingsServiceGrpc.getListSettingsMethod) == null) {
          SettingsServiceGrpc.getListSettingsMethod = getListSettingsMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.ListSettingsRequest, ai.rever.boss.ipc.proto.services.SettingsListResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListSettings"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.ListSettingsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.SettingsListResponse.getDefaultInstance()))
              .setSchemaDescriptor(new SettingsServiceMethodDescriptorSupplier("ListSettings"))
              .build();
        }
      }
    }
    return getListSettingsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static SettingsServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SettingsServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SettingsServiceStub>() {
        @java.lang.Override
        public SettingsServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SettingsServiceStub(channel, callOptions);
        }
      };
    return SettingsServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static SettingsServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SettingsServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SettingsServiceBlockingV2Stub>() {
        @java.lang.Override
        public SettingsServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SettingsServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return SettingsServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static SettingsServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SettingsServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SettingsServiceBlockingStub>() {
        @java.lang.Override
        public SettingsServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SettingsServiceBlockingStub(channel, callOptions);
        }
      };
    return SettingsServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static SettingsServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SettingsServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SettingsServiceFutureStub>() {
        @java.lang.Override
        public SettingsServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SettingsServiceFutureStub(channel, callOptions);
        }
      };
    return SettingsServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * SettingsService provides persistent key-value settings storage across processes.
   * Out-of-process plugins use this to read, write, and watch application settings
   * without direct access to the host's storage layer.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Get a single setting by key
     * </pre>
     */
    default void getSetting(ai.rever.boss.ipc.proto.services.GetSettingRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingValue> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetSettingMethod(), responseObserver);
    }

    /**
     * <pre>
     * Set (create or update) a setting
     * </pre>
     */
    default void setSetting(ai.rever.boss.ipc.proto.services.SetSettingRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingValue> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSetSettingMethod(), responseObserver);
    }

    /**
     * <pre>
     * Stream setting changes for a specific key
     * </pre>
     */
    default void watchSetting(ai.rever.boss.ipc.proto.services.GetSettingRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingValue> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getWatchSettingMethod(), responseObserver);
    }

    /**
     * <pre>
     * List all settings, optionally filtered by namespace prefix
     * </pre>
     */
    default void listSettings(ai.rever.boss.ipc.proto.services.ListSettingsRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingsListResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListSettingsMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service SettingsService.
   * <pre>
   * SettingsService provides persistent key-value settings storage across processes.
   * Out-of-process plugins use this to read, write, and watch application settings
   * without direct access to the host's storage layer.
   * </pre>
   */
  public static abstract class SettingsServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return SettingsServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service SettingsService.
   * <pre>
   * SettingsService provides persistent key-value settings storage across processes.
   * Out-of-process plugins use this to read, write, and watch application settings
   * without direct access to the host's storage layer.
   * </pre>
   */
  public static final class SettingsServiceStub
      extends io.grpc.stub.AbstractAsyncStub<SettingsServiceStub> {
    private SettingsServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SettingsServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SettingsServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get a single setting by key
     * </pre>
     */
    public void getSetting(ai.rever.boss.ipc.proto.services.GetSettingRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingValue> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetSettingMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Set (create or update) a setting
     * </pre>
     */
    public void setSetting(ai.rever.boss.ipc.proto.services.SetSettingRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingValue> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSetSettingMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Stream setting changes for a specific key
     * </pre>
     */
    public void watchSetting(ai.rever.boss.ipc.proto.services.GetSettingRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingValue> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getWatchSettingMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * List all settings, optionally filtered by namespace prefix
     * </pre>
     */
    public void listSettings(ai.rever.boss.ipc.proto.services.ListSettingsRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingsListResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListSettingsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service SettingsService.
   * <pre>
   * SettingsService provides persistent key-value settings storage across processes.
   * Out-of-process plugins use this to read, write, and watch application settings
   * without direct access to the host's storage layer.
   * </pre>
   */
  public static final class SettingsServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<SettingsServiceBlockingV2Stub> {
    private SettingsServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SettingsServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SettingsServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Get a single setting by key
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.SettingValue getSetting(ai.rever.boss.ipc.proto.services.GetSettingRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetSettingMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Set (create or update) a setting
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.SettingValue setSetting(ai.rever.boss.ipc.proto.services.SetSettingRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSetSettingMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Stream setting changes for a specific key
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.services.SettingValue>
        watchSetting(ai.rever.boss.ipc.proto.services.GetSettingRequest request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getWatchSettingMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * List all settings, optionally filtered by namespace prefix
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.SettingsListResponse listSettings(ai.rever.boss.ipc.proto.services.ListSettingsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListSettingsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service SettingsService.
   * <pre>
   * SettingsService provides persistent key-value settings storage across processes.
   * Out-of-process plugins use this to read, write, and watch application settings
   * without direct access to the host's storage layer.
   * </pre>
   */
  public static final class SettingsServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<SettingsServiceBlockingStub> {
    private SettingsServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SettingsServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SettingsServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get a single setting by key
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.SettingValue getSetting(ai.rever.boss.ipc.proto.services.GetSettingRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetSettingMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Set (create or update) a setting
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.SettingValue setSetting(ai.rever.boss.ipc.proto.services.SetSettingRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSetSettingMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Stream setting changes for a specific key
     * </pre>
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.services.SettingValue> watchSetting(
        ai.rever.boss.ipc.proto.services.GetSettingRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getWatchSettingMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * List all settings, optionally filtered by namespace prefix
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.SettingsListResponse listSettings(ai.rever.boss.ipc.proto.services.ListSettingsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListSettingsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service SettingsService.
   * <pre>
   * SettingsService provides persistent key-value settings storage across processes.
   * Out-of-process plugins use this to read, write, and watch application settings
   * without direct access to the host's storage layer.
   * </pre>
   */
  public static final class SettingsServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<SettingsServiceFutureStub> {
    private SettingsServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SettingsServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SettingsServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get a single setting by key
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.SettingValue> getSetting(
        ai.rever.boss.ipc.proto.services.GetSettingRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetSettingMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Set (create or update) a setting
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.SettingValue> setSetting(
        ai.rever.boss.ipc.proto.services.SetSettingRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSetSettingMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * List all settings, optionally filtered by namespace prefix
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.SettingsListResponse> listSettings(
        ai.rever.boss.ipc.proto.services.ListSettingsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListSettingsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_SETTING = 0;
  private static final int METHODID_SET_SETTING = 1;
  private static final int METHODID_WATCH_SETTING = 2;
  private static final int METHODID_LIST_SETTINGS = 3;

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
        case METHODID_GET_SETTING:
          serviceImpl.getSetting((ai.rever.boss.ipc.proto.services.GetSettingRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingValue>) responseObserver);
          break;
        case METHODID_SET_SETTING:
          serviceImpl.setSetting((ai.rever.boss.ipc.proto.services.SetSettingRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingValue>) responseObserver);
          break;
        case METHODID_WATCH_SETTING:
          serviceImpl.watchSetting((ai.rever.boss.ipc.proto.services.GetSettingRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingValue>) responseObserver);
          break;
        case METHODID_LIST_SETTINGS:
          serviceImpl.listSettings((ai.rever.boss.ipc.proto.services.ListSettingsRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SettingsListResponse>) responseObserver);
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
          getGetSettingMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.GetSettingRequest,
              ai.rever.boss.ipc.proto.services.SettingValue>(
                service, METHODID_GET_SETTING)))
        .addMethod(
          getSetSettingMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.SetSettingRequest,
              ai.rever.boss.ipc.proto.services.SettingValue>(
                service, METHODID_SET_SETTING)))
        .addMethod(
          getWatchSettingMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.GetSettingRequest,
              ai.rever.boss.ipc.proto.services.SettingValue>(
                service, METHODID_WATCH_SETTING)))
        .addMethod(
          getListSettingsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.ListSettingsRequest,
              ai.rever.boss.ipc.proto.services.SettingsListResponse>(
                service, METHODID_LIST_SETTINGS)))
        .build();
  }

  private static abstract class SettingsServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    SettingsServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.services.Settings.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("SettingsService");
    }
  }

  private static final class SettingsServiceFileDescriptorSupplier
      extends SettingsServiceBaseDescriptorSupplier {
    SettingsServiceFileDescriptorSupplier() {}
  }

  private static final class SettingsServiceMethodDescriptorSupplier
      extends SettingsServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    SettingsServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (SettingsServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new SettingsServiceFileDescriptorSupplier())
              .addMethod(getGetSettingMethod())
              .addMethod(getSetSettingMethod())
              .addMethod(getWatchSettingMethod())
              .addMethod(getListSettingsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
