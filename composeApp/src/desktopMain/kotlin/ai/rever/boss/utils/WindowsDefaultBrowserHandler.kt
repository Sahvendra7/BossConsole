package ai.rever.boss.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Windows-specific handler for default browser functionality
 *
 * Handles registry operations to:
 * - Register BOSS as a browser candidate
 * - Check if BOSS is the default browser
 * - Guide user to set BOSS as default (Windows 10+ requires manual selection)
 *
 * Note: Windows 10+ does not allow programmatic default browser changes
 * due to security restrictions. We open Windows Settings for user to select.
 */
object WindowsDefaultBrowserHandler {
    // Registry paths
    private const val START_MENU_KEY = "HKEY_CURRENT_USER\\SOFTWARE\\Clients\\StartMenuInternet\\BOSS"
    private const val REGISTERED_APPS = "HKEY_CURRENT_USER\\SOFTWARE\\RegisteredApplications"
    private const val HTTP_USER_CHOICE = "HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\http\\UserChoice"
    private const val HTTPS_USER_CHOICE = "HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\https\\UserChoice"

    /**
     * Check if BOSS is currently the default browser on Windows
     *
     * Queries registry UserChoice keys for http/https associations
     */
    suspend fun isDefaultBrowser(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val httpDefault = getDefaultBrowserProgId("http")
            val httpsDefault = getDefaultBrowserProgId("https")

            println("Windows default browser check: http=$httpDefault, https=$httpsDefault")

            // BOSS is default if both schemes point to BOSS
            val isDefault = httpDefault == "BOSS" && httpsDefault == "BOSS"

            Result.success(isDefault)
        } catch (e: Exception) {
            println("Error checking default browser on Windows: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Set BOSS as the default browser on Windows
     *
     * Registers BOSS as a browser candidate and opens Windows Settings
     * for user to manually select BOSS as default (Windows 10+ requirement)
     */
    suspend fun setAsDefaultBrowser(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // First register BOSS as a browser candidate
            val registered = registerAsBrowserCandidate()

            if (!registered) {
                return@withContext Result.failure(
                    Exception("Failed to register BOSS as browser candidate")
                )
            }

            // Open Windows Settings for user to select default browser
            openDefaultAppsSettings()

            // Return false to indicate user action is required
            println("⚠️ Windows Settings opened - user must manually select BOSS as default")
            Result.success(false)
        } catch (e: Exception) {
            println("Error setting default browser on Windows: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get the current default browser ProgId for a scheme
     */
    private fun getDefaultBrowserProgId(scheme: String): String? {
        return try {
            val keyPath = when (scheme) {
                "http" -> HTTP_USER_CHOICE
                "https" -> HTTPS_USER_CHOICE
                else -> return null
            }

            val process = Runtime.getRuntime().exec("""reg query "$keyPath" /v ProgId""")
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            process.waitFor()

            // Parse output: "    ProgId    REG_SZ    ChromeHTML"
            val regex = """ProgId\s+REG_SZ\s+(.+)""".toRegex()
            regex.find(output)?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            println("Error querying default browser for $scheme: ${e.message}")
            null
        }
    }

    /**
     * Register BOSS as a browser candidate in Windows Registry
     *
     * Creates necessary registry keys for Windows to recognize BOSS as a browser
     */
    private fun registerAsBrowserCandidate(): Boolean {
        try {
            val appPath = getApplicationPath()
            if (appPath.isNullOrEmpty()) {
                println("Could not determine application path for browser registration")
                return false
            }

            println("Registering BOSS as browser candidate with path: $appPath")

            // Create registry entries
            val commands = listOf(
                // Create main key
                """reg add "$START_MENU_KEY" /ve /d "BOSS Console" /f""",

                // Set icon
                """reg add "$START_MENU_KEY\DefaultIcon" /ve /d "$appPath,0" /f""",

                // Set command to open the app
                """reg add "$START_MENU_KEY\shell\open\command" /ve /d "\"$appPath\" \"%1\"" /f""",

                // Install info
                """reg add "$START_MENU_KEY\InstallInfo" /v IconsVisible /t REG_DWORD /d 1 /f""",
                """reg add "$START_MENU_KEY\InstallInfo" /v ShowIconsCommand /d "$appPath" /f""",
                """reg add "$START_MENU_KEY\InstallInfo" /v HideIconsCommand /d "$appPath" /f""",
                """reg add "$START_MENU_KEY\InstallInfo" /v ReinstallCommand /d "$appPath" /f""",

                // Capabilities
                """reg add "$START_MENU_KEY\Capabilities" /v ApplicationName /d "BOSS Console" /f""",
                """reg add "$START_MENU_KEY\Capabilities" /v ApplicationIcon /d "$appPath,0" /f""",
                """reg add "$START_MENU_KEY\Capabilities" /v ApplicationDescription /d "Business Operating System + Simulation - Intelligent service automation platform" /f""",

                // URL Associations
                """reg add "$START_MENU_KEY\Capabilities\URLAssociations" /v http /d "BOSS" /f""",
                """reg add "$START_MENU_KEY\Capabilities\URLAssociations" /v https /d "BOSS" /f""",
                """reg add "$START_MENU_KEY\Capabilities\URLAssociations" /v ftp /d "BOSS" /f""",

                // File Associations
                """reg add "$START_MENU_KEY\Capabilities\FileAssociations" /v .htm /d "BOSS" /f""",
                """reg add "$START_MENU_KEY\Capabilities\FileAssociations" /v .html /d "BOSS" /f""",

                // Register in RegisteredApplications
                """reg add "$REGISTERED_APPS" /v BOSS /d "SOFTWARE\\Clients\\StartMenuInternet\\BOSS\\Capabilities" /f"""
            )

            var allSucceeded = true
            commands.forEachIndexed { index, command ->
                try {
                    val process = Runtime.getRuntime().exec(command)
                    val exitCode = process.waitFor()

                    if (exitCode == 0) {
                        println("✅ Registry command succeeded")
                    } else {
                        println("❌ Registry command failed: $command")

                        // Fail fast on critical keys (first 3: main key, DefaultIcon, shell command)
                        if (index < 3) {
                            println("⚠️ Critical registry key failed, aborting registration")
                            return false
                        }
                        allSucceeded = false
                    }
                } catch (e: Exception) {
                    println("Error executing registry command: ${e.message}")

                    // Fail fast on critical keys
                    if (index < 3) {
                        println("⚠️ Critical registry key failed with exception, aborting registration")
                        return false
                    }
                    allSucceeded = false
                }
            }

            if (allSucceeded) {
                println("✅ BOSS successfully registered as browser candidate")
            }

            return allSucceeded
        } catch (e: Exception) {
            println("Failed to register as browser candidate: ${e.message}")
            return false
        }
    }

    /**
     * Get the path to the running application executable
     */
    private fun getApplicationPath(): String? {
        return try {
            // Try to get the path from the running JAR/EXE
            val jarPath = WindowsDefaultBrowserHandler::class.java.protectionDomain.codeSource.location.toURI().path

            when {
                jarPath.endsWith(".jar") -> {
                    // Running from JAR - look for launcher executable
                    val jarFile = File(jarPath)
                    val launcherPath = jarFile.parentFile.resolve("BOSS.exe")
                    if (launcherPath.exists()) {
                        launcherPath.absolutePath
                    } else {
                        // Fallback to java command with jar
                        val javaHome = System.getProperty("java.home")
                        val javawExe = File(javaHome, "bin\\javaw.exe")
                        "\"${javawExe.absolutePath}\" -jar \"${jarFile.absolutePath}\""
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
                        workingDir.resolve("composeApp\\build\\compose\\binaries\\main\\app\\BOSS\\BOSS.exe"),
                        workingDir.resolve("build\\compose\\binaries\\main\\app\\BOSS\\BOSS.exe")
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
     * Open Windows Settings to Default Apps page
     *
     * Opens the settings page where user can select BOSS as default browser
     */
    private fun openDefaultAppsSettings() {
        try {
            // Open Windows Settings to Default Apps
            val process = Runtime.getRuntime().exec("cmd /c start ms-settings:defaultapps")
            process.waitFor()

            println("Opened Windows Settings - Default Apps")
        } catch (e: Exception) {
            println("Error opening Windows Settings: ${e.message}")
        }
    }

    /**
     * Check if BOSS is already registered as a browser candidate
     */
    fun isBrowserCandidateRegistered(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("""reg query "$START_MENU_KEY" """)
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
}
