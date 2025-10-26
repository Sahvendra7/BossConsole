package ai.rever.boss.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * macOS-specific handler for default browser functionality
 *
 * Uses macOS APIs and system commands to:
 * - Check if BOSS is the default browser
 * - Set BOSS as the default browser
 * - Register http/https URL handlers
 */
object MacOSDefaultBrowserHandler {
    private const val BUNDLE_ID = "ai.rever.boss"

    /**
     * Check if BOSS is currently the default browser on macOS
     *
     * Uses `defaults read` to query LaunchServices for http/https handlers
     */
    suspend fun isDefaultBrowser(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Check both http and https handlers
            val httpDefault = getDefaultHandlerViaDefaults("http")
            val httpsDefault = getDefaultHandlerViaDefaults("https")

            println("macOS default browser check: http=$httpDefault, https=$httpsDefault")

            // BOSS is default if both schemes point to our bundle ID
            val isDefault = httpDefault == BUNDLE_ID && httpsDefault == BUNDLE_ID

            Result.success(isDefault)
        } catch (e: Exception) {
            println("Error checking default browser on macOS: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Set BOSS as the default browser on macOS
     *
     * Uses LSSetDefaultHandlerForURLScheme via Swift script or system commands
     */
    suspend fun setAsDefaultBrowser(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // First check if already default
            val checkResult = isDefaultBrowser()
            if (checkResult.isSuccess && checkResult.getOrNull() == true) {
                println("BOSS is already the default browser")
                return@withContext Result.success(true)
            }

            // Try to set as default using Swift script
            val setHttpResult = setDefaultHandlerForScheme("http")
            val setHttpsResult = setDefaultHandlerForScheme("https")

            if (setHttpResult && setHttpsResult) {
                println("✅ Successfully set BOSS as default browser on macOS")
                Result.success(true)
            } else {
                // If Swift approach fails, open System Preferences
                println("⚠️ Could not set default programmatically, opening System Preferences")
                openSystemPreferences()
                Result.success(false)
            }
        } catch (e: Exception) {
            println("Error setting default browser on macOS: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get default handler using defaults read command
     */
    private fun getDefaultHandlerViaDefaults(scheme: String): String? {
        return try {
            val process = ProcessBuilder(
                "defaults",
                "read",
                "com.apple.LaunchServices/com.apple.launchservices.secure",
                "LSHandlers"
            ).redirectErrorStream(true).start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            process.waitFor()

            // Parse the plist-style output to find the handler for our scheme
            // Output format: { LSHandlerURLScheme = "http"; LSHandlerRoleAll = "com.apple.safari"; }
            val regex = """LSHandlerURLScheme\s*=\s*"?$scheme"?;[^}]*LSHandlerRoleAll\s*=\s*"?([^";]+)"?""".toRegex()
            regex.find(output)?.groupValues?.get(1)
        } catch (e: Exception) {
            println("Error reading defaults: ${e.message}")
            null
        }
    }

    /**
     * Set default handler for a URL scheme using Swift script
     */
    private fun setDefaultHandlerForScheme(scheme: String): Boolean {
        return try {
            // Create temporary Swift script to set default handler
            val swiftScript = """
                import AppKit
                import ApplicationServices

                let bundleId = "$BUNDLE_ID"
                let scheme = "$scheme"

                let status = LSSetDefaultHandlerForURLScheme(scheme as CFString, bundleId as CFString)

                if status == noErr {
                    print("✅ Set default handler for \(scheme)")
                    exit(0)
                } else {
                    print("❌ Failed to set default handler for \(scheme): \(status)")
                    exit(1)
                }
            """.trimIndent()

            val scriptFile = createTempFile("set_default_browser", ".swift")
            scriptFile.deleteOnExit()
            scriptFile.writeText(swiftScript)

            val process = ProcessBuilder("swift", scriptFile.absolutePath)
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            val exitCode = process.waitFor()

            println("Swift script output for $scheme: $output")

            exitCode == 0
        } catch (e: Exception) {
            println("Error setting default handler for $scheme: ${e.message}")
            false
        }
    }

    /**
     * Open System Preferences to Default Browser settings
     */
    private fun openSystemPreferences() {
        try {
            // Open System Preferences to General pane where default browser is set
            val process = ProcessBuilder(
                "open",
                "x-apple.systempreferences:com.apple.preference.general"
            ).start()

            process.waitFor()

            println("Opened System Preferences for user to set default browser")
        } catch (e: Exception) {
            println("Error opening System Preferences: ${e.message}")
        }
    }

    /**
     * Register URL handler for http/https with Desktop API
     */
    fun registerURLHandler(onURL: (String) -> Unit) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().setOpenURIHandler { event ->
                    val uri = event.uri.toString()
                    if (uri.startsWith("http://") || uri.startsWith("https://")) {
                        println("Received HTTP(S) URL (macOS): $uri")
                        onURL(uri)
                    }
                }
                println("✅ Registered macOS URL handler for http/https")
            } catch (e: Exception) {
                println("Failed to register macOS URL handler: ${e.message}")
            }
        }
    }
}
