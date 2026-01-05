package ai.rever.bosseditor.lsp.client

import ai.rever.bosseditor.lsp.logging.LspLogger
import ai.rever.bosseditor.lsp.logging.LogCategory
import ai.rever.bosseditor.lsp.protocol.*
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * LSP transport layer handling JSON-RPC framing over stdio.
 *
 * The LSP protocol uses a simple framing format:
 * ```
 * Content-Length: <length>\r\n
 * \r\n
 * <JSON content>
 * ```
 */
class LspTransport(
    private val input: InputStream,
    private val output: OutputStream,
    private val config: LspClientConfig = LspClientConfig()
) {
    private val logger = LspLogger.forComponent("LspTransport")

    companion object {
        /** Buffer capacity for notification SharedFlow */
        private const val NOTIFICATION_BUFFER_CAPACITY = 64

        /** Timeout for stop() cleanup operations in milliseconds */
        private const val STOP_TIMEOUT_MS = 1000L

        /** Maximum consecutive read errors before stopping transport */
        private const val MAX_CONSECUTIVE_ERRORS = 10

        /** Base delay for exponential backoff in milliseconds */
        private const val BACKOFF_BASE_MS = 10L

        /** Maximum backoff delay in milliseconds */
        private const val BACKOFF_MAX_MS = 1000L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val requestIdCounter = AtomicInteger(0)

    // Pending requests waiting for responses
    private val pendingRequests = mutableMapOf<Int, CompletableDeferred<ResponseMessage>>()
    private val pendingRequestsMutex = Mutex()

    // Mutex for thread-safe message sending
    private val sendMutex = Mutex()

    // Error tracking for backoff
    private var consecutiveErrors = 0

    // Channel for incoming messages
    private val incomingMessages = Channel<JsonElement>(Channel.BUFFERED)

    // Flow of notifications from server
    private val _notifications = MutableSharedFlow<Pair<String, JsonElement?>>(extraBufferCapacity = NOTIFICATION_BUFFER_CAPACITY)
    val notifications: SharedFlow<Pair<String, JsonElement?>> = _notifications.asSharedFlow()

    // Server request handlers
    private val requestHandlers = mutableMapOf<String, suspend (JsonElement?) -> JsonElement?>()

    @Volatile
    private var isRunning = false

    /**
     * Start the transport, beginning to read messages from input.
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        // Start reader coroutine
        scope.launch {
            try {
                readLoop()
            } catch (e: Exception) {
                if (isRunning) {
                    logger.error(LogCategory.TRANSPORT, "Read error", error = e)
                }
            }
        }

        // Start message processor
        scope.launch {
            processIncomingMessages()
        }
    }

    /**
     * Stop the transport and clean up resources.
     * Uses runBlocking with timeout for critical cleanup to ensure completion.
     */
    fun stop() {
        isRunning = false

        // Cancel pending requests synchronously with timeout to ensure completion
        try {
            runBlocking {
                withTimeout(STOP_TIMEOUT_MS) {
                    pendingRequestsMutex.withLock {
                        pendingRequests.values.forEach { deferred ->
                            deferred.completeExceptionally(LspConnectionException("Transport stopped"))
                        }
                        pendingRequests.clear()
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.TRANSPORT, "Error canceling pending requests", error = e)
        }

        scope.cancel()
        incomingMessages.close()

        // Close streams on IO dispatcher (fire-and-forget is OK for stream cleanup)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                input.close()
            } catch (e: Exception) {
                logger.warn(LogCategory.TRANSPORT, "Error closing input stream", error = e)
            }
            try {
                output.close()
            } catch (e: Exception) {
                logger.warn(LogCategory.TRANSPORT, "Error closing output stream", error = e)
            }
        }
    }

    /**
     * Send a request and wait for response.
     */
    suspend fun sendRequest(method: String, params: JsonElement?): JsonElement? {
        val id = requestIdCounter.incrementAndGet()
        val request = RequestMessage(
            id = id,
            method = method,
            params = params
        )

        val deferred = CompletableDeferred<ResponseMessage>()
        pendingRequestsMutex.withLock {
            if (pendingRequests.size >= config.maxPendingRequests) {
                throw LspException("Too many pending requests")
            }
            pendingRequests[id] = deferred
        }

        try {
            sendMessageSuspend(json.encodeToJsonElement(request))

            val timeoutMs = if (method == LspMethods.INITIALIZE) {
                config.initializeTimeoutMs
            } else {
                config.requestTimeoutMs
            }

            val response = withTimeout(timeoutMs) {
                deferred.await()
            }

            if (response.error != null) {
                throw LspRequestException(
                    method = method,
                    errorCode = response.error.code,
                    errorMessage = response.error.message,
                    errorData = response.error.data
                )
            }

            return response.result
        } catch (e: TimeoutCancellationException) {
            pendingRequestsMutex.withLock {
                pendingRequests.remove(id)
            }
            throw LspTimeoutException(method, config.requestTimeoutMs)
        } finally {
            pendingRequestsMutex.withLock {
                pendingRequests.remove(id)
            }
        }
    }

    /**
     * Send a notification (no response expected).
     * Uses fire-and-forget coroutine for non-blocking send.
     */
    fun sendNotification(method: String, params: JsonElement?) {
        val notification = NotificationMessage(
            method = method,
            params = params
        )
        scope.launch {
            sendMessageSuspend(json.encodeToJsonElement(notification))
        }
    }

    /**
     * Register a handler for server requests.
     */
    fun registerRequestHandler(method: String, handler: suspend (JsonElement?) -> JsonElement?) {
        requestHandlers[method] = handler
    }

    /**
     * Send a JSON message with LSP framing (suspend version with mutex).
     * Note: Scope already uses Dispatchers.IO, so no additional context switch needed.
     */
    private suspend fun sendMessageSuspend(message: JsonElement) {
        sendMutex.withLock {
            val content = json.encodeToString(message)
            val bytes = content.toByteArray(StandardCharsets.UTF_8)

            if (config.traceMessages) {
                logger.trace(LogCategory.PROTOCOL, ">>> $content")
            }

            val header = "Content-Length: ${bytes.size}\r\n\r\n"
            output.write(header.toByteArray(StandardCharsets.US_ASCII))
            output.write(bytes)
            output.flush()
        }
    }

    /**
     * Read loop - continuously read messages from input.
     * Implements exponential backoff for consecutive errors.
     */
    private suspend fun readLoop() {
        val reader = input.bufferedReader(StandardCharsets.UTF_8)

        while (isRunning && coroutineContext.isActive) {
            try {
                // Read headers
                val contentLength = readHeaders(reader) ?: continue

                // Read content
                val content = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(content, totalRead, contentLength - totalRead)
                    if (read == -1) {
                        throw LspConnectionException("Unexpected end of stream")
                    }
                    totalRead += read
                }

                val jsonString = String(content)
                if (config.traceMessages) {
                    logger.trace(LogCategory.PROTOCOL, "<<< $jsonString")
                }

                val jsonElement = json.parseToJsonElement(jsonString)
                incomingMessages.send(jsonElement)

                // Reset error count on successful read
                consecutiveErrors = 0

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isRunning) {
                    consecutiveErrors++
                    logger.warn(
                        LogCategory.TRANSPORT,
                        "Read error ($consecutiveErrors/$MAX_CONSECUTIVE_ERRORS)",
                        error = e
                    )

                    // Exponential backoff for consecutive errors
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        logger.error(LogCategory.TRANSPORT, "Too many consecutive errors, stopping transport")
                        isRunning = false
                        break
                    }

                    // Backoff delay: 10ms, 20ms, 40ms, 80ms, ... up to max
                    val backoffMs = minOf(BACKOFF_BASE_MS * (1L shl (consecutiveErrors - 1)), BACKOFF_MAX_MS)
                    delay(backoffMs)
                }
            }
        }
    }

    /**
     * Read LSP headers and return Content-Length.
     */
    private fun readHeaders(reader: java.io.BufferedReader): Int? {
        var contentLength: Int? = null

        while (true) {
            val line = reader.readLine() ?: return null

            if (line.isEmpty()) {
                // End of headers
                break
            }

            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toIntOrNull()
            }
            // Ignore other headers (Content-Type, etc.)
        }

        return contentLength
    }

    /**
     * Process incoming messages - dispatch to appropriate handlers.
     */
    private suspend fun processIncomingMessages() {
        for (message in incomingMessages) {
            try {
                when {
                    // Response to a request
                    message.jsonObject.containsKey("id") && !message.jsonObject.containsKey("method") -> {
                        handleResponse(message)
                    }
                    // Request from server
                    message.jsonObject.containsKey("id") && message.jsonObject.containsKey("method") -> {
                        handleServerRequest(message)
                    }
                    // Notification from server
                    message.jsonObject.containsKey("method") -> {
                        handleNotification(message)
                    }
                }
            } catch (e: Exception) {
                logger.error(LogCategory.PROTOCOL, "Error processing message", error = e)
            }
        }
    }

    /**
     * Handle a response to a pending request.
     */
    private suspend fun handleResponse(message: JsonElement) {
        val response = json.decodeFromJsonElement<ResponseMessage>(message)
        val id = response.id ?: return

        val deferred = pendingRequestsMutex.withLock {
            pendingRequests.remove(id)
        }

        deferred?.complete(response)
    }

    /**
     * Handle a request from the server.
     */
    private suspend fun handleServerRequest(message: JsonElement) {
        val request = json.decodeFromJsonElement<RequestMessage>(message)
        val handler = requestHandlers[request.method]

        val response = if (handler != null) {
            try {
                val result = handler(request.params)
                ResponseMessage(
                    id = request.id,
                    result = result
                )
            } catch (e: Exception) {
                ResponseMessage(
                    id = request.id,
                    error = ResponseError(
                        code = ErrorCodes.INTERNAL_ERROR,
                        message = e.message ?: "Internal error"
                    )
                )
            }
        } else {
            ResponseMessage(
                id = request.id,
                error = ResponseError(
                    code = ErrorCodes.METHOD_NOT_FOUND,
                    message = "Method not found: ${request.method}"
                )
            )
        }

        sendMessageSuspend(json.encodeToJsonElement(response))
    }

    /**
     * Handle a notification from the server.
     * Uses tryEmit to avoid suspension and handles buffer overflow gracefully.
     */
    private fun handleNotification(message: JsonElement) {
        val notification = json.decodeFromJsonElement<NotificationMessage>(message)
        val emitted = _notifications.tryEmit(notification.method to notification.params)
        if (!emitted) {
            logger.warn(
                LogCategory.PROTOCOL,
                "Notification buffer full, dropping notification",
                data = mapOf("method" to notification.method)
            )
        }
    }
}
