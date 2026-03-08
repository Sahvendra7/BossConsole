package ai.rever.boss.ipc.proto.services;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * WorkspaceService provides workspace lifecycle and persistence operations.
 * Plugins and UI components use this to read and watch workspace state across processes.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/services/workspace.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class WorkspaceServiceGrpc {

  private WorkspaceServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.services.WorkspaceService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.WorkspacesResponse> getGetWorkspacesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetWorkspaces",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.WorkspacesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.WorkspacesResponse> getGetWorkspacesMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.WorkspacesResponse> getGetWorkspacesMethod;
    if ((getGetWorkspacesMethod = WorkspaceServiceGrpc.getGetWorkspacesMethod) == null) {
      synchronized (WorkspaceServiceGrpc.class) {
        if ((getGetWorkspacesMethod = WorkspaceServiceGrpc.getGetWorkspacesMethod) == null) {
          WorkspaceServiceGrpc.getGetWorkspacesMethod = getGetWorkspacesMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.WorkspacesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetWorkspaces"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.WorkspacesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkspaceServiceMethodDescriptorSupplier("GetWorkspaces"))
              .build();
        }
      }
    }
    return getGetWorkspacesMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.WorkspacesResponse> getWatchWorkspacesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WatchWorkspaces",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.WorkspacesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.WorkspacesResponse> getWatchWorkspacesMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.WorkspacesResponse> getWatchWorkspacesMethod;
    if ((getWatchWorkspacesMethod = WorkspaceServiceGrpc.getWatchWorkspacesMethod) == null) {
      synchronized (WorkspaceServiceGrpc.class) {
        if ((getWatchWorkspacesMethod = WorkspaceServiceGrpc.getWatchWorkspacesMethod) == null) {
          WorkspaceServiceGrpc.getWatchWorkspacesMethod = getWatchWorkspacesMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.WorkspacesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WatchWorkspaces"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.WorkspacesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkspaceServiceMethodDescriptorSupplier("WatchWorkspaces"))
              .build();
        }
      }
    }
    return getWatchWorkspacesMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.WorkspaceResponse> getGetCurrentWorkspaceMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetCurrentWorkspace",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.WorkspaceResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.WorkspaceResponse> getGetCurrentWorkspaceMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.WorkspaceResponse> getGetCurrentWorkspaceMethod;
    if ((getGetCurrentWorkspaceMethod = WorkspaceServiceGrpc.getGetCurrentWorkspaceMethod) == null) {
      synchronized (WorkspaceServiceGrpc.class) {
        if ((getGetCurrentWorkspaceMethod = WorkspaceServiceGrpc.getGetCurrentWorkspaceMethod) == null) {
          WorkspaceServiceGrpc.getGetCurrentWorkspaceMethod = getGetCurrentWorkspaceMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.WorkspaceResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetCurrentWorkspace"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.WorkspaceResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkspaceServiceMethodDescriptorSupplier("GetCurrentWorkspace"))
              .build();
        }
      }
    }
    return getGetCurrentWorkspaceMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.WorkspaceResponse> getWatchCurrentWorkspaceMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WatchCurrentWorkspace",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.WorkspaceResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.WorkspaceResponse> getWatchCurrentWorkspaceMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.WorkspaceResponse> getWatchCurrentWorkspaceMethod;
    if ((getWatchCurrentWorkspaceMethod = WorkspaceServiceGrpc.getWatchCurrentWorkspaceMethod) == null) {
      synchronized (WorkspaceServiceGrpc.class) {
        if ((getWatchCurrentWorkspaceMethod = WorkspaceServiceGrpc.getWatchCurrentWorkspaceMethod) == null) {
          WorkspaceServiceGrpc.getWatchCurrentWorkspaceMethod = getWatchCurrentWorkspaceMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.WorkspaceResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WatchCurrentWorkspace"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.WorkspaceResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkspaceServiceMethodDescriptorSupplier("WatchCurrentWorkspace"))
              .build();
        }
      }
    }
    return getWatchCurrentWorkspaceMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest,
      ai.rever.boss.ipc.proto.services.WorkspaceResponse> getLoadWorkspaceMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "LoadWorkspace",
      requestType = ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.WorkspaceResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest,
      ai.rever.boss.ipc.proto.services.WorkspaceResponse> getLoadWorkspaceMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest, ai.rever.boss.ipc.proto.services.WorkspaceResponse> getLoadWorkspaceMethod;
    if ((getLoadWorkspaceMethod = WorkspaceServiceGrpc.getLoadWorkspaceMethod) == null) {
      synchronized (WorkspaceServiceGrpc.class) {
        if ((getLoadWorkspaceMethod = WorkspaceServiceGrpc.getLoadWorkspaceMethod) == null) {
          WorkspaceServiceGrpc.getLoadWorkspaceMethod = getLoadWorkspaceMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest, ai.rever.boss.ipc.proto.services.WorkspaceResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "LoadWorkspace"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.WorkspaceResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkspaceServiceMethodDescriptorSupplier("LoadWorkspace"))
              .build();
        }
      }
    }
    return getLoadWorkspaceMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest,
      ai.rever.boss.ipc.proto.services.WorkspaceResponse> getSaveWorkspaceMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SaveWorkspace",
      requestType = ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.WorkspaceResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest,
      ai.rever.boss.ipc.proto.services.WorkspaceResponse> getSaveWorkspaceMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest, ai.rever.boss.ipc.proto.services.WorkspaceResponse> getSaveWorkspaceMethod;
    if ((getSaveWorkspaceMethod = WorkspaceServiceGrpc.getSaveWorkspaceMethod) == null) {
      synchronized (WorkspaceServiceGrpc.class) {
        if ((getSaveWorkspaceMethod = WorkspaceServiceGrpc.getSaveWorkspaceMethod) == null) {
          WorkspaceServiceGrpc.getSaveWorkspaceMethod = getSaveWorkspaceMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest, ai.rever.boss.ipc.proto.services.WorkspaceResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SaveWorkspace"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.WorkspaceResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkspaceServiceMethodDescriptorSupplier("SaveWorkspace"))
              .build();
        }
      }
    }
    return getSaveWorkspaceMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest,
      ai.rever.boss.ipc.proto.Empty> getDeleteWorkspaceMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DeleteWorkspace",
      requestType = ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest,
      ai.rever.boss.ipc.proto.Empty> getDeleteWorkspaceMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest, ai.rever.boss.ipc.proto.Empty> getDeleteWorkspaceMethod;
    if ((getDeleteWorkspaceMethod = WorkspaceServiceGrpc.getDeleteWorkspaceMethod) == null) {
      synchronized (WorkspaceServiceGrpc.class) {
        if ((getDeleteWorkspaceMethod = WorkspaceServiceGrpc.getDeleteWorkspaceMethod) == null) {
          WorkspaceServiceGrpc.getDeleteWorkspaceMethod = getDeleteWorkspaceMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DeleteWorkspace"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new WorkspaceServiceMethodDescriptorSupplier("DeleteWorkspace"))
              .build();
        }
      }
    }
    return getDeleteWorkspaceMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static WorkspaceServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkspaceServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkspaceServiceStub>() {
        @java.lang.Override
        public WorkspaceServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkspaceServiceStub(channel, callOptions);
        }
      };
    return WorkspaceServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static WorkspaceServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkspaceServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkspaceServiceBlockingV2Stub>() {
        @java.lang.Override
        public WorkspaceServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkspaceServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return WorkspaceServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static WorkspaceServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkspaceServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkspaceServiceBlockingStub>() {
        @java.lang.Override
        public WorkspaceServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkspaceServiceBlockingStub(channel, callOptions);
        }
      };
    return WorkspaceServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static WorkspaceServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkspaceServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkspaceServiceFutureStub>() {
        @java.lang.Override
        public WorkspaceServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkspaceServiceFutureStub(channel, callOptions);
        }
      };
    return WorkspaceServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * WorkspaceService provides workspace lifecycle and persistence operations.
   * Plugins and UI components use this to read and watch workspace state across processes.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Get all available workspaces
     * </pre>
     */
    default void getWorkspaces(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspacesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetWorkspacesMethod(), responseObserver);
    }

    /**
     * <pre>
     * Stream workspace list changes in real time
     * </pre>
     */
    default void watchWorkspaces(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspacesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getWatchWorkspacesMethod(), responseObserver);
    }

    /**
     * <pre>
     * Get the currently active workspace
     * </pre>
     */
    default void getCurrentWorkspace(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetCurrentWorkspaceMethod(), responseObserver);
    }

    /**
     * <pre>
     * Stream the currently active workspace as it changes
     * </pre>
     */
    default void watchCurrentWorkspace(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getWatchCurrentWorkspaceMethod(), responseObserver);
    }

    /**
     * <pre>
     * Load (activate) a workspace by ID or path
     * </pre>
     */
    default void loadWorkspace(ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getLoadWorkspaceMethod(), responseObserver);
    }

    /**
     * <pre>
     * Persist workspace state (tabs, layout, scroll positions, etc.)
     * </pre>
     */
    default void saveWorkspace(ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSaveWorkspaceMethod(), responseObserver);
    }

    /**
     * <pre>
     * Delete a saved workspace
     * </pre>
     */
    default void deleteWorkspace(ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDeleteWorkspaceMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service WorkspaceService.
   * <pre>
   * WorkspaceService provides workspace lifecycle and persistence operations.
   * Plugins and UI components use this to read and watch workspace state across processes.
   * </pre>
   */
  public static abstract class WorkspaceServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return WorkspaceServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service WorkspaceService.
   * <pre>
   * WorkspaceService provides workspace lifecycle and persistence operations.
   * Plugins and UI components use this to read and watch workspace state across processes.
   * </pre>
   */
  public static final class WorkspaceServiceStub
      extends io.grpc.stub.AbstractAsyncStub<WorkspaceServiceStub> {
    private WorkspaceServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkspaceServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkspaceServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get all available workspaces
     * </pre>
     */
    public void getWorkspaces(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspacesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetWorkspacesMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Stream workspace list changes in real time
     * </pre>
     */
    public void watchWorkspaces(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspacesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getWatchWorkspacesMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Get the currently active workspace
     * </pre>
     */
    public void getCurrentWorkspace(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetCurrentWorkspaceMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Stream the currently active workspace as it changes
     * </pre>
     */
    public void watchCurrentWorkspace(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getWatchCurrentWorkspaceMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Load (activate) a workspace by ID or path
     * </pre>
     */
    public void loadWorkspace(ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getLoadWorkspaceMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Persist workspace state (tabs, layout, scroll positions, etc.)
     * </pre>
     */
    public void saveWorkspace(ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSaveWorkspaceMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Delete a saved workspace
     * </pre>
     */
    public void deleteWorkspace(ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDeleteWorkspaceMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service WorkspaceService.
   * <pre>
   * WorkspaceService provides workspace lifecycle and persistence operations.
   * Plugins and UI components use this to read and watch workspace state across processes.
   * </pre>
   */
  public static final class WorkspaceServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<WorkspaceServiceBlockingV2Stub> {
    private WorkspaceServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkspaceServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkspaceServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Get all available workspaces
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.WorkspacesResponse getWorkspaces(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetWorkspacesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Stream workspace list changes in real time
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.services.WorkspacesResponse>
        watchWorkspaces(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getWatchWorkspacesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get the currently active workspace
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.WorkspaceResponse getCurrentWorkspace(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetCurrentWorkspaceMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Stream the currently active workspace as it changes
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.services.WorkspaceResponse>
        watchCurrentWorkspace(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getWatchCurrentWorkspaceMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Load (activate) a workspace by ID or path
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.WorkspaceResponse loadWorkspace(ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getLoadWorkspaceMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Persist workspace state (tabs, layout, scroll positions, etc.)
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.WorkspaceResponse saveWorkspace(ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSaveWorkspaceMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Delete a saved workspace
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty deleteWorkspace(ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeleteWorkspaceMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service WorkspaceService.
   * <pre>
   * WorkspaceService provides workspace lifecycle and persistence operations.
   * Plugins and UI components use this to read and watch workspace state across processes.
   * </pre>
   */
  public static final class WorkspaceServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<WorkspaceServiceBlockingStub> {
    private WorkspaceServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkspaceServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkspaceServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get all available workspaces
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.WorkspacesResponse getWorkspaces(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetWorkspacesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Stream workspace list changes in real time
     * </pre>
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.services.WorkspacesResponse> watchWorkspaces(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getWatchWorkspacesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get the currently active workspace
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.WorkspaceResponse getCurrentWorkspace(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetCurrentWorkspaceMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Stream the currently active workspace as it changes
     * </pre>
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.services.WorkspaceResponse> watchCurrentWorkspace(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getWatchCurrentWorkspaceMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Load (activate) a workspace by ID or path
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.WorkspaceResponse loadWorkspace(ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getLoadWorkspaceMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Persist workspace state (tabs, layout, scroll positions, etc.)
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.WorkspaceResponse saveWorkspace(ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSaveWorkspaceMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Delete a saved workspace
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty deleteWorkspace(ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeleteWorkspaceMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service WorkspaceService.
   * <pre>
   * WorkspaceService provides workspace lifecycle and persistence operations.
   * Plugins and UI components use this to read and watch workspace state across processes.
   * </pre>
   */
  public static final class WorkspaceServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<WorkspaceServiceFutureStub> {
    private WorkspaceServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkspaceServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkspaceServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get all available workspaces
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.WorkspacesResponse> getWorkspaces(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetWorkspacesMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Get the currently active workspace
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.WorkspaceResponse> getCurrentWorkspace(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetCurrentWorkspaceMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Load (activate) a workspace by ID or path
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.WorkspaceResponse> loadWorkspace(
        ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getLoadWorkspaceMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Persist workspace state (tabs, layout, scroll positions, etc.)
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.WorkspaceResponse> saveWorkspace(
        ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSaveWorkspaceMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Delete a saved workspace
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> deleteWorkspace(
        ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDeleteWorkspaceMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_WORKSPACES = 0;
  private static final int METHODID_WATCH_WORKSPACES = 1;
  private static final int METHODID_GET_CURRENT_WORKSPACE = 2;
  private static final int METHODID_WATCH_CURRENT_WORKSPACE = 3;
  private static final int METHODID_LOAD_WORKSPACE = 4;
  private static final int METHODID_SAVE_WORKSPACE = 5;
  private static final int METHODID_DELETE_WORKSPACE = 6;

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
        case METHODID_GET_WORKSPACES:
          serviceImpl.getWorkspaces((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspacesResponse>) responseObserver);
          break;
        case METHODID_WATCH_WORKSPACES:
          serviceImpl.watchWorkspaces((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspacesResponse>) responseObserver);
          break;
        case METHODID_GET_CURRENT_WORKSPACE:
          serviceImpl.getCurrentWorkspace((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse>) responseObserver);
          break;
        case METHODID_WATCH_CURRENT_WORKSPACE:
          serviceImpl.watchCurrentWorkspace((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse>) responseObserver);
          break;
        case METHODID_LOAD_WORKSPACE:
          serviceImpl.loadWorkspace((ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse>) responseObserver);
          break;
        case METHODID_SAVE_WORKSPACE:
          serviceImpl.saveWorkspace((ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WorkspaceResponse>) responseObserver);
          break;
        case METHODID_DELETE_WORKSPACE:
          serviceImpl.deleteWorkspace((ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest) request,
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
          getGetWorkspacesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.WorkspacesResponse>(
                service, METHODID_GET_WORKSPACES)))
        .addMethod(
          getWatchWorkspacesMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.WorkspacesResponse>(
                service, METHODID_WATCH_WORKSPACES)))
        .addMethod(
          getGetCurrentWorkspaceMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.WorkspaceResponse>(
                service, METHODID_GET_CURRENT_WORKSPACE)))
        .addMethod(
          getWatchCurrentWorkspaceMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.WorkspaceResponse>(
                service, METHODID_WATCH_CURRENT_WORKSPACE)))
        .addMethod(
          getLoadWorkspaceMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.LoadWorkspaceRequest,
              ai.rever.boss.ipc.proto.services.WorkspaceResponse>(
                service, METHODID_LOAD_WORKSPACE)))
        .addMethod(
          getSaveWorkspaceMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.SaveWorkspaceRequest,
              ai.rever.boss.ipc.proto.services.WorkspaceResponse>(
                service, METHODID_SAVE_WORKSPACE)))
        .addMethod(
          getDeleteWorkspaceMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.DeleteWorkspaceRequest,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_DELETE_WORKSPACE)))
        .build();
  }

  private static abstract class WorkspaceServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    WorkspaceServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.services.Workspace.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("WorkspaceService");
    }
  }

  private static final class WorkspaceServiceFileDescriptorSupplier
      extends WorkspaceServiceBaseDescriptorSupplier {
    WorkspaceServiceFileDescriptorSupplier() {}
  }

  private static final class WorkspaceServiceMethodDescriptorSupplier
      extends WorkspaceServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    WorkspaceServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (WorkspaceServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new WorkspaceServiceFileDescriptorSupplier())
              .addMethod(getGetWorkspacesMethod())
              .addMethod(getWatchWorkspacesMethod())
              .addMethod(getGetCurrentWorkspaceMethod())
              .addMethod(getWatchCurrentWorkspaceMethod())
              .addMethod(getLoadWorkspaceMethod())
              .addMethod(getSaveWorkspaceMethod())
              .addMethod(getDeleteWorkspaceMethod())
              .build();
        }
      }
    }
    return result;
  }
}
