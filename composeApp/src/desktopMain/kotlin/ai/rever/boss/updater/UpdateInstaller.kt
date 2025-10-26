package ai.rever.boss.updater

import ai.rever.boss.utils.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths
import java.util.*

/**
 * Platform-specific update installation logic
 *
 * This class handles the actual installation of updates for different platforms.
 * For macOS, it uses a helper script pattern to safely install updates after the app quits.
 */
sealed class InstallResult {
    data class Success(val message: String) : InstallResult()
    data class RequiresRestart(val message: String) : InstallResult()
    data class Error(val message: String) : InstallResult()
}

object UpdateInstaller {

    /**
     * Validate download file for security concerns
     *
     * Performs early validation to detect potentially malicious files:
     * - File existence check
     * - Extension validation (.dmg, .msi, .jar)
     * - Path canonicalization to prevent directory traversal
     * - Filename sanitization check
     *
     * @param downloadFile The file to validate
     * @param expectedExtension Expected file extension (e.g., ".dmg")
     * @throws SecurityException if file is invalid or suspicious
     */
    private fun validateDownloadFile(downloadFile: File, expectedExtension: String) {
        // Check file exists
        if (!downloadFile.exists()) {
            throw SecurityException("Download file does not exist: ${downloadFile.absolutePath}")
        }

        // Validate file extension
        if (!downloadFile.name.endsWith(expectedExtension, ignoreCase = true)) {
            throw SecurityException(
                "Invalid file extension. Expected $expectedExtension but got: ${downloadFile.name}"
            )
        }

        // Canonicalize path to detect directory traversal attempts
        val canonicalPath = try {
            downloadFile.canonicalPath
        } catch (e: Exception) {
            throw SecurityException("Failed to canonicalize path: ${downloadFile.absolutePath}")
        }

        // Ensure canonicalized path is in expected temp directory
        val expectedTempDir = File(System.getProperty("java.io.tmpdir"), "boss-updates").canonicalPath
        if (!canonicalPath.startsWith(expectedTempDir)) {
            println("⚠️ Security Warning: Download file outside expected directory")
            println("   Expected: $expectedTempDir")
            println("   Actual: $canonicalPath")
        }

        // Check for suspicious characters in filename
        val filename = downloadFile.name
        if (filename.contains('\u0000') || filename.contains('\n') || filename.contains('\r')) {
            throw SecurityException("Filename contains invalid characters: $filename")
        }

        // Check for shell metacharacters (defense in depth)
        if (filename.contains('$') || filename.contains('`') || filename.contains(';')) {
            println("⚠️ Security Warning: Filename contains shell metacharacters: $filename")
        }

        println("✅ Security: Validated download file: ${downloadFile.name}")
    }

    /**
     * Extract version from update file name.
     *
     * Expected formats:
     * - macOS: BOSS-8.12.18-Universal.dmg
     * - Windows: BOSS-8.12.18.msi
     * - Linux: BOSS-8.12.18.jar
     *
     * @param file The update file
     * @return Parsed version, or null if version cannot be extracted
     */
    private fun extractVersionFromFilename(file: File): Version? {
        return try {
            val filename = file.name
            println("Extracting version from filename: $filename")

            // Remove BOSS- prefix and file extension
            val versionStr = filename
                .removePrefix("BOSS-")
                .removeSuffix("-Universal.dmg")
                .removeSuffix(".dmg")
                .removeSuffix(".msi")
                .removeSuffix(".jar")
                .removeSuffix(".deb")
                .removeSuffix(".rpm")

            println("Extracted version string: $versionStr")

            Version.parse(versionStr)
        } catch (e: Exception) {
            println("Failed to extract version from filename: ${e.message}")
            null
        }
    }

