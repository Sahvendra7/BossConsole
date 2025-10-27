package ai.rever.boss.components.plugin.panels.bottom.terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.BufferOverflow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class DesktopTerminal : Terminal {
    private var ptyProcess: PtyProcess? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private var scope: CoroutineScope? = null

    private val _output = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val output: Flow<String> = _output.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    override suspend fun start() {
        if (_isRunning.value) return
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val scope = scope!!

        withContext(Dispatchers.IO) {
            val env = System.getenv().toMutableMap()
            val shell = System.getenv("SHELL") ?: "/bin/bash"
            val isWindows = System.getProperty("os.name").lowercase().contains("win")

            env["PATH"] = "/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${env["PATH"].orEmpty()}"
            env["HOME"] = env["HOME"] ?: System.getProperty("user.home")
            env["TERM"] = "xterm-256color"
            env["LANG"] = env["LANG"] ?: "en_US.UTF-8"
            env["LC_ALL"] = env["LC_ALL"] ?: "en_US.UTF-8"
            env["LC_CTYPE"] = "en_US.UTF-8"
            env.remove("COLUMNS")
            env.remove("LINES")

            val cmd = when {
                isWindows -> arrayOf("powershell.exe", "-NoLogo", "-NoExit")
                shell.contains("zsh") -> arrayOf(shell, "-l", "-i")
                shell.contains("bash") -> arrayOf(shell, "-l", "-i")
                else -> arrayOf(shell, "-l", "-i")
            }

            val builder = PtyProcessBuilder()
                .setCommand(cmd)
                .setEnvironment(env)
                .setDirectory(System.getProperty("user.home"))
                .setInitialColumns(80)
                .setInitialRows(30)
                .setConsole(false)
                .setRedirectErrorStream(true)
            if (isWindows) builder.setWindowsAnsiColorEnabled(true)

            val process = builder.start()
            ptyProcess = process
            reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8), 8192)
            writer = OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)

            _isRunning.value = true

            scope.launch {
                val buf = CharArray(1024)
                try {
                    while (isActive && process.isAlive) {
                        val n = reader?.read(buf) ?: -1
                        if (n <= 0) break

                        val output = String(buf, 0, n)

                        _output.tryEmit(output)
                    }
                } finally {
                    closeInternal()
                }
            }

            scope.launch {
                try { process.waitFor() } finally { closeInternal() }
            }
        }
    }

    private fun closeInternal() {
        if (_isRunning.value) _isRunning.value = false
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { ptyProcess?.destroy() }
        ptyProcess = null
        writer = null
        reader = null
    }

    override suspend fun write(input: String) {
        withContext(Dispatchers.IO) {
            writer?.apply { write(input); flush() }
        }
    }

    override suspend fun resize(columns: Int, rows: Int) = withContext(Dispatchers.IO) {
        if (columns > 0 && rows > 0) ptyProcess?.winSize = WinSize(columns, rows)
    }

    override fun stop() {
        _isRunning.value = false
        scope?.cancel()
        closeInternal()
        scope = null
    }
}

actual class TerminalFactory actual constructor() {
    actual fun createTerminal(): Terminal = DesktopTerminal()
} 
