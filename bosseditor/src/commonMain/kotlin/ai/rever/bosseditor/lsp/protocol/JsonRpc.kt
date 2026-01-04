package ai.rever.bosseditor.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 protocol types for Language Server Protocol communication.
 *
 * Based on the JSON-RPC 2.0 specification and LSP 3.17.
 * Reference: https://www.jsonrpc.org/specification
 */

/**
 * JSON-RPC request message.
 *
 * A request message to describe a request between client and server.
 * Every processed request must send a response back to the sender.
 */
@Serializable
data class RequestMessage(
    /**
     * JSON-RPC version. Always "2.0".
     */
    val jsonrpc: String = "2.0",

    /**
     * The request id. Used to correlate requests with responses.
     */
    val id: Int,

    /**
     * The method to be invoked.
     */
    val method: String,

    /**
     * The method's params. Can be omitted if the method has no parameters.
     */
    val params: JsonElement? = null
)

/**
 * JSON-RPC response message.
 *
 * A response message sent as a result of a request.
 * If a request doesn't provide a result value, the result field should be null.
 */
@Serializable
data class ResponseMessage(
    /**
     * JSON-RPC version. Always "2.0".
     */
    val jsonrpc: String = "2.0",

    /**
     * The request id this response corresponds to.
     */
    val id: Int?,

    /**
     * The result of a request. This member is required on success.
     * This member must not exist if there was an error.
     */
    val result: JsonElement? = null,

    /**
     * The error object in case a request fails.
     */
    val error: ResponseError? = null
)

/**
 * JSON-RPC error object.
 */
@Serializable
data class ResponseError(
    /**
     * A number indicating the error type.
     */
    val code: Int,

    /**
     * A string providing a short description of the error.
     */
    val message: String,

    /**
     * Additional information about the error.
     */
    val data: JsonElement? = null
)

/**
 * JSON-RPC notification message.
 *
 * A notification message is a request without an id.
 * No response is expected for a notification.
 */
@Serializable
data class NotificationMessage(
    /**
     * JSON-RPC version. Always "2.0".
     */
    val jsonrpc: String = "2.0",

    /**
     * The method to be invoked.
     */
    val method: String,

    /**
     * The notification's params.
     */
    val params: JsonElement? = null
)

/**
 * Request cancellation parameters.
 */
@Serializable
data class CancelParams(
    /**
     * The request id to cancel.
     */
    val id: Int
)

/**
 * Standard JSON-RPC error codes.
 */
object ErrorCodes {
    // JSON-RPC defined errors
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    // LSP defined errors
    const val SERVER_NOT_INITIALIZED = -32002
    const val UNKNOWN_ERROR_CODE = -32001

    // LSP request errors
    const val REQUEST_FAILED = -32803
    const val SERVER_CANCELLED = -32802
    const val CONTENT_MODIFIED = -32801
    const val REQUEST_CANCELLED = -32800
}

/**
 * Progress token for work done progress and partial results.
 */
@Serializable
data class ProgressToken(
    val value: String
) {
    companion object {
        fun fromInt(value: Int): ProgressToken = ProgressToken(value.toString())
        fun fromString(value: String): ProgressToken = ProgressToken(value)
    }
}

/**
 * Progress parameters for reporting progress.
 */
@Serializable
data class ProgressParams(
    /**
     * The progress token provided by the client or server.
     */
    val token: ProgressToken,

    /**
     * The progress data.
     */
    val value: JsonElement
)
