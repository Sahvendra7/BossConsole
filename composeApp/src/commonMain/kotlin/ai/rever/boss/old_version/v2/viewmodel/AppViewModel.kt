package ai.rever.boss.old_version.v2.viewmodel

import ai.rever.boss.Work
import ai.rever.boss.old_version.v2.ui.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppViewModel {
    // UI State
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    
    // Navigation
    fun navigateTo(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }
    
    // Work List Management
    fun addWork(work: Work) {
        _uiState.update { 
            it.copy(workList = it.workList + work) 
        }
    }
    
    fun removeWork(work: Work) {
        _uiState.update { 
            it.copy(workList = it.workList.filter { item -> item != work }) 
        }
    }
    
    // File Selection
    fun setSelectedFile(filePath: String) {
        _uiState.update { it.copy(selectedFilePath = filePath) }
    }
}

// State class that holds all UI-related state
data class AppUiState(
    val currentScreen: Screen = Screen.BossConsole,
    val workList: List<Work> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedFilePath: String? = null
) 