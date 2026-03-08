package ai.rever.boss.ipc.proto.services;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * FileSystemService provides file and directory operations across process boundaries.
 * Out-of-process plugins use this instead of direct java.io.File access.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/services/filesystem.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class FileSystemServiceGrpc {

  private FileSystemServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.services.FileSystemService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ScanDirectoryRequest,
      ai.rever.boss.ipc.proto.services.ScanDirectoryResponse> getScanDirectoryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ScanDirectory",
      requestType = ai.rever.boss.ipc.proto.services.ScanDirectoryRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.ScanDirectoryResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ScanDirectoryRequest,
      ai.rever.boss.ipc.proto.services.ScanDirectoryResponse> getScanDirectoryMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ScanDirectoryRequest, ai.rever.boss.ipc.proto.services.ScanDirectoryResponse> getScanDirectoryMethod;
    if ((getScanDirectoryMethod = FileSystemServiceGrpc.getScanDirectoryMethod) == null) {
      synchronized (FileSystemServiceGrpc.class) {
        if ((getScanDirectoryMethod = FileSystemServiceGrpc.getScanDirectoryMethod) == null) {
          FileSystemServiceGrpc.getScanDirectoryMethod = getScanDirectoryMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.ScanDirectoryRequest, ai.rever.boss.ipc.proto.services.ScanDirectoryResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ScanDirectory"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.ScanDirectoryRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.ScanDirectoryResponse.getDefaultInstance()))
              .setSchemaDescriptor(new FileSystemServiceMethodDescriptorSupplier("ScanDirectory"))
              .build();
        }
      }
    }
    return getScanDirectoryMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ReadFileRequest,
      ai.rever.boss.ipc.proto.services.ReadFileResponse> getReadFileMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReadFile",
      requestType = ai.rever.boss.ipc.proto.services.ReadFileRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.ReadFileResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ReadFileRequest,
      ai.rever.boss.ipc.proto.services.ReadFileResponse> getReadFileMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.ReadFileRequest, ai.rever.boss.ipc.proto.services.ReadFileResponse> getReadFileMethod;
    if ((getReadFileMethod = FileSystemServiceGrpc.getReadFileMethod) == null) {
      synchronized (FileSystemServiceGrpc.class) {
        if ((getReadFileMethod = FileSystemServiceGrpc.getReadFileMethod) == null) {
          FileSystemServiceGrpc.getReadFileMethod = getReadFileMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.ReadFileRequest, ai.rever.boss.ipc.proto.services.ReadFileResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReadFile"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.ReadFileRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.ReadFileResponse.getDefaultInstance()))
              .setSchemaDescriptor(new FileSystemServiceMethodDescriptorSupplier("ReadFile"))
              .build();
        }
      }
    }
    return getReadFileMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.WriteFileRequest,
      ai.rever.boss.ipc.proto.services.WriteFileResponse> getWriteFileMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WriteFile",
      requestType = ai.rever.boss.ipc.proto.services.WriteFileRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.WriteFileResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.WriteFileRequest,
      ai.rever.boss.ipc.proto.services.WriteFileResponse> getWriteFileMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.WriteFileRequest, ai.rever.boss.ipc.proto.services.WriteFileResponse> getWriteFileMethod;
    if ((getWriteFileMethod = FileSystemServiceGrpc.getWriteFileMethod) == null) {
      synchronized (FileSystemServiceGrpc.class) {
        if ((getWriteFileMethod = FileSystemServiceGrpc.getWriteFileMethod) == null) {
          FileSystemServiceGrpc.getWriteFileMethod = getWriteFileMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.WriteFileRequest, ai.rever.boss.ipc.proto.services.WriteFileResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WriteFile"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.WriteFileRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.WriteFileResponse.getDefaultInstance()))
              .setSchemaDescriptor(new FileSystemServiceMethodDescriptorSupplier("WriteFile"))
              .build();
        }
      }
    }
    return getWriteFileMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.CreateFileRequest,
      ai.rever.boss.ipc.proto.Empty> getCreateFileMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreateFile",
      requestType = ai.rever.boss.ipc.proto.services.CreateFileRequest.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.CreateFileRequest,
      ai.rever.boss.ipc.proto.Empty> getCreateFileMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.CreateFileRequest, ai.rever.boss.ipc.proto.Empty> getCreateFileMethod;
    if ((getCreateFileMethod = FileSystemServiceGrpc.getCreateFileMethod) == null) {
      synchronized (FileSystemServiceGrpc.class) {
        if ((getCreateFileMethod = FileSystemServiceGrpc.getCreateFileMethod) == null) {
          FileSystemServiceGrpc.getCreateFileMethod = getCreateFileMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.CreateFileRequest, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateFile"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.CreateFileRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new FileSystemServiceMethodDescriptorSupplier("CreateFile"))
              .build();
        }
      }
    }
    return getCreateFileMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.DeleteFileRequest,
      ai.rever.boss.ipc.proto.Empty> getDeleteFileMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DeleteFile",
      requestType = ai.rever.boss.ipc.proto.services.DeleteFileRequest.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.DeleteFileRequest,
      ai.rever.boss.ipc.proto.Empty> getDeleteFileMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.DeleteFileRequest, ai.rever.boss.ipc.proto.Empty> getDeleteFileMethod;
    if ((getDeleteFileMethod = FileSystemServiceGrpc.getDeleteFileMethod) == null) {
      synchronized (FileSystemServiceGrpc.class) {
        if ((getDeleteFileMethod = FileSystemServiceGrpc.getDeleteFileMethod) == null) {
          FileSystemServiceGrpc.getDeleteFileMethod = getDeleteFileMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.DeleteFileRequest, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DeleteFile"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.DeleteFileRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new FileSystemServiceMethodDescriptorSupplier("DeleteFile"))
              .build();
        }
      }
    }
    return getDeleteFileMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.RenameFileRequest,
      ai.rever.boss.ipc.proto.Empty> getRenameFileMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RenameFile",
      requestType = ai.rever.boss.ipc.proto.services.RenameFileRequest.class,
      responseType = ai.rever.boss.ipc.proto.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.RenameFileRequest,
      ai.rever.boss.ipc.proto.Empty> getRenameFileMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.RenameFileRequest, ai.rever.boss.ipc.proto.Empty> getRenameFileMethod;
    if ((getRenameFileMethod = FileSystemServiceGrpc.getRenameFileMethod) == null) {
      synchronized (FileSystemServiceGrpc.class) {
        if ((getRenameFileMethod = FileSystemServiceGrpc.getRenameFileMethod) == null) {
          FileSystemServiceGrpc.getRenameFileMethod = getRenameFileMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.RenameFileRequest, ai.rever.boss.ipc.proto.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RenameFile"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.RenameFileRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new FileSystemServiceMethodDescriptorSupplier("RenameFile"))
              .build();
        }
      }
    }
    return getRenameFileMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.WatchFileChangesRequest,
      ai.rever.boss.ipc.proto.services.FileChangeEvent> getWatchFileChangesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WatchFileChanges",
      requestType = ai.rever.boss.ipc.proto.services.WatchFileChangesRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.FileChangeEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.WatchFileChangesRequest,
      ai.rever.boss.ipc.proto.services.FileChangeEvent> getWatchFileChangesMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.WatchFileChangesRequest, ai.rever.boss.ipc.proto.services.FileChangeEvent> getWatchFileChangesMethod;
    if ((getWatchFileChangesMethod = FileSystemServiceGrpc.getWatchFileChangesMethod) == null) {
      synchronized (FileSystemServiceGrpc.class) {
        if ((getWatchFileChangesMethod = FileSystemServiceGrpc.getWatchFileChangesMethod) == null) {
          FileSystemServiceGrpc.getWatchFileChangesMethod = getWatchFileChangesMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.WatchFileChangesRequest, ai.rever.boss.ipc.proto.services.FileChangeEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WatchFileChanges"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.WatchFileChangesRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.FileChangeEvent.getDefaultInstance()))
              .setSchemaDescriptor(new FileSystemServiceMethodDescriptorSupplier("WatchFileChanges"))
              .build();
        }
      }
    }
    return getWatchFileChangesMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static FileSystemServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<FileSystemServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<FileSystemServiceStub>() {
        @java.lang.Override
        public FileSystemServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new FileSystemServiceStub(channel, callOptions);
        }
      };
    return FileSystemServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static FileSystemServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<FileSystemServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<FileSystemServiceBlockingV2Stub>() {
        @java.lang.Override
        public FileSystemServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new FileSystemServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return FileSystemServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static FileSystemServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<FileSystemServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<FileSystemServiceBlockingStub>() {
        @java.lang.Override
        public FileSystemServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new FileSystemServiceBlockingStub(channel, callOptions);
        }
      };
    return FileSystemServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static FileSystemServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<FileSystemServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<FileSystemServiceFutureStub>() {
        @java.lang.Override
        public FileSystemServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new FileSystemServiceFutureStub(channel, callOptions);
        }
      };
    return FileSystemServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * FileSystemService provides file and directory operations across process boundaries.
   * Out-of-process plugins use this instead of direct java.io.File access.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Scan a directory (non-recursive by default)
     * </pre>
     */
    default void scanDirectory(ai.rever.boss.ipc.proto.services.ScanDirectoryRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ScanDirectoryResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getScanDirectoryMethod(), responseObserver);
    }

    /**
     * <pre>
     * Read file contents as bytes
     * </pre>
     */
    default void readFile(ai.rever.boss.ipc.proto.services.ReadFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ReadFileResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReadFileMethod(), responseObserver);
    }

    /**
     * <pre>
     * Write (create or overwrite) a file
     * </pre>
     */
    default void writeFile(ai.rever.boss.ipc.proto.services.WriteFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WriteFileResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getWriteFileMethod(), responseObserver);
    }

    /**
     * <pre>
     * Create an empty file (fails if it already exists)
     * </pre>
     */
    default void createFile(ai.rever.boss.ipc.proto.services.CreateFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreateFileMethod(), responseObserver);
    }

    /**
     * <pre>
     * Delete a file or empty directory
     * </pre>
     */
    default void deleteFile(ai.rever.boss.ipc.proto.services.DeleteFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDeleteFileMethod(), responseObserver);
    }

    /**
     * <pre>
     * Rename or move a file/directory
     * </pre>
     */
    default void renameFile(ai.rever.boss.ipc.proto.services.RenameFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRenameFileMethod(), responseObserver);
    }

    /**
     * <pre>
     * Stream file system change events for a watched path
     * </pre>
     */
    default void watchFileChanges(ai.rever.boss.ipc.proto.services.WatchFileChangesRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.FileChangeEvent> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getWatchFileChangesMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service FileSystemService.
   * <pre>
   * FileSystemService provides file and directory operations across process boundaries.
   * Out-of-process plugins use this instead of direct java.io.File access.
   * </pre>
   */
  public static abstract class FileSystemServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return FileSystemServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service FileSystemService.
   * <pre>
   * FileSystemService provides file and directory operations across process boundaries.
   * Out-of-process plugins use this instead of direct java.io.File access.
   * </pre>
   */
  public static final class FileSystemServiceStub
      extends io.grpc.stub.AbstractAsyncStub<FileSystemServiceStub> {
    private FileSystemServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected FileSystemServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new FileSystemServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Scan a directory (non-recursive by default)
     * </pre>
     */
    public void scanDirectory(ai.rever.boss.ipc.proto.services.ScanDirectoryRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ScanDirectoryResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getScanDirectoryMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Read file contents as bytes
     * </pre>
     */
    public void readFile(ai.rever.boss.ipc.proto.services.ReadFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ReadFileResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReadFileMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Write (create or overwrite) a file
     * </pre>
     */
    public void writeFile(ai.rever.boss.ipc.proto.services.WriteFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WriteFileResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getWriteFileMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Create an empty file (fails if it already exists)
     * </pre>
     */
    public void createFile(ai.rever.boss.ipc.proto.services.CreateFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreateFileMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Delete a file or empty directory
     * </pre>
     */
    public void deleteFile(ai.rever.boss.ipc.proto.services.DeleteFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDeleteFileMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Rename or move a file/directory
     * </pre>
     */
    public void renameFile(ai.rever.boss.ipc.proto.services.RenameFileRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRenameFileMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Stream file system change events for a watched path
     * </pre>
     */
    public void watchFileChanges(ai.rever.boss.ipc.proto.services.WatchFileChangesRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.FileChangeEvent> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getWatchFileChangesMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service FileSystemService.
   * <pre>
   * FileSystemService provides file and directory operations across process boundaries.
   * Out-of-process plugins use this instead of direct java.io.File access.
   * </pre>
   */
  public static final class FileSystemServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<FileSystemServiceBlockingV2Stub> {
    private FileSystemServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected FileSystemServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new FileSystemServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Scan a directory (non-recursive by default)
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.ScanDirectoryResponse scanDirectory(ai.rever.boss.ipc.proto.services.ScanDirectoryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getScanDirectoryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Read file contents as bytes
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.ReadFileResponse readFile(ai.rever.boss.ipc.proto.services.ReadFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReadFileMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Write (create or overwrite) a file
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.WriteFileResponse writeFile(ai.rever.boss.ipc.proto.services.WriteFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getWriteFileMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Create an empty file (fails if it already exists)
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty createFile(ai.rever.boss.ipc.proto.services.CreateFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateFileMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Delete a file or empty directory
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty deleteFile(ai.rever.boss.ipc.proto.services.DeleteFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeleteFileMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Rename or move a file/directory
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty renameFile(ai.rever.boss.ipc.proto.services.RenameFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRenameFileMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Stream file system change events for a watched path
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.services.FileChangeEvent>
        watchFileChanges(ai.rever.boss.ipc.proto.services.WatchFileChangesRequest request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getWatchFileChangesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service FileSystemService.
   * <pre>
   * FileSystemService provides file and directory operations across process boundaries.
   * Out-of-process plugins use this instead of direct java.io.File access.
   * </pre>
   */
  public static final class FileSystemServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<FileSystemServiceBlockingStub> {
    private FileSystemServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected FileSystemServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new FileSystemServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Scan a directory (non-recursive by default)
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.ScanDirectoryResponse scanDirectory(ai.rever.boss.ipc.proto.services.ScanDirectoryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getScanDirectoryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Read file contents as bytes
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.ReadFileResponse readFile(ai.rever.boss.ipc.proto.services.ReadFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReadFileMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Write (create or overwrite) a file
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.WriteFileResponse writeFile(ai.rever.boss.ipc.proto.services.WriteFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getWriteFileMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Create an empty file (fails if it already exists)
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty createFile(ai.rever.boss.ipc.proto.services.CreateFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateFileMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Delete a file or empty directory
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty deleteFile(ai.rever.boss.ipc.proto.services.DeleteFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeleteFileMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Rename or move a file/directory
     * </pre>
     */
    public ai.rever.boss.ipc.proto.Empty renameFile(ai.rever.boss.ipc.proto.services.RenameFileRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRenameFileMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Stream file system change events for a watched path
     * </pre>
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.services.FileChangeEvent> watchFileChanges(
        ai.rever.boss.ipc.proto.services.WatchFileChangesRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getWatchFileChangesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service FileSystemService.
   * <pre>
   * FileSystemService provides file and directory operations across process boundaries.
   * Out-of-process plugins use this instead of direct java.io.File access.
   * </pre>
   */
  public static final class FileSystemServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<FileSystemServiceFutureStub> {
    private FileSystemServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected FileSystemServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new FileSystemServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Scan a directory (non-recursive by default)
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.ScanDirectoryResponse> scanDirectory(
        ai.rever.boss.ipc.proto.services.ScanDirectoryRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getScanDirectoryMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Read file contents as bytes
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.ReadFileResponse> readFile(
        ai.rever.boss.ipc.proto.services.ReadFileRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReadFileMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Write (create or overwrite) a file
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.WriteFileResponse> writeFile(
        ai.rever.boss.ipc.proto.services.WriteFileRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getWriteFileMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Create an empty file (fails if it already exists)
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> createFile(
        ai.rever.boss.ipc.proto.services.CreateFileRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreateFileMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Delete a file or empty directory
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> deleteFile(
        ai.rever.boss.ipc.proto.services.DeleteFileRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDeleteFileMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Rename or move a file/directory
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.Empty> renameFile(
        ai.rever.boss.ipc.proto.services.RenameFileRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRenameFileMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SCAN_DIRECTORY = 0;
  private static final int METHODID_READ_FILE = 1;
  private static final int METHODID_WRITE_FILE = 2;
  private static final int METHODID_CREATE_FILE = 3;
  private static final int METHODID_DELETE_FILE = 4;
  private static final int METHODID_RENAME_FILE = 5;
  private static final int METHODID_WATCH_FILE_CHANGES = 6;

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
        case METHODID_SCAN_DIRECTORY:
          serviceImpl.scanDirectory((ai.rever.boss.ipc.proto.services.ScanDirectoryRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ScanDirectoryResponse>) responseObserver);
          break;
        case METHODID_READ_FILE:
          serviceImpl.readFile((ai.rever.boss.ipc.proto.services.ReadFileRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.ReadFileResponse>) responseObserver);
          break;
        case METHODID_WRITE_FILE:
          serviceImpl.writeFile((ai.rever.boss.ipc.proto.services.WriteFileRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.WriteFileResponse>) responseObserver);
          break;
        case METHODID_CREATE_FILE:
          serviceImpl.createFile((ai.rever.boss.ipc.proto.services.CreateFileRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty>) responseObserver);
          break;
        case METHODID_DELETE_FILE:
          serviceImpl.deleteFile((ai.rever.boss.ipc.proto.services.DeleteFileRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty>) responseObserver);
          break;
        case METHODID_RENAME_FILE:
          serviceImpl.renameFile((ai.rever.boss.ipc.proto.services.RenameFileRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.Empty>) responseObserver);
          break;
        case METHODID_WATCH_FILE_CHANGES:
          serviceImpl.watchFileChanges((ai.rever.boss.ipc.proto.services.WatchFileChangesRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.FileChangeEvent>) responseObserver);
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
          getScanDirectoryMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.ScanDirectoryRequest,
              ai.rever.boss.ipc.proto.services.ScanDirectoryResponse>(
                service, METHODID_SCAN_DIRECTORY)))
        .addMethod(
          getReadFileMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.ReadFileRequest,
              ai.rever.boss.ipc.proto.services.ReadFileResponse>(
                service, METHODID_READ_FILE)))
        .addMethod(
          getWriteFileMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.WriteFileRequest,
              ai.rever.boss.ipc.proto.services.WriteFileResponse>(
                service, METHODID_WRITE_FILE)))
        .addMethod(
          getCreateFileMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.CreateFileRequest,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_CREATE_FILE)))
        .addMethod(
          getDeleteFileMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.DeleteFileRequest,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_DELETE_FILE)))
        .addMethod(
          getRenameFileMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.RenameFileRequest,
              ai.rever.boss.ipc.proto.Empty>(
                service, METHODID_RENAME_FILE)))
        .addMethod(
          getWatchFileChangesMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.WatchFileChangesRequest,
              ai.rever.boss.ipc.proto.services.FileChangeEvent>(
                service, METHODID_WATCH_FILE_CHANGES)))
        .build();
  }

  private static abstract class FileSystemServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    FileSystemServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.services.Filesystem.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("FileSystemService");
    }
  }

  private static final class FileSystemServiceFileDescriptorSupplier
      extends FileSystemServiceBaseDescriptorSupplier {
    FileSystemServiceFileDescriptorSupplier() {}
  }

  private static final class FileSystemServiceMethodDescriptorSupplier
      extends FileSystemServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    FileSystemServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (FileSystemServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new FileSystemServiceFileDescriptorSupplier())
              .addMethod(getScanDirectoryMethod())
              .addMethod(getReadFileMethod())
              .addMethod(getWriteFileMethod())
              .addMethod(getCreateFileMethod())
              .addMethod(getDeleteFileMethod())
              .addMethod(getRenameFileMethod())
              .addMethod(getWatchFileChangesMethod())
              .build();
        }
      }
    }
    return result;
  }
}
