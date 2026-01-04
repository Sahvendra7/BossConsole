package ai.rever.bosseditor.lsp.protocol

import kotlinx.serialization.Serializable

/**
 * LSP Document Synchronization types.
 *
 * These types are used for synchronizing text documents between the client
 * and the language server.
 *
 * Reference: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_synchronization
 */

/**
 * Parameters for textDocument/didOpen notification.
 *
 * Sent from the client to the server when a new text document was opened.
 */
@Serializable
data class DidOpenTextDocumentParams(
    /**
     * The document that was opened.
     */
    val textDocument: TextDocumentItem
)

/**
 * Parameters for textDocument/didChange notification.
 *
 * Sent from the client to the server when a text document changed.
 */
@Serializable
data class DidChangeTextDocumentParams(
    /**
     * The document that did change.
     * The version number points to the version after all changes have been applied.
     */
    val textDocument: VersionedTextDocumentIdentifier,

    /**
     * The actual content changes.
     *
     * For TextDocumentSyncKind.Full, contains one change with the full document content.
     * For TextDocumentSyncKind.Incremental, contains incremental changes.
     */
    val contentChanges: List<TextDocumentContentChangeEvent>
)

/**
 * An event describing a change to a text document.
 *
 * If range and rangeLength are omitted, the new text is considered
 * to be the full content of the document.
 */
@Serializable
data class TextDocumentContentChangeEvent(
    /**
     * The range of the document that changed.
     * If omitted, the whole document content is replaced.
     */
    val range: Range? = null,

    /**
     * The optional length of the range that got replaced.
     * @deprecated use range instead
     */
    val rangeLength: Int? = null,

    /**
     * The new text for the provided range, or the new full document content
     * if range is omitted.
     */
    val text: String
) {
    companion object {
        /**
         * Create a full document change event.
         */
        fun fullDocument(text: String) = TextDocumentContentChangeEvent(text = text)

        /**
         * Create an incremental change event.
         */
        fun incremental(range: Range, text: String) = TextDocumentContentChangeEvent(
            range = range,
            text = text
        )
    }
}

/**
 * Parameters for textDocument/willSave notification.
 *
 * Sent from the client to the server before the document is actually saved.
 */
@Serializable
data class WillSaveTextDocumentParams(
    /**
     * The document that will be saved.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The 'TextDocumentSaveReason'.
     */
    val reason: Int
)

/**
 * Represents reasons why a text document is saved.
 */
object TextDocumentSaveReason {
    /**
     * Manually triggered, e.g. by the user pressing save.
     */
    const val MANUAL = 1

    /**
     * Automatic after a delay.
     */
    const val AFTER_DELAY = 2

    /**
     * When the editor lost focus.
     */
    const val FOCUS_OUT = 3
}

/**
 * Parameters for textDocument/didSave notification.
 *
 * Sent from the client to the server when the document was saved.
 */
@Serializable
data class DidSaveTextDocumentParams(
    /**
     * The document that was saved.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * Optional the content when saved. Depends on the includeText value
     * when the save notification was requested.
     */
    val text: String? = null
)

/**
 * Parameters for textDocument/didClose notification.
 *
 * Sent from the client to the server when a text document was closed.
 */
@Serializable
data class DidCloseTextDocumentParams(
    /**
     * The document that was closed.
     */
    val textDocument: TextDocumentIdentifier
)

/**
 * LSP method names for document synchronization.
 */
object DocumentSyncMethods {
    const val DID_OPEN = "textDocument/didOpen"
    const val DID_CHANGE = "textDocument/didChange"
    const val WILL_SAVE = "textDocument/willSave"
    const val WILL_SAVE_WAIT_UNTIL = "textDocument/willSaveWaitUntil"
    const val DID_SAVE = "textDocument/didSave"
    const val DID_CLOSE = "textDocument/didClose"
}
