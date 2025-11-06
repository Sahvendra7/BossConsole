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
     *
     * Production-safe: Only registers if needed, validates existing registrations,
     * and prevents conflicts with other BOSS installations
     */
    fun registerProtocol() {
        if (!isWindows) return

        try {
            // 1. Get application path
            val appPath = getApplicationPath()
            if (appPath.isNullOrEmpty()) {
                // Development mode or unable to determine path
                return
            }

            // 2. Check current registry state
            val currentCommand = getCurrentRegistryCommand()

            // 3. Determine if registration is needed
            val needsRegistration = when {
                currentCommand == null -> {
                    println("Protocol not registered. Registering...")
                    true
                }
                !commandPointsToValidExecutable(currentCommand) -> {
                    println("Protocol points to invalid path: $currentCommand")
                    println("Re-registering with correct path...")
                    true
                }
                !currentCommand.contains(appPath, ignoreCase = true) -> {
                    // SAFETY CHECK: Only re-register if current path doesn't exist
                    val currentExePath = extractExecutablePath(currentCommand)
                    if (currentExePath != null && File(currentExePath).exists()) {
                        println("Protocol already registered to different valid BOSS installation: $currentExePath")
                        println("Skipping re-registration to avoid conflicts.")
                        false
                    } else {
                        println("Protocol points to non-existent path: $currentCommand")
                        println("Re-registering with current installation path...")
                        true
                    }
                }
                else -> {
                    println("Protocol already correctly registered.")
                    false
                }
            }

            // 4. Perform registration if needed
            if (needsRegistration) {
                performRegistration(appPath)
            }
        } catch (e: Exception) {
            println("Failed to register Windows protocol: ${e.message}")
        }
    }

    /**
     * Perform the actual registry writes
     */
    private fun performRegistration(appPath: String) {
        println("=== BOSS Protocol Registration ===")
        println("Registering boss:// protocol for: $appPath")

        val commands = listOf(
            // Create protocol key
            """reg add "HKEY_CURRENT_USER\Software\Classes\boss" /ve /d "URL:BOSS Protocol" /f""",
            """reg add "HKEY_CURRENT_USER\Software\Classes\boss" /v "URL Protocol" /d "" /f""",

            // Set icon
            """reg add "HKEY_CURRENT_USER\Software\Classes\boss\DefaultIcon" /ve /d "$appPath,0" /f""",

            // Set command to open the app with URL
            """reg add "HKEY_CURRENT_USER\Software\Classes\boss\shell\open\command" /ve /d "\"$appPath\" \"%1\"" /f"""
        )

        var successCount = 0
        commands.forEach { command ->
            try {
                val process = Runtime.getRuntime().exec(command)
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    successCount++
                } else {
                    println("WARNING: Registry command failed (exit $exitCode)")
                }
            } catch (e: Exception) {
                println("ERROR: Failed to execute registry command: ${e.message}")
            }
        }

        if (successCount == commands.size) {
            println("✓ Protocol registration successful")
        } else {
            println("⚠ Protocol registration partial ($successCount/${commands.size} succeeded)")
        }
        println("===================================")
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
            // Priority 1: Check for jpackage installation (MSI/EXE)
            // This is the most reliable method for production deployments
            val jpackagePath = System.getProperty("jpackage.app-path")
            if (!jpackagePath.isNullOrEmpty()) {
                val file = File(jpackagePath)
                if (file.exists()) {
                    println("Detected jpackage installation: $jpackagePath")
                    return jpackagePath
                } else {
                    println("WARNING: jpackage.app-path set but file doesn't exist: $jpackagePath")
                }
            }

            // Priority 2: Try to get the path from the running JAR/EXE
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
                        // Cannot use "javaw.exe -jar" as registry needs executable path
                        println("WARNING: Running from JAR without launcher executable")
                        null
                    }
                }
                jarPath.contains("BOSS.exe") -> {
                    // Already an executable
                    File(jarPath).absolutePath
                }
                else -> {
                    // Development environment - return null to skip registration
                    println("INFO: Running in development mode. Deep links require MSI installation.")
                    null
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

    /**
     * Get the current command registered in the Windows registry for boss:// protocol
     */
    private fun getCurrentRegistryCommand(): String? {
        return try {
            val process = ProcessBuilder(
                "reg", "query",
                "HKEY_CURRENT_USER\\Software\\Classes\\boss\\shell\\open\\command",
                "/ve"
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            // Parse: "    (Default)    REG_SZ    C:\Path\To\BOSS.exe "%1""
            val match = Regex("""REG_SZ\s+(.+)$""", RegexOption.MULTILINE).find(output)
            match?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract executable path from registry command string
     * Example: "C:\Path\To\BOSS.exe" "%1" -> C:\Path\To\BOSS.exe
     */
    private fun extractExecutablePath(command: String): String? {
        val match = Regex("""^"([^"]+)"""").find(command)
        return match?.groupValues?.get(1)
    }

    /**
     * Check if the command points to a valid executable file
     */
    private fun commandPointsToValidExecutable(command: String): Boolean {
        val exePath = extractExecutablePath(command) ?: return false
        return File(exePath).exists()
    }
}
