package ai.rever.boss.ipc.proto.services;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/services/browser.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class BrowserServiceGrpc {

  private BrowserServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.services.BrowserService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.NavigateBrowserRequest,
      ai.rever.boss.ipc.proto.services.NavigateBrowserResponse> getNavigateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Navigate",
      requestType = ai.rever.boss.ipc.proto.services.NavigateBrowserRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.NavigateBrowserResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.NavigateBrowserRequest,
      ai.rever.boss.ipc.proto.services.NavigateBrowserResponse> getNavigateMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.NavigateBrowserRequest, ai.rever.boss.ipc.proto.services.NavigateBrowserResponse> getNavigateMethod;
    if ((getNavigateMethod = BrowserServiceGrpc.getNavigateMethod) == null) {
      synchronized (BrowserServiceGrpc.class) {
        if ((getNavigateMethod = BrowserServiceGrpc.getNavigateMethod) == null) {
          BrowserServiceGrpc.getNavigateMethod = getNavigateMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.NavigateBrowserRequest, ai.rever.boss.ipc.proto.services.NavigateBrowserResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Navigate"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.NavigateBrowserRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.NavigateBrowserResponse.getDefaultInstance()))
              .setSchemaDescriptor(new BrowserServiceMethodDescriptorSupplier("Navigate"))
              .build();
        }
      }
    }
    return getNavigateMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ExecuteJSRequest,
      ai.rever.boss.ipc.proto.services.ExecuteJSResponse> getExecuteJSMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ExecuteJS",
      requestType = ai.rever.boss.ipc.proto.services.ExecuteJSRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.ExecuteJSResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ExecuteJSRequest,
      ai.rever.boss.ipc.proto.services.ExecuteJSResponse> getExecuteJSMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ExecuteJSRequest, ai.rever.boss.ipc.proto.services.ExecuteJSResponse> getExecuteJSMethod;
    if ((getExecuteJSMethod = BrowserServiceGrpc.getExecuteJSMethod) == null) {
      synchronized (BrowserServiceGrpc.class) {
        if ((getExecuteJSMethod = BrowserServiceGrpc.getExecuteJSMethod) == null) {
          BrowserServiceGrpc.getExecuteJSMethod = getExecuteJSMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.ExecuteJSRequest, ai.rever.boss.ipc.proto.services.ExecuteJSResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ExecuteJS"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.ExecuteJSRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.ExecuteJSResponse.getDefaultInstance()))
              .setSchemaDescriptor(new BrowserServiceMethodDescriptorSupplier("ExecuteJS"))
              .build();
        }
      }
    }
    return getExecuteJSMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.BrowserNavigationEvent> getOnNavigationEventMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "OnNavigationEvent",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.BrowserNavigationEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.BrowserNavigationEvent> getOnNavigationEventMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.BrowserNavigationEvent> getOnNavigationEventMethod;
    if ((getOnNavigationEventMethod = BrowserServiceGrpc.getOnNavigationEventMethod) == null) {
      synchronized (BrowserServiceGrpc.class) {
        if ((getOnNavigationEventMethod = BrowserServiceGrpc.getOnNavigationEventMethod) == null) {
          BrowserServiceGrpc.getOnNavigationEventMethod = getOnNavigationEventMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.BrowserNavigationEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "OnNavigationEvent"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.BrowserNavigationEvent.getDefaultInstance()))
              .setSchemaDescriptor(new BrowserServiceMethodDescriptorSupplier("OnNavigationEvent"))
              .build();
        }
      }
    }
    return getOnNavigationEventMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetFaviconRequest,
      ai.rever.boss.ipc.proto.services.GetFaviconResponse> getGetFaviconMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetFavicon",
      requestType = ai.rever.boss.ipc.proto.services.GetFaviconRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.GetFaviconResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetFaviconRequest,
      ai.rever.boss.ipc.proto.services.GetFaviconResponse> getGetFaviconMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetFaviconRequest, ai.rever.boss.ipc.proto.services.GetFaviconResponse> getGetFaviconMethod;
    if ((getGetFaviconMethod = BrowserServiceGrpc.getGetFaviconMethod) == null) {
      synchronized (BrowserServiceGrpc.class) {
        if ((getGetFaviconMethod = BrowserServiceGrpc.getGetFaviconMethod) == null) {
          BrowserServiceGrpc.getGetFaviconMethod = getGetFaviconMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.GetFaviconRequest, ai.rever.boss.ipc.proto.services.GetFaviconResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetFavicon"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.GetFaviconRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.GetFaviconResponse.getDefaultInstance()))
              .setSchemaDescriptor(new BrowserServiceMethodDescriptorSupplier("GetFavicon"))
              .build();
        }
      }
    }
    return getGetFaviconMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.PageInfoResponse> getGetPageInfoMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetPageInfo",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.PageInfoResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.PageInfoResponse> getGetPageInfoMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.PageInfoResponse> getGetPageInfoMethod;
    if ((getGetPageInfoMethod = BrowserServiceGrpc.getGetPageInfoMethod) == null) {
      synchronized (BrowserServiceGrpc.class) {
        if ((getGetPageInfoMethod = BrowserServiceGrpc.getGetPageInfoMethod) == null) {
          BrowserServiceGrpc.getGetPageInfoMethod = getGetPageInfoMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.PageInfoResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetPageInfo"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.PageInfoResponse.getDefaultInstance()))
              .setSchemaDescriptor(new BrowserServiceMethodDescriptorSupplier("GetPageInfo"))
              .build();
        }
      }
    }
    return getGetPageInfoMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.Empty> getGoBackMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GoBack",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.Empty> getGoBackMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.Empty> getGoBackMethod;
    if ((getGoBackMethod = BrowserServiceGrpc.getGoBackMethod) == null) {
      synchronized (BrowserServiceGrpc.class) {
        if ((getGoBackMethod = BrowserServiceGrpc.getGoBackMethod) == null) {
          BrowserServiceGrpc.getGoBackMethod = getGoBackMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GoBack"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new BrowserServiceMethodDescriptorSupplier("GoBack"))
              .build();
        }
      }
    }
    return getGoBackMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.Empty> getGoForwardMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GoForward",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.Empty> getGoForwardMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.Empty> getGoForwardMethod;
    if ((getGoForwardMethod = BrowserServiceGrpc.getGoForwardMethod) == null) {
      synchronized (BrowserServiceGrpc.class) {
        if ((getGoForwardMethod = BrowserServiceGrpc.getGoForwardMethod) == null) {
          BrowserServiceGrpc.getGoForwardMethod = getGoForwardMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GoForward"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new BrowserServiceMethodDescriptorSupplier("GoForward"))
              .build();
        }
      }
    }
    return getGoForwardMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.Empty> getReloadMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Reload",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.Empty> getReloadMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.Empty> getReloadMethod;
    if ((getReloadMethod = BrowserServiceGrpc.getReloadMethod) == null) {
      synchronized (BrowserServiceGrpc.class) {
        if ((getReloadMethod = BrowserServiceGrpc.getReloadMethod) == null) {
          BrowserServiceGrpc.getReloadMethod = getReloadMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Reload"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new BrowserServiceMethodDescriptorSupplier("Reload"))
              .build();
        }
      }
    }
    return getReloadMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static BrowserServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BrowserServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BrowserServiceStub>() {
        @java.lang.Override
        public BrowserServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BrowserServiceStub(channel, callOptions);
        }
      };
    return BrowserServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static BrowserServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BrowserServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BrowserServiceBlockingV2Stub>() {
        @java.lang.Override
        public BrowserServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BrowserServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return BrowserServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static BrowserServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BrowserServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BrowserServiceBlockingStub>() {
        @java.lang.Override
        public BrowserServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BrowserServiceBlockingStub(channel, callOptions);
        }
      };
    return BrowserServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static BrowserServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BrowserServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BrowserServiceFutureStub>() {
        @java.lang.Override
        public BrowserServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BrowserServiceFutureStub(channel, callOptions);
        }
      };
    return BrowserServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void navigate(ai.rever.boss.ipc.proto.services.NavigateBrowserRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.NavigateBrowserResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getNavigateMethod(), responseObserver);
    }

    /**
     */
    default void executeJS(ai.rever.boss.ipc.proto.services.ExecuteJSRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ExecuteJSResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getExecuteJSMethod(), responseObserver);
    }

    /**
     */
    default void onNavigationEvent(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.BrowserNavigationEvent> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getOnNavigationEventMethod(), responseObserver);
    }

    /**
     */
    default void getFavicon(ai.rever.boss.ipc.proto.services.GetFaviconRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.GetFaviconResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetFaviconMethod(), responseObserver);
    }

    /**
     */
    default void getPageInfo(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.PageInfoResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetPageInfoMethod(), responseObserver);
    }

    /**
     */
    default void goBack(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGoBackMethod(), responseObserver);
    }

    /**
     */
    default void goForward(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGoForwardMethod(), responseObserver);
    }

    /**
     */
    default void reload(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReloadMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service BrowserService.
   */
  public static abstract class BrowserServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return BrowserServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service BrowserService.
   */
  public static final class BrowserServiceStub
      extends io.grpc.stub.AbstractAsyncStub<BrowserServiceStub> {
    private BrowserServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BrowserServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BrowserServiceStub(channel, callOptions);
    }

    /**
     */
    public void navigate(ai.rever.boss.ipc.proto.services.NavigateBrowserRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.NavigateBrowserResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getNavigateMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void executeJS(ai.rever.boss.ipc.proto.services.ExecuteJSRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ExecuteJSResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getExecuteJSMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void onNavigationEvent(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.BrowserNavigationEvent> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getOnNavigationEventMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getFavicon(ai.rever.boss.ipc.proto.services.GetFaviconRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.GetFaviconResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetFaviconMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getPageInfo(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.PageInfoResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetPageInfoMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void goBack(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGoBackMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void goForward(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGoForwardMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void reload(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReloadMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service BrowserService.
   */
  public static final class BrowserServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<BrowserServiceBlockingV2Stub> {
    private BrowserServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BrowserServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BrowserServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.NavigateBrowserResponse navigate(ai.rever.boss.ipc.proto.services.NavigateBrowserRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getNavigateMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.ExecuteJSResponse executeJS(ai.rever.boss.ipc.proto.services.ExecuteJSRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getExecuteJSMethod(), getCallOptions(), request);
    }

    /**
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.services.BrowserNavigationEvent>
        onNavigationEvent(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getOnNavigationEventMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.GetFaviconResponse getFavicon(ai.rever.boss.ipc.proto.services.GetFaviconRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetFaviconMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.PageInfoResponse getPageInfo(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetPageInfoMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty goBack(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGoBackMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty goForward(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGoForwardMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty reload(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReloadMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service BrowserService.
   */
  public static final class BrowserServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<BrowserServiceBlockingStub> {
    private BrowserServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BrowserServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BrowserServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.NavigateBrowserResponse navigate(ai.rever.boss.ipc.proto.services.NavigateBrowserRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getNavigateMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.ExecuteJSResponse executeJS(ai.rever.boss.ipc.proto.services.ExecuteJSRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getExecuteJSMethod(), getCallOptions(), request);
    }

    /**
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.services.BrowserNavigationEvent> onNavigationEvent(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getOnNavigationEventMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.GetFaviconResponse getFavicon(ai.rever.boss.ipc.proto.services.GetFaviconRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetFaviconMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.PageInfoResponse getPageInfo(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetPageInfoMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty goBack(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGoBackMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty goForward(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGoForwardMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty reload(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReloadMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service BrowserService.
   */
  public static final class BrowserServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<BrowserServiceFutureStub> {
    private BrowserServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BrowserServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BrowserServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.NavigateBrowserResponse> navigate(
        ai.rever.boss.ipc.proto.services.NavigateBrowserRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getNavigateMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.ExecuteJSResponse> executeJS(
        ai.rever.boss.ipc.proto.services.ExecuteJSRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getExecuteJSMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.GetFaviconResponse> getFavicon(
        ai.rever.boss.ipc.proto.services.GetFaviconRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetFaviconMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.PageInfoResponse> getPageInfo(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetPageInfoMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> goBack(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGoBackMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> goForward(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGoForwardMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> reload(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReloadMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_NAVIGATE = 0;
  private static final int METHODID_EXECUTE_JS = 1;
  private static final int METHODID_ON_NAVIGATION_EVENT = 2;
  private static final int METHODID_GET_FAVICON = 3;
  private static final int METHODID_GET_PAGE_INFO = 4;
  private static final int METHODID_GO_BACK = 5;
  private static final int METHODID_GO_FORWARD = 6;
  private static final int METHODID_RELOAD = 7;

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
        case METHODID_NAVIGATE:
          serviceImpl.navigate((ai.rever.boss.ipc.proto.services.NavigateBrowserRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.NavigateBrowserResponse>) responseObserver);
          break;
        case METHODID_EXECUTE_JS:
          serviceImpl.executeJS((ai.rever.boss.ipc.proto.services.ExecuteJSRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ExecuteJSResponse>) responseObserver);
          break;
        case METHODID_ON_NAVIGATION_EVENT:
          serviceImpl.onNavigationEvent((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.BrowserNavigationEvent>) responseObserver);
          break;
        case METHODID_GET_FAVICON:
          serviceImpl.getFavicon((ai.rever.boss.ipc.proto.services.GetFaviconRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.GetFaviconResponse>) responseObserver);
          break;
        case METHODID_GET_PAGE_INFO:
          serviceImpl.getPageInfo((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.PageInfoResponse>) responseObserver);
          break;
        case METHODID_GO_BACK:
          serviceImpl.goBack((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty>) responseObserver);
          break;
        case METHODID_GO_FORWARD:
          serviceImpl.goForward((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty>) responseObserver);
          break;
        case METHODID_RELOAD:
          serviceImpl.reload((ai.rever.boss.ipc.proto.Empty) request,
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
          getNavigateMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.NavigateBrowserRequest,
              ai.rever.boss.ipc.proto.services.NavigateBrowserResponse>(
                service, METHODID_NAVIGATE)))
        .addMethod(
          getExecuteJSMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.ExecuteJSRequest,
              ai.rever.boss.ipc.proto.services.ExecuteJSResponse>(
                service, METHODID_EXECUTE_JS)))
        .addMethod(
          getOnNavigationEventMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.BrowserNavigationEvent>(
                service, METHODID_ON_NAVIGATION_EVENT)))
        .addMethod(
          getGetFaviconMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.GetFaviconRequest,
              ai.rever.boss.ipc.proto.services.GetFaviconResponse>(
                service, METHODID_GET_FAVICON)))
        .addMethod(
          getGetPageInfoMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.PageInfoResponse>(
                service, METHODID_GET_PAGE_INFO)))
        .addMethod(
          getGoBackMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_GO_BACK)))
        .addMethod(
          getGoForwardMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_GO_FORWARD)))
        .addMethod(
          getReloadMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_RELOAD)))
        .build();
  }

  private static abstract class BrowserServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    BrowserServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.services.Browser.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("BrowserService");
    }
  }

  private static final class BrowserServiceFileDescriptorSupplier
      extends BrowserServiceBaseDescriptorSupplier {
    BrowserServiceFileDescriptorSupplier() {}
  }

  private static final class BrowserServiceMethodDescriptorSupplier
      extends BrowserServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    BrowserServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (BrowserServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new BrowserServiceFileDescriptorSupplier())
              .addMethod(getNavigateMethod())
              .addMethod(getExecuteJSMethod())
              .addMethod(getOnNavigationEventMethod())
              .addMethod(getGetFaviconMethod())
              .addMethod(getGetPageInfoMethod())
              .addMethod(getGoBackMethod())
              .addMethod(getGoForwardMethod())
              .addMethod(getReloadMethod())
              .build();
        }
      }
    }
    return result;
  }
}
