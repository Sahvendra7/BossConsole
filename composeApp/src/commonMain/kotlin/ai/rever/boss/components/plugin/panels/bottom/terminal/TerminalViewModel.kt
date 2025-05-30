package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TerminalViewModel {
    
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
    
    // Terminal cursor visibility
    private val _terminalCursorVisible = MutableStateFlow(true)
    val terminalCursorVisible: StateFlow<Boolean> = _terminalCursorVisible.asStateFlow()
    
    // Track if terminal was ever started
    var wasStarted = false
        private set
    
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
                    // First set up the monitoring
                    launch {
                        term.isRunning.collect { running ->
                            _isRunning.value = running
                            if (running && !wasStarted) {
                                wasStarted = true
                            }
                        }
                    }
                    
                    // Set up the output collection
                    launch {
                        term.output.collect { output ->
                            processOutput(output)
                        }
                    }
                    
                    // Give coroutines a moment to set up
                    delay(50)
                    
                    // Then start the terminal
                    term.start()
                    
                    // Give terminal a moment to initialize
                    delay(200)
                    
                    // Check if terminal is available on this platform
                    if (!term.isRunning.value) {
                        // Wait a bit more for stub implementations
                        delay(500)
                        if (!term.isRunning.value) {
                            terminalEmulator.processInput("Terminal is not available on this platform.\n")
                            terminalEmulator.processInput("Terminal functionality is only supported on desktop (Windows, macOS, Linux).\n")
                            updateDisplay()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                terminalEmulator.processInput("Error starting terminal: ${e.message}\n")
                updateDisplay()
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
        val cursorVisible = terminalEmulator.isCursorVisible()
        _terminalLines.value = lines
        _terminalCursorPosition.value = cursorPos
        _terminalCursorVisible.value = cursorVisible
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

} 