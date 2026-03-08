package ai.rever.boss.ipc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * PluginUIService enables out-of-process plugins to render UI in the kernel's Compose window.
 * Plugins send declarative widget trees; the kernel renders them as Compose components.
 * User events flow back from kernel to plugin for handling.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/ui_protocol.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class PluginUIServiceGrpc {

  private PluginUIServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.PluginUIService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.UIRegistration,
      ai.rever.boss.ipc.proto.UIRegistrationResponse> getRegisterUIMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RegisterUI",
      requestType = ai.rever.boss.ipc.proto.UIRegistration.class,
      responseType = ai.rever.boss.ipc.proto.UIRegistrationResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.UIRegistration,
      ai.rever.boss.ipc.proto.UIRegistrationResponse> getRegisterUIMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.UIRegistration, ai.rever.boss.ipc.proto.UIRegistrationResponse> getRegisterUIMethod;
    if ((getRegisterUIMethod = PluginUIServiceGrpc.getRegisterUIMethod) == null) {
      synchronized (PluginUIServiceGrpc.class) {
        if ((getRegisterUIMethod = PluginUIServiceGrpc.getRegisterUIMethod) == null) {
          PluginUIServiceGrpc.getRegisterUIMethod = getRegisterUIMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.UIRegistration, ai.rever.boss.ipc.proto.UIRegistrationResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RegisterUI"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.UIRegistration.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.UIRegistrationResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PluginUIServiceMethodDescriptorSupplier("RegisterUI"))
              .build();
        }
      }
    }
    return getRegisterUIMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.WidgetUpdate,
      ai.rever.boss.ipc.proto.UIEvent> getStreamUIMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "StreamUI",
      requestType = ai.rever.boss.ipc.proto.WidgetUpdate.class,
      responseType = ai.rever.boss.ipc.proto.UIEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.WidgetUpdate,
      ai.rever.boss.ipc.proto.UIEvent> getStreamUIMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.WidgetUpdate, ai.rever.boss.ipc.proto.UIEvent> getStreamUIMethod;
    if ((getStreamUIMethod = PluginUIServiceGrpc.getStreamUIMethod) == null) {
      synchronized (PluginUIServiceGrpc.class) {
        if ((getStreamUIMethod = PluginUIServiceGrpc.getStreamUIMethod) == null) {
          PluginUIServiceGrpc.getStreamUIMethod = getStreamUIMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.WidgetUpdate, ai.rever.boss.ipc.proto.UIEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StreamUI"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.WidgetUpdate.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.UIEvent.getDefaultInstance()))
              .setSchemaDescriptor(new PluginUIServiceMethodDescriptorSupplier("StreamUI"))
              .build();
        }
      }
    }
    return getStreamUIMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.UIUnregistration,
      ai.rever.boss.ipc.proto.Empty> getUnregisterUIMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "UnregisterUI",
      requestType = ai.rever.boss.ipc.proto.UIUnregistration.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.UIUnregistration,
      ai.rever.boss.ipc.proto.Empty> getUnregisterUIMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.UIUnregistration, ai.rever.boss.ipc.proto.Empty> getUnregisterUIMethod;
    if ((getUnregisterUIMethod = PluginUIServiceGrpc.getUnregisterUIMethod) == null) {
      synchronized (PluginUIServiceGrpc.class) {
        if ((getUnregisterUIMethod = PluginUIServiceGrpc.getUnregisterUIMethod) == null) {
          PluginUIServiceGrpc.getUnregisterUIMethod = getUnregisterUIMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.UIUnregistration, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "UnregisterUI"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.UIUnregistration.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new PluginUIServiceMethodDescriptorSupplier("UnregisterUI"))
              .build();
        }
      }
    }
    return getUnregisterUIMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PluginUIServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PluginUIServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PluginUIServiceStub>() {
        @java.lang.Override
        public PluginUIServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PluginUIServiceStub(channel, callOptions);
        }
      };
    return PluginUIServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static PluginUIServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PluginUIServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PluginUIServiceBlockingV2Stub>() {
        @java.lang.Override
        public PluginUIServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PluginUIServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return PluginUIServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PluginUIServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PluginUIServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PluginUIServiceBlockingStub>() {
        @java.lang.Override
        public PluginUIServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PluginUIServiceBlockingStub(channel, callOptions);
        }
      };
    return PluginUIServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PluginUIServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PluginUIServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PluginUIServiceFutureStub>() {
        @java.lang.Override
        public PluginUIServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PluginUIServiceFutureStub(channel, callOptions);
        }
      };
    return PluginUIServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * PluginUIService enables out-of-process plugins to render UI in the kernel's Compose window.
   * Plugins send declarative widget trees; the kernel renders them as Compose components.
   * User events flow back from kernel to plugin for handling.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Register a UI surface (panel or tab) with the kernel
     * </pre>
     */
    default void registerUI(ai.rever.boss.ipc.proto.UIRegistration request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.UIRegistrationResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterUIMethod(), responseObserver);
    }

    /**
     * <pre>
     * Bidirectional stream: plugin sends widget updates, kernel sends user events
     * </pre>
     */
    default io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.WidgetUpdate> streamUI(
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.UIEvent> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getStreamUIMethod(), responseObserver);
    }

    /**
     * <pre>
     * Unregister a UI surface
     * </pre>
     */
    default void unregisterUI(ai.rever.boss.ipc.proto.UIUnregistration request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getUnregisterUIMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service PluginUIService.
   * <pre>
   * PluginUIService enables out-of-process plugins to render UI in the kernel's Compose window.
   * Plugins send declarative widget trees; the kernel renders them as Compose components.
   * User events flow back from kernel to plugin for handling.
   * </pre>
   */
  public static abstract class PluginUIServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return PluginUIServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service PluginUIService.
   * <pre>
   * PluginUIService enables out-of-process plugins to render UI in the kernel's Compose window.
   * Plugins send declarative widget trees; the kernel renders them as Compose components.
   * User events flow back from kernel to plugin for handling.
   * </pre>
   */
  public static final class PluginUIServiceStub
      extends io.grpc.stub.AbstractAsyncStub<PluginUIServiceStub> {
    private PluginUIServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PluginUIServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PluginUIServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Register a UI surface (panel or tab) with the kernel
     * </pre>
     */
    public void registerUI(ai.rever.boss.ipc.proto.UIRegistration request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.UIRegistrationResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterUIMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Bidirectional stream: plugin sends widget updates, kernel sends user events
     * </pre>
     */
    public io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.WidgetUpdate> streamUI(
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.UIEvent> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getStreamUIMethod(), getCallOptions()), responseObserver);
    }

    /**
     * <pre>
     * Unregister a UI surface
     * </pre>
     */
    public void unregisterUI(ai.rever.boss.ipc.proto.UIUnregistration request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getUnregisterUIMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service PluginUIService.
   * <pre>
   * PluginUIService enables out-of-process plugins to render UI in the kernel's Compose window.
   * Plugins send declarative widget trees; the kernel renders them as Compose components.
   * User events flow back from kernel to plugin for handling.
   * </pre>
   */
  public static final class PluginUIServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<PluginUIServiceBlockingV2Stub> {
    private PluginUIServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PluginUIServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PluginUIServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Register a UI surface (panel or tab) with the kernel
     * </pre>
     */
    public ai.rever.boss.ipc.proto.UIRegistrationResponse registerUI(ai.rever.boss.ipc.proto.UIRegistration request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterUIMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Bidirectional stream: plugin sends widget updates, kernel sends user events
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<ai.rever.boss.ipc.proto.WidgetUpdate, ai.rever.boss.ipc.proto.UIEvent>
        streamUI() {
      return io.grpc.stub.ClientCalls.blockingBidiStreamingCall(
          getChannel(), getStreamUIMethod(), getCallOptions());
    }

    /**
     * <pre>
     * Unregister a UI surface
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty unregisterUI(ai.rever.boss.ipc.proto.UIUnregistration request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getUnregisterUIMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service PluginUIService.
   * <pre>
   * PluginUIService enables out-of-process plugins to render UI in the kernel's Compose window.
   * Plugins send declarative widget trees; the kernel renders them as Compose components.
   * User events flow back from kernel to plugin for handling.
   * </pre>
   */
  public static final class PluginUIServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<PluginUIServiceBlockingStub> {
    private PluginUIServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PluginUIServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PluginUIServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Register a UI surface (panel or tab) with the kernel
     * </pre>
     */
    public ai.rever.boss.ipc.proto.UIRegistrationResponse registerUI(ai.rever.boss.ipc.proto.UIRegistration request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterUIMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Unregister a UI surface
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty unregisterUI(ai.rever.boss.ipc.proto.UIUnregistration request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getUnregisterUIMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service PluginUIService.
   * <pre>
   * PluginUIService enables out-of-process plugins to render UI in the kernel's Compose window.
   * Plugins send declarative widget trees; the kernel renders them as Compose components.
   * User events flow back from kernel to plugin for handling.
   * </pre>
   */
  public static final class PluginUIServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<PluginUIServiceFutureStub> {
    private PluginUIServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PluginUIServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PluginUIServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Register a UI surface (panel or tab) with the kernel
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.UIRegistrationResponse> registerUI(
        ai.rever.boss.ipc.proto.UIRegistration request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterUIMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Unregister a UI surface
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> unregisterUI(
        ai.rever.boss.ipc.proto.UIUnregistration request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getUnregisterUIMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER_UI = 0;
  private static final int METHODID_UNREGISTER_UI = 1;
  private static final int METHODID_STREAM_UI = 2;

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
        case METHODID_REGISTER_UI:
          serviceImpl.registerUI((ai.rever.boss.ipc.proto.UIRegistration) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.UIRegistrationResponse>) responseObserver);
          break;
        case METHODID_UNREGISTER_UI:
          serviceImpl.unregisterUI((ai.rever.boss.ipc.proto.UIUnregistration) request,
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
        case METHODID_STREAM_UI:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.streamUI(
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.UIEvent>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRegisterUIMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.UIRegistration,
              ai.rever.boss.ipc.proto.UIRegistrationResponse>(
                service, METHODID_REGISTER_UI)))
        .addMethod(
          getStreamUIMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.WidgetUpdate,
              ai.rever.boss.ipc.proto.UIEvent>(
                service, METHODID_STREAM_UI)))
        .addMethod(
          getUnregisterUIMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.UIUnregistration,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_UNREGISTER_UI)))
        .build();
  }

  private static abstract class PluginUIServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PluginUIServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.UiProtocol.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PluginUIService");
    }
  }

  private static final class PluginUIServiceFileDescriptorSupplier
      extends PluginUIServiceBaseDescriptorSupplier {
    PluginUIServiceFileDescriptorSupplier() {}
  }

  private static final class PluginUIServiceMethodDescriptorSupplier
      extends PluginUIServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    PluginUIServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (PluginUIServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PluginUIServiceFileDescriptorSupplier())
              .addMethod(getRegisterUIMethod())
              .addMethod(getStreamUIMethod())
              .addMethod(getUnregisterUIMethod())
              .build();
        }
      }
    }
    return result;
  }
}
