package ai.rever.boss.utils

import java.io.File

/**
 * Windows-specific protocol handler for registering URL schemes
 */
object WindowsProtocolHandler {
    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    
    /**
     * Register the boss:// protocol in Windows Registry
     * This should be called on first launch or during installation
     */
    fun registerProtocol() {
        if (!isWindows) return
        
        try {
            val appPath = getApplicationPath()
            if (appPath.isNullOrEmpty()) {
                println("Could not determine application path for protocol registration")
                return
            }
            
            // Create registry entries for boss:// protocol
            val commands = listOf(
                // Create protocol key
                """reg add "HKEY_CURRENT_USER\Software\Classes\boss" /ve /d "URL:BOSS Protocol" /f""",
                """reg add "HKEY_CURRENT_USER\Software\Classes\boss" /v "URL Protocol" /d "" /f""",
                
                // Set icon
                """reg add "HKEY_CURRENT_USER\Software\Classes\boss\DefaultIcon" /ve /d "$appPath,0" /f""",
                
                // Set command to open the app with URL
                """reg add "HKEY_CURRENT_USER\Software\Classes\boss\shell\open\command" /ve /d "\"$appPath\" \"%1\"" /f"""
            )
            
            commands.forEach { command ->
                try {
                    val process = Runtime.getRuntime().exec(command)
                    process.waitFor()
                    if (process.exitValue() == 0) {
                        println("Successfully executed: $command")
                    } else {
                        println("Failed to execute: $command")
                    }
                } catch (e: Exception) {
                    println("Error executing registry command: ${e.message}")
                }
            }
            
            println("Windows protocol registration completed")
        } catch (e: Exception) {
            println("Failed to register Windows protocol: ${e.message}")
        }
    }
    
    /**
     * Check if the protocol is already registered
     */
    fun isProtocolRegistered(): Boolean {
        if (!isWindows) return false
        
        return try {
            val process = Runtime.getRuntime().exec("""reg query "HKEY_CURRENT_USER\Software\Classes\boss" """)
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get the path to the running application
     */
    private fun getApplicationPath(): String? {
        return try {
            // Try to get the path from the running JAR/EXE
            val jarPath = WindowsProtocolHandler::class.java.protectionDomain.codeSource.location.toURI().path
            
            // Convert to Windows path format and handle different packaging scenarios
            when {
                jarPath.endsWith(".jar") -> {
                    // Running from JAR - look for launcher executable
                    val jarFile = File(jarPath)
                    val launcherPath = jarFile.parentFile.resolve("BOSS.exe")
                    if (launcherPath.exists()) {
                        launcherPath.absolutePath
                    } else {
                        // Fallback to java command with jar
                        "javaw.exe -jar \"${jarFile.absolutePath}\""
                    }
                }
                jarPath.contains("BOSS.exe") -> {
                    // Already an executable
                    File(jarPath).absolutePath
                }
                else -> {
                    // Development environment - look for packaged executable
                    val workingDir = File(System.getProperty("user.dir"))
                    val possiblePaths = listOf(
                        workingDir.resolve("composeApp/build/compose/binaries/main/app/BOSS/BOSS.exe"),
                        workingDir.resolve("build/compose/binaries/main/app/BOSS/BOSS.exe")
                    )
                    possiblePaths.firstOrNull { it.exists() }?.absolutePath
                }
            }
        } catch (e: Exception) {
            println("Error determining application path: ${e.message}")
            null
        }
    }
    
    /**
     * Parse command line arguments to extract deep link URL
     */
    fun extractDeepLinkFromArgs(args: Array<String>): String? {
        // Windows passes the URL as the first argument when launched via protocol
        return args.firstOrNull { it.startsWith("boss://") }
    }
}