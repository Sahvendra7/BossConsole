package ai.rever.boss.components.plugin.panels.bottom.terminal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

class IosTerminal : Terminal {
    override val output: Flow<String> = emptyFlow()
    
    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    override suspend fun start() {
        // Terminal not supported on iOS
    }
    
    override suspend fun write(input: String) {
        // Terminal not supported on iOS
    }
    
    override suspend fun resize(columns: Int, rows: Int) {
        // Terminal not supported on iOS
    }
    
    override fun stop() {
        // Terminal not supported on iOS
    }
}

actual class TerminalFactory actual constructor() {
    actual fun createTerminal(): Terminal = IosTerminal()
} 
