package ai.rever.boss.utils

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/**
 * Manages single-instance application behavior
 *
 * Uses file-based locking combined with TCP IPC to ensure only one instance of BOSS runs
 * and to pass URLs between instances.
 *
 * Architecture:
 * - Lock file in temp directory prevents multiple instances
 * - TCP socket on localhost allows new instances to send URLs to existing instance
 * - Stale lock detection handles crashed processes
 *
 * Usage:
 * ```
 * if (!SingleInstanceManager.acquireLock()) {
 *     // Another instance is running
 *     SingleInstanceManager.sendToExistingInstance("boss://auth/verify?token=...")
 *     exitProcess(0)
 * }
 *
 * // First instance - start listening
 * SingleInstanceManager.startListening { url ->
 *     DeepLinkHandler.processDeepLink(url)
 * }
 * ```
 */
object SingleInstanceManager {
    private const val LOCK_FILE_NAME = "boss-instance.lock"
    private const val IPC_PORT_BASE = 56789
    private const val IPC_PORT_RANGE = 10 // Try ports 56789-56798
    private const val CONNECTION_TIMEOUT_MS = 10000 // 10 seconds - important for auth deep links

    private var serverSocket: ServerSocket? = null
    private var lockFile: File? = null
    private var actualPort: Int = IPC_PORT_BASE
    private var listenerThread: Thread? = null
    private var isListening: Boolean = false

    /**
     * Check if another instance of BOSS is already running
     * Does not acquire the lock - use acquireLock() for that
     */
    fun isAnotherInstanceRunning(): Boolean {
        val tempDir = System.getProperty("java.io.tmpdir")
        val lock = File(tempDir, LOCK_FILE_NAME)

        if (!lock.exists()) {
            return false
        }

        // Check if the process is still alive
        return try {
            val pid = lock.readText().trim().toLongOrNull()
            if (pid != null && isProcessAlive(pid)) {
                true
            } else {
                // Stale lock file
                println("Stale lock file detected (PID: $pid)")
                false
            }
        } catch (e: Exception) {
            println("Error checking lock file: ${e.message}")
            false
        }
    }

    /**
     * Try to acquire the single-instance lock
     * Returns true if we're the first instance, false if another instance is already running
     *
     * If successful, automatically starts the IPC server
     */
    fun acquireLock(): Boolean {
        val tempDir = System.getProperty("java.io.tmpdir")
        lockFile = File(tempDir, LOCK_FILE_NAME)

        return try {
            // Check for existing instance
            if (lockFile!!.exists()) {
                val pid = lockFile!!.readText().trim().toLongOrNull()
                if (pid != null && isProcessAlive(pid)) {
                    println("Another instance is running (PID: $pid)")
                    return false
                } else {
                    // Stale lock - delete and continue
                    println("Removing stale lock file (PID: $pid)")
                    lockFile!!.delete()
                }
            }

            // Try to create lock file
            if (lockFile!!.createNewFile()) {
                lockFile!!.deleteOnExit()

                // Write current PID to lock file
                val currentPid = ProcessHandle.current().pid()
                lockFile!!.writeText("$currentPid\n$actualPort")
                println("Acquired single-instance lock (PID: $currentPid)")

                // Start IPC server
                startServer()
                true
            } else {
                println("Failed to create lock file")
                false
            }
        } catch (e: Exception) {
            println("Failed to acquire lock: ${e.message}")
            false
        }
    }

    /**
     * Start the IPC server to receive URLs from new instances
     * Automatically finds an available port in the configured range
     */
    private fun startServer() {
        // Try to bind to a port in the range
        var boundSuccessfully = false
        var lastException: Exception? = null

        for (port in IPC_PORT_BASE until (IPC_PORT_BASE + IPC_PORT_RANGE)) {
            try {
                serverSocket = ServerSocket(port, 5, InetAddress.getLoopbackAddress())
                actualPort = port
                boundSuccessfully = true
                println("IPC server started on port $port")

                // Update lock file with actual port atomically
                // Write PID and port together to prevent race condition
                lockFile?.let { file ->
                    val currentPid = ProcessHandle.current().pid()
                    file.writeText("$currentPid\n$actualPort")
                }

                break
            } catch (e: Exception) {
                lastException = e
                // Try next port
            }
        }

        if (!boundSuccessfully) {
            // Port exhaustion - clean up lock file and fail gracefully
            println("ERROR: Failed to start IPC server on any port in range $IPC_PORT_BASE-${IPC_PORT_BASE + IPC_PORT_RANGE - 1}")
            println("Last error: ${lastException?.message}")

            // Delete lock file so next instance can try again
            try {
                lockFile?.delete()
                lockFile = null
            } catch (e: Exception) {
                println("Failed to clean up lock file: ${e.message}")
            }

            throw java.io.IOException(
                "Unable to bind IPC server: All ports $IPC_PORT_BASE-${IPC_PORT_BASE + IPC_PORT_RANGE - 1} are in use. " +
                "This may indicate port conflicts or firewall issues. Original error: ${lastException?.message}"
            )
        }

        // Start listener thread
        isListening = true
        listenerThread = thread(isDaemon = true, name = "BOSS-IPC-Listener") {
            println("IPC listener thread started")

            while (isListening && !Thread.currentThread().isInterrupted) {
                try {
                    val clientSocket = serverSocket?.accept()
                    if (clientSocket != null) {
                        handleClient(clientSocket)
                    }
                } catch (e: SocketException) {
                    if (isListening) {
                        println("Socket error in IPC listener: ${e.message}")
                    }
                    break
                } catch (e: Exception) {
                    if (isListening) {
                        println("Error in IPC listener: ${e.message}")
                    }
                }
            }

            println("IPC listener thread stopped")
        }
    }

