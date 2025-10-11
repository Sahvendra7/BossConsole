package ai.rever.boss.services.passkey

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Executes PowerShell scripts for Windows Hello authentication
 * Similar to SwiftScriptExecutor but for Windows PowerShell
 */
object PowerShellExecutor {

    private val powerShellScriptsDir: String by lazy {
        findPowerShellScriptsDirectory()
    }

    /**
     * Check if PowerShell is available on this system
     */
    fun isPowerShellAvailable(): Boolean {
        return try {
            val os = System.getProperty("os.name").lowercase()
            if (!os.contains("windows")) return false

            val process = ProcessBuilder("powershell", "-Command", "echo 'test'")
                .start()
            
            val exitCode = process.waitFor(5, TimeUnit.SECONDS)
            exitCode && process.exitValue() == 0
        } catch (e: Exception) {
            println("PowerShellExecutor: PowerShell not available: ${e.message}")
            false
        }
    }

    /**
     * Execute a PowerShell script file with arguments
     */
    fun executePowerShellScript(scriptName: String, vararg args: String): String {
        return try {
            val scriptPath = Paths.get(powerShellScriptsDir, scriptName)
            
            if (!Files.exists(scriptPath)) {
                throw IOException("PowerShell script not found: $scriptPath")
            }

            val command = mutableListOf("powershell", "-ExecutionPolicy", "Bypass", "-File", scriptPath.toString())
            command.addAll(args)

            println("PowerShellExecutor: Executing: ${command.joinToString(" ")}")

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor(30, TimeUnit.SECONDS)

            if (!exitCode || process.exitValue() != 0) {
                throw RuntimeException("PowerShell script failed with exit code: ${process.exitValue()}, output: $output")
            }

            output.trim()

        } catch (e: Exception) {
            println("PowerShellExecutor: Error executing PowerShell script: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Find the PowerShell scripts directory in the project
     */
    private fun findPowerShellScriptsDirectory(): String {
        val projectDir = System.getProperty("user.dir")
        println("PowerShellExecutor: Project dir: $projectDir")

        // Look for the powershell directory in various locations
        val possiblePaths = listOf(
            "$projectDir/composeApp/src/desktopMain/kotlin/ai/rever/boss/services/passkey/powershell",
            "$projectDir/src/desktopMain/kotlin/ai/rever/boss/services/passkey/powershell"
        )

        for (path in possiblePaths) {
            println("PowerShellExecutor: Checking path: $path")
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                println("PowerShellExecutor: Found PowerShell directory: $path")
                return path
            }
        }

        // Create the directory if it doesn't exist
        val defaultPath = possiblePaths.first()
        val dir = File(defaultPath)
        if (dir.mkdirs()) {
            println("PowerShellExecutor: Created PowerShell directory: $defaultPath")
            return defaultPath
        } else {
            throw RuntimeException("Could not find or create PowerShell scripts directory")
        }
    }
}