package ai.rever.boss.ipc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * EventBusService provides cross-process pub/sub event routing.
 * Replaces in-process SharedFlow event buses when running in kernel mode.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/event_bus.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class EventBusServiceGrpc {

  private EventBusServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.EventBusService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.SubscribeRequest,
      ai.rever.boss.ipc.proto.EventEnvelope> getSubscribeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Subscribe",
      requestType = ai.rever.boss.ipc.proto.SubscribeRequest.class,
      responseType = ai.rever.boss.ipc.proto.EventEnvelope.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.SubscribeRequest,
      ai.rever.boss.ipc.proto.EventEnvelope> getSubscribeMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.SubscribeRequest, ai.rever.boss.ipc.proto.EventEnvelope> getSubscribeMethod;
    if ((getSubscribeMethod = EventBusServiceGrpc.getSubscribeMethod) == null) {
      synchronized (EventBusServiceGrpc.class) {
        if ((getSubscribeMethod = EventBusServiceGrpc.getSubscribeMethod) == null) {
          EventBusServiceGrpc.getSubscribeMethod = getSubscribeMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.SubscribeRequest, ai.rever.boss.ipc.proto.EventEnvelope>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Subscribe"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.SubscribeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.EventEnvelope.getDefaultInstance()))
              .setSchemaDescriptor(new EventBusServiceMethodDescriptorSupplier("Subscribe"))
              .build();
        }
      }
    }
    return getSubscribeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.EventEnvelope,
      ai.rever.boss.ipc.proto.PublishResponse> getPublishMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Publish",
      requestType = ai.rever.boss.ipc.proto.EventEnvelope.class,
      responseType = ai.rever.boss.ipc.proto.PublishResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.EventEnvelope,
      ai.rever.boss.ipc.proto.PublishResponse> getPublishMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.EventEnvelope, ai.rever.boss.ipc.proto.PublishResponse> getPublishMethod;
    if ((getPublishMethod = EventBusServiceGrpc.getPublishMethod) == null) {
      synchronized (EventBusServiceGrpc.class) {
        if ((getPublishMethod = EventBusServiceGrpc.getPublishMethod) == null) {
          EventBusServiceGrpc.getPublishMethod = getPublishMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.EventEnvelope, ai.rever.boss.ipc.proto.PublishResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Publish"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.EventEnvelope.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.PublishResponse.getDefaultInstance()))
              .setSchemaDescriptor(new EventBusServiceMethodDescriptorSupplier("Publish"))
              .build();
        }
      }
    }
    return getPublishMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.PublishBatchRequest,
      ai.rever.boss.ipc.proto.PublishResponse> getPublishBatchMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "PublishBatch",
      requestType = ai.rever.boss.ipc.proto.PublishBatchRequest.class,
      responseType = ai.rever.boss.ipc.proto.PublishResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.PublishBatchRequest,
      ai.rever.boss.ipc.proto.PublishResponse> getPublishBatchMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.PublishBatchRequest, ai.rever.boss.ipc.proto.PublishResponse> getPublishBatchMethod;
    if ((getPublishBatchMethod = EventBusServiceGrpc.getPublishBatchMethod) == null) {
      synchronized (EventBusServiceGrpc.class) {
        if ((getPublishBatchMethod = EventBusServiceGrpc.getPublishBatchMethod) == null) {
          EventBusServiceGrpc.getPublishBatchMethod = getPublishBatchMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.PublishBatchRequest, ai.rever.boss.ipc.proto.PublishResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "PublishBatch"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.PublishBatchRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.PublishResponse.getDefaultInstance()))
              .setSchemaDescriptor(new EventBusServiceMethodDescriptorSupplier("PublishBatch"))
              .build();
        }
      }
    }
    return getPublishBatchMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static EventBusServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EventBusServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EventBusServiceStub>() {
        @java.lang.Override
        public EventBusServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EventBusServiceStub(channel, callOptions);
        }
      };
    return EventBusServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static EventBusServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EventBusServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EventBusServiceBlockingV2Stub>() {
        @java.lang.Override
        public EventBusServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EventBusServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return EventBusServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static EventBusServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EventBusServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EventBusServiceBlockingStub>() {
        @java.lang.Override
        public EventBusServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EventBusServiceBlockingStub(channel, callOptions);
        }
      };
    return EventBusServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static EventBusServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EventBusServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EventBusServiceFutureStub>() {
        @java.lang.Override
        public EventBusServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EventBusServiceFutureStub(channel, callOptions);
        }
      };
    return EventBusServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * EventBusService provides cross-process pub/sub event routing.
   * Replaces in-process SharedFlow event buses when running in kernel mode.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Subscribe to events of a specific type (or all events)
     * </pre>
     */
    default void subscribe(ai.rever.boss.ipc.proto.SubscribeRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.EventEnvelope> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubscribeMethod(), responseObserver);
    }

    /**
     * <pre>
     * Publish an event to all subscribers
     * </pre>
     */
    default void publish(ai.rever.boss.ipc.proto.EventEnvelope request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.PublishResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPublishMethod(), responseObserver);
    }

    /**
     * <pre>
     * Publish a batch of events atomically
     * </pre>
     */
    default void publishBatch(ai.rever.boss.ipc.proto.PublishBatchRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.PublishResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPublishBatchMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service EventBusService.
   * <pre>
   * EventBusService provides cross-process pub/sub event routing.
   * Replaces in-process SharedFlow event buses when running in kernel mode.
   * </pre>
   */
  public static abstract class EventBusServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return EventBusServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service EventBusService.
   * <pre>
   * EventBusService provides cross-process pub/sub event routing.
   * Replaces in-process SharedFlow event buses when running in kernel mode.
   * </pre>
   */
  public static final class EventBusServiceStub
      extends io.grpc.stub.AbstractAsyncStub<EventBusServiceStub> {
    private EventBusServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EventBusServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EventBusServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Subscribe to events of a specific type (or all events)
     * </pre>
     */
    public void subscribe(ai.rever.boss.ipc.proto.SubscribeRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.EventEnvelope> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getSubscribeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Publish an event to all subscribers
     * </pre>
     */
    public void publish(ai.rever.boss.ipc.proto.EventEnvelope request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.PublishResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Publish a batch of events atomically
     * </pre>
     */
    public void publishBatch(ai.rever.boss.ipc.proto.PublishBatchRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.PublishResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPublishBatchMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service EventBusService.
   * <pre>
   * EventBusService provides cross-process pub/sub event routing.
   * Replaces in-process SharedFlow event buses when running in kernel mode.
   * </pre>
   */
  public static final class EventBusServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<EventBusServiceBlockingV2Stub> {
    private EventBusServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EventBusServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EventBusServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Subscribe to events of a specific type (or all events)
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.EventEnvelope>
        subscribe(ai.rever.boss.ipc.proto.SubscribeRequest request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getSubscribeMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Publish an event to all subscribers
     * </pre>
     */
    public ai.rever.boss.ipc.proto.PublishResponse publish(ai.rever.boss.ipc.proto.EventEnvelope request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPublishMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Publish a batch of events atomically
     * </pre>
     */
    public ai.rever.boss.ipc.proto.PublishResponse publishBatch(ai.rever.boss.ipc.proto.PublishBatchRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPublishBatchMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service EventBusService.
   * <pre>
   * EventBusService provides cross-process pub/sub event routing.
   * Replaces in-process SharedFlow event buses when running in kernel mode.
   * </pre>
   */
  public static final class EventBusServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<EventBusServiceBlockingStub> {
    private EventBusServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EventBusServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EventBusServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Subscribe to events of a specific type (or all events)
     * </pre>
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.EventEnvelope> subscribe(
        ai.rever.boss.ipc.proto.SubscribeRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getSubscribeMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Publish an event to all subscribers
     * </pre>
     */
    public ai.rever.boss.ipc.proto.PublishResponse publish(ai.rever.boss.ipc.proto.EventEnvelope request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPublishMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Publish a batch of events atomically
     * </pre>
     */
    public ai.rever.boss.ipc.proto.PublishResponse publishBatch(ai.rever.boss.ipc.proto.PublishBatchRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPublishBatchMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service EventBusService.
   * <pre>
   * EventBusService provides cross-process pub/sub event routing.
   * Replaces in-process SharedFlow event buses when running in kernel mode.
   * </pre>
   */
  public static final class EventBusServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<EventBusServiceFutureStub> {
    private EventBusServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EventBusServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EventBusServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Publish an event to all subscribers
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.PublishResponse> publish(
        ai.rever.boss.ipc.proto.EventEnvelope request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Publish a batch of events atomically
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.PublishResponse> publishBatch(
        ai.rever.boss.ipc.proto.PublishBatchRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPublishBatchMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SUBSCRIBE = 0;
  private static final int METHODID_PUBLISH = 1;
  private static final int METHODID_PUBLISH_BATCH = 2;

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
        case METHODID_SUBSCRIBE:
          serviceImpl.subscribe((ai.rever.boss.ipc.proto.SubscribeRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.EventEnvelope>) responseObserver);
          break;
        case METHODID_PUBLISH:
          serviceImpl.publish((ai.rever.boss.ipc.proto.EventEnvelope) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.PublishResponse>) responseObserver);
          break;
        case METHODID_PUBLISH_BATCH:
          serviceImpl.publishBatch((ai.rever.boss.ipc.proto.PublishBatchRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.PublishResponse>) responseObserver);
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
          getSubscribeMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.SubscribeRequest,
              ai.rever.boss.ipc.proto.EventEnvelope>(
                service, METHODID_SUBSCRIBE)))
        .addMethod(
          getPublishMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.EventEnvelope,
              ai.rever.boss.ipc.proto.PublishResponse>(
                service, METHODID_PUBLISH)))
        .addMethod(
          getPublishBatchMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.PublishBatchRequest,
              ai.rever.boss.ipc.proto.PublishResponse>(
                service, METHODID_PUBLISH_BATCH)))
        .build();
  }

  private static abstract class EventBusServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    EventBusServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.EventBus.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("EventBusService");
    }
  }

  private static final class EventBusServiceFileDescriptorSupplier
      extends EventBusServiceBaseDescriptorSupplier {
    EventBusServiceFileDescriptorSupplier() {}
  }

  private static final class EventBusServiceMethodDescriptorSupplier
      extends EventBusServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    EventBusServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (EventBusServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new EventBusServiceFileDescriptorSupplier())
              .addMethod(getSubscribeMethod())
              .addMethod(getPublishMethod())
              .addMethod(getPublishBatchMethod())
              .build();
        }
      }
    }
    return result;
  }
}
