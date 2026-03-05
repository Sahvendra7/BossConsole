package ai.rever.boss.components.plugin.tab_types

/**
 * Event bus for search actions.
 * Allows triggering search from keyboard shortcuts.
 */
object EditorSearchEventBus {

    /**
     * Callback interface for search actions.
     */
    interface SearchActionListener {
        fun onFind()
        fun onReplace()
        fun onFindNext()
        fun onFindPrevious()
        fun onGoToLine()
    }

    /**
     * Interface for querying search state from the focused editor.
     */
    interface SearchStateProvider {
        fun getSearchQuery(): String?
        fun getSearchMatchCount(): Int
        fun getCurrentSearchMatchIndex(): Int
    }

    /**
     * Interface for undo/redo operations on the focused editor.
     */
    interface UndoRedoProvider {
        fun undo(): Boolean
        fun redo(): Boolean
        fun canUndo(): Boolean
        fun canRedo(): Boolean
    }

    @Volatile private var currentListener: SearchActionListener? = null
    @Volatile private var currentSearchStateProvider: SearchStateProvider? = null
    @Volatile private var currentUndoRedoProvider: UndoRedoProvider? = null

    /**
     * Register a listener for search actions.
     * Only one listener can be active at a time (the focused editor).
     */
    fun registerListener(listener: SearchActionListener) {
        currentListener = listener
    }

    /**
     * Unregister the current listener.
     */
    fun unregisterListener(listener: SearchActionListener) {
        if (currentListener == listener) {
            currentListener = null
        }
    }

    /**
     * Register a search state provider from the focused editor.
     */
    fun registerSearchStateProvider(provider: SearchStateProvider) {
        currentSearchStateProvider = provider
    }

    /**
     * Unregister a search state provider.
     */
    fun unregisterSearchStateProvider(provider: SearchStateProvider) {
        if (currentSearchStateProvider == provider) {
            currentSearchStateProvider = null
        }
    }

    /**
     * Register an undo/redo provider from the focused editor.
     */
    fun registerUndoRedoProvider(provider: UndoRedoProvider) {
        currentUndoRedoProvider = provider
    }

    /**
     * Unregister an undo/redo provider.
     */
    fun unregisterUndoRedoProvider(provider: UndoRedoProvider) {
        if (currentUndoRedoProvider == provider) {
            currentUndoRedoProvider = null
        }
    }

    // Search action triggers
    fun triggerFind() { currentListener?.onFind() }
    fun triggerReplace() { currentListener?.onReplace() }
    fun triggerFindNext() { currentListener?.onFindNext() }
    fun triggerFindPrevious() { currentListener?.onFindPrevious() }
    fun triggerGoToLine() { currentListener?.onGoToLine() }

    // Search state queries
    fun getSearchQuery(): String? = currentSearchStateProvider?.getSearchQuery()
    fun getSearchMatchCount(): Int = currentSearchStateProvider?.getSearchMatchCount() ?: 0
    fun getCurrentSearchMatchIndex(): Int = currentSearchStateProvider?.getCurrentSearchMatchIndex() ?: -1

    // Undo/Redo operations
    fun undo(): Boolean = currentUndoRedoProvider?.undo() ?: false
    fun redo(): Boolean = currentUndoRedoProvider?.redo() ?: false
    fun canUndo(): Boolean = currentUndoRedoProvider?.canUndo() ?: false
    fun canRedo(): Boolean = currentUndoRedoProvider?.canRedo() ?: false
}
