package ai.rever.boss.ipc

import io.grpc.BindableService
import io.grpc.Server
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * gRPC server wrapper that supports Unix domain sockets (macOS/Linux) and TCP (Windows).
 *
 * Usage:
 * ```kotlin
 * val server = BossIpcServer(address)
 *     .addService(MyServiceImpl())
 *     .start()
 * ```
 */
class BossIpcServer(private val address: String) {

    private val logger = LoggerFactory.getLogger(BossIpcServer::class.java)
    private val services = mutableListOf<BindableService>()
    private var server: Server? = null

    fun addService(service: BindableService): BossIpcServer {
        services.add(service)
        return this
    }

    fun start(): BossIpcServer {
        val builder = IpcAddressResolver.configureServerBuilder(address)

        services.forEach { builder.addService(it) }

        server = builder.build().start()
        logger.info("IPC server started on: {}", address)
        // Set owner-only permissions on Unix socket to prevent other local users from connecting
        IpcAddressResolver.secureSocketFile(address)

        return this
    }

    fun stop(timeoutMs: Long = 5000) {
        server?.let { s ->
            logger.info("Shutting down IPC server on: {}", address)
            s.shutdown()
            if (!s.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                logger.warn("IPC server did not terminate in {}ms, forcing shutdown", timeoutMs)
                s.shutdownNow()
            }
        }
        IpcAddressResolver.cleanupAddress(address)
        server = null
    }

    fun awaitTermination() {
        server?.awaitTermination()
    }

    val isRunning: Boolean
        get() = server?.isShutdown == false

    val port: Int
        get() = server?.port ?: -1
}
