package ai.rever.boss.ipc.proto.services;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/services/editor.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class EditorServiceGrpc {

  private EditorServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.services.EditorService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.OpenFileRequest,
      ai.rever.boss.ipc.proto.services.OpenFileResponse> getOpenFileMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "OpenFile",
      requestType = ai.rever.boss.ipc.proto.services.OpenFileRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.OpenFileResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.OpenFileRequest,
      ai.rever.boss.ipc.proto.services.OpenFileResponse> getOpenFileMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.OpenFileRequest, ai.rever.boss.ipc.proto.services.OpenFileResponse> getOpenFileMethod;
    if ((getOpenFileMethod = EditorServiceGrpc.getOpenFileMethod) == null) {
      synchronized (EditorServiceGrpc.class) {
        if ((getOpenFileMethod = EditorServiceGrpc.getOpenFileMethod) == null) {
          EditorServiceGrpc.getOpenFileMethod = getOpenFileMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.OpenFileRequest, ai.rever.boss.ipc.proto.services.OpenFileResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "OpenFile"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.OpenFileRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.OpenFileResponse.getDefaultInstance()))
              .setSchemaDescriptor(new EditorServiceMethodDescriptorSupplier("OpenFile"))
              .build();
        }
      }
    }
    return getOpenFileMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SaveFileRequest,
      ai.rever.boss.ipc.proto.Empty> getSaveFileMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SaveFile",
      requestType = ai.rever.boss.ipc.proto.services.SaveFileRequest.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SaveFileRequest,
      ai.rever.boss.ipc.proto.Empty> getSaveFileMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SaveFileRequest, ai.rever.boss.ipc.proto.Empty> getSaveFileMethod;
    if ((getSaveFileMethod = EditorServiceGrpc.getSaveFileMethod) == null) {
      synchronized (EditorServiceGrpc.class) {
        if ((getSaveFileMethod = EditorServiceGrpc.getSaveFileMethod) == null) {
          EditorServiceGrpc.getSaveFileMethod = getSaveFileMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.SaveFileRequest, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SaveFile"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.SaveFileRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new EditorServiceMethodDescriptorSupplier("SaveFile"))
              .build();
        }
      }
    }
    return getSaveFileMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetTokensRequest,
      ai.rever.boss.ipc.proto.services.GetTokensResponse> getGetTokensMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetTokens",
      requestType = ai.rever.boss.ipc.proto.services.GetTokensRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.GetTokensResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetTokensRequest,
      ai.rever.boss.ipc.proto.services.GetTokensResponse> getGetTokensMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.GetTokensRequest, ai.rever.boss.ipc.proto.services.GetTokensResponse> getGetTokensMethod;
    if ((getGetTokensMethod = EditorServiceGrpc.getGetTokensMethod) == null) {
      synchronized (EditorServiceGrpc.class) {
        if ((getGetTokensMethod = EditorServiceGrpc.getGetTokensMethod) == null) {
          EditorServiceGrpc.getGetTokensMethod = getGetTokensMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.GetTokensRequest, ai.rever.boss.ipc.proto.services.GetTokensResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetTokens"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.GetTokensRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.GetTokensResponse.getDefaultInstance()))
              .setSchemaDescriptor(new EditorServiceMethodDescriptorSupplier("GetTokens"))
              .build();
        }
      }
    }
    return getGetTokensMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.NavigateRequest,
      ai.rever.boss.ipc.proto.services.NavigateResponse> getNavigateToDefinitionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "NavigateToDefinition",
      requestType = ai.rever.boss.ipc.proto.services.NavigateRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.NavigateResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.NavigateRequest,
      ai.rever.boss.ipc.proto.services.NavigateResponse> getNavigateToDefinitionMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.NavigateRequest, ai.rever.boss.ipc.proto.services.NavigateResponse> getNavigateToDefinitionMethod;
    if ((getNavigateToDefinitionMethod = EditorServiceGrpc.getNavigateToDefinitionMethod) == null) {
      synchronized (EditorServiceGrpc.class) {
        if ((getNavigateToDefinitionMethod = EditorServiceGrpc.getNavigateToDefinitionMethod) == null) {
          EditorServiceGrpc.getNavigateToDefinitionMethod = getNavigateToDefinitionMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.NavigateRequest, ai.rever.boss.ipc.proto.services.NavigateResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "NavigateToDefinition"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.NavigateRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.NavigateResponse.getDefaultInstance()))
              .setSchemaDescriptor(new EditorServiceMethodDescriptorSupplier("NavigateToDefinition"))
              .build();
        }
      }
    }
    return getNavigateToDefinitionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.DetectMainRequest,
      ai.rever.boss.ipc.proto.services.DetectMainResponse> getDetectMainFunctionsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DetectMainFunctions",
      requestType = ai.rever.boss.ipc.proto.services.DetectMainRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.DetectMainResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.DetectMainRequest,
      ai.rever.boss.ipc.proto.services.DetectMainResponse> getDetectMainFunctionsMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.DetectMainRequest, ai.rever.boss.ipc.proto.services.DetectMainResponse> getDetectMainFunctionsMethod;
    if ((getDetectMainFunctionsMethod = EditorServiceGrpc.getDetectMainFunctionsMethod) == null) {
      synchronized (EditorServiceGrpc.class) {
        if ((getDetectMainFunctionsMethod = EditorServiceGrpc.getDetectMainFunctionsMethod) == null) {
          EditorServiceGrpc.getDetectMainFunctionsMethod = getDetectMainFunctionsMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.DetectMainRequest, ai.rever.boss.ipc.proto.services.DetectMainResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DetectMainFunctions"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.DetectMainRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.DetectMainResponse.getDefaultInstance()))
              .setSchemaDescriptor(new EditorServiceMethodDescriptorSupplier("DetectMainFunctions"))
              .build();
        }
      }
    }
    return getDetectMainFunctionsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.ListOpenFilesResponse> getListOpenFilesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListOpenFiles",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.ListOpenFilesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.ListOpenFilesResponse> getListOpenFilesMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.ListOpenFilesResponse> getListOpenFilesMethod;
    if ((getListOpenFilesMethod = EditorServiceGrpc.getListOpenFilesMethod) == null) {
      synchronized (EditorServiceGrpc.class) {
        if ((getListOpenFilesMethod = EditorServiceGrpc.getListOpenFilesMethod) == null) {
          EditorServiceGrpc.getListOpenFilesMethod = getListOpenFilesMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.ListOpenFilesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListOpenFiles"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.ListOpenFilesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new EditorServiceMethodDescriptorSupplier("ListOpenFiles"))
              .build();
        }
      }
    }
    return getListOpenFilesMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static EditorServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EditorServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EditorServiceStub>() {
        @java.lang.Override
        public EditorServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EditorServiceStub(channel, callOptions);
        }
      };
    return EditorServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static EditorServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EditorServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EditorServiceBlockingV2Stub>() {
        @java.lang.Override
        public EditorServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EditorServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return EditorServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static EditorServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EditorServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EditorServiceBlockingStub>() {
        @java.lang.Override
        public EditorServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EditorServiceBlockingStub(channel, callOptions);
        }
      };
    return EditorServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static EditorServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EditorServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EditorServiceFutureStub>() {
        @java.lang.Override
        public EditorServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EditorServiceFutureStub(channel, callOptions);
        }
      };
    return EditorServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void openFile(ai.rever.boss.ipc.proto.services.OpenFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.OpenFileResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getOpenFileMethod(), responseObserver);
    }

    /**
     */
    default void saveFile(ai.rever.boss.ipc.proto.services.SaveFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSaveFileMethod(), responseObserver);
    }

    /**
     */
    default void getTokens(ai.rever.boss.ipc.proto.services.GetTokensRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.GetTokensResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetTokensMethod(), responseObserver);
    }

    /**
     */
    default void navigateToDefinition(ai.rever.boss.ipc.proto.services.NavigateRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.NavigateResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getNavigateToDefinitionMethod(), responseObserver);
    }

    /**
     */
    default void detectMainFunctions(ai.rever.boss.ipc.proto.services.DetectMainRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.DetectMainResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDetectMainFunctionsMethod(), responseObserver);
    }

    /**
     */
    default void listOpenFiles(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ListOpenFilesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListOpenFilesMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service EditorService.
   */
  public static abstract class EditorServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return EditorServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service EditorService.
   */
  public static final class EditorServiceStub
      extends io.grpc.stub.AbstractAsyncStub<EditorServiceStub> {
    private EditorServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EditorServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EditorServiceStub(channel, callOptions);
    }

    /**
     */
    public void openFile(ai.rever.boss.ipc.proto.services.OpenFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.OpenFileResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getOpenFileMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void saveFile(ai.rever.boss.ipc.proto.services.SaveFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSaveFileMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getTokens(ai.rever.boss.ipc.proto.services.GetTokensRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.GetTokensResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetTokensMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void navigateToDefinition(ai.rever.boss.ipc.proto.services.NavigateRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.NavigateResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getNavigateToDefinitionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void detectMainFunctions(ai.rever.boss.ipc.proto.services.DetectMainRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.DetectMainResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDetectMainFunctionsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void listOpenFiles(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ListOpenFilesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListOpenFilesMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service EditorService.
   */
  public static final class EditorServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<EditorServiceBlockingV2Stub> {
    private EditorServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EditorServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EditorServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.OpenFileResponse openFile(ai.rever.boss.ipc.proto.services.OpenFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getOpenFileMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty saveFile(ai.rever.boss.ipc.proto.services.SaveFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSaveFileMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.GetTokensResponse getTokens(ai.rever.boss.ipc.proto.services.GetTokensRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetTokensMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.NavigateResponse navigateToDefinition(ai.rever.boss.ipc.proto.services.NavigateRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getNavigateToDefinitionMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.DetectMainResponse detectMainFunctions(ai.rever.boss.ipc.proto.services.DetectMainRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDetectMainFunctionsMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.ListOpenFilesResponse listOpenFiles(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListOpenFilesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service EditorService.
   */
  public static final class EditorServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<EditorServiceBlockingStub> {
    private EditorServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EditorServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EditorServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.OpenFileResponse openFile(ai.rever.boss.ipc.proto.services.OpenFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getOpenFileMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty saveFile(ai.rever.boss.ipc.proto.services.SaveFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSaveFileMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.GetTokensResponse getTokens(ai.rever.boss.ipc.proto.services.GetTokensRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetTokensMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.NavigateResponse navigateToDefinition(ai.rever.boss.ipc.proto.services.NavigateRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getNavigateToDefinitionMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.DetectMainResponse detectMainFunctions(ai.rever.boss.ipc.proto.services.DetectMainRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDetectMainFunctionsMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.ListOpenFilesResponse listOpenFiles(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListOpenFilesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service EditorService.
   */
  public static final class EditorServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<EditorServiceFutureStub> {
    private EditorServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EditorServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EditorServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.OpenFileResponse> openFile(
        ai.rever.boss.ipc.proto.services.OpenFileRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getOpenFileMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> saveFile(
        ai.rever.boss.ipc.proto.services.SaveFileRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSaveFileMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.GetTokensResponse> getTokens(
        ai.rever.boss.ipc.proto.services.GetTokensRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetTokensMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.NavigateResponse> navigateToDefinition(
        ai.rever.boss.ipc.proto.services.NavigateRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getNavigateToDefinitionMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.DetectMainResponse> detectMainFunctions(
        ai.rever.boss.ipc.proto.services.DetectMainRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDetectMainFunctionsMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.ListOpenFilesResponse> listOpenFiles(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListOpenFilesMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_OPEN_FILE = 0;
  private static final int METHODID_SAVE_FILE = 1;
  private static final int METHODID_GET_TOKENS = 2;
  private static final int METHODID_NAVIGATE_TO_DEFINITION = 3;
  private static final int METHODID_DETECT_MAIN_FUNCTIONS = 4;
  private static final int METHODID_LIST_OPEN_FILES = 5;

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
        case METHODID_OPEN_FILE:
          serviceImpl.openFile((ai.rever.boss.ipc.proto.services.OpenFileRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.OpenFileResponse>) responseObserver);
          break;
        case METHODID_SAVE_FILE:
          serviceImpl.saveFile((ai.rever.boss.ipc.proto.services.SaveFileRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty>) responseObserver);
          break;
        case METHODID_GET_TOKENS:
          serviceImpl.getTokens((ai.rever.boss.ipc.proto.services.GetTokensRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.GetTokensResponse>) responseObserver);
          break;
        case METHODID_NAVIGATE_TO_DEFINITION:
          serviceImpl.navigateToDefinition((ai.rever.boss.ipc.proto.services.NavigateRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.NavigateResponse>) responseObserver);
          break;
        case METHODID_DETECT_MAIN_FUNCTIONS:
          serviceImpl.detectMainFunctions((ai.rever.boss.ipc.proto.services.DetectMainRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.DetectMainResponse>) responseObserver);
          break;
        case METHODID_LIST_OPEN_FILES:
          serviceImpl.listOpenFiles((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ListOpenFilesResponse>) responseObserver);
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
          getOpenFileMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.OpenFileRequest,
              ai.rever.boss.ipc.proto.services.OpenFileResponse>(
                service, METHODID_OPEN_FILE)))
        .addMethod(
          getSaveFileMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.SaveFileRequest,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_SAVE_FILE)))
        .addMethod(
          getGetTokensMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.GetTokensRequest,
              ai.rever.boss.ipc.proto.services.GetTokensResponse>(
                service, METHODID_GET_TOKENS)))
        .addMethod(
          getNavigateToDefinitionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.NavigateRequest,
              ai.rever.boss.ipc.proto.services.NavigateResponse>(
                service, METHODID_NAVIGATE_TO_DEFINITION)))
        .addMethod(
          getDetectMainFunctionsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.DetectMainRequest,
              ai.rever.boss.ipc.proto.services.DetectMainResponse>(
                service, METHODID_DETECT_MAIN_FUNCTIONS)))
        .addMethod(
          getListOpenFilesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.ListOpenFilesResponse>(
                service, METHODID_LIST_OPEN_FILES)))
        .build();
  }

  private static abstract class EditorServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    EditorServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.services.Editor.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("EditorService");
    }
  }

  private static final class EditorServiceFileDescriptorSupplier
      extends EditorServiceBaseDescriptorSupplier {
    EditorServiceFileDescriptorSupplier() {}
  }

  private static final class EditorServiceMethodDescriptorSupplier
      extends EditorServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    EditorServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (EditorServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new EditorServiceFileDescriptorSupplier())
              .addMethod(getOpenFileMethod())
              .addMethod(getSaveFileMethod())
              .addMethod(getGetTokensMethod())
              .addMethod(getNavigateToDefinitionMethod())
              .addMethod(getDetectMainFunctionsMethod())
              .addMethod(getListOpenFilesMethod())
              .build();
        }
      }
    }
    return result;
  }
}