    /**
     * Verify update is not a downgrade (Issue #111 fix).
     *
     * Prevents installing older versions which was the root cause of Issue #111.
     *
     * @param downloadFile The update file to verify
     * @return true if safe to install, false if downgrade detected
     */
    private fun verifyNoDowngrade(downloadFile: File): Boolean {
        val downloadedVersion = extractVersionFromFilename(downloadFile)

        if (downloadedVersion == null) {
            println("⚠️ Cannot verify update version - version extraction failed")
            println("   Filename: ${downloadFile.name}")
            println("   Proceeding with caution...")
            // Allow installation if version cannot be extracted (for manual updates)
            return true
        }

        val currentVersion = Version.CURRENT

        println("Version check:")
        println("  Current: $currentVersion")
        println("  Download: $downloadedVersion")

        if (downloadedVersion < currentVersion) {
            println("❌ DOWNGRADE DETECTED!")
            println("   Cannot install older version $downloadedVersion")
            println("   Current version is $currentVersion")
            println("   This is prevented to avoid Issue #111")
            return false
        }

        if (downloadedVersion == currentVersion) {
            println("⚠️ Same version detected ($downloadedVersion)")
            println("   Allowing reinstall of same version")
            // Allow reinstall of same version (useful for repairs)
        } else {
            println("✅ Update verified: $currentVersion → $downloadedVersion")
        }

        return true
    }

