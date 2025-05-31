package ai.rever.boss.components.plugin.panels.bottom.terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class DesktopTerminal : Terminal {
    private var ptyProcess: PtyProcess? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _output = MutableSharedFlow<String>()
    override val output: Flow<String> = _output.asSharedFlow()
    
    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    override suspend fun start() {
        if (_isRunning.value) {
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                // Get the user's shell
                val shell = System.getenv("SHELL") ?: "/bin/bash"
                val env = System.getenv().toMutableMap()
                

                // Use full terminal support for oh-my-zsh and powerline
                env["TERM"] = "xterm-256color"
                // Ensure COLUMNS and LINES are not set - let PTY handle it
                env.remove("COLUMNS")
                env.remove("LINES")
                
                // Build the PTY process
                val cmd = when {
                    shell.contains("zsh") -> arrayOf(shell, "-i")
                    shell.contains("bash") -> arrayOf(shell, "-i")
                    else -> arrayOf(shell)
                }
                
                
                val builder = PtyProcessBuilder()
                    .setCommand(cmd)
                    .setEnvironment(env)
                    .setDirectory(System.getProperty("user.home"))
                    .setInitialColumns(120)  // Start with a wider default
                    .setInitialRows(24)
                    .setConsole(false)
                    .setWindowsAnsiColorEnabled(true)
                    .setRedirectErrorStream(true)
                
                ptyProcess = builder.start()
                
                ptyProcess?.let { process ->
                    reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8), 8192)
                    writer = OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)
                    
                    _isRunning.value = true
                    
                    // Give the shell a moment to initialize
                    delay(100)
                    
                    // Start reading output in a coroutine
                    coroutineScope.launch {
                        try {
                            val buffer = CharArray(4096)
                            var totalBytesRead = 0
                            
                            while (isActive && process.isAlive) {
                                try {
                                    val count = reader?.read(buffer) ?: -1
                                    if (count > 0) {
                                        totalBytesRead += count
                                        val output = String(buffer, 0, count)
                                        _output.emit(output)
                                    } else if (count == -1) {
                                        // End of stream reached
                                            break
                                    }
                                } catch (e: Exception) {
                                    if (!process.isAlive) break
                                    delay(100)
                                }
                            }
                        } catch (e: Exception) {
                            if (process.isAlive) {
                                e.printStackTrace()
                            }
                        } finally {
                            _isRunning.value = false
                        }
                    }
                    
                    // Monitor the process itself
                    coroutineScope.launch {
                        try {
                            process.waitFor()
                            _isRunning.value = false
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isRunning.value = false
                // Emit error information to help diagnose
                _output.emit("\n[Terminal Error]\n")
                _output.emit("Failed to start terminal: ${e.message}\n")
                _output.emit("Error type: ${e.javaClass.simpleName}\n")
                _output.emit("\nPossible causes:\n")
                _output.emit("- PTY4J native libraries not found or incompatible\n")
                _output.emit("- Security restrictions preventing terminal access\n")
                _output.emit("- Architecture mismatch (Intel vs Apple Silicon)\n")
                _output.emit("\nSystem info:\n")
                _output.emit("- OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}\n")
                _output.emit("- Arch: ${System.getProperty("os.arch")}\n")
                _output.emit("- Java: ${System.getProperty("java.version")}\n")
                // Don't throw, just log the error - the UI will handle the error state
            }
        }
    }
    
    override suspend fun write(input: String) {
        withContext(Dispatchers.IO) {
            writer?.let {
                it.write(input)
                it.flush()
            }
        }
    }
    
    override suspend fun resize(columns: Int, rows: Int) {
        withContext(Dispatchers.IO) {
            ptyProcess?.let {
                it.winSize = com.pty4j.WinSize(columns, rows)
            }
        }
    }
    
    override fun stop() {
        _isRunning.value = false
        coroutineScope.cancel()
        
        try {
            writer?.close()
            reader?.close()
            ptyProcess?.destroyForcibly()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        ptyProcess = null
        writer = null
        reader = null
    }
}

actual class TerminalFactory actual constructor() {
    actual fun createTerminal(): Terminal = DesktopTerminal()
} 