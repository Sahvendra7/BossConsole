package ai.rever.bosseditor.core

/**
 * Manages undo/redo operations for an EditorDocument.
 *
 * Features:
 * - Typing coalescing: Sequential character inserts at the same position are grouped
 * - Configurable undo limit
 * - Undo grouping for compound operations
 * - Separate undo/redo stacks
 */
class UndoManager(
    private val document: EditorDocument,
    private val maxUndoCount: Int = DEFAULT_MAX_UNDO
) : DocumentListener {

    private val undoStack = ArrayDeque<UndoGroup>()
    private val redoStack = ArrayDeque<UndoGroup>()

    // Current group for coalescing edits
    private var currentGroup: UndoGroup? = null
    private var lastEditTime: Long = 0
    private var isUndoing = false
    private var isRedoing = false

    // Compound edit tracking
    private var compoundDepth = 0
    private var compoundGroup: UndoGroup? = null

    init {
        document.addDocumentListener(this)
    }

    /**
     * Returns true if undo is available.
     */
    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    /**
     * Returns true if redo is available.
     */
    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    /**
     * Returns the number of undo operations available.
     */
    val undoCount: Int
        get() = undoStack.size

    /**
     * Returns the number of redo operations available.
     */
    val redoCount: Int
        get() = redoStack.size

    /**
     * Performs an undo operation.
     * @return true if undo was performed, false if nothing to undo
     */
    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false

        // Finalize any current group
        finalizeCurrentGroup()

        val group = undoStack.removeLast()

        isUndoing = true
        try {
            // Apply edits in reverse order
            for (i in group.edits.indices.reversed()) {
                val edit = group.edits[i]
                document.replace(edit.offset, edit.offset + edit.newText.length, edit.oldText)
            }
        } finally {
            isUndoing = false
        }

        redoStack.addLast(group)
        return true
    }

    /**
     * Performs a redo operation.
     * @return true if redo was performed, false if nothing to redo
     */
    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false

        val group = redoStack.removeLast()

        isRedoing = true
        try {
            // Apply edits in forward order
            for (edit in group.edits) {
                document.replace(edit.offset, edit.offset + edit.oldText.length, edit.newText)
            }
        } finally {
            isRedoing = false
        }

        undoStack.addLast(group)
        return true
    }

    /**
     * Clears all undo/redo history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        currentGroup = null
        compoundGroup = null
        compoundDepth = 0
    }

    /**
     * Begins a compound edit group.
     * All edits until endCompoundEdit() will be grouped as a single undo operation.
     */
    fun beginCompoundEdit() {
        if (compoundDepth == 0) {
            finalizeCurrentGroup()
            compoundGroup = UndoGroup()
        }
        compoundDepth++
    }

    /**
     * Ends a compound edit group.
     */
    fun endCompoundEdit() {
        if (compoundDepth > 0) {
            compoundDepth--
            if (compoundDepth == 0) {
                compoundGroup?.let { group ->
                    if (group.edits.isNotEmpty()) {
                        undoStack.addLast(group)
                        trimUndoStack()
                    }
                }
                compoundGroup = null
            }
        }
    }

    /**
     * Forces the current group to be finalized.
     * Call this to ensure the next edit starts a new undo group.
     */
    fun breakUndoGroup() {
        finalizeCurrentGroup()
    }

    override fun documentChanged(change: DocumentChange) {
        // Ignore changes from undo/redo operations
        if (isUndoing || isRedoing) return

        val edit = UndoEdit(
            offset = change.offset,
            oldText = change.oldText,
            newText = change.newText
        )

        // If in compound edit, add to compound group
        if (compoundDepth > 0 && compoundGroup != null) {
            compoundGroup!!.edits.add(edit)
            redoStack.clear()
            return
        }

        val currentTime = System.currentTimeMillis()
        val timeSinceLastEdit = currentTime - lastEditTime

        // Check if we should coalesce with current group
        val shouldCoalesce = currentGroup != null &&
                timeSinceLastEdit < COALESCE_TIMEOUT_MS &&
                canCoalesce(currentGroup!!.lastEdit, edit)

        if (shouldCoalesce) {
            currentGroup!!.edits.add(edit)
        } else {
            finalizeCurrentGroup()
            currentGroup = UndoGroup().apply { edits.add(edit) }
        }

        lastEditTime = currentTime

        // Clear redo stack on new edit
        redoStack.clear()
    }

    private fun canCoalesce(lastEdit: UndoEdit?, newEdit: UndoEdit): Boolean {
        if (lastEdit == null) return false

        // Coalesce single character inserts at adjacent positions
        if (lastEdit.oldText.isEmpty() && newEdit.oldText.isEmpty() &&
            lastEdit.newText.length == 1 && newEdit.newText.length == 1
        ) {
            // Check if new insert is right after last insert
            val expectedOffset = lastEdit.offset + lastEdit.newText.length
            if (newEdit.offset == expectedOffset) {
                // Don't coalesce if newline or whitespace after non-whitespace
                val lastChar = lastEdit.newText.last()
                val newChar = newEdit.newText.first()
                if (newChar == '\n') return false
                if (lastChar.isWhitespace() != newChar.isWhitespace()) return false
                return true
            }
        }

        // Coalesce single character deletions at same position (backspace)
        if (lastEdit.newText.isEmpty() && newEdit.newText.isEmpty() &&
            lastEdit.oldText.length == 1 && newEdit.oldText.length == 1
        ) {
            // Backspace: new deletion at offset one less than last
            if (newEdit.offset == lastEdit.offset - 1) {
                return true
            }
            // Delete key: deletion at same offset
            if (newEdit.offset == lastEdit.offset) {
                return true
            }
        }

        return false
    }

    private fun finalizeCurrentGroup() {
        currentGroup?.let { group ->
            if (group.edits.isNotEmpty()) {
                undoStack.addLast(group)
                trimUndoStack()
            }
        }
        currentGroup = null
    }

    private fun trimUndoStack() {
        while (undoStack.size > maxUndoCount) {
            undoStack.removeFirst()
        }
    }

    companion object {
        private const val DEFAULT_MAX_UNDO = 1000
        private const val COALESCE_TIMEOUT_MS = 500
    }
}

/**
 * A single edit operation.
 */
private data class UndoEdit(
    val offset: Int,
    val oldText: String,
    val newText: String
)

/**
 * A group of edits that should be undone/redone together.
 */
private class UndoGroup {
    val edits = mutableListOf<UndoEdit>()

    val lastEdit: UndoEdit?
        get() = edits.lastOrNull()
}
