package ai.rever.boss.utils

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.io.path.createTempFile

private val logger = BossLogger.forComponent("MacOSDefaultBrowserHandler")

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

            logger.debug(LogCategory.BROWSER, "macOS default browser check", mapOf("httpHandler" to (httpDefault ?: "none"), "httpsHandler" to (httpsDefault ?: "none")))

            // BOSS is default if both schemes point to our bundle ID
            val isDefault = httpDefault == BUNDLE_ID && httpsDefault == BUNDLE_ID

            Result.success(isDefault)
        } catch (e: Exception) {
            logger.warn(LogCategory.BROWSER, "Error checking default browser on macOS", error = e)
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
                logger.info(LogCategory.BROWSER, "BOSS is already the default browser")
                return@withContext Result.success(true)
            }

            // Try to set as default using Swift script
            val setHttpResult = setDefaultHandlerForScheme("http")
            val setHttpsResult = setDefaultHandlerForScheme("https")

            if (setHttpResult && setHttpsResult) {
                logger.info(LogCategory.BROWSER, "Successfully set BOSS as default browser on macOS")
                Result.success(true)
            } else {
                // If Swift approach fails, open System Preferences
                logger.warn(LogCategory.BROWSER, "Could not set default programmatically, opening System Preferences")
                openSystemPreferences()
                Result.success(false)
            }
        } catch (e: Exception) {
            logger.error(LogCategory.BROWSER, "Error setting default browser on macOS", error = e)
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
            logger.warn(LogCategory.BROWSER, "Error reading defaults", error = e)
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

            val scriptFile = createTempFile("set_default_browser", ".swift").toFile()
            scriptFile.deleteOnExit()
            scriptFile.writeText(swiftScript)

            val process = ProcessBuilder("swift", scriptFile.absolutePath)
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            val exitCode = process.waitFor()

            logger.debug(LogCategory.BROWSER, "Swift script output", mapOf("scheme" to scheme, "output" to output.trim()))

            exitCode == 0
        } catch (e: Exception) {
            logger.warn(LogCategory.BROWSER, "Error setting default handler", mapOf("scheme" to scheme, "error" to (e.message ?: "unknown")))
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

            logger.info(LogCategory.BROWSER, "Opened System Preferences for user to set default browser")
        } catch (e: Exception) {
            logger.warn(LogCategory.BROWSER, "Error opening System Preferences", error = e)
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
                        logger.debug(LogCategory.BROWSER, "Received HTTP(S) URL (macOS)", mapOf("uri" to uri))
                        onURL(uri)
                    }
                }
                logger.info(LogCategory.BROWSER, "Registered macOS URL handler for http/https")
            } catch (e: Exception) {
                logger.error(LogCategory.BROWSER, "Failed to register macOS URL handler", error = e)
            }
        }
    }
}