    /**
     * Install update for the current platform
     *
     * @param downloadPath Path to the downloaded update file
     * @return InstallResult indicating success, restart required, or error
     */
    suspend fun installUpdate(downloadPath: String): InstallResult {
        return try {
            val downloadFile = File(downloadPath)
            if (!downloadFile.exists()) {
                println("Update file not found: $downloadPath")
                return InstallResult.Error("Update file not found")
            }

            // Verify this is not a downgrade (Issue #111 fix)
            if (!verifyNoDowngrade(downloadFile)) {
                return InstallResult.Error(
                    "Cannot install older version. This update appears to be a downgrade from your current version."
                )
            }

            when (getCurrentPlatform()) {
                "macOS" -> installMacOSUpdate(downloadFile)
                "Windows" -> installWindowsUpdate(downloadFile)
                else -> installJarUpdate(downloadFile)
            }
        } catch (e: Exception) {
            println("Error installing update: ${e.message}")
            InstallResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Install macOS update using helper script pattern
     *
     * The app cannot delete itself while running. Instead:
     * 1. Generate a helper script with the current process PID
     * 2. Launch the script in the background
     * 3. Return RequiresRestart to signal the app should quit
     * 4. Script waits for app to quit, then installs update
     */
    private suspend fun installMacOSUpdate(downloadFile: File): InstallResult {
        return withContext(Dispatchers.IO) {
            try {
                println("Starting macOS update installation...")

                // Validate download file for security (early check)
                validateDownloadFile(downloadFile, ".dmg")

                // Get current application bundle path
                val currentAppPath = getCurrentApplicationPath()
                if (currentAppPath == null) {
                    println("⚠️ Could not determine current application path")
                    println("   This is expected when running in development mode (IDE/Gradle)")
                    println("   Falling back to manual DMG installation")
                    return@withContext openDMGForManualInstallation(downloadFile)
                }

                println("🎯 Target application path: $currentAppPath")

                // Verify DMG is valid by attempting to mount it
                println("📦 Mounting DMG for verification...")
                val mountTest = ProcessBuilder(
                    "hdiutil", "attach", downloadFile.absolutePath,
                    "-nobrowse", "-quiet", "-verify"
                ).start()
                mountTest.waitFor()

                if (mountTest.exitValue() != 0) {
                    println("❌ DMG mounting failed")
                    return@withContext InstallResult.Error("Failed to mount DMG for verification")
                }

                // Find the mounted volume
                val mountedVolume = findMountedBossVolume()
                if (mountedVolume == null) {
                    println("❌ Could not find mounted BOSS volume after successful mount")
                    cleanupDMG(null) // Try to cleanup any stray mounts
                    return@withContext InstallResult.Error("Could not locate mounted DMG volume")
                }

                // Use try-finally to ensure DMG is always unmounted, even if exceptions occur
                try {
                    println("📂 Verifying DMG contents (volume: ${mountedVolume.name})...")

                    // Verify app bundle exists in DMG
                    val appBundle = findAppBundleInVolume(mountedVolume)
                        ?: throw IllegalStateException("Could not find BOSS.app in mounted DMG")

                    println("✅ DMG verified successfully (found: ${appBundle.name})")

                    // DMG is valid - now we can safely unmount it (script will remount it)
                    // Unmounting happens in the finally block below

                } finally {
                    // CRITICAL: Always unmount the DMG, even if verification failed
                    println("🧹 Cleaning up verification mount...")
                    cleanupDMG(mountedVolume)
                }

                // At this point, DMG has been verified and unmounted
                // Generate the update script that will remount, install, and cleanup
                val currentPid = ProcessHandle.current().pid()
                println("📝 Generating update script (PID: $currentPid)")

                val scriptFile = UpdateScriptGenerator.generateMacOSUpdateScript(
                    dmgPath = downloadFile.absolutePath,
                    targetAppPath = currentAppPath,
                    appPid = currentPid
                )

                // Launch the script in the background
                println("🚀 Launching update script")
                UpdateScriptGenerator.launchScript(scriptFile)

                // Return RequiresRestart - the UpdateManager will handle quitting
                InstallResult.RequiresRestart(
                    "Update is ready to install. The app will quit and install the update."
                )

            } catch (e: Exception) {
                println("❌ Error during update preparation: ${e.message}")
                InstallResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Install Windows update using helper script pattern
     * Similar to macOS, but uses MSI installer
     */
    private suspend fun installWindowsUpdate(downloadFile: File): InstallResult {
        return withContext(Dispatchers.IO) {
            try {
                println("Starting Windows update installation...")

                // Validate download file for security (early check)
                validateDownloadFile(downloadFile, ".msi")

                // Generate update script with current process PID
                val currentPid = ProcessHandle.current().pid()
                println("📝 Generating update script (PID: $currentPid)")

                val scriptFile = UpdateScriptGenerator.generateWindowsUpdateScript(
                    msiPath = downloadFile.absolutePath,
                    appPid = currentPid
                )

                // Launch the script in the background
                println("🚀 Launching update script")
                UpdateScriptGenerator.launchScript(scriptFile)

                // Return RequiresRestart
                InstallResult.RequiresRestart(
                    "Update is ready to install. The app will quit and install the update."
                )

            } catch (e: Exception) {
                println("❌ Error during update preparation: ${e.message}")
                InstallResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Install JAR update (Linux/other platforms)
     * JAR files can be replaced while running, so no restart needed
     */
    private suspend fun installJarUpdate(downloadFile: File): InstallResult {
        return withContext(Dispatchers.IO) {
            try {
                println("Starting JAR update installation...")

                // Validate download file for security (early check)
                validateDownloadFile(downloadFile, ".jar")

                // Get current JAR path
                val currentJar = getCurrentJarPath()
                if (currentJar == null) {
                    println("❌ Could not determine current JAR path")
                    return@withContext InstallResult.Error("Could not locate current JAR")
                }

                // Backup current JAR
                val backupJar = File(currentJar.parentFile, "${currentJar.name}.backup")
                currentJar.copyTo(backupJar, overwrite = true)
                println("📦 Backed up current JAR to: ${backupJar.absolutePath}")

                // Replace current JAR
                downloadFile.copyTo(currentJar, overwrite = true)

                println("✅ JAR updated successfully")
                InstallResult.Success("Update installed. Restart the app to use the new version.")

            } catch (e: Exception) {
                println("❌ Failed to update JAR: ${e.message}")
                InstallResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Get current application path for macOS .app bundle
     * Returns null if running in development mode or path cannot be determined
     */
    fun getCurrentApplicationPath(): String? {
        return try {
            println("🔍 Attempting to detect current application path...")

            // Method 1: Check java.library.path for .app bundle
            val libraryPath = System.getProperty("java.library.path")
            println("   java.library.path: $libraryPath")

            val bundlePath = libraryPath
                ?.split(":")
                ?.find { it.contains(".app") }
                ?.let { "${it.substringBefore(".app")}.app" }

            if (bundlePath?.contains(".app") == true && File(bundlePath).exists()) {
                println("✅ Found app bundle via library path: $bundlePath")
                return bundlePath
            }

            // Method 2: Try to find app bundle from current JAR/class location
            val jarPath = UpdateInstaller::class.java.protectionDomain.codeSource.location.path
            println("   Current code source: $jarPath")

            var currentFile = File(jarPath)
            // Walk up the directory tree looking for .app bundle
            for (i in 0..5) {
                println("   Checking parent $i: ${currentFile.absolutePath}")
                if (currentFile.name.endsWith(".app")) {
                    println("✅ Found app bundle via directory traversal: ${currentFile.absolutePath}")
                    return currentFile.absolutePath
                }
                currentFile = currentFile.parentFile ?: break
            }

            // Method 3: Check if running from Applications folder
            val applicationsPath = "/Applications/BOSS.app"
            if (File(applicationsPath).exists()) {
                println("✅ Found BOSS in Applications folder: $applicationsPath")
                return applicationsPath
            }

            println("❌ Could not determine application path - running in development mode?")
            println("   This is normal when running from IDE/Gradle")
            null

        } catch (e: Exception) {
            println("❌ Error getting application path: ${e.message}")
            null
        }
    }

    /**
     * Find the mounted BOSS volume after DMG mount
     */
    private fun findMountedBossVolume(): File? {
        val volumesDir = File("/Volumes")
        return volumesDir.listFiles()?.find {
            it.name.contains("BOSS", ignoreCase = true) && it.isDirectory
        }
    }

    /**
     * Find the .app bundle in the mounted volume
     */
    fun findAppBundleInVolume(mountedVolume: File): File? {
        return mountedVolume.listFiles()?.find {
            it.name.endsWith(".app") && it.name.contains("BOSS", ignoreCase = true)
        }
    }

    /**
     * Open DMG for manual installation (fallback for development mode)
     */
    private fun openDMGForManualInstallation(downloadFile: File): InstallResult {
        return try {
            val process = ProcessBuilder("open", downloadFile.absolutePath).start()
            process.waitFor()
            println("DMG opened for manual installation: ${downloadFile.absolutePath}")
            InstallResult.Success("DMG opened for manual installation")
        } catch (e: Exception) {
            println("Failed to open DMG: ${e.message}")
            InstallResult.Error(e.message ?: "Failed to open DMG")
        }
    }

    /**
     * Unmount a DMG volume
     */
    private fun cleanupDMG(mountedVolume: File?) {
        try {
            if (mountedVolume != null) {
                ProcessBuilder("hdiutil", "detach", mountedVolume.absolutePath, "-quiet")
                    .start()
                    .waitFor()
                println("DMG unmounted successfully")
            } else {
                // Try to unmount any BOSS volume
                val bossVolume = findMountedBossVolume()
                if (bossVolume != null) {
                    ProcessBuilder("hdiutil", "detach", bossVolume.absolutePath, "-quiet")
                        .start()
                        .waitFor()
                    println("DMG unmounted successfully")
                }
            }
        } catch (e: Exception) {
            println("Warning: Could not unmount DMG: ${e.message}")
        }
    }

    /**
     * Get the current JAR path (for JAR updates)
     */
    private fun getCurrentJarPath(): File? {
        return try {
            val jarPath = UpdateInstaller::class.java.protectionDomain.codeSource.location.toURI().path
            val jarFile = File(jarPath)
            if (jarFile.exists() && jarFile.name.endsWith(".jar")) {
                jarFile
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the current operating system platform
     */
    fun getCurrentPlatform(): String {
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        return when {
            osName.contains("mac") || osName.contains("darwin") -> "macOS"
            osName.contains("win") -> "Windows"
            osName.contains("linux") -> "Linux"
            else -> "Unknown"
        }
    }
}
