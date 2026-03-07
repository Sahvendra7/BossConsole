package ai.rever.boss.utils

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 */
object MacOSDefaultBrowserHandler {
    private const val BUNDLE_ID = "ai.rever.boss"

    /**
     * Check if BOSS is currently the default browser on macOS
     *
     * Uses LSCopyDefaultHandlerForURLScheme via Swift script to query http/https handlers
     */
    suspend fun isDefaultBrowser(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val httpDefault = getDefaultHandlerForScheme("http")
            val httpsDefault = getDefaultHandlerForScheme("https")

            logger.debug(LogCategory.BROWSER, "macOS default browser check", mapOf("httpHandler" to (httpDefault ?: "none"), "httpsHandler" to (httpsDefault ?: "none")))

            // BOSS is default if both schemes point to our bundle ID
            val isDefault = BUNDLE_ID.equals(httpDefault, ignoreCase = true) &&
                BUNDLE_ID.equals(httpsDefault, ignoreCase = true)

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
     * Get default handler for a URL scheme using LSCopyDefaultHandlerForURLScheme via Swift script
     */
    private fun getDefaultHandlerForScheme(scheme: String): String? {
        return try {
            val swiftScript = """
                import Foundation
                import ApplicationServices

                if let handler = LSCopyDefaultHandlerForURLScheme("$scheme" as CFString) {
                    print(handler.takeRetainedValue() as String)
                    exit(0)
                } else {
                    exit(1)
                }
            """.trimIndent()

            val scriptFile = createTempFile("get_default_browser", ".swift").toFile()
            scriptFile.deleteOnExit()
            scriptFile.writeText(swiftScript)

            val process = ProcessBuilder("swift", scriptFile.absolutePath)
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText().trim()
            }

            val exitCode = process.waitFor()

            if (exitCode == 0 && output.isNotEmpty()) output else null
        } catch (e: Exception) {
            logger.warn(LogCategory.BROWSER, "Error getting default handler for scheme", mapOf("scheme" to scheme), error = e)
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
     * Open System Settings (Ventura+) or System Preferences (Monterey and earlier)
     * to the Default Browser settings pane
     */
    private fun openSystemPreferences() {
        try {
            val url = if (getMacOSMajorVersion() >= 13) {
                "x-apple.systempreferences:com.apple.Desktop-Settings.extension"
            } else {
                "x-apple.systempreferences:com.apple.preference.general"
            }

            val process = ProcessBuilder("open", url).start()
            process.waitFor()

            logger.info(LogCategory.BROWSER, "Opened System Settings for user to set default browser")
        } catch (e: Exception) {
            logger.warn(LogCategory.BROWSER, "Error opening System Settings", error = e)
        }
    }

    /**
     * Get the macOS major version number (e.g. 13 for Ventura, 14 for Sonoma)
     */
    private fun getMacOSMajorVersion(): Int {
        return try {
            val process = ProcessBuilder("sw_vers", "-productVersion").start()
            val version = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            process.waitFor()
            version.substringBefore(".").toIntOrNull() ?: 0
        } catch (e: Exception) {
            logger.warn(LogCategory.BROWSER, "Error getting macOS version", error = e)
            0
        }
    }
}
