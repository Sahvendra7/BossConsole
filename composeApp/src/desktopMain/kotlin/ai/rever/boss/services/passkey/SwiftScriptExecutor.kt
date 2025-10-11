package ai.rever.boss.services.passkey

import java.io.File

/**
 * Utility for executing Swift scripts on macOS
 * Handles temporary file creation and cleanup for Swift code execution
 */
object SwiftScriptExecutor {

    /**
     * Execute a Swift script and return the output
     */
    fun executeSwiftScript(swiftCode: String): String {
        val tempFile = createTempFile("boss_swift", ".swift")
        return try {
            tempFile.writeText(swiftCode)
            
            val process = ProcessBuilder("swift", tempFile.absolutePath).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            println("SwiftScriptExecutor: Exit code: $exitCode")
            
            output
        } finally {
            // Clean up temp file
            tempFile.delete()
        }
    }

    /**
     * Execute a Swift file with arguments and return the output
     */
    fun executeSwiftFile(fileName: String, vararg args: String): String {
        val swiftFilesDir = getSwiftFilesDirectory()
        val swiftFile = File(swiftFilesDir, fileName)
        
        if (!swiftFile.exists()) {
            throw IllegalArgumentException("Swift file not found: ${swiftFile.absolutePath}")
        }
        
        val command = mutableListOf("swift", swiftFile.absolutePath)
        command.addAll(args)
        
        val process = ProcessBuilder(command).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        
        println("SwiftScriptExecutor: Executed $fileName, exit code: $exitCode")
        
        return output
    }

    /**
     * Get the directory containing Swift files
     */
    private fun getSwiftFilesDirectory(): File {
        val projectDir = System.getProperty("user.dir")
        println("SwiftScriptExecutor: Project dir: $projectDir")
        
        // Try multiple possible paths
        val possiblePaths = listOf(
            "$projectDir/composeApp/src/desktopMain/kotlin/ai/rever/boss/services/passkey/swift",
            "$projectDir/src/desktopMain/kotlin/ai/rever/boss/services/passkey/swift"
        )
        
        for (path in possiblePaths) {
            val dir = File(path)
            println("SwiftScriptExecutor: Checking path: ${dir.absolutePath}")
            if (dir.exists() && dir.isDirectory) {
                println("SwiftScriptExecutor: Found Swift directory: ${dir.absolutePath}")
                return dir
            }
        }
        
        throw IllegalStateException("Swift files directory not found. Checked paths: $possiblePaths")
    }

    /**
     * Check if macOS Swift compiler is available
     */
    fun isSwiftAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("swift", "--version").start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            println("SwiftScriptExecutor: Swift not available: ${e.message}")
            false
        }
    }
}