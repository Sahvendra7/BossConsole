package ai.rever.boss.ipc

import ai.rever.boss.ipc.proto.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

/**
 * Bootstrap utility for child processes (services, apps, plugins).
 *
 * Every child process uses this to:
 * 1. Read kernel IPC address from environment
 * 2. Connect to the kernel
 * 3. Register itself with its manifest
 * 4. Start heartbeat stream
 * 5. Start its own gRPC server for incoming calls
 *
 * Usage:
 * ```kotlin
 * fun main() {
 *     val bootstrap = ChildProcessBootstrap()
 *     runBlocking {
 *         val connection = bootstrap.connect(manifest)
 *         // connection.kernelChannel is ready for making calls
 *         // connection.processServer is ready for receiving calls
 *         connection.awaitTermination()
 *     }
 * }
 * ```
 */
class ChildProcessBootstrap {

    private val logger = LoggerFactory.getLogger(ChildProcessBootstrap::class.java)

    val processId: String = System.getenv("BOSS_PROCESS_ID")
        ?: throw IllegalStateException("BOSS_PROCESS_ID environment variable not set")

    val processType: String = System.getenv("BOSS_PROCESS_TYPE")
        ?: throw IllegalStateException("BOSS_PROCESS_TYPE environment variable not set")

    val kernelAddress: String = System.getenv("BOSS_KERNEL_IPC_ADDR")
        ?: throw IllegalStateException("BOSS_KERNEL_IPC_ADDR environment variable not set")

    val processAddress: String = System.getenv("BOSS_IPC_ADDR")
        ?: IpcAddressResolver.resolveAddress(processType.lowercase(), processId)

    /**
     * Connect to the kernel, register, and start heartbeat.
     */
    suspend fun connect(
        manifest: ProcessManifest,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    ): ChildProcessConnection {
        logger.info("Connecting to kernel at: {}", kernelAddress)

        // Connect to kernel
        val kernelClient = BossIpcClient(kernelAddress)
        if (!kernelClient.waitForReady(30_000)) {
            throw IllegalStateException("Failed to connect to kernel at $kernelAddress")
        }
        logger.info("Connected to kernel")

        // Register with kernel
        val kernelStub = KernelServiceGrpcKt.KernelServiceCoroutineStub(kernelClient.channel)
        val registerResponse = kernelStub.registerProcess(
            RegisterProcessRequest.newBuilder()
                .setManifest(manifest)
                .setIpcAddress(processAddress)
                .build()
        )

        if (!registerResponse.success) {
            throw IllegalStateException(
                "Failed to register with kernel: ${registerResponse.errorMessage}"
            )
        }
        logger.info("Registered with kernel. Service addresses: {}", registerResponse.serviceAddressesMap)

        // Start heartbeat
        val heartbeatJob = scope.launch {
            startHeartbeat(kernelStub, manifest)
        }

        // Create process gRPC server
        val processServer = BossIpcServer(processAddress)

        return ChildProcessConnection(
            processId = processId,
            kernelClient = kernelClient,
            kernelStub = kernelStub,
            processServer = processServer,
            heartbeatJob = heartbeatJob,
            serviceAddresses = registerResponse.serviceAddressesMap,
            scope = scope,
        )
    }

    private suspend fun startHeartbeat(
        kernelStub: KernelServiceGrpcKt.KernelServiceCoroutineStub,
        manifest: ProcessManifest,
    ) {
        val intervalMs = manifest.healthContract.heartbeatIntervalMs.takeIf { it > 0 } ?: 5000L

        try {
            val pingFlow = flow {
                while (currentCoroutineContext().isActive) {
                    emit(
                        HeartbeatPing.newBuilder()
                            .setProcessId(processId)
                            .setTimestamp(System.currentTimeMillis())
                            .setMetrics(collectHealthMetrics())
                            .build()
                    )
                    delay(intervalMs)
                }
            }

            kernelStub.heartbeat(pingFlow).collect { pong ->
                if (!pong.acknowledged) {
                    logger.warn("Heartbeat not acknowledged by kernel")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Heartbeat stream failed", e)
        }
    }

    private fun collectHealthMetrics(): ProcessHealthMetrics {
        val runtime = Runtime.getRuntime()
        return ProcessHealthMetrics.newBuilder()
            .setHeapUsedBytes(runtime.totalMemory() - runtime.freeMemory())
            .setHeapMaxBytes(runtime.maxMemory())
            .setActiveThreads(Thread.activeCount())
            .setUptimeMs(System.currentTimeMillis()) // Approximate
            .build()
    }
}

/**
 * Represents an established connection from a child process to the kernel.
 */
class ChildProcessConnection(
    val processId: String,
    val kernelClient: BossIpcClient,
    val kernelStub: KernelServiceGrpcKt.KernelServiceCoroutineStub,
    val processServer: BossIpcServer,
    val heartbeatJob: Job,
    val serviceAddresses: Map<String, String>,
    private val scope: CoroutineScope,
) {
    /**
     * Start the process's own gRPC server (after adding services to processServer).
     */
    fun startServer(): ChildProcessConnection {
        processServer.start()
        return this
    }

    /**
     * Wait for the process to terminate.
     */
    fun awaitTermination() {
        processServer.awaitTermination()
    }

    /**
     * Create a client to another service process using its address from the kernel.
     */
    fun connectToService(serviceName: String): BossIpcClient? {
        val address = serviceAddresses[serviceName] ?: return null
        return BossIpcClient(address)
    }

    /**
     * Gracefully shut down the connection.
     */
    fun shutdown() {
        heartbeatJob.cancel()
        processServer.stop()
        kernelClient.shutdown()
        scope.cancel()
    }
}
