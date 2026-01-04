package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.core.EditorPosition

/**
 * Determines when code completion should be triggered.
 *
 * This class handles the logic for when to show the completion popup,
 * including:
 * - Manual invocation (Ctrl+Space)
 * - Trigger characters (., :, etc.)
 * - Automatic triggering after typing
 *
 * ## Usage
 * ```kotlin
 * val trigger = CompletionTrigger(
 *     triggerCharacters = listOf('.', ':', '<'),
 *     autoTriggerMinChars = 3
 * )
 *
 * // Check on each keystroke
 * if (trigger.shouldTrigger(typed, position, lineText)) {
 *     showCompletionPopup()
 * }
 * ```
 */
class CompletionTrigger(
    /**
     * Characters that immediately trigger completion (from server capabilities).
     */
    private val triggerCharacters: List<Char> = DEFAULT_TRIGGER_CHARACTERS,

    /**
     * Minimum characters before auto-triggering completion.
     */
    private val autoTriggerMinChars: Int = DEFAULT_AUTO_TRIGGER_MIN_CHARS,

    /**
     * Whether to auto-trigger after typing an identifier.
     */
    private val autoTriggerEnabled: Boolean = true,

    /**
     * Characters that should cancel completion.
     */
    private val cancelCharacters: List<Char> = DEFAULT_CANCEL_CHARACTERS
) {
    /**
     * Determines if completion should be triggered.
     *
     * @param char The character just typed (null for manual invocation)
     * @param position The current cursor position
     * @param lineText The current line text
     * @return TriggerResult indicating whether and how to trigger
     */
    fun shouldTrigger(
        char: Char?,
        position: EditorPosition,
        lineText: String
    ): TriggerResult {
        // Manual invocation (Ctrl+Space)
        if (char == null) {
            return TriggerResult.Trigger(TriggerKind.MANUAL)
        }

        // Check for cancel characters
        if (char in cancelCharacters) {
            return TriggerResult.Cancel
        }

        // Check for trigger characters
        if (char in triggerCharacters) {
            return TriggerResult.Trigger(TriggerKind.CHARACTER, char)
        }

        // Auto-trigger after typing identifier characters
        if (autoTriggerEnabled && isIdentifierChar(char)) {
            val prefix = getIdentifierPrefix(lineText, position.column)
            if (prefix.length >= autoTriggerMinChars) {
                return TriggerResult.Trigger(TriggerKind.AUTO, prefix = prefix)
            }
        }

        return TriggerResult.NoTrigger
    }

    /**
     * Determines if completion should be cancelled.
     *
     * @param char The character just typed
     * @param currentPrefix The current completion prefix
     * @return true if completion should be cancelled
     */
    fun shouldCancel(char: Char, currentPrefix: String): Boolean {
        // Cancel on explicit cancel characters
        if (char in cancelCharacters) {
            return true
        }

        // Don't cancel on identifier characters
        if (isIdentifierChar(char)) {
            return false
        }

        // Cancel on whitespace
        if (char.isWhitespace()) {
            return true
        }

        // Keep open for trigger characters (might want to re-trigger)
        if (char in triggerCharacters) {
            return false
        }

        // Cancel on other characters
        return true
    }

    /**
     * Get the identifier prefix at the given position.
     *
     * @param lineText The current line text
     * @param column The column position (0-based)
     * @return The identifier prefix before the cursor
     */
    fun getIdentifierPrefix(lineText: String, column: Int): String {
        if (column <= 0 || column > lineText.length) {
            return ""
        }

        var start = column - 1
        while (start >= 0 && isIdentifierChar(lineText[start])) {
            start--
        }

        return lineText.substring(start + 1, column)
    }

    /**
     * Checks if a character is part of an identifier.
     */
    private fun isIdentifierChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_'
    }

    /**
     * Get the trigger character if position is right after one.
     *
     * @param lineText The current line text
     * @param column The column position (0-based)
     * @return The trigger character, or null if not applicable
     */
    fun getTriggerCharacterAt(lineText: String, column: Int): Char? {
        if (column <= 0 || column > lineText.length) {
            return null
        }

        val charBefore = lineText[column - 1]
        return if (charBefore in triggerCharacters) charBefore else null
    }

    companion object {
        /**
         * Default trigger characters used by most language servers.
         */
        val DEFAULT_TRIGGER_CHARACTERS = listOf('.', ':', '<', '(', ',', '"', '\'', '/', '@')

        /**
         * Default minimum characters for auto-triggering.
         */
        const val DEFAULT_AUTO_TRIGGER_MIN_CHARS = 1

        /**
         * Characters that typically cancel completion.
         */
        val DEFAULT_CANCEL_CHARACTERS = listOf('\n', '\r', '\t')

        /**
         * Create a trigger with server-provided trigger characters.
         */
        fun fromServerCapabilities(
            triggerCharacters: List<Char>,
            autoTriggerMinChars: Int = DEFAULT_AUTO_TRIGGER_MIN_CHARS
        ): CompletionTrigger {
            return CompletionTrigger(
                triggerCharacters = if (triggerCharacters.isEmpty())
                    DEFAULT_TRIGGER_CHARACTERS
                else
                    triggerCharacters,
                autoTriggerMinChars = autoTriggerMinChars
            )
        }
    }
}

