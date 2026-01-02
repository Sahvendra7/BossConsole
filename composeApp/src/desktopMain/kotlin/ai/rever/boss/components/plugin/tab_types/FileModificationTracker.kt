package ai.rever.boss.components.plugin.tab_types

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import ai.rever.bosseditor.core.EditorDocument
import java.io.File

/**
 * Tracks file modification state and provides save functionality.
 *
 * Features:
 * - Tracks whether content has been modified since last save
 * - Provides save file functionality
 * - Tracks last saved timestamp
 * - Supports auto-save (if enabled in settings)
 */
class FileModificationTracker(
    private val filePath: String,
    initialContent: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    /**
     * State representing the current modification status.
     */
    data class ModificationState(
        val originalContent: String,
        val currentContent: String,
        val isModified: Boolean,
        val lastSaved: Instant?,
        val isSaving: Boolean = false,
        val lastError: String? = null
    )

    private val _state = MutableStateFlow(
        ModificationState(
            originalContent = initialContent,
            currentContent = initialContent,
            isModified = false,
            lastSaved = null
        )
    )
    val state: StateFlow<ModificationState> = _state.asStateFlow()

    /**
     * Whether the file has been modified since last save.
     */
    val isModified: Boolean
        get() = _state.value.isModified

    /**
     * Current content being edited.
     */
    val currentContent: String
        get() = _state.value.currentContent

    /**
     * Updates the current content and recalculates modification state.
     */
    fun updateContent(newContent: String) {
        val currentState = _state.value
        _state.value = currentState.copy(
            currentContent = newContent,
            isModified = newContent != currentState.originalContent,
            lastError = null
        )
    }

    /**
     * Saves the current content to the file.
     *
     * @return true if save was successful, false otherwise
     */
    suspend fun save(): Boolean {
        if (filePath.isEmpty()) {
            _state.value = _state.value.copy(
                lastError = "No file path specified"
            )
            return false
        }

        _state.value = _state.value.copy(isSaving = true, lastError = null)

        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)

                // Create parent directories if they don't exist
                file.parentFile?.mkdirs()

                // Write content to file
                file.writeText(_state.value.currentContent)

                val savedContent = _state.value.currentContent
                val now = Clock.System.now()

                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        originalContent = savedContent,
                        isModified = false,
                        lastSaved = now,
                        isSaving = false,
                        lastError = null
                    )
                }

                println("[FileModificationTracker] Saved file: $filePath")
                true
            } catch (e: Exception) {
                val errorMsg = "Failed to save file: ${e.message}"
                println("[FileModificationTracker] $errorMsg")

                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        lastError = errorMsg
                    )
                }
                false
            }
        }
    }

    /**
     * Saves the file asynchronously (fire and forget with callback).
     */
    fun saveAsync(onComplete: (Boolean) -> Unit = {}) {
        scope.launch {
            val result = save()
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    /**
     * Reloads the file from disk, discarding any unsaved changes.
     *
     * @return true if reload was successful, false otherwise
     */
    suspend fun reload(): Boolean {
        if (filePath.isEmpty()) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    println("[FileModificationTracker] File does not exist: $filePath")
                    return@withContext false
                }

                // Check file size before loading to prevent memory issues
                if (file.length() > EditorDocument.DEFAULT_MAX_DOCUMENT_SIZE) {
                    println("[FileModificationTracker] File too large to reload: ${file.length()} bytes (max: ${EditorDocument.DEFAULT_MAX_DOCUMENT_SIZE})")
                    return@withContext false
                }

                val newContent = file.readText()

                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        originalContent = newContent,
                        currentContent = newContent,
                        isModified = false,
                        lastError = null
                    )
                }

                println("[FileModificationTracker] Reloaded file: $filePath")
                true
            } catch (e: Exception) {
                println("[FileModificationTracker] Failed to reload: ${e.message}")
                false
            }
        }
    }

    /**
     * Resets the modification state without saving (marks as unmodified).
     * Use with caution - this discards the "modified" flag without saving.
     */
    fun resetModificationState() {
        val currentState = _state.value
        _state.value = currentState.copy(
            originalContent = currentState.currentContent,
            isModified = false
        )
    }

    /**
     * Checks if the file on disk has changed since we last loaded/saved.
     * Useful for detecting external modifications.
     *
     * @return true if file has been externally modified, false otherwise
     */
    suspend fun hasExternalChanges(): Boolean {
        if (filePath.isEmpty()) return false

        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext false

                val diskContent = file.readText()
                diskContent != _state.value.originalContent
            } catch (e: Exception) {
                false
            }
        }
    }

    companion object {
        /**
         * Creates a FileModificationTracker by loading content from a file.
         *
         * @param filePath Path to the file
         * @param scope Coroutine scope for async operations
         * @return FileModificationTracker instance, or null if file couldn't be read
         */
        suspend fun fromFile(
            filePath: String,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
        ): FileModificationTracker? {
            return withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists()) {
                        // For new files, create with empty content
                        FileModificationTracker(filePath, "", scope)
                    } else {
                        val content = file.readText()
                        FileModificationTracker(filePath, content, scope)
                    }
                } catch (e: Exception) {
                    println("[FileModificationTracker] Failed to create from file: ${e.message}")
                    null
                }
            }
        }
    }
}

/**
 * Event bus for file save events across the application.
 * Components can subscribe to be notified when files are saved.
 */
object FileSaveEventBus {
    private val _saveRequests = MutableStateFlow<String?>(null)
    val saveRequests: StateFlow<String?> = _saveRequests.asStateFlow()

    /**
     * Requests a save for the currently active editor.
     * The active editor component should listen for this and trigger save.
     */
    fun requestSave() {
        _saveRequests.value = Clock.System.now().toString()
    }

    /**
     * Clears the save request after it has been handled.
     */
    fun clearRequest() {
        _saveRequests.value = null
    }
}
