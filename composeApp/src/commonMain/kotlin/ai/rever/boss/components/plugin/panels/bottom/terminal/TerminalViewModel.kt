package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TerminalViewModel {
    companion object {
        const val MAX_BUFFER_SIZE = 2000 // Maximum lines to keep in buffer
        private var instanceCounter = 0
    }
    
    private val instanceId = ++instanceCounter
    
    private val terminalFactory = TerminalFactory()
    private var terminal: Terminal? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Terminal emulator - start with conservative default size
    private val terminalEmulator = TerminalEmulator(columns = 120, rows = 24)
    
    // Terminal display lines
    private val _terminalLines = MutableStateFlow<List<AnnotatedString>>(emptyList())
    val terminalLines: StateFlow<List<AnnotatedString>> = _terminalLines.asStateFlow()
    
    // Terminal running state
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    // Terminal cursor position
    private val _terminalCursorPosition = MutableStateFlow(0 to 0)
    val terminalCursorPosition: StateFlow<Pair<Int, Int>> = _terminalCursorPosition.asStateFlow()
    
    fun ensureStarted() {
        if (terminal == null) {
            coroutineScope.launch {
                startTerminal()
            }
        }
    }
    
    private fun startTerminal() {
        coroutineScope.launch {
            try {
                terminal = terminalFactory.createTerminal()
                terminal?.let { term ->
                    term.start()
                    
                    // If the terminal doesn't start (stub implementation), show a message
                    if (!term.isRunning.value) {
                        terminalEmulator.processInput("Terminal is not available on this platform.\n")
                        terminalEmulator.processInput("Terminal functionality is only supported on desktop (Windows, macOS, Linux).\n")
                        updateDisplay()
                        return@launch
                    }
                    
                    // Collect terminal output
                    term.output.collect { output ->
                        processOutput(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                terminalEmulator.processInput("Error starting terminal: ${e.message}\n")
                updateDisplay()
            }
        }
        
        // Monitor running state
        coroutineScope.launch {
            terminal?.isRunning?.collect { running ->
                _isRunning.value = running
            }
        }
    }
    
    private fun processOutput(output: String) {
        // Send output to terminal emulator
        terminalEmulator.processInput(output)
        
        // Update the display
        updateDisplay()
    }
    
    private fun updateDisplay() {
        val lines = terminalEmulator.getAnnotatedLines()
        val cursorPos = terminalEmulator.getCursorPosition()
        // Only log significant display updates
        _terminalLines.value = lines
        _terminalCursorPosition.value = cursorPos
    }
    
    fun sendInput(input: String) {
        coroutineScope.launch {
            terminal?.write(input)
        }
    }
    
    fun resize(columns: Int, rows: Int) {
        coroutineScope.launch {
            terminal?.resize(columns, rows)
            terminalEmulator.resize(columns, rows)
        }
    }
    
    fun dispose() {
        terminal?.stop()
        coroutineScope.cancel()
    }
    
    private fun getPlainTextLines(): List<String> {
        return _terminalLines.value.takeLast(MAX_BUFFER_SIZE).map { annotatedString ->
            annotatedString.text
        }
    }
} 