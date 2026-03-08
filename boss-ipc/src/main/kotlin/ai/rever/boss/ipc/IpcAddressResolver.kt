package ai.rever.boss.ipc

import io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.NettyServerBuilder
import io.netty.channel.epoll.EpollDomainSocketChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerDomainSocketChannel
import io.netty.channel.kqueue.KQueueDomainSocketChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel
import io.netty.channel.unix.DomainSocketAddress
import java.io.File
import java.net.InetSocketAddress

/**
 * Resolves IPC addresses for inter-process communication.
 *
 * On macOS/Linux: Uses Unix domain sockets for zero-overhead local IPC.
 * On Windows: Falls back to TCP localhost.
 *
 * UDS path convention: $BOSS_DATA_DIR/ipc/boss-{type}-{id}.sock
 */
object IpcAddressResolver {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")
    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")

    /** Base directory for IPC socket files */
    private val ipcDir: File by lazy {
        val bossDataDir = System.getenv("BOSS_DATA_DIR")
            ?: System.getProperty("boss.data.dir")
            ?: "${System.getProperty("user.home")}/.boss"
        File(bossDataDir, "ipc").also { it.mkdirs() }
    }

    /** TCP port range for Windows fallback */
    private const val TCP_PORT_BASE = 57000
    private const val TCP_PORT_RANGE = 100

    /**
     * Get the IPC address for a process.
     * Returns a UDS path on macOS/Linux, or TCP localhost address on Windows.
     */
    fun resolveAddress(processType: String, processId: String): String {
        return if (isWindows) {
            "tcp://localhost:${findAvailableTcpPort()}"
        } else {
            val socketFile = File(ipcDir, "boss-${processType}-${processId}.sock")
            "unix://${socketFile.absolutePath}"
        }
    }

    /**
     * Get the kernel's IPC address from environment or generate one.
     */
    fun kernelAddress(): String {
        // Check if kernel address is provided (child process scenario)
        System.getenv("BOSS_KERNEL_IPC_ADDR")?.let { return it }

        return resolveAddress("kernel", "main")
    }

    /**
     * Parse an IPC address string into a form usable by gRPC Netty.
     */
    fun parseAddress(address: String): Any {
        return when {
            address.startsWith("unix://") -> {
                val path = address.removePrefix("unix://")
                DomainSocketAddress(path)
            }
            address.startsWith("tcp://") -> {
                val hostPort = address.removePrefix("tcp://")
                val parts = hostPort.split(":")
                InetSocketAddress(parts[0], parts[1].toInt())
            }
            else -> throw IllegalArgumentException("Unknown IPC address format: $address")
        }
    }

    /**
     * Configure a NettyServerBuilder for the given address.
     */
    fun configureServerBuilder(address: String): NettyServerBuilder {
        val parsed = parseAddress(address)
        return when (parsed) {
            is DomainSocketAddress -> {
                // Clean up stale socket file
                File(parsed.path()).delete()

                when {
                    isMacOS -> NettyServerBuilder.forAddress(parsed)
                        .channelType(KQueueServerDomainSocketChannel::class.java)
                        .bossEventLoopGroup(KQueueEventLoopGroup(1))
                        .workerEventLoopGroup(KQueueEventLoopGroup())

                    isLinux -> NettyServerBuilder.forAddress(parsed)
                        .channelType(EpollServerDomainSocketChannel::class.java)
                        .bossEventLoopGroup(EpollEventLoopGroup(1))
                        .workerEventLoopGroup(EpollEventLoopGroup())

                    else -> throw UnsupportedOperationException(
                        "Unix domain sockets not supported on this platform"
                    )
                }
            }
            is InetSocketAddress -> NettyServerBuilder.forAddress(parsed)
            else -> throw IllegalArgumentException("Unknown address type: $parsed")
        }
    }

    /**
     * Configure a NettyChannelBuilder for the given address.
     */
    fun configureChannelBuilder(address: String): NettyChannelBuilder {
        val parsed = parseAddress(address)
        return when (parsed) {
            is DomainSocketAddress -> {
                when {
                    isMacOS -> NettyChannelBuilder.forAddress(parsed)
                        .channelType(KQueueDomainSocketChannel::class.java)
                        .eventLoopGroup(KQueueEventLoopGroup())

                    isLinux -> NettyChannelBuilder.forAddress(parsed)
                        .channelType(EpollDomainSocketChannel::class.java)
                        .eventLoopGroup(EpollEventLoopGroup())

                    else -> throw UnsupportedOperationException(
                        "Unix domain sockets not supported on this platform"
                    )
                }
            }
            is InetSocketAddress -> NettyChannelBuilder.forAddress(parsed)
            else -> throw IllegalArgumentException("Unknown address type: $parsed")
        }
    }

    /**
     * Clean up socket file on shutdown.
     */
    fun cleanupAddress(address: String) {
        if (address.startsWith("unix://")) {
            val path = address.removePrefix("unix://")
            File(path).delete()
        }
    }

    private fun findAvailableTcpPort(): Int {
        for (port in TCP_PORT_BASE until TCP_PORT_BASE + TCP_PORT_RANGE) {
            try {
                java.net.ServerSocket(port).use { return port }
            } catch (_: Exception) {
                continue
            }
        }
        throw IllegalStateException("No available TCP ports in range $TCP_PORT_BASE-${TCP_PORT_BASE + TCP_PORT_RANGE}")
    }
}
