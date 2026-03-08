package ai.rever.boss.service.filesystem

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.*
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

/**
 * gRPC implementation of FileSystemService.
 * Phase 6 skeleton — real file I/O using Java NIO, with skeleton for WatchFileChanges.
 */
class FileSystemServiceImpl : FileSystemServiceGrpcKt.FileSystemServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(FileSystemServiceImpl::class.java)

    override suspend fun scanDirectory(request: ScanDirectoryRequest): ScanDirectoryResponse =
        withContext(Dispatchers.IO) {
            logger.debug("scanDirectory: path={}, recursive={}", request.path, request.recursive)
            val dir = File(request.path)
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext ScanDirectoryResponse.newBuilder()
                    .setErrorMessage("Directory not found: ${request.path}")
                    .build()
            }

            val maxDepth = if (request.maxDepth > 0) request.maxDepth else Int.MAX_VALUE
            val sequence: Sequence<File> = if (request.recursive) {
                dir.walkTopDown().maxDepth(maxDepth)
            } else {
                dir.listFiles()?.asSequence() ?: emptySequence()
            }

            val filterExtensions = request.extensionsList.toSet()

            val entries = sequence
                .filter { it != dir }
                .filter { request.includeHidden || !it.name.startsWith(".") }
                .filter { it.isDirectory || filterExtensions.isEmpty() || it.extension in filterExtensions }
                .map { f ->
                    FileEntry.newBuilder()
                        .setPath(f.absolutePath)
                        .setName(f.name)
                        .setIsDirectory(f.isDirectory)
                        .setSizeBytes(if (f.isDirectory) 0L else f.length())
                        .setModifiedAt(f.lastModified())
                        .setIsHidden(f.name.startsWith("."))
                        .build()
                }.toList()

            ScanDirectoryResponse.newBuilder()
                .addAllEntries(entries)
                .build()
        }

    override suspend fun readFile(request: ReadFileRequest): ReadFileResponse =
        withContext(Dispatchers.IO) {
            logger.debug("readFile: path={}", request.path)
            val file = File(request.path)
            if (!file.exists()) {
                return@withContext ReadFileResponse.newBuilder()
                    .setErrorMessage("File not found: ${request.path}")
                    .build()
            }
            return@withContext try {
                val totalSize = file.length()
                val bytes = file.readBytes()
                val offsetBytes = request.offsetBytes.coerceAtLeast(0L).toInt()
                val slice = if (offsetBytes > 0 && offsetBytes < bytes.size) bytes.drop(offsetBytes).toByteArray() else bytes
                val maxBytes = request.maxBytes
                val (content, truncated) = if (maxBytes > 0 && slice.size > maxBytes) {
                    slice.take(maxBytes.toInt()).toByteArray() to true
                } else {
                    slice to false
                }
                ReadFileResponse.newBuilder()
                    .setContent(ByteString.copyFrom(content))
                    .setTotalSizeBytes(totalSize)
                    .setTruncated(truncated)
                    .build()
            } catch (e: Exception) {
                ReadFileResponse.newBuilder()
                    .setErrorMessage(e.message ?: "Read failed")
                    .build()
            }
        }

    override suspend fun writeFile(request: WriteFileRequest): WriteFileResponse =
        withContext(Dispatchers.IO) {
            logger.debug("writeFile: path={}", request.path)
            return@withContext try {
                val file = File(request.path)
                if (request.createParents) file.parentFile?.mkdirs()
                if (!request.overwrite && file.exists()) {
                    return@withContext WriteFileResponse.newBuilder()
                        .setSuccess(false)
                        .setErrorMessage("File already exists: ${request.path}")
                        .build()
                }
                val bytes = request.content.toByteArray()
                file.writeBytes(bytes)
                WriteFileResponse.newBuilder()
                    .setSuccess(true)
                    .setBytesWritten(bytes.size.toLong())
                    .build()
            } catch (e: Exception) {
                WriteFileResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage(e.message ?: "Write failed")
                    .build()
            }
        }

    override suspend fun createFile(request: CreateFileRequest): Empty =
        withContext(Dispatchers.IO) {
            logger.info("createFile: path={}, isDirectory={}", request.path, request.isDirectory)
            val file = File(request.path)
            if (request.createParents) file.parentFile?.mkdirs()
            if (request.isDirectory) file.mkdirs() else file.createNewFile()
            Empty.getDefaultInstance()
        }

    override suspend fun deleteFile(request: DeleteFileRequest): Empty =
        withContext(Dispatchers.IO) {
            logger.info("deleteFile: path={}, recursive={}", request.path, request.recursive)
            val file = File(request.path)
            if (request.recursive && file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            Empty.getDefaultInstance()
        }

    override suspend fun renameFile(request: RenameFileRequest): Empty =
        withContext(Dispatchers.IO) {
            logger.info("renameFile: from={}, to={}", request.sourcePath, request.destinationPath)
            val dest = File(request.destinationPath)
            if (!request.overwrite && dest.exists()) {
                throw IllegalStateException("Destination already exists: ${request.destinationPath}")
            }
            File(request.sourcePath).renameTo(dest)
            Empty.getDefaultInstance()
        }

    override fun watchFileChanges(request: WatchFileChangesRequest): Flow<FileChangeEvent> {
        logger.info("watchFileChanges: path={}, recursive={}", request.path, request.recursive)
        // WatchService integration pending — returns empty stream for now
        return emptyFlow()
    }
}
