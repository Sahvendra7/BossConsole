package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TerminalViewModel {
    private val terminalFactory = TerminalFactory()
    private var terminal: Terminal? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Terminal emulator
    private val terminalEmulator = TerminalEmulator(columns = 120, rows = 24)
    
    // Terminal display lines
    private val _terminalLines = MutableStateFlow<List<AnnotatedString>>(emptyList())
    val terminalLines: StateFlow<List<AnnotatedString>> = _terminalLines.asStateFlow()
    
    // Current input line
    var currentInput by mutableStateOf("")
        private set
    
    // Terminal running state
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    // Cursor position in input
    var cursorPosition by mutableStateOf(0)
        private set
    
    // Terminal cursor position
    private val _terminalCursorPosition = MutableStateFlow(0 to 0)
    val terminalCursorPosition: StateFlow<Pair<Int, Int>> = _terminalCursorPosition.asStateFlow()
    
    init {
        startTerminal()
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
        _terminalLines.value = terminalEmulator.getAnnotatedLines()
        _terminalCursorPosition.value = terminalEmulator.getCursorPosition()
    }
    
    fun onInputChange(input: String) {
        currentInput = input
        cursorPosition = input.length
    }
    
    fun onKeyPress(key: TerminalKey) {
        when (key) {
            is TerminalKey.Character -> {
                val newInput = currentInput.substring(0, cursorPosition) + 
                    key.char + 
                    currentInput.substring(cursorPosition)
                currentInput = newInput
                cursorPosition++
            }
            TerminalKey.Enter -> {
                sendCommand(currentInput)
                currentInput = ""
                cursorPosition = 0
            }
            TerminalKey.Backspace -> {
                if (cursorPosition > 0) {
                    currentInput = currentInput.removeRange(cursorPosition - 1, cursorPosition)
                    cursorPosition--
                }
            }
            TerminalKey.Delete -> {
                if (cursorPosition < currentInput.length) {
                    currentInput = currentInput.removeRange(cursorPosition, cursorPosition + 1)
                }
            }
            TerminalKey.Left -> {
                if (cursorPosition > 0) {
                    cursorPosition--
                }
            }
            TerminalKey.Right -> {
                if (cursorPosition < currentInput.length) {
                    cursorPosition++
                }
            }
            TerminalKey.Home -> {
                cursorPosition = 0
            }
            TerminalKey.End -> {
                cursorPosition = currentInput.length
            }
            is TerminalKey.ControlKey -> {
                // Send control character directly
                sendControlChar(key.char)
            }
        }
    }
    
    private fun sendCommand(command: String) {
        coroutineScope.launch {
            terminal?.write(command + "\n")
        }
    }
    
    private fun sendControlChar(char: Char) {
        coroutineScope.launch {
            terminal?.write(char.toString())
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

sealed class TerminalKey {
    data class Character(val char: Char) : TerminalKey()
    data class ControlKey(val char: Char) : TerminalKey()
    object Enter : TerminalKey()
    object Backspace : TerminalKey()
    object Delete : TerminalKey()
    object Left : TerminalKey()
    object Right : TerminalKey()
    object Home : TerminalKey()
    object End : TerminalKey()
} 