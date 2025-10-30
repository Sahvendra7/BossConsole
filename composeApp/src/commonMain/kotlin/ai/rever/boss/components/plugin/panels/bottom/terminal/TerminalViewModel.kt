package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TerminalViewModel {
    private val terminalFactory = TerminalFactory()
    private var terminal: Terminal? = null
    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null
    
    // Terminal emulator with clean initialization
    private val terminalEmulator = TerminalEmulator(columns = 80, rows = 30)
    
    // Clean StateFlows with proper initialization
    private val _terminalLines = MutableStateFlow<List<AnnotatedString>>(emptyList())
    val terminalLines: StateFlow<List<AnnotatedString>> = _terminalLines.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _terminalCursorPosition = MutableStateFlow(0 to 0)
    val terminalCursorPosition: StateFlow<Pair<Int, Int>> = _terminalCursorPosition.asStateFlow()
    
    private val _terminalCursorVisible = MutableStateFlow(true)
    val terminalCursorVisible: StateFlow<Boolean> = _terminalCursorVisible.asStateFlow()
    
    var wasStarted = false
        private set
    
    init {
        // Set up emulator response callback
        terminalEmulator.responseCallback = { response ->
            scope?.launch(Dispatchers.IO) {
                terminal?.write(response)
            }
        }
    }
    
    fun ensureStarted() {
        if (terminal == null && scope == null) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            scope!!.launch(Dispatchers.IO) {
                startTerminal()
            }
        }
    }
    
    private suspend fun startTerminal() {
        try {
            terminal = terminalFactory.createTerminal()
            terminal?.let { term ->
                // Monitor running state
                scope!!.launch {
                    term.isRunning.collect { running ->
                        _isRunning.value = running
                        if (running && !wasStarted) {
                            wasStarted = true
                        }
                    }
                }

                // Monitor output
                scope!!.launch {
                    term.output.collect { output ->
                        processOutput(output)
                    }
                }

                // Start terminal
                term.start()

                // Wait for startup with timeout
                withTimeoutOrNull(2000) {
                    term.isRunning.first { it }
                } ?: handleStartupFailure()
            }
        } catch (e: Exception) {
            handleStartupError(e)
        }
    }
    
    private fun handleStartupFailure() {
        terminalEmulator.processInput("Terminal is not available on this platform.\n")
        scheduleDisplayUpdate()
    }
    
    private fun handleStartupError(e: Exception) {
        e.printStackTrace()
        terminalEmulator.processInput("Error starting terminal: ${e.message}\n")
        scheduleDisplayUpdate()
    }
    
    private fun processOutput(output: String) {
        synchronized(terminalEmulator) {
            terminalEmulator.processInput(output)
        }
        scheduleDisplayUpdate()
    }
    
    private fun scheduleDisplayUpdate() {
        updateJob?.cancel()
        updateJob = scope?.launch {
            delay(16) // Simple debouncing
            updateDisplay()
        }
    }
    
    private suspend fun updateDisplay() {
        // Get terminal state safely
        val lines: List<AnnotatedString>
        val cursorPos: Pair<Int, Int>
        val cursorVisible: Boolean
        val scrollbackSize: Int

        synchronized(terminalEmulator) {
            lines = terminalEmulator.getAnnotatedLines()
            cursorPos = terminalEmulator.getCursorPosition()
            cursorVisible = terminalEmulator.isCursorVisible()
            scrollbackSize = terminalEmulator.getScrollbackSize()
        }

        // Calculate absolute cursor position (includes scrollback offset)
        val absoluteCursorRow = scrollbackSize + cursorPos.first
        val absoluteCursorPos = absoluteCursorRow to cursorPos.second

        // Update on main thread
        withContext(Dispatchers.Main.immediate) {
            _terminalLines.value = lines
            _terminalCursorPosition.value = absoluteCursorPos
            _terminalCursorVisible.value = cursorVisible
        }
    }
    
    fun sendInput(input: String) {
        scope?.launch(Dispatchers.IO) {
            terminal?.write(input)
            scheduleDisplayUpdate()
        }
    }
    
    fun resizeWithDeception(displayColumns: Int, displayRows: Int, ptyColumns: Int, ptyRows: Int) {
        scope?.launch(Dispatchers.IO) {
            terminal?.resize(ptyColumns, ptyRows)
            terminalEmulator.resize(displayColumns, displayRows)
        }
    }
    
    fun dispose() {
        terminal?.stop()
        terminalEmulator.dispose()
        scope?.cancel()
        scope = null
        terminal = null
        updateJob = null
    }
}
