package ai.rever.boss.ipc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/capability.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class CapabilityServiceGrpc {

  private CapabilityServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.CapabilityService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.InvokeCapabilityRequest,
      ai.rever.boss.ipc.proto.InvokeCapabilityResponse> getInvokeCapabilityMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "InvokeCapability",
      requestType = ai.rever.boss.ipc.proto.InvokeCapabilityRequest.class,
      responseType = ai.rever.boss.ipc.proto.InvokeCapabilityResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.InvokeCapabilityRequest,
      ai.rever.boss.ipc.proto.InvokeCapabilityResponse> getInvokeCapabilityMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.InvokeCapabilityRequest, ai.rever.boss.ipc.proto.InvokeCapabilityResponse> getInvokeCapabilityMethod;
    if ((getInvokeCapabilityMethod = CapabilityServiceGrpc.getInvokeCapabilityMethod) == null) {
      synchronized (CapabilityServiceGrpc.class) {
        if ((getInvokeCapabilityMethod = CapabilityServiceGrpc.getInvokeCapabilityMethod) == null) {
          CapabilityServiceGrpc.getInvokeCapabilityMethod = getInvokeCapabilityMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.InvokeCapabilityRequest, ai.rever.boss.ipc.proto.InvokeCapabilityResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "InvokeCapability"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.InvokeCapabilityRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.InvokeCapabilityResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CapabilityServiceMethodDescriptorSupplier("InvokeCapability"))
              .build();
        }
      }
    }
    return getInvokeCapabilityMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.ListCapabilitiesResponse> getListCapabilitiesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListCapabilities",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.ListCapabilitiesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.ListCapabilitiesResponse> getListCapabilitiesMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.ListCapabilitiesResponse> getListCapabilitiesMethod;
    if ((getListCapabilitiesMethod = CapabilityServiceGrpc.getListCapabilitiesMethod) == null) {
      synchronized (CapabilityServiceGrpc.class) {
        if ((getListCapabilitiesMethod = CapabilityServiceGrpc.getListCapabilitiesMethod) == null) {
          CapabilityServiceGrpc.getListCapabilitiesMethod = getListCapabilitiesMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.ListCapabilitiesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListCapabilities"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.ListCapabilitiesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CapabilityServiceMethodDescriptorSupplier("ListCapabilities"))
              .build();
        }
      }
    }
    return getListCapabilitiesMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static CapabilityServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CapabilityServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CapabilityServiceStub>() {
        @java.lang.Override
        public CapabilityServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CapabilityServiceStub(channel, callOptions);
        }
      };
    return CapabilityServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static CapabilityServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CapabilityServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CapabilityServiceBlockingV2Stub>() {
        @java.lang.Override
        public CapabilityServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CapabilityServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return CapabilityServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static CapabilityServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CapabilityServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CapabilityServiceBlockingStub>() {
        @java.lang.Override
        public CapabilityServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CapabilityServiceBlockingStub(channel, callOptions);
        }
      };
    return CapabilityServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static CapabilityServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CapabilityServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CapabilityServiceFutureStub>() {
        @java.lang.Override
        public CapabilityServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CapabilityServiceFutureStub(channel, callOptions);
        }
      };
    return CapabilityServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void invokeCapability(ai.rever.boss.ipc.proto.InvokeCapabilityRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.InvokeCapabilityResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getInvokeCapabilityMethod(), responseObserver);
    }

    /**
     */
    default void listCapabilities(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListCapabilitiesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListCapabilitiesMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service CapabilityService.
   */
  public static abstract class CapabilityServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return CapabilityServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service CapabilityService.
   */
  public static final class CapabilityServiceStub
      extends io.grpc.stub.AbstractAsyncStub<CapabilityServiceStub> {
    private CapabilityServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CapabilityServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CapabilityServiceStub(channel, callOptions);
    }

    /**
     */
    public void invokeCapability(ai.rever.boss.ipc.proto.InvokeCapabilityRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.InvokeCapabilityResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getInvokeCapabilityMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void listCapabilities(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListCapabilitiesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListCapabilitiesMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service CapabilityService.
   */
  public static final class CapabilityServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<CapabilityServiceBlockingV2Stub> {
    private CapabilityServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CapabilityServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CapabilityServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.InvokeCapabilityResponse invokeCapability(ai.rever.boss.ipc.proto.InvokeCapabilityRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getInvokeCapabilityMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.ListCapabilitiesResponse listCapabilities(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListCapabilitiesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service CapabilityService.
   */
  public static final class CapabilityServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<CapabilityServiceBlockingStub> {
    private CapabilityServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CapabilityServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CapabilityServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.InvokeCapabilityResponse invokeCapability(ai.rever.boss.ipc.proto.InvokeCapabilityRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getInvokeCapabilityMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.ListCapabilitiesResponse listCapabilities(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListCapabilitiesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service CapabilityService.
   */
  public static final class CapabilityServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<CapabilityServiceFutureStub> {
    private CapabilityServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CapabilityServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CapabilityServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.InvokeCapabilityResponse> invokeCapability(
        ai.rever.boss.ipc.proto.InvokeCapabilityRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getInvokeCapabilityMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.ListCapabilitiesResponse> listCapabilities(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListCapabilitiesMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_INVOKE_CAPABILITY = 0;
  private static final int METHODID_LIST_CAPABILITIES = 1;

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
        case METHODID_INVOKE_CAPABILITY:
          serviceImpl.invokeCapability((ai.rever.boss.ipc.proto.InvokeCapabilityRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.InvokeCapabilityResponse>) responseObserver);
          break;
        case METHODID_LIST_CAPABILITIES:
          serviceImpl.listCapabilities((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.ListCapabilitiesResponse>) responseObserver);
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
          getInvokeCapabilityMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.InvokeCapabilityRequest,
              ai.rever.boss.ipc.proto.InvokeCapabilityResponse>(
                service, METHODID_INVOKE_CAPABILITY)))
        .addMethod(
          getListCapabilitiesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.ListCapabilitiesResponse>(
                service, METHODID_LIST_CAPABILITIES)))
        .build();
  }

  private static abstract class CapabilityServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    CapabilityServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.Capability.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("CapabilityService");
    }
  }

  private static final class CapabilityServiceFileDescriptorSupplier
      extends CapabilityServiceBaseDescriptorSupplier {
    CapabilityServiceFileDescriptorSupplier() {}
  }

  private static final class CapabilityServiceMethodDescriptorSupplier
      extends CapabilityServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    CapabilityServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (CapabilityServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new CapabilityServiceFileDescriptorSupplier())
              .addMethod(getInvokeCapabilityMethod())
              .addMethod(getListCapabilitiesMethod())
              .build();
        }
      }
    }
    return result;
  }
}
