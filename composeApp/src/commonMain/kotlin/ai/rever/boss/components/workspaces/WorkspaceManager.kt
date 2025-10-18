package ai.rever.boss.components.workspaces

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Manages layout workspaces with file-based storage
 */
class WorkspaceManager {
    private val _currentWorkspace = MutableStateFlow<LayoutWorkspace?>(null)
    val currentWorkspace: StateFlow<LayoutWorkspace?> = _currentWorkspace.asStateFlow()
    
    private val _workspaces = MutableStateFlow<List<LayoutWorkspace>>(emptyList())
    val workspaces: StateFlow<List<LayoutWorkspace>> = _workspaces.asStateFlow()
    
    private val fileManager = WorkspaceFileManager()
    private val scope = CoroutineScope(Dispatchers.Default)
    
    // Callback for when a workspace is deleted
    private var onWorkspaceDeleted: ((String) -> Unit)? = null
    
    init {
        // Load workspaces from both predefined and saved files
        loadAllWorkspaces()
    }
    
    private fun loadAllWorkspaces() {
        scope.launch {
            // Start with predefined workspaces
            val allWorkspaces = mutableListOf<LayoutWorkspace>()
            allWorkspaces.addAll(PredefinedWorkspaces.allWorkspaces)
            
            // Load saved workspaces from disk
            try {
                val savedWorkspaces = fileManager.listWorkspaces()
                savedWorkspaces.forEach { fileInfo ->
                    fileManager.loadWorkspace(fileInfo.fileName)?.let { workspace ->
                        // Ensure workspace has an ID
                        val workspaceWithId = if (workspace.id.isEmpty()) {
                            workspace.copy(id = LayoutWorkspace.generateId())
                        } else {
                            workspace
                        }
                        // Only add if not already in predefined list
                        if (allWorkspaces.none { it.name == workspaceWithId.name }) {
                            allWorkspaces.add(workspaceWithId)
                        }
                    }
                }
            } catch (e: Exception) {
                // Log error but continue with predefined workspaces
                e.printStackTrace()
            }
            
            _workspaces.value = allWorkspaces
        }
    }
    
    /**
     * Load a workspace
     */
    fun loadWorkspace(workspace: LayoutWorkspace) {
        _currentWorkspace.value = workspace
    }
    
    /**
     * Save current workspace to disk
     */
    fun saveCurrentWorkspace(name: String? = null): LayoutWorkspace? {
        val current = _currentWorkspace.value ?: return null
        val savedWorkspace = current.copy(
            id = if (current.id.isEmpty()) LayoutWorkspace.generateId() else current.id,
            name = name ?: current.name,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
        
        scope.launch {
            // Save to disk
            val filePath = fileManager.saveWorkspace(savedWorkspace)
            if (filePath != null) {
                // Update workspaces list
                val workspaces = _workspaces.value.toMutableList()
                val existingIndex = workspaces.indexOfFirst { it.name == savedWorkspace.name }
                
                if (existingIndex >= 0) {
                    workspaces[existingIndex] = savedWorkspace
                } else {
                    workspaces.add(savedWorkspace)
                }
                
                _workspaces.value = workspaces
                _currentWorkspace.value = savedWorkspace
            }
        }
        
        return savedWorkspace
    }
    
    /**
     * Reset to default workspace
     */
    fun resetToDefault() {
        _currentWorkspace.value = null
    }
    
    /**
     * Export workspace to JSON
     */
    fun exportWorkspace(workspace: LayoutWorkspace): String {
        return WorkspaceSerializer.serialize(workspace)
    }
    
    /**
     * Import workspace from JSON
     */
    fun importWorkspace(jsonString: String): LayoutWorkspace? {
        return try {
            val workspace = WorkspaceSerializer.deserialize(jsonString)
            
            // Save the imported workspace to disk
            scope.launch {
                fileManager.saveWorkspace(workspace)
                
                // Update workspaces list
                val workspaces = _workspaces.value.toMutableList()
                if (workspaces.none { it.name == workspace.name }) {
                    workspaces.add(workspace)
                    _workspaces.value = workspaces
                }
            }
            
            workspace
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Set callback for when a workspace is deleted
     */
    fun setOnWorkspaceDeleted(callback: (String) -> Unit) {
        onWorkspaceDeleted = callback
    }

    /**
     * Delete a workspace
     */
    fun deleteWorkspace(name: String) {
        scope.launch {
            // Find workspace
            val workspace = _workspaces.value.find { it.name == name }
            if (workspace != null && !PredefinedWorkspaces.allWorkspaces.any { it.name == name }) {
                // Only delete if it's not a predefined workspace
                val fileName = WorkspaceFileManagerCommon.generateFileName(name)
                if (fileManager.deleteWorkspace(fileName)) {
                    _workspaces.value = _workspaces.value.filter { it.name != name }
                    
                    // Notify that workspace was deleted (this will cleanup tabs)
                    onWorkspaceDeleted?.invoke(workspace.id)
                    
                    // If current workspace was deleted, reset
                    if (_currentWorkspace.value?.name == name) {
                        resetToDefault()
                    }
                }
            }
        }
    }
    
    /**
     * Update current workspace with new layout
     */
    fun updateCurrentWorkspace(newWorkspace: LayoutWorkspace) {
        _currentWorkspace.value = newWorkspace
    }
    
    /**
     * Get the workspace directory path
     */
    fun getWorkspaceDirectory(): String {
        return fileManager.getDefaultWorkspaceDirectory()
    }
}

// Global instance
val workspaceManager = WorkspaceManager()