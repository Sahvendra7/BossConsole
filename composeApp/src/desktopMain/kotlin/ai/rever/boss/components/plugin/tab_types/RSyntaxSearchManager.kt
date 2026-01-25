package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import org.fife.rsta.ui.GoToDialog
import org.fife.rsta.ui.search.FindDialog
import org.fife.rsta.ui.search.ReplaceDialog
import org.fife.rsta.ui.search.SearchEvent
import org.fife.rsta.ui.search.SearchListener
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.SearchEngine
import org.fife.ui.rtextarea.SearchContext
import java.awt.Frame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/**
 * Manages search, replace, and go-to-line functionality for RSyntaxTextArea.
 *
 * This manager uses RSTAUI dialogs for a professional search experience:
 * - FindDialog: Standard find with regex, case-sensitive, whole word options
 * - ReplaceDialog: Find and replace with same options
 * - GoToDialog: Navigate to specific line number
 *
 * Usage:
 * 1. Create a manager instance for each editor
 * 2. Call showFind(), showReplace(), or showGoToLine() to show dialogs
 * 3. The manager handles all search operations internally
 */
class RSyntaxSearchManager(
    private val textArea: RSyntaxTextArea
) : SearchListener {

    private val logger = BossLogger.forComponent("RSyntaxSearchManager")
    private var findDialog: FindDialog? = null
    private var replaceDialog: ReplaceDialog? = null
    private var goToDialog: GoToDialog? = null
    private val searchContext = SearchContext()

    /**
     * Get the parent frame for dialogs.
     * Returns the root frame containing the text area, or null if not in a Frame hierarchy.
     */
    private fun getParentFrame(): Frame? {
        var component: java.awt.Component? = textArea
        while (component != null) {
            if (component is Frame) {
                return component
            }
            component = component.parent
        }
        return null
    }

    /**
     * Lazily initialize and return the Find dialog.
     * Returns null if no parent frame is available.
     */
    private fun getFindDialog(): FindDialog? {
        if (findDialog == null) {
            val frame = getParentFrame()
            if (frame == null) {
                logger.warn(LogCategory.EDITOR, "Cannot create Find dialog - no parent frame")
                return null
            }
            findDialog = FindDialog(frame, this).apply {
                title = "Find"
                searchContext = this@RSyntaxSearchManager.searchContext
            }
        }
        return findDialog
    }

    /**
     * Lazily initialize and return the Replace dialog.
     * Returns null if no parent frame is available.
     */
    private fun getReplaceDialog(): ReplaceDialog? {
        if (replaceDialog == null) {
            val frame = getParentFrame()
            if (frame == null) {
                logger.warn(LogCategory.EDITOR, "Cannot create Replace dialog - no parent frame")
                return null
            }
            replaceDialog = ReplaceDialog(frame, this).apply {
                title = "Replace"
                searchContext = this@RSyntaxSearchManager.searchContext
            }
        }
        return replaceDialog
    }

    /**
     * Lazily initialize and return the GoTo Line dialog.
     * Returns null if no parent frame is available.
     */
    private fun getGoToDialog(): GoToDialog? {
        if (goToDialog == null) {
            val frame = getParentFrame()
            if (frame == null) {
                logger.warn(LogCategory.EDITOR, "Cannot create GoTo dialog - no parent frame")
                return null
            }
            goToDialog = GoToDialog(frame)
        }
        return goToDialog
    }

    /**
     * Show the Find dialog.
     * Pre-populates search text if there's a selection.
     */
    fun showFind() {
        SwingUtilities.invokeLater {
            // Pre-populate with selected text
            val selectedText = textArea.selectedText
            if (!selectedText.isNullOrEmpty() && !selectedText.contains("\n")) {
                searchContext.searchFor = selectedText
            }

            // Hide replace dialog if visible
            replaceDialog?.isVisible = false

            getFindDialog()?.apply {
                isVisible = true
                requestFocus()
            } ?: logger.warn(LogCategory.EDITOR, "Find dialog unavailable")
        }
    }

    /**
     * Show the Replace dialog.
     * Pre-populates search text if there's a selection.
     */
    fun showReplace() {
        SwingUtilities.invokeLater {
            // Pre-populate with selected text
            val selectedText = textArea.selectedText
            if (!selectedText.isNullOrEmpty() && !selectedText.contains("\n")) {
                searchContext.searchFor = selectedText
            }

            // Hide find dialog if visible
            findDialog?.isVisible = false

            getReplaceDialog()?.apply {
                isVisible = true
                requestFocus()
            } ?: logger.warn(LogCategory.EDITOR, "Replace dialog unavailable")
        }
    }

    /**
     * Show the Go To Line dialog.
     * Uses cached dialog for better performance.
     */
    fun showGoToLine() {
        SwingUtilities.invokeLater {
            val dialog = getGoToDialog()
            if (dialog == null) {
                logger.warn(LogCategory.EDITOR, "GoTo dialog unavailable")
                return@invokeLater
            }

            dialog.maxLineNumberAllowed = textArea.lineCount
            dialog.isVisible = true

            val line = dialog.lineNumber
            if (line > 0) {
                try {
                    textArea.caretPosition = textArea.getLineStartOffset(line - 1)
                    textArea.requestFocusInWindow()
                } catch (e: Exception) {
                    logger.warn(LogCategory.EDITOR, "Error going to line", mapOf("line" to line), error = e)
                }
            }
        }
    }

    /**
     * Find the next occurrence of the current search term.
     */
    fun findNext() {
        if (searchContext.searchFor.isNullOrEmpty()) {
            // If no search term, show find dialog
            showFind()
            return
        }

        SwingUtilities.invokeLater {
            searchContext.searchForward = true
            val result = SearchEngine.find(textArea, searchContext)
            handleSearchResult(result.wasFound(), "Find")
        }
    }

    /**
     * Find the previous occurrence of the current search term.
     */
    fun findPrevious() {
        if (searchContext.searchFor.isNullOrEmpty()) {
            // If no search term, show find dialog
            showFind()
            return
        }

        SwingUtilities.invokeLater {
            searchContext.searchForward = false
            val result = SearchEngine.find(textArea, searchContext)
            handleSearchResult(result.wasFound(), "Find")
        }
    }

    /**
     * Handle search events from RSTAUI dialogs.
     */
    override fun searchEvent(e: SearchEvent) {
        when (e.type) {
            SearchEvent.Type.MARK_ALL -> {
                val result = SearchEngine.markAll(textArea, searchContext)
                logger.debug(LogCategory.EDITOR, "Marked occurrences", mapOf("count" to result.count))
            }
            SearchEvent.Type.FIND -> {
                val result = SearchEngine.find(textArea, searchContext)
                handleSearchResult(result.wasFound(), "Find")
            }
            SearchEvent.Type.REPLACE -> {
                val result = SearchEngine.replace(textArea, searchContext)
                handleSearchResult(result.wasFound(), "Replace")
            }
            SearchEvent.Type.REPLACE_ALL -> {
                val result = SearchEngine.replaceAll(textArea, searchContext)
                val message = if (result.count > 0) {
                    "Replaced ${result.count} occurrences"
                } else {
                    "No occurrences found"
                }
                showMessage(message, "Replace All")
            }
            else -> {
                logger.debug(LogCategory.EDITOR, "Unknown search event", mapOf("type" to e.type.toString()))
            }
        }
    }

    /**
     * Handle search result by wrapping or showing not found message.
     */
    private fun handleSearchResult(found: Boolean, operation: String) {
        if (!found) {
            val searchTerm = searchContext.searchFor ?: ""
            showMessage("'$searchTerm' not found", operation)
        }
    }

    /**
     * Show a message dialog.
     */
    private fun showMessage(message: String, title: String) {
        SwingUtilities.invokeLater {
            val frame = getParentFrame()
            JOptionPane.showMessageDialog(
                frame,
                message,
                title,
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    /**
     * Get the current search string.
     * Returns null if no search has been performed.
     */
    override fun getSelectedText(): String? = textArea.selectedText

    /**
     * Dispose of dialogs and release resources.
     */
    fun dispose() {
        findDialog?.dispose()
        replaceDialog?.dispose()
        goToDialog?.dispose()
        findDialog = null
        replaceDialog = null
        goToDialog = null
    }
}

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

    private var currentListener: SearchActionListener? = null

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
     * Trigger find action on the current listener.
     */
    fun triggerFind() {
        currentListener?.onFind()
    }

    /**
     * Trigger replace action on the current listener.
     */
    fun triggerReplace() {
        currentListener?.onReplace()
    }

    /**
     * Trigger find next action on the current listener.
     */
    fun triggerFindNext() {
        currentListener?.onFindNext()
    }

    /**
     * Trigger find previous action on the current listener.
     */
    fun triggerFindPrevious() {
        currentListener?.onFindPrevious()
    }

    /**
     * Trigger go to line action on the current listener.
     */
    fun triggerGoToLine() {
        currentListener?.onGoToLine()
    }
}
