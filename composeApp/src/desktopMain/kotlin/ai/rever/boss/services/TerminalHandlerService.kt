package ai.rever.boss.services

import ai.rever.boss.cli.CLICommandHandler
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.atomic.AtomicInteger

/**
 * Desktop implementation of TerminalHandlerService.
 *
 * Delegates to CLICommandHandler for terminal event queueing management.
 * Tracks terminal processing to prevent New Tab Dialog race condition.
 * Uses hybrid AtomicInteger + mutableStateOf for thread safety and Compose reactivity.
 */
actual object TerminalHandlerService {
    private val processingCount = AtomicInteger(0)
    private val _isProcessing = mutableStateOf(false)

    actual fun markReady() {
        CLICommandHandler.getInstance().markTerminalHandlerReady()
    }

    /**
     * Check if terminal events are currently being processed.
     * Used by BossApp to prevent showing New Tab Dialog during terminal creation.
     * Returns Compose State to trigger recomposition.
     */
    actual fun isProcessingTerminals(): Boolean {
        return _isProcessing.value
    }

    /**
     * Increment processing counter when terminal event starts.
     * Should be called before emitting terminal event.
     * Updates both AtomicInteger (thread-safe) and mutableStateOf (Compose reactive).
     */
    internal fun incrementProcessing() {
        val count = processingCount.incrementAndGet()
        _isProcessing.value = (count > 0)
        println("TerminalHandlerService: incrementProcessing (count: $count, isProcessing: ${_isProcessing.value})")
    }

    /**
     * Decrement processing counter when terminal event completes.
     * Should be called after terminal tab is created.
     * Updates both AtomicInteger (thread-safe) and mutableStateOf (Compose reactive).
     */
    internal fun decrementProcessing() {
        val count = processingCount.decrementAndGet()
        _isProcessing.value = (count > 0)
        println("TerminalHandlerService: decrementProcessing (count: $count, isProcessing: ${_isProcessing.value})")
    }
}
