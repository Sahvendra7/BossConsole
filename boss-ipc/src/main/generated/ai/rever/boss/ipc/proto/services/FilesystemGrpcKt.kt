package ai.rever.boss.ipc.proto.services

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.FileSystemServiceGrpc.getServiceDescriptor
import io.grpc.CallOptions
import io.grpc.CallOptions.DEFAULT
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.ServerServiceDefinition.builder
import io.grpc.ServiceDescriptor
import io.grpc.Status.UNIMPLEMENTED
import io.grpc.StatusException
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.AbstractCoroutineStub
import io.grpc.kotlin.ClientCalls.serverStreamingRpc
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls.serverStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.String
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow

/**
 * Holder for Kotlin coroutine-based client and server APIs for
 * boss.ipc.v1.services.FileSystemService.
 */
public object FileSystemServiceGrpcKt {
  public const val SERVICE_NAME: String = FileSystemServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val scanDirectoryMethod: MethodDescriptor<ScanDirectoryRequest, ScanDirectoryResponse>
    @JvmStatic
    get() = FileSystemServiceGrpc.getScanDirectoryMethod()

  public val readFileMethod: MethodDescriptor<ReadFileRequest, ReadFileResponse>
    @JvmStatic
    get() = FileSystemServiceGrpc.getReadFileMethod()

  public val writeFileMethod: MethodDescriptor<WriteFileRequest, WriteFileResponse>
    @JvmStatic
    get() = FileSystemServiceGrpc.getWriteFileMethod()

  public val createFileMethod: MethodDescriptor<CreateFileRequest, Empty>
    @JvmStatic
    get() = FileSystemServiceGrpc.getCreateFileMethod()

  public val deleteFileMethod: MethodDescriptor<DeleteFileRequest, Empty>
    @JvmStatic
    get() = FileSystemServiceGrpc.getDeleteFileMethod()

  public val renameFileMethod: MethodDescriptor<RenameFileRequest, Empty>
    @JvmStatic
    get() = FileSystemServiceGrpc.getRenameFileMethod()

  public val watchFileChangesMethod: MethodDescriptor<WatchFileChangesRequest, FileChangeEvent>
    @JvmStatic
    get() = FileSystemServiceGrpc.getWatchFileChangesMethod()

  /**
   * A stub for issuing RPCs to a(n) boss.ipc.v1.services.FileSystemService service as suspending
   * coroutines.
   */
  @StubFor(FileSystemServiceGrpc::class)
  public class FileSystemServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<FileSystemServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): FileSystemServiceCoroutineStub =
        FileSystemServiceCoroutineStub(channel, callOptions)

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun scanDirectory(request: ScanDirectoryRequest, headers: Metadata = Metadata()):
        ScanDirectoryResponse = unaryRpc(
      channel,
      FileSystemServiceGrpc.getScanDirectoryMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun readFile(request: ReadFileRequest, headers: Metadata = Metadata()):
        ReadFileResponse = unaryRpc(
      channel,
      FileSystemServiceGrpc.getReadFileMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun writeFile(request: WriteFileRequest, headers: Metadata = Metadata()):
        WriteFileResponse = unaryRpc(
      channel,
      FileSystemServiceGrpc.getWriteFileMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun createFile(request: CreateFileRequest, headers: Metadata = Metadata()): Empty
        = unaryRpc(
      channel,
      FileSystemServiceGrpc.getCreateFileMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun deleteFile(request: DeleteFileRequest, headers: Metadata = Metadata()): Empty
        = unaryRpc(
      channel,
      FileSystemServiceGrpc.getDeleteFileMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun renameFile(request: RenameFileRequest, headers: Metadata = Metadata()): Empty
        = unaryRpc(
      channel,
      FileSystemServiceGrpc.getRenameFileMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][io.grpc.Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    public fun watchFileChanges(request: WatchFileChangesRequest, headers: Metadata = Metadata()):
        Flow<FileChangeEvent> = serverStreamingRpc(
      channel,
      FileSystemServiceGrpc.getWatchFileChangesMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the boss.ipc.v1.services.FileSystemService service based on Kotlin
   * coroutines.
   */
  public abstract class FileSystemServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for boss.ipc.v1.services.FileSystemService.ScanDirectory.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun scanDirectory(request: ScanDirectoryRequest): ScanDirectoryResponse =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.FileSystemService.ScanDirectory is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.FileSystemService.ReadFile.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun readFile(request: ReadFileRequest): ReadFileResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.FileSystemService.ReadFile is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.FileSystemService.WriteFile.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun writeFile(request: WriteFileRequest): WriteFileResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.FileSystemService.WriteFile is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.FileSystemService.CreateFile.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun createFile(request: CreateFileRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.FileSystemService.CreateFile is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.FileSystemService.DeleteFile.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun deleteFile(request: DeleteFileRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.FileSystemService.DeleteFile is unimplemented"))

    /**
     * Returns the response to an RPC for boss.ipc.v1.services.FileSystemService.RenameFile.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun renameFile(request: RenameFileRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.FileSystemService.RenameFile is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * boss.ipc.v1.services.FileSystemService.WatchFileChanges.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [io.grpc.Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open fun watchFileChanges(request: WatchFileChangesRequest): Flow<FileChangeEvent> =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method boss.ipc.v1.services.FileSystemService.WatchFileChanges is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = FileSystemServiceGrpc.getScanDirectoryMethod(),
      implementation = ::scanDirectory
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = FileSystemServiceGrpc.getReadFileMethod(),
      implementation = ::readFile
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = FileSystemServiceGrpc.getWriteFileMethod(),
      implementation = ::writeFile
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = FileSystemServiceGrpc.getCreateFileMethod(),
      implementation = ::createFile
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = FileSystemServiceGrpc.getDeleteFileMethod(),
      implementation = ::deleteFile
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = FileSystemServiceGrpc.getRenameFileMethod(),
      implementation = ::renameFile
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = FileSystemServiceGrpc.getWatchFileChangesMethod(),
      implementation = ::watchFileChanges
    )).build()
  }
}