    /**
     * Handle incoming connection from a new instance
     */
    private fun handleClient(clientSocket: Socket) {
        thread(name = "BOSS-IPC-Client-Handler") {
            try {
                clientSocket.use { socket ->
                    socket.soTimeout = CONNECTION_TIMEOUT_MS

                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val url = reader.readLine()

                    if (url.isNullOrBlank()) {
                        println("Received empty URL from new instance")
                        return@use
                    }

                    println("Received URL from new instance: $url")

                    // Process the URL
                    if (url.startsWith("boss://") || url.startsWith("http://") || url.startsWith("https://")) {
                        DeepLinkHandler.processDeepLink(url)
                    } else {
                        println("Invalid URL protocol: $url")
                    }

                    // Send acknowledgment
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println("OK")
                }
            } catch (e: SocketTimeoutException) {
                println("Client connection timeout")
            } catch (e: Exception) {
                println("Error handling client: ${e.message}")
            }
        }
    }

    /**
     * Send a URL to the existing instance
     * Returns true if successfully sent, false otherwise
     */
    fun sendToExistingInstance(url: String): Boolean {
        if (url.isBlank()) {
            println("Cannot send empty URL")
            return false
        }

        // Read port from lock file
        val port = getPortFromLockFile() ?: IPC_PORT_BASE

        return try {
            println("Attempting to connect to existing instance on port $port...")

            // Create socket with connection timeout
            val socket = Socket()
            socket.connect(
                java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port),
                CONNECTION_TIMEOUT_MS
            )

            socket.use {
                // Set read timeout for acknowledgment
                it.soTimeout = CONNECTION_TIMEOUT_MS

                // Send URL
                val writer = PrintWriter(it.getOutputStream(), true)
                writer.println(url)
                println("Sent URL to existing instance")

                // Wait for acknowledgment
                val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                val response = reader.readLine()

                if (response == "OK") {
                    println("Existing instance acknowledged URL")
                    true
                } else {
                    println("Unexpected response from existing instance: $response")
                    false
                }
            }
        } catch (e: Exception) {
            println("Failed to send URL to existing instance: ${e.message}")
            false
        }
    }

    /**
     * Get the IPC port from the lock file
     */
    private fun getPortFromLockFile(): Int? {
        val tempDir = System.getProperty("java.io.tmpdir")
        val lock = File(tempDir, LOCK_FILE_NAME)

        if (!lock.exists()) {
            return null
        }

        return try {
            val lines = lock.readLines()
            if (lines.size >= 2) {
                lines[1].trim().toIntOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error reading port from lock file: ${e.message}")
            null
        }
    }

    /**
     * Check if a process with the given PID is alive
     */
    private fun isProcessAlive(pid: Long): Boolean {
        return try {
            ProcessHandle.of(pid)
                .map { it.isAlive }
                .orElse(false)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Start listening for URLs from new instances
     * The callback will be invoked on a background thread
     *
     * Note: This is automatically called by acquireLock() if successful
     */
    fun startListening(onUrlReceived: (String) -> Unit) {
        // IPC server is already started by acquireLock()
        // This method exists for API completeness but doesn't need to do anything
        println("Already listening for URLs via IPC server")
    }

    /**
     * Release the lock and stop the IPC server
     * Should be called on application shutdown
     */
    fun release() {
        println("Releasing single-instance lock...")

        // Stop listening
        isListening = false

        // Close server socket
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            println("Error closing server socket: ${e.message}")
        }

        // Wait for listener thread to finish
        try {
            listenerThread?.join(1000)
        } catch (e: Exception) {
            println("Error waiting for listener thread: ${e.message}")
        }

        // Delete lock file
        try {
            lockFile?.delete()
            lockFile = null
        } catch (e: Exception) {
            println("Error deleting lock file: ${e.message}")
        }

        println("Single-instance lock released")
    }
}
