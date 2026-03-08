package ai.rever.boss.ipc.proto.services;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/services/terminal.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class TerminalServiceGrpc {

  private TerminalServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.services.TerminalService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.CreateSessionRequest,
      ai.rever.boss.ipc.proto.services.CreateSessionResponse> getCreateSessionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreateSession",
      requestType = ai.rever.boss.ipc.proto.services.CreateSessionRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.CreateSessionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.CreateSessionRequest,
      ai.rever.boss.ipc.proto.services.CreateSessionResponse> getCreateSessionMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.CreateSessionRequest, ai.rever.boss.ipc.proto.services.CreateSessionResponse> getCreateSessionMethod;
    if ((getCreateSessionMethod = TerminalServiceGrpc.getCreateSessionMethod) == null) {
      synchronized (TerminalServiceGrpc.class) {
        if ((getCreateSessionMethod = TerminalServiceGrpc.getCreateSessionMethod) == null) {
          TerminalServiceGrpc.getCreateSessionMethod = getCreateSessionMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.CreateSessionRequest, ai.rever.boss.ipc.proto.services.CreateSessionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateSession"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.CreateSessionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.CreateSessionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new TerminalServiceMethodDescriptorSupplier("CreateSession"))
              .build();
        }
      }
    }
    return getCreateSessionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SendInputRequest,
      ai.rever.boss.ipc.proto.Empty> getSendInputMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SendInput",
      requestType = ai.rever.boss.ipc.proto.services.SendInputRequest.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SendInputRequest,
      ai.rever.boss.ipc.proto.Empty> getSendInputMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SendInputRequest, ai.rever.boss.ipc.proto.Empty> getSendInputMethod;
    if ((getSendInputMethod = TerminalServiceGrpc.getSendInputMethod) == null) {
      synchronized (TerminalServiceGrpc.class) {
        if ((getSendInputMethod = TerminalServiceGrpc.getSendInputMethod) == null) {
          TerminalServiceGrpc.getSendInputMethod = getSendInputMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.SendInputRequest, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SendInput"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.SendInputRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new TerminalServiceMethodDescriptorSupplier("SendInput"))
              .build();
        }
      }
    }
    return getSendInputMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.StreamOutputRequest,
      ai.rever.boss.ipc.proto.services.TerminalOutputChunk> getStreamOutputMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "StreamOutput",
      requestType = ai.rever.boss.ipc.proto.services.StreamOutputRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.TerminalOutputChunk.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.StreamOutputRequest,
      ai.rever.boss.ipc.proto.services.TerminalOutputChunk> getStreamOutputMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.StreamOutputRequest, ai.rever.boss.ipc.proto.services.TerminalOutputChunk> getStreamOutputMethod;
    if ((getStreamOutputMethod = TerminalServiceGrpc.getStreamOutputMethod) == null) {
      synchronized (TerminalServiceGrpc.class) {
        if ((getStreamOutputMethod = TerminalServiceGrpc.getStreamOutputMethod) == null) {
          TerminalServiceGrpc.getStreamOutputMethod = getStreamOutputMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.StreamOutputRequest, ai.rever.boss.ipc.proto.services.TerminalOutputChunk>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StreamOutput"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.StreamOutputRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.TerminalOutputChunk.getDefaultInstance()))
              .setSchemaDescriptor(new TerminalServiceMethodDescriptorSupplier("StreamOutput"))
              .build();
        }
      }
    }
    return getStreamOutputMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ResizeRequest,
      ai.rever.boss.ipc.proto.Empty> getResizeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Resize",
      requestType = ai.rever.boss.ipc.proto.services.ResizeRequest.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ResizeRequest,
      ai.rever.boss.ipc.proto.Empty> getResizeMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ResizeRequest, ai.rever.boss.ipc.proto.Empty> getResizeMethod;
    if ((getResizeMethod = TerminalServiceGrpc.getResizeMethod) == null) {
      synchronized (TerminalServiceGrpc.class) {
        if ((getResizeMethod = TerminalServiceGrpc.getResizeMethod) == null) {
          TerminalServiceGrpc.getResizeMethod = getResizeMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.ResizeRequest, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Resize"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.ResizeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new TerminalServiceMethodDescriptorSupplier("Resize"))
              .build();
        }
      }
    }
    return getResizeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.CloseSessionRequest,
      ai.rever.boss.ipc.proto.Empty> getCloseSessionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CloseSession",
      requestType = ai.rever.boss.ipc.proto.services.CloseSessionRequest.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.CloseSessionRequest,
      ai.rever.boss.ipc.proto.Empty> getCloseSessionMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.CloseSessionRequest, ai.rever.boss.ipc.proto.Empty> getCloseSessionMethod;
    if ((getCloseSessionMethod = TerminalServiceGrpc.getCloseSessionMethod) == null) {
      synchronized (TerminalServiceGrpc.class) {
        if ((getCloseSessionMethod = TerminalServiceGrpc.getCloseSessionMethod) == null) {
          TerminalServiceGrpc.getCloseSessionMethod = getCloseSessionMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.CloseSessionRequest, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CloseSession"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.CloseSessionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new TerminalServiceMethodDescriptorSupplier("CloseSession"))
              .build();
        }
      }
    }
    return getCloseSessionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.ListSessionsResponse> getListSessionsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListSessions",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.ListSessionsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.ListSessionsResponse> getListSessionsMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.ListSessionsResponse> getListSessionsMethod;
    if ((getListSessionsMethod = TerminalServiceGrpc.getListSessionsMethod) == null) {
      synchronized (TerminalServiceGrpc.class) {
        if ((getListSessionsMethod = TerminalServiceGrpc.getListSessionsMethod) == null) {
          TerminalServiceGrpc.getListSessionsMethod = getListSessionsMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.ListSessionsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListSessions"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.ListSessionsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new TerminalServiceMethodDescriptorSupplier("ListSessions"))
              .build();
        }
      }
    }
    return getListSessionsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static TerminalServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TerminalServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TerminalServiceStub>() {
        @java.lang.Override
        public TerminalServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TerminalServiceStub(channel, callOptions);
        }
      };
    return TerminalServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static TerminalServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TerminalServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TerminalServiceBlockingV2Stub>() {
        @java.lang.Override
        public TerminalServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TerminalServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return TerminalServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static TerminalServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TerminalServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TerminalServiceBlockingStub>() {
        @java.lang.Override
        public TerminalServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TerminalServiceBlockingStub(channel, callOptions);
        }
      };
    return TerminalServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static TerminalServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TerminalServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TerminalServiceFutureStub>() {
        @java.lang.Override
        public TerminalServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TerminalServiceFutureStub(channel, callOptions);
        }
      };
    return TerminalServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void createSession(ai.rever.boss.ipc.proto.services.CreateSessionRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.CreateSessionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreateSessionMethod(), responseObserver);
    }

    /**
     */
    default void sendInput(ai.rever.boss.ipc.proto.services.SendInputRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSendInputMethod(), responseObserver);
    }

    /**
     */
    default void streamOutput(ai.rever.boss.ipc.proto.services.StreamOutputRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.TerminalOutputChunk> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getStreamOutputMethod(), responseObserver);
    }

    /**
     */
    default void resize(ai.rever.boss.ipc.proto.services.ResizeRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getResizeMethod(), responseObserver);
    }

    /**
     */
    default void closeSession(ai.rever.boss.ipc.proto.services.CloseSessionRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCloseSessionMethod(), responseObserver);
    }

    /**
     */
    default void listSessions(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ListSessionsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListSessionsMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service TerminalService.
   */
  public static abstract class TerminalServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return TerminalServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service TerminalService.
   */
  public static final class TerminalServiceStub
      extends io.grpc.stub.AbstractAsyncStub<TerminalServiceStub> {
    private TerminalServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TerminalServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TerminalServiceStub(channel, callOptions);
    }

    /**
     */
    public void createSession(ai.rever.boss.ipc.proto.services.CreateSessionRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.CreateSessionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreateSessionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void sendInput(ai.rever.boss.ipc.proto.services.SendInputRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSendInputMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void streamOutput(ai.rever.boss.ipc.proto.services.StreamOutputRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.TerminalOutputChunk> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getStreamOutputMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void resize(ai.rever.boss.ipc.proto.services.ResizeRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getResizeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void closeSession(ai.rever.boss.ipc.proto.services.CloseSessionRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCloseSessionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void listSessions(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ListSessionsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListSessionsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service TerminalService.
   */
  public static final class TerminalServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<TerminalServiceBlockingV2Stub> {
    private TerminalServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TerminalServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TerminalServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.CreateSessionResponse createSession(ai.rever.boss.ipc.proto.services.CreateSessionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateSessionMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty sendInput(ai.rever.boss.ipc.proto.services.SendInputRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSendInputMethod(), getCallOptions(), request);
    }

    /**
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.services.TerminalOutputChunk>
        streamOutput(ai.rever.boss.ipc.proto.services.StreamOutputRequest request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getStreamOutputMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty resize(ai.rever.boss.ipc.proto.services.ResizeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getResizeMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty closeSession(ai.rever.boss.ipc.proto.services.CloseSessionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCloseSessionMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.ListSessionsResponse listSessions(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListSessionsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service TerminalService.
   */
  public static final class TerminalServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<TerminalServiceBlockingStub> {
    private TerminalServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TerminalServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TerminalServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.CreateSessionResponse createSession(ai.rever.boss.ipc.proto.services.CreateSessionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateSessionMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty sendInput(ai.rever.boss.ipc.proto.services.SendInputRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSendInputMethod(), getCallOptions(), request);
    }

    /**
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.services.TerminalOutputChunk> streamOutput(
        ai.rever.boss.ipc.proto.services.StreamOutputRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getStreamOutputMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty resize(ai.rever.boss.ipc.proto.services.ResizeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getResizeMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.Empty closeSession(ai.rever.boss.ipc.proto.services.CloseSessionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCloseSessionMethod(), getCallOptions(), request);
    }

    /**
     */
    public ai.rever.boss.ipc.proto.services.ListSessionsResponse listSessions(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListSessionsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service TerminalService.
   */
  public static final class TerminalServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<TerminalServiceFutureStub> {
    private TerminalServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TerminalServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TerminalServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.CreateSessionResponse> createSession(
        ai.rever.boss.ipc.proto.services.CreateSessionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreateSessionMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> sendInput(
        ai.rever.boss.ipc.proto.services.SendInputRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSendInputMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> resize(
        ai.rever.boss.ipc.proto.services.ResizeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getResizeMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> closeSession(
        ai.rever.boss.ipc.proto.services.CloseSessionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCloseSessionMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.ListSessionsResponse> listSessions(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListSessionsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_SESSION = 0;
  private static final int METHODID_SEND_INPUT = 1;
  private static final int METHODID_STREAM_OUTPUT = 2;
  private static final int METHODID_RESIZE = 3;
  private static final int METHODID_CLOSE_SESSION = 4;
  private static final int METHODID_LIST_SESSIONS = 5;

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
        case METHODID_CREATE_SESSION:
          serviceImpl.createSession((ai.rever.boss.ipc.proto.services.CreateSessionRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.CreateSessionResponse>) responseObserver);
          break;
        case METHODID_SEND_INPUT:
          serviceImpl.sendInput((ai.rever.boss.ipc.proto.services.SendInputRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty>) responseObserver);
          break;
        case METHODID_STREAM_OUTPUT:
          serviceImpl.streamOutput((ai.rever.boss.ipc.proto.services.StreamOutputRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.TerminalOutputChunk>) responseObserver);
          break;
        case METHODID_RESIZE:
          serviceImpl.resize((ai.rever.boss.ipc.proto.services.ResizeRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty>) responseObserver);
          break;
        case METHODID_CLOSE_SESSION:
          serviceImpl.closeSession((ai.rever.boss.ipc.proto.services.CloseSessionRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty>) responseObserver);
          break;
        case METHODID_LIST_SESSIONS:
          serviceImpl.listSessions((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ListSessionsResponse>) responseObserver);
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
          getCreateSessionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.CreateSessionRequest,
              ai.rever.boss.ipc.proto.services.CreateSessionResponse>(
                service, METHODID_CREATE_SESSION)))
        .addMethod(
          getSendInputMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.SendInputRequest,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_SEND_INPUT)))
        .addMethod(
          getStreamOutputMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.StreamOutputRequest,
              ai.rever.boss.ipc.proto.services.TerminalOutputChunk>(
                service, METHODID_STREAM_OUTPUT)))
        .addMethod(
          getResizeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.ResizeRequest,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_RESIZE)))
        .addMethod(
          getCloseSessionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.CloseSessionRequest,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_CLOSE_SESSION)))
        .addMethod(
          getListSessionsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.ListSessionsResponse>(
                service, METHODID_LIST_SESSIONS)))
        .build();
  }

  private static abstract class TerminalServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    TerminalServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.services.Terminal.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("TerminalService");
    }
  }

  private static final class TerminalServiceFileDescriptorSupplier
      extends TerminalServiceBaseDescriptorSupplier {
    TerminalServiceFileDescriptorSupplier() {}
  }

  private static final class TerminalServiceMethodDescriptorSupplier
      extends TerminalServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    TerminalServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (TerminalServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new TerminalServiceFileDescriptorSupplier())
              .addMethod(getCreateSessionMethod())
              .addMethod(getSendInputMethod())
              .addMethod(getStreamOutputMethod())
              .addMethod(getResizeMethod())
              .addMethod(getCloseSessionMethod())
              .addMethod(getListSessionsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