/**
 * Result of a trigger check.
 */
sealed class TriggerResult {
    /**
     * Completion should be triggered.
     */
    data class Trigger(
        /**
         * The kind of trigger.
         */
        val kind: TriggerKind,

        /**
         * The trigger character (if CHARACTER kind).
         */
        val triggerCharacter: Char? = null,

        /**
         * The current prefix (if AUTO kind).
         */
        val prefix: String = ""
    ) : TriggerResult()

    /**
     * Completion should not be triggered.
     */
    data object NoTrigger : TriggerResult()

    /**
     * Active completion should be cancelled.
     */
    data object Cancel : TriggerResult()
}

/**
 * The kind of completion trigger.
 */
enum class TriggerKind {
    /**
     * Manual invocation (Ctrl+Space).
     */
    MANUAL,

    /**
     * Triggered by a trigger character (., :, etc.).
     */
    CHARACTER,

    /**
     * Auto-triggered after typing characters.
     */
    AUTO
}

/**
 * Manages the completion session state.
 */
class CompletionSession(
    /**
     * The document URI.
     */
    val documentUri: String,

    /**
     * The position where completion was triggered.
     */
    val triggerPosition: EditorPosition,

    /**
     * The trigger kind.
     */
    val triggerKind: TriggerKind,

    /**
     * The trigger character (if applicable).
     */
    val triggerCharacter: Char? = null,

    /**
     * The initial prefix at trigger time.
     */
    val initialPrefix: String = ""
) {
    /**
     * Current prefix being typed.
     */
    var currentPrefix: String = initialPrefix
        private set

    /**
     * Whether the session is still active.
     */
    var isActive: Boolean = true
        private set

    /**
     * Update the prefix based on cursor movement.
     *
     * @param newPrefix The new prefix
     */
    fun updatePrefix(newPrefix: String) {
        currentPrefix = newPrefix
    }

    /**
     * Cancel this session.
     */
    fun cancel() {
        isActive = false
    }

    /**
     * Check if the cursor is still within the valid range for this session.
     *
     * @param position Current cursor position
     * @param lineText Current line text
     * @return true if the session should remain active
     */
    fun isValidPosition(position: EditorPosition, lineText: String): Boolean {
        // Must be on the same line
        if (position.line != triggerPosition.line) {
            return false
        }

        // Cursor must be at or after trigger position
        if (position.column < triggerPosition.column) {
            return false
        }

        return true
    }
}

/**
 * Debounce helper for completion requests.
 */
class CompletionDebouncer(
    private val delayMs: Long = DEFAULT_DEBOUNCE_MS
) {
    private var lastTriggerTime: Long = 0
    private var pendingJob: Any? = null

    /**
     * Check if enough time has passed since the last trigger.
     */
    fun shouldRequest(): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = now - lastTriggerTime
        return elapsed >= delayMs
    }

    /**
     * Mark that a request was made.
     */
    fun markRequested() {
        lastTriggerTime = System.currentTimeMillis()
    }

    /**
     * Reset the debouncer.
     */
    fun reset() {
        lastTriggerTime = 0
        pendingJob = null
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 100L
    }
}
