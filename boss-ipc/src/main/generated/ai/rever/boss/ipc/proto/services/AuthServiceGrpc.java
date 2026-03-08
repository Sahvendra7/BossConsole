package ai.rever.boss.ipc.proto.services;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * AuthService provides authentication and authorization functionality.
 * This is the first service to be extracted to its own process (Phase 2).
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.72.0)",
    comments = "Source: boss/ipc/v1/services/auth.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class AuthServiceGrpc {

  private AuthServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "boss.ipc.v1.services.AuthService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.AuthStateResponse> getGetAuthStateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetAuthState",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.AuthStateResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.AuthStateResponse> getGetAuthStateMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.AuthStateResponse> getGetAuthStateMethod;
    if ((getGetAuthStateMethod = AuthServiceGrpc.getGetAuthStateMethod) == null) {
      synchronized (AuthServiceGrpc.class) {
        if ((getGetAuthStateMethod = AuthServiceGrpc.getGetAuthStateMethod) == null) {
          AuthServiceGrpc.getGetAuthStateMethod = getGetAuthStateMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.AuthStateResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetAuthState"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.AuthStateResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuthServiceMethodDescriptorSupplier("GetAuthState"))
              .build();
        }
      }
    }
    return getGetAuthStateMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.AuthStateResponse> getWatchAuthStateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WatchAuthState",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.AuthStateResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.AuthStateResponse> getWatchAuthStateMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.AuthStateResponse> getWatchAuthStateMethod;
    if ((getWatchAuthStateMethod = AuthServiceGrpc.getWatchAuthStateMethod) == null) {
      synchronized (AuthServiceGrpc.class) {
        if ((getWatchAuthStateMethod = AuthServiceGrpc.getWatchAuthStateMethod) == null) {
          AuthServiceGrpc.getWatchAuthStateMethod = getWatchAuthStateMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.AuthStateResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WatchAuthState"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.AuthStateResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuthServiceMethodDescriptorSupplier("WatchAuthState"))
              .build();
        }
      }
    }
    return getWatchAuthStateMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SignInRequest,
      ai.rever.boss.ipc.proto.services.SignInResponse> getSignInMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SignIn",
      requestType = ai.rever.boss.ipc.proto.services.SignInRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.SignInResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SignInRequest,
      ai.rever.boss.ipc.proto.services.SignInResponse> getSignInMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.SignInRequest, ai.rever.boss.ipc.proto.services.SignInResponse> getSignInMethod;
    if ((getSignInMethod = AuthServiceGrpc.getSignInMethod) == null) {
      synchronized (AuthServiceGrpc.class) {
        if ((getSignInMethod = AuthServiceGrpc.getSignInMethod) == null) {
          AuthServiceGrpc.getSignInMethod = getSignInMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.SignInRequest, ai.rever.boss.ipc.proto.services.SignInResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SignIn"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.SignInRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.SignInResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuthServiceMethodDescriptorSupplier("SignIn"))
              .build();
        }
      }
    }
    return getSignInMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.SignOutResponse> getSignOutMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SignOut",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.SignOutResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.SignOutResponse> getSignOutMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.SignOutResponse> getSignOutMethod;
    if ((getSignOutMethod = AuthServiceGrpc.getSignOutMethod) == null) {
      synchronized (AuthServiceGrpc.class) {
        if ((getSignOutMethod = AuthServiceGrpc.getSignOutMethod) == null) {
          AuthServiceGrpc.getSignOutMethod = getSignOutMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.SignOutResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SignOut"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.SignOutResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuthServiceMethodDescriptorSupplier("SignOut"))
              .build();
        }
      }
    }
    return getSignOutMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.UserInfoResponse> getGetCurrentUserMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetCurrentUser",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.UserInfoResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.UserInfoResponse> getGetCurrentUserMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.UserInfoResponse> getGetCurrentUserMethod;
    if ((getGetCurrentUserMethod = AuthServiceGrpc.getGetCurrentUserMethod) == null) {
      synchronized (AuthServiceGrpc.class) {
        if ((getGetCurrentUserMethod = AuthServiceGrpc.getGetCurrentUserMethod) == null) {
          AuthServiceGrpc.getGetCurrentUserMethod = getGetCurrentUserMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.UserInfoResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetCurrentUser"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.UserInfoResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuthServiceMethodDescriptorSupplier("GetCurrentUser"))
              .build();
        }
      }
    }
    return getGetCurrentUserMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.UserInfoResponse> getWatchCurrentUserMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WatchCurrentUser",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.UserInfoResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.UserInfoResponse> getWatchCurrentUserMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.UserInfoResponse> getWatchCurrentUserMethod;
    if ((getWatchCurrentUserMethod = AuthServiceGrpc.getWatchCurrentUserMethod) == null) {
      synchronized (AuthServiceGrpc.class) {
        if ((getWatchCurrentUserMethod = AuthServiceGrpc.getWatchCurrentUserMethod) == null) {
          AuthServiceGrpc.getWatchCurrentUserMethod = getWatchCurrentUserMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.UserInfoResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WatchCurrentUser"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.UserInfoResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuthServiceMethodDescriptorSupplier("WatchCurrentUser"))
              .build();
        }
      }
    }
    return getWatchCurrentUserMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.PermissionRequest,
      ai.rever.boss.ipc.proto.services.PermissionResponse> getHasPermissionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "HasPermission",
      requestType = ai.rever.boss.ipc.proto.services.PermissionRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.PermissionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.PermissionRequest,
      ai.rever.boss.ipc.proto.services.PermissionResponse> getHasPermissionMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.PermissionRequest, ai.rever.boss.ipc.proto.services.PermissionResponse> getHasPermissionMethod;
    if ((getHasPermissionMethod = AuthServiceGrpc.getHasPermissionMethod) == null) {
      synchronized (AuthServiceGrpc.class) {
        if ((getHasPermissionMethod = AuthServiceGrpc.getHasPermissionMethod) == null) {
          AuthServiceGrpc.getHasPermissionMethod = getHasPermissionMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.PermissionRequest, ai.rever.boss.ipc.proto.services.PermissionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "HasPermission"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.PermissionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.PermissionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuthServiceMethodDescriptorSupplier("HasPermission"))
              .build();
        }
      }
    }
    return getHasPermissionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest,
      ai.rever.boss.ipc.proto.services.PermissionResponse> getHasAnyPermissionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "HasAnyPermission",
      requestType = ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest.class,
      responseType = ai.rever.boss.ipc.proto.services.PermissionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest,
      ai.rever.boss.ipc.proto.services.PermissionResponse> getHasAnyPermissionMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest, ai.rever.boss.ipc.proto.services.PermissionResponse> getHasAnyPermissionMethod;
    if ((getHasAnyPermissionMethod = AuthServiceGrpc.getHasAnyPermissionMethod) == null) {
      synchronized (AuthServiceGrpc.class) {
        if ((getHasAnyPermissionMethod = AuthServiceGrpc.getHasAnyPermissionMethod) == null) {
          AuthServiceGrpc.getHasAnyPermissionMethod = getHasAnyPermissionMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest, ai.rever.boss.ipc.proto.services.PermissionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "HasAnyPermission"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.PermissionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuthServiceMethodDescriptorSupplier("HasAnyPermission"))
              .build();
        }
      }
    }
    return getHasAnyPermissionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.UserPermissionsResponse> getGetUserPermissionsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetUserPermissions",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.UserPermissionsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.UserPermissionsResponse> getGetUserPermissionsMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.UserPermissionsResponse> getGetUserPermissionsMethod;
    if ((getGetUserPermissionsMethod = AuthServiceGrpc.getGetUserPermissionsMethod) == null) {
      synchronized (AuthServiceGrpc.class) {
        if ((getGetUserPermissionsMethod = AuthServiceGrpc.getGetUserPermissionsMethod) == null) {
          AuthServiceGrpc.getGetUserPermissionsMethod = getGetUserPermissionsMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.UserPermissionsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetUserPermissions"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.UserPermissionsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuthServiceMethodDescriptorSupplier("GetUserPermissions"))
              .build();
        }
      }
    }
    return getGetUserPermissionsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.IsAdminResponse> getIsAdminMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "IsAdmin",
      requestType = ai.rever.boss.ipc.proto.Empty.class,
      responseType = ai.rever.boss.ipc.proto.services.IsAdminResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty,
      ai.rever.boss.ipc.proto.services.IsAdminResponse> getIsAdminMethod() {
    io.grpc.MethodDescriptor<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.IsAdminResponse> getIsAdminMethod;
    if ((getIsAdminMethod = AuthServiceGrpc.getIsAdminMethod) == null) {
      synchronized (AuthServiceGrpc.class) {
        if ((getIsAdminMethod = AuthServiceGrpc.getIsAdminMethod) == null) {
          AuthServiceGrpc.getIsAdminMethod = getIsAdminMethod =
              io.grpc.MethodDescriptor.<ai.rever.boss.ipc.proto.Empty, ai.rever.boss.ipc.proto.services.IsAdminResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "IsAdmin"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ai.rever.boss.ipc.proto.services.IsAdminResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuthServiceMethodDescriptorSupplier("IsAdmin"))
              .build();
        }
      }
    }
    return getIsAdminMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static AuthServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AuthServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AuthServiceStub>() {
        @java.lang.Override
        public AuthServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AuthServiceStub(channel, callOptions);
        }
      };
    return AuthServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static AuthServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AuthServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AuthServiceBlockingV2Stub>() {
        @java.lang.Override
        public AuthServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AuthServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return AuthServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static AuthServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AuthServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AuthServiceBlockingStub>() {
        @java.lang.Override
        public AuthServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AuthServiceBlockingStub(channel, callOptions);
        }
      };
    return AuthServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static AuthServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AuthServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AuthServiceFutureStub>() {
        @java.lang.Override
        public AuthServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AuthServiceFutureStub(channel, callOptions);
        }
      };
    return AuthServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * AuthService provides authentication and authorization functionality.
   * This is the first service to be extracted to its own process (Phase 2).
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Get the current authentication state
     * </pre>
     */
    default void getAuthState(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.AuthStateResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetAuthStateMethod(), responseObserver);
    }

    /**
     * <pre>
     * Watch for authentication state changes (like StateFlow.collect across processes)
     * </pre>
     */
    default void watchAuthState(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.AuthStateResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getWatchAuthStateMethod(), responseObserver);
    }

    /**
     * <pre>
     * Sign in with email/password
     * </pre>
     */
    default void signIn(ai.rever.boss.ipc.proto.services.SignInRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SignInResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSignInMethod(), responseObserver);
    }

    /**
     * <pre>
     * Sign out the current user
     * </pre>
     */
    default void signOut(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SignOutResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSignOutMethod(), responseObserver);
    }

    /**
     * <pre>
     * Get the current user info
     * </pre>
     */
    default void getCurrentUser(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.UserInfoResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetCurrentUserMethod(), responseObserver);
    }

    /**
     * <pre>
     * Watch for current user changes
     * </pre>
     */
    default void watchCurrentUser(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.UserInfoResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getWatchCurrentUserMethod(), responseObserver);
    }

    /**
     * <pre>
     * Check if the current user has a specific permission
     * </pre>
     */
    default void hasPermission(ai.rever.boss.ipc.proto.services.PermissionRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.PermissionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHasPermissionMethod(), responseObserver);
    }

    /**
     * <pre>
     * Check if the current user has any of the specified permissions
     * </pre>
     */
    default void hasAnyPermission(ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.PermissionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHasAnyPermissionMethod(), responseObserver);
    }

    /**
     * <pre>
     * Get all permissions for the current user
     * </pre>
     */
    default void getUserPermissions(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.UserPermissionsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetUserPermissionsMethod(), responseObserver);
    }

    /**
     * <pre>
     * Check if the current user is an admin
     * </pre>
     */
    default void isAdmin(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.IsAdminResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getIsAdminMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service AuthService.
   * <pre>
   * AuthService provides authentication and authorization functionality.
   * This is the first service to be extracted to its own process (Phase 2).
   * </pre>
   */
  public static abstract class AuthServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return AuthServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service AuthService.
   * <pre>
   * AuthService provides authentication and authorization functionality.
   * This is the first service to be extracted to its own process (Phase 2).
   * </pre>
   */
  public static final class AuthServiceStub
      extends io.grpc.stub.AbstractAsyncStub<AuthServiceStub> {
    private AuthServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AuthServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AuthServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get the current authentication state
     * </pre>
     */
    public void getAuthState(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.AuthStateResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetAuthStateMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Watch for authentication state changes (like StateFlow.collect across processes)
     * </pre>
     */
    public void watchAuthState(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.AuthStateResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getWatchAuthStateMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Sign in with email/password
     * </pre>
     */
    public void signIn(ai.rever.boss.ipc.proto.services.SignInRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SignInResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSignInMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Sign out the current user
     * </pre>
     */
    public void signOut(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SignOutResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSignOutMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Get the current user info
     * </pre>
     */
    public void getCurrentUser(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.UserInfoResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetCurrentUserMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Watch for current user changes
     * </pre>
     */
    public void watchCurrentUser(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.UserInfoResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getWatchCurrentUserMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Check if the current user has a specific permission
     * </pre>
     */
    public void hasPermission(ai.rever.boss.ipc.proto.services.PermissionRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.PermissionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHasPermissionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Check if the current user has any of the specified permissions
     * </pre>
     */
    public void hasAnyPermission(ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.PermissionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHasAnyPermissionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Get all permissions for the current user
     * </pre>
     */
    public void getUserPermissions(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.UserPermissionsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetUserPermissionsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Check if the current user is an admin
     * </pre>
     */
    public void isAdmin(ai.rever.boss.ipc.proto.Empty request,
        io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.IsAdminResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getIsAdminMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service AuthService.
   * <pre>
   * AuthService provides authentication and authorization functionality.
   * This is the first service to be extracted to its own process (Phase 2).
   * </pre>
   */
  public static final class AuthServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<AuthServiceBlockingV2Stub> {
    private AuthServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AuthServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AuthServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Get the current authentication state
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.AuthStateResponse getAuthState(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetAuthStateMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Watch for authentication state changes (like StateFlow.collect across processes)
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.services.AuthStateResponse>
        watchAuthState(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getWatchAuthStateMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Sign in with email/password
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.SignInResponse signIn(ai.rever.boss.ipc.proto.services.SignInRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSignInMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Sign out the current user
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.SignOutResponse signOut(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSignOutMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get the current user info
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.UserInfoResponse getCurrentUser(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetCurrentUserMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Watch for current user changes
     * </pre>
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, ai.rever.boss.ipc.proto.services.UserInfoResponse>
        watchCurrentUser(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getWatchCurrentUserMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Check if the current user has a specific permission
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.PermissionResponse hasPermission(ai.rever.boss.ipc.proto.services.PermissionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHasPermissionMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Check if the current user has any of the specified permissions
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.PermissionResponse hasAnyPermission(ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHasAnyPermissionMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get all permissions for the current user
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.UserPermissionsResponse getUserPermissions(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetUserPermissionsMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Check if the current user is an admin
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.IsAdminResponse isAdmin(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getIsAdminMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service AuthService.
   * <pre>
   * AuthService provides authentication and authorization functionality.
   * This is the first service to be extracted to its own process (Phase 2).
   * </pre>
   */
  public static final class AuthServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<AuthServiceBlockingStub> {
    private AuthServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AuthServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AuthServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get the current authentication state
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.AuthStateResponse getAuthState(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetAuthStateMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Watch for authentication state changes (like StateFlow.collect across processes)
     * </pre>
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.services.AuthStateResponse> watchAuthState(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getWatchAuthStateMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Sign in with email/password
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.SignInResponse signIn(ai.rever.boss.ipc.proto.services.SignInRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSignInMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Sign out the current user
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.SignOutResponse signOut(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSignOutMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get the current user info
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.UserInfoResponse getCurrentUser(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetCurrentUserMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Watch for current user changes
     * </pre>
     */
    public java.util.Iterator<ai.rever.boss.ipc.proto.services.UserInfoResponse> watchCurrentUser(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getWatchCurrentUserMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Check if the current user has a specific permission
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.PermissionResponse hasPermission(ai.rever.boss.ipc.proto.services.PermissionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHasPermissionMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Check if the current user has any of the specified permissions
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.PermissionResponse hasAnyPermission(ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHasAnyPermissionMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get all permissions for the current user
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.UserPermissionsResponse getUserPermissions(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetUserPermissionsMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Check if the current user is an admin
     * </pre>
     */
    public ai.rever.boss.ipc.proto.services.IsAdminResponse isAdmin(ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getIsAdminMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service AuthService.
   * <pre>
   * AuthService provides authentication and authorization functionality.
   * This is the first service to be extracted to its own process (Phase 2).
   * </pre>
   */
  public static final class AuthServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<AuthServiceFutureStub> {
    private AuthServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AuthServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AuthServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Get the current authentication state
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.AuthStateResponse> getAuthState(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetAuthStateMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Sign in with email/password
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.SignInResponse> signIn(
        ai.rever.boss.ipc.proto.services.SignInRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSignInMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Sign out the current user
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.SignOutResponse> signOut(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSignOutMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Get the current user info
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.UserInfoResponse> getCurrentUser(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetCurrentUserMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Check if the current user has a specific permission
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.PermissionResponse> hasPermission(
        ai.rever.boss.ipc.proto.services.PermissionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHasPermissionMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Check if the current user has any of the specified permissions
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.PermissionResponse> hasAnyPermission(
        ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHasAnyPermissionMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Get all permissions for the current user
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.UserPermissionsResponse> getUserPermissions(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetUserPermissionsMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Check if the current user is an admin
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<ai.rever.boss.ipc.proto.services.IsAdminResponse> isAdmin(
        ai.rever.boss.ipc.proto.Empty request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getIsAdminMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_AUTH_STATE = 0;
  private static final int METHODID_WATCH_AUTH_STATE = 1;
  private static final int METHODID_SIGN_IN = 2;
  private static final int METHODID_SIGN_OUT = 3;
  private static final int METHODID_GET_CURRENT_USER = 4;
  private static final int METHODID_WATCH_CURRENT_USER = 5;
  private static final int METHODID_HAS_PERMISSION = 6;
  private static final int METHODID_HAS_ANY_PERMISSION = 7;
  private static final int METHODID_GET_USER_PERMISSIONS = 8;
  private static final int METHODID_IS_ADMIN = 9;

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
        case METHODID_GET_AUTH_STATE:
          serviceImpl.getAuthState((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.AuthStateResponse>) responseObserver);
          break;
        case METHODID_WATCH_AUTH_STATE:
          serviceImpl.watchAuthState((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.AuthStateResponse>) responseObserver);
          break;
        case METHODID_SIGN_IN:
          serviceImpl.signIn((ai.rever.boss.ipc.proto.services.SignInRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SignInResponse>) responseObserver);
          break;
        case METHODID_SIGN_OUT:
          serviceImpl.signOut((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.SignOutResponse>) responseObserver);
          break;
        case METHODID_GET_CURRENT_USER:
          serviceImpl.getCurrentUser((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.UserInfoResponse>) responseObserver);
          break;
        case METHODID_WATCH_CURRENT_USER:
          serviceImpl.watchCurrentUser((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.UserInfoResponse>) responseObserver);
          break;
        case METHODID_HAS_PERMISSION:
          serviceImpl.hasPermission((ai.rever.boss.ipc.proto.services.PermissionRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.PermissionResponse>) responseObserver);
          break;
        case METHODID_HAS_ANY_PERMISSION:
          serviceImpl.hasAnyPermission((ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.PermissionResponse>) responseObserver);
          break;
        case METHODID_GET_USER_PERMISSIONS:
          serviceImpl.getUserPermissions((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.UserPermissionsResponse>) responseObserver);
          break;
        case METHODID_IS_ADMIN:
          serviceImpl.isAdmin((ai.rever.boss.ipc.proto.Empty) request,
              (io.grpc.stub.StreamObserver<ai.rever.boss.ipc.proto.services.IsAdminResponse>) responseObserver);
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
          getGetAuthStateMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.AuthStateResponse>(
                service, METHODID_GET_AUTH_STATE)))
        .addMethod(
          getWatchAuthStateMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.AuthStateResponse>(
                service, METHODID_WATCH_AUTH_STATE)))
        .addMethod(
          getSignInMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.SignInRequest,
              ai.rever.boss.ipc.proto.services.SignInResponse>(
                service, METHODID_SIGN_IN)))
        .addMethod(
          getSignOutMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.SignOutResponse>(
                service, METHODID_SIGN_OUT)))
        .addMethod(
          getGetCurrentUserMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.UserInfoResponse>(
                service, METHODID_GET_CURRENT_USER)))
        .addMethod(
          getWatchCurrentUserMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.UserInfoResponse>(
                service, METHODID_WATCH_CURRENT_USER)))
        .addMethod(
          getHasPermissionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.PermissionRequest,
              ai.rever.boss.ipc.proto.services.PermissionResponse>(
                service, METHODID_HAS_PERMISSION)))
        .addMethod(
          getHasAnyPermissionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.services.HasAnyPermissionRequest,
              ai.rever.boss.ipc.proto.services.PermissionResponse>(
                service, METHODID_HAS_ANY_PERMISSION)))
        .addMethod(
          getGetUserPermissionsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.UserPermissionsResponse>(
                service, METHODID_GET_USER_PERMISSIONS)))
        .addMethod(
          getIsAdminMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              ai.rever.boss.ipc.proto.Empty,
              ai.rever.boss.ipc.proto.services.IsAdminResponse>(
                service, METHODID_IS_ADMIN)))
        .build();
  }

  private static abstract class AuthServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    AuthServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return ai.rever.boss.ipc.proto.services.Auth.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("AuthService");
    }
  }

  private static final class AuthServiceFileDescriptorSupplier
      extends AuthServiceBaseDescriptorSupplier {
    AuthServiceFileDescriptorSupplier() {}
  }

  private static final class AuthServiceMethodDescriptorSupplier
      extends AuthServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    AuthServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (AuthServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new AuthServiceFileDescriptorSupplier())
              .addMethod(getGetAuthStateMethod())
              .addMethod(getWatchAuthStateMethod())
              .addMethod(getSignInMethod())
              .addMethod(getSignOutMethod())
              .addMethod(getGetCurrentUserMethod())
              .addMethod(getWatchCurrentUserMethod())
              .addMethod(getHasPermissionMethod())
              .addMethod(getHasAnyPermissionMethod())
              .addMethod(getGetUserPermissionsMethod())
              .addMethod(getIsAdminMethod())
              .build();
        }
      }
    }
    return result;
  }
}
