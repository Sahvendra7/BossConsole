package ai.rever.boss.updater

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Generates platform-specific update helper scripts
 *
 * These scripts run AFTER the main app quits and handle:
 * - Waiting for app to fully terminate
 * - Performing the actual installation
 * - Launching the updated app
 * - Cleaning up the script itself
 */
object UpdateScriptGenerator {

    /**
     * Escape a string for safe use as a shell argument
     *
     * Uses single quotes to prevent command substitution, variable expansion,
     * and other shell metacharacter interpretation. Single quotes are escaped
     * using the '\'' pattern (end quote, escaped quote, start quote).
     *
     * @param arg The string to escape
     * @return The escaped string, safe for shell interpolation
     */
    private fun escapeShellArg(arg: String): String {
        // Single quotes prevent ALL interpolation and command substitution
        // To include a literal single quote, we use: '\''
        // (end quote, escaped quote, start quote)
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    /**
     * Escape a string for safe use in Windows batch files
     *
     * Windows batch files have different metacharacters than Unix shells:
     * - % : Variable expansion (%VAR%)
     * - ^ : Escape character
     * - ! : Delayed expansion (!VAR!)
     * - " : Quote character
     * - & | < > : Command operators
     *
     * Strategy: Wrap in quotes, escape internal quotes by doubling, escape percent signs
     *
     * @param arg The string to escape
     * @return The escaped string, safe for Windows batch file interpolation
     */
    private fun escapeWindowsArg(arg: String): String {
        // Escape internal double quotes by doubling them
        val escapedQuotes = arg.replace("\"", "\"\"")
        // Escape percent signs with double percent (prevents variable expansion)
        val escapedPercent = escapedQuotes.replace("%", "%%")
        // Wrap in quotes to handle spaces and special characters
        return "\"$escapedPercent\""
    }

    /**
     * Validate a path for security concerns
     *
     * Performs defensive checks to detect potentially malicious inputs:
     * - Null bytes (directory traversal attacks)
     * - Shell metacharacters (command injection)
     * - Newlines (script injection)
     * - Suspicious patterns (logs warnings)
     *
     * @param path The path to validate
     * @param description Description for error messages (e.g., "DMG path")
     * @throws SecurityException if path contains dangerous characters
     */
    private fun validatePath(path: String, description: String) {
        // Check for null bytes (can bypass path checks)
        if (path.contains('\u0000')) {
            throw SecurityException("$description contains null byte - possible directory traversal attack")
        }

        // Check for shell metacharacters that could enable command injection
        if (path.contains('$') || path.contains('`')) {
            throw SecurityException("$description contains shell metacharacters - possible command injection")
        }

        // Check for newlines (could inject additional commands)
        if (path.contains('\n') || path.contains('\r')) {
            throw SecurityException("$description contains newline characters - possible script injection")
        }

        // HARDENED: Path traversal is now a hard failure (was warning)
        // Legitimate update paths should always be absolute, never use ..
        if (path.contains("..")) {
            throw SecurityException("$description contains path traversal sequence '..' - rejected for security")
        }

        // HARDENED: Command separators are now hard failures (was warning)
        // These characters are never legitimate in filenames and indicate attack attempts
        if (path.contains(";") || path.contains("|") || path.contains("&")) {
            throw SecurityException("$description contains command separator characters - rejected for security")
        }

        // NEW: Windows batch metacharacters validation
        // These characters enable variable expansion and command injection in Windows batch files
        if (path.contains("%") || path.contains("^") || path.contains("!")) {
            throw SecurityException("$description contains Windows batch metacharacters - rejected for security")
        }
    }

    /**
     * Generate macOS update script
     *
     * @param dmgPath Path to the downloaded DMG file
     * @param targetAppPath Path where BOSS.app should be installed (e.g. /Applications/BOSS.app)
     * @param appPid Process ID of the running app to wait for
     * @return File object pointing to the generated script
     */
    fun generateMacOSUpdateScript(
        dmgPath: String,
        targetAppPath: String,
        appPid: Long
    ): File {
        // Validate inputs for security
        validatePath(dmgPath, "DMG path")
        validatePath(targetAppPath, "Target app path")

        // Escape paths for safe shell interpolation
        val escapedDmgPath = escapeShellArg(dmgPath)
        val escapedTargetAppPath = escapeShellArg(targetAppPath)

        println("🔒 Security: Validated and escaped update script parameters")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "boss-updater")
        tempDir.mkdirs()

        val scriptFile = File(tempDir, "update_boss_${System.currentTimeMillis()}.sh")

        val script = """
            #!/bin/bash

            # BOSS Update Helper Script
            # This script runs after BOSS quits to install the update

            echo "BOSS Update Helper started"
            echo "Waiting for BOSS to quit (PID: $appPid)..."

            # Wait for the app process to terminate (max 30 seconds)
            WAIT_COUNT=0
            MAX_WAIT=30
            while kill -0 $appPid 2>/dev/null; do
                sleep 1
                WAIT_COUNT=${'$'}((WAIT_COUNT + 1))
                if [ ${'$'}WAIT_COUNT -ge ${'$'}MAX_WAIT ]; then
                    echo "Timeout waiting for app to quit"
                    exit 1
                fi
            done

            echo "BOSS has quit. Starting installation..."

            # Give extra time for file locks to release
            sleep 2

            # Mount the DMG (using escaped path for security)
            echo "Mounting DMG: $escapedDmgPath"
            hdiutil attach $escapedDmgPath -nobrowse -quiet
            if [ ${'$'}? -ne 0 ]; then
                echo "Failed to mount DMG"
                # Fallback: Open DMG for manual installation (using escaped path)
                open $escapedDmgPath
                exit 1
            fi

            # Find the mounted volume
            VOLUME=${'$'}(ls -d /Volumes/BOSS* 2>/dev/null | head -n 1)
            if [ -z "${'$'}VOLUME" ]; then
                echo "Could not find mounted BOSS volume"
                # Try to open DMG manually (using escaped path)
                open $escapedDmgPath
                exit 1
            fi

            echo "Found mounted volume: ${'$'}VOLUME"

            # Find the .app bundle in the volume
            APP_BUNDLE=${'$'}(find "${'$'}VOLUME" -name "*.app" -maxdepth 1 | grep -i boss | head -n 1)
            if [ -z "${'$'}APP_BUNDLE" ]; then
                echo "Could not find BOSS.app in volume"
                hdiutil detach "${'$'}VOLUME" -quiet
                open $escapedDmgPath
                exit 1
            fi

            echo "Found app bundle: ${'$'}APP_BUNDLE"

            # Remove old app (using escaped path for security)
            echo "Removing old BOSS: $escapedTargetAppPath"
            if [ -d $escapedTargetAppPath ]; then
                rm -rf $escapedTargetAppPath
                if [ ${'$'}? -ne 0 ]; then
                    echo "Failed to remove old app (may need admin permissions)"
                    hdiutil detach "${'$'}VOLUME" -quiet
                    exit 1
                fi
            fi

            # Copy new app (using escaped target path for security)
            echo "Installing new BOSS..."
            cp -R "${'$'}APP_BUNDLE" $escapedTargetAppPath
            if [ ${'$'}? -ne 0 ]; then
                echo "Failed to copy new app"
                hdiutil detach "${'$'}VOLUME" -quiet
                exit 1
            fi

            echo "Installation successful!"

            # Unmount DMG
            echo "Cleaning up..."
            hdiutil detach "${'$'}VOLUME" -quiet

            # Launch the updated app (using escaped path for security)
            echo "Launching new BOSS..."
            open $escapedTargetAppPath

            # Give the app time to start
            sleep 2

            # Self-destruct - remove this script
            echo "Update complete. Cleaning up script..."
            rm -f "${'$'}0"

            exit 0
        """.trimIndent()

        scriptFile.writeText(script)
        makeExecutable(scriptFile)

        println("Generated macOS update script: ${scriptFile.absolutePath}")
        return scriptFile
    }

    /**
     * Generate Windows update script
     *
     * @param msiPath Path to the downloaded MSI file
     * @param appPid Process ID of the running app to wait for
     * @return File object pointing to the generated script
     */
    fun generateWindowsUpdateScript(
        msiPath: String,
        appPid: Long
    ): File {
        // Validate input for security
        validatePath(msiPath, "MSI path")

        // Escape path for Windows batch file (handles quotes, percent signs, etc.)
        val escapedMsiPath = escapeWindowsArg(msiPath)

        println("🔒 Security: Validated and escaped Windows update script parameters")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "boss-updater")
        tempDir.mkdirs()

        val scriptFile = File(tempDir, "update_boss_${System.currentTimeMillis()}.bat")

        val script = """
            @echo off
            REM BOSS Update Helper Script

            echo BOSS Update Helper started
            echo Waiting for BOSS to quit (PID: $appPid)...

            REM Wait for process to terminate
            :waitloop
            tasklist /FI "PID eq $appPid" 2>NUL | find /I /N "$appPid">NUL
            if "%ERRORLEVEL%"=="0" (
                timeout /t 1 /nobreak >NUL
                goto waitloop
            )

            echo BOSS has quit. Starting installation...
            timeout /t 2 /nobreak >NUL

            REM Launch MSI installer (using escaped path for security)
            echo Installing update...
            msiexec /i $escapedMsiPath /quiet /norestart

            if %ERRORLEVEL% NEQ 0 (
                echo Installation failed. Opening installer manually...
                start "" $escapedMsiPath
            ) else (
                echo Installation successful!
            )

            REM Clean up
            timeout /t 2 /nobreak >NUL
            del "%~f0"
        """.trimIndent()

        scriptFile.writeText(script)

        println("Generated Windows update script: ${scriptFile.absolutePath}")
        return scriptFile
    }

    /**
     * Generate Linux DEB update script
     *
     * @param debPath Path to the downloaded DEB file
     * @param appPid Process ID of the running app to wait for
     * @return File object pointing to the generated script
     */
    fun generateLinuxDebUpdateScript(
        debPath: String,
        appPid: Long
    ): File {
        // Validate input for security
        validatePath(debPath, "DEB path")

        // Escape path for safe shell interpolation
        val escapedDebPath = escapeShellArg(debPath)

        println("🔒 Security: Validated and escaped Linux DEB update script parameters")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "boss-updater")
        tempDir.mkdirs()

        val scriptFile = File(tempDir, "update_boss_${System.currentTimeMillis()}.sh")

        val script = """
            #!/bin/bash

            # BOSS Update Helper Script (Debian/Ubuntu)

            echo "BOSS Update Helper started"
            echo "Waiting for BOSS to quit (PID: $appPid)..."

            # Wait for the app process to terminate (max 30 seconds)
            WAIT_COUNT=0
            MAX_WAIT=30
            while kill -0 $appPid 2>/dev/null; do
                sleep 1
                WAIT_COUNT=${'$'}((WAIT_COUNT + 1))
                if [ ${'$'}WAIT_COUNT -ge ${'$'}MAX_WAIT ]; then
                    echo "Timeout waiting for app to quit"
                    exit 1
                fi
            done

            echo "BOSS has quit. Starting installation..."

            # Give extra time for file locks to release
            sleep 2

            echo "Installing DEB package: $escapedDebPath"

            # Batch all privileged operations into a single pkexec/sudo call
            # This ensures only ONE password prompt for the entire update
            PRIVILEGED_SCRIPT='
                set -e
                DEB_PATH='"$escapedDebPath"'
                DESKTOP_FILE="/usr/share/applications/boss-BOSS.desktop"
                ICON_DIR="/usr/share/icons/hicolor/256x256/apps"

                echo "=== Installing DEB package ==="
                dpkg -i "${'$'}DEB_PATH" || {
                    echo "Initial install failed, fixing dependencies..."
                    apt-get install -f -y 2>/dev/null || true
                    echo "Retrying installation..."
                    dpkg -i "${'$'}DEB_PATH"
                }

                echo "=== Fixing desktop file ==="
                if [ -f "${'$'}DESKTOP_FILE" ]; then
                    grep -q "StartupWMClass" "${'$'}DESKTOP_FILE" || echo "StartupWMClass=BOSS" >> "${'$'}DESKTOP_FILE"
                    sed -i "s|Icon=/opt/boss/lib/BOSS.png|Icon=boss|g" "${'$'}DESKTOP_FILE"
                    echo "Desktop file updated"
                fi

                echo "=== Installing icon ==="
                if [ -f /opt/boss/lib/BOSS.png ]; then
                    mkdir -p "${'$'}ICON_DIR"
                    cp /opt/boss/lib/BOSS.png "${'$'}ICON_DIR/boss.png"
                    echo "Icon installed to hicolor theme"
                fi

                echo "=== All privileged operations complete ==="
            '

            # Try pkexec first (graphical sudo prompt), fall back to sudo
            if command -v pkexec &> /dev/null; then
                echo "Using pkexec for privileged operations..."
                pkexec sh -c "${'$'}PRIVILEGED_SCRIPT"
                INSTALL_RESULT=${'$'}?
            elif command -v sudo &> /dev/null; then
                echo "Using sudo for privileged operations..."
                sudo sh -c "${'$'}PRIVILEGED_SCRIPT"
                INSTALL_RESULT=${'$'}?
            else
                echo "Error: Neither pkexec nor sudo available for installation"
                exit 1
            fi

            if [ ${'$'}INSTALL_RESULT -ne 0 ]; then
                echo "Installation failed with exit code ${'$'}INSTALL_RESULT"
            else
                echo "Installation complete!"
            fi

            # Refresh icon cache and desktop database (non-privileged, may fail silently)
            gtk-update-icon-cache -f /usr/share/icons/hicolor 2>/dev/null || true
            if command -v update-desktop-database &> /dev/null; then
                update-desktop-database /usr/share/applications 2>/dev/null || true
            fi

            # Launch the updated app
            echo "Launching BOSS..."
            if [ -x /opt/boss/bin/BOSS ]; then
                nohup /opt/boss/bin/BOSS > /dev/null 2>&1 &
            elif [ -x /usr/bin/boss ]; then
                nohup /usr/bin/boss > /dev/null 2>&1 &
            elif command -v boss &> /dev/null; then
                nohup boss > /dev/null 2>&1 &
            else
                echo "Warning: Could not find BOSS executable to launch"
            fi

            # Give the app time to start
            sleep 2

            # Self-destruct - remove this script
            echo "Update complete. Cleaning up script..."
            rm -f "${'$'}0"

            exit 0
        """.trimIndent()

        scriptFile.writeText(script)
        makeExecutable(scriptFile)

        println("Generated Linux DEB update script: ${scriptFile.absolutePath}")
        return scriptFile
    }

    /**
     * Generate Linux RPM update script
     *
     * @param rpmPath Path to the downloaded RPM file
     * @param appPid Process ID of the running app to wait for
     * @return File object pointing to the generated script
     */
    fun generateLinuxRpmUpdateScript(
        rpmPath: String,
        appPid: Long
    ): File {
        // Validate input for security
        validatePath(rpmPath, "RPM path")

        // Escape path for safe shell interpolation
        val escapedRpmPath = escapeShellArg(rpmPath)

        println("🔒 Security: Validated and escaped Linux RPM update script parameters")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "boss-updater")
        tempDir.mkdirs()

        val scriptFile = File(tempDir, "update_boss_${System.currentTimeMillis()}.sh")

        val script = """
            #!/bin/bash

            # BOSS Update Helper Script (Fedora/RHEL)

            echo "BOSS Update Helper started"
            echo "Waiting for BOSS to quit (PID: $appPid)..."

            # Wait for the app process to terminate (max 30 seconds)
            WAIT_COUNT=0
            MAX_WAIT=30
            while kill -0 $appPid 2>/dev/null; do
                sleep 1
                WAIT_COUNT=${'$'}((WAIT_COUNT + 1))
                if [ ${'$'}WAIT_COUNT -ge ${'$'}MAX_WAIT ]; then
                    echo "Timeout waiting for app to quit"
                    exit 1
                fi
            done

            echo "BOSS has quit. Starting installation..."

            # Give extra time for file locks to release
            sleep 2

            echo "Installing RPM package: $escapedRpmPath"

            # Batch all privileged operations into a single pkexec/sudo call
            # This ensures only ONE password prompt for the entire update
            PRIVILEGED_SCRIPT='
                set -e
                RPM_PATH='"$escapedRpmPath"'
                DESKTOP_FILE="/usr/share/applications/boss-BOSS.desktop"
                ICON_DIR="/usr/share/icons/hicolor/256x256/apps"

                echo "=== Installing RPM package ==="
                rpm -U "${'$'}RPM_PATH" || {
                    echo "Direct rpm install failed, trying package manager..."
                    if command -v dnf &> /dev/null; then
                        dnf install -y "${'$'}RPM_PATH"
                    elif command -v yum &> /dev/null; then
                        yum install -y "${'$'}RPM_PATH"
                    else
                        echo "No package manager found for dependency resolution"
                        exit 1
                    fi
                }

                echo "=== Fixing desktop file ==="
                if [ -f "${'$'}DESKTOP_FILE" ]; then
                    grep -q "StartupWMClass" "${'$'}DESKTOP_FILE" || echo "StartupWMClass=BOSS" >> "${'$'}DESKTOP_FILE"
                    sed -i "s|Icon=/opt/boss/lib/BOSS.png|Icon=boss|g" "${'$'}DESKTOP_FILE"
                    echo "Desktop file updated"
                fi

                echo "=== Installing icon ==="
                if [ -f /opt/boss/lib/BOSS.png ]; then
                    mkdir -p "${'$'}ICON_DIR"
                    cp /opt/boss/lib/BOSS.png "${'$'}ICON_DIR/boss.png"
                    echo "Icon installed to hicolor theme"
                fi

                echo "=== All privileged operations complete ==="
            '

            # Try pkexec first (graphical sudo prompt), fall back to sudo
            if command -v pkexec &> /dev/null; then
                echo "Using pkexec for privileged operations..."
                pkexec sh -c "${'$'}PRIVILEGED_SCRIPT"
                INSTALL_RESULT=${'$'}?
            elif command -v sudo &> /dev/null; then
                echo "Using sudo for privileged operations..."
                sudo sh -c "${'$'}PRIVILEGED_SCRIPT"
                INSTALL_RESULT=${'$'}?
            else
                echo "Error: Neither pkexec nor sudo available for installation"
                exit 1
            fi

            if [ ${'$'}INSTALL_RESULT -ne 0 ]; then
                echo "Installation failed with exit code ${'$'}INSTALL_RESULT"
            else
                echo "Installation complete!"
            fi

            # Refresh icon cache and desktop database (non-privileged, may fail silently)
            gtk-update-icon-cache -f /usr/share/icons/hicolor 2>/dev/null || true
            if command -v update-desktop-database &> /dev/null; then
                update-desktop-database /usr/share/applications 2>/dev/null || true
            fi

            # Launch the updated app
            echo "Launching BOSS..."
            if [ -x /opt/boss/bin/BOSS ]; then
                nohup /opt/boss/bin/BOSS > /dev/null 2>&1 &
            elif [ -x /usr/bin/boss ]; then
                nohup /usr/bin/boss > /dev/null 2>&1 &
            elif command -v boss &> /dev/null; then
                nohup boss > /dev/null 2>&1 &
            else
                echo "Warning: Could not find BOSS executable to launch"
            fi

            # Give the app time to start
            sleep 2

            # Self-destruct - remove this script
            echo "Update complete. Cleaning up script..."
            rm -f "${'$'}0"

            exit 0
        """.trimIndent()

        scriptFile.writeText(script)
        makeExecutable(scriptFile)

        println("Generated Linux RPM update script: ${scriptFile.absolutePath}")
        return scriptFile
    }

    /**
     * Launch the update script in the background
     *
     * @param scriptFile The script file to execute
     */
    fun launchScript(scriptFile: File) {
        try {
            val os = System.getProperty("os.name").lowercase()
            val command = when {
                os.contains("mac") || os.contains("darwin") || os.contains("linux") -> {
                    // macOS/Linux: Launch in background with nohup
                    listOf("nohup", "sh", scriptFile.absolutePath)
                }
                os.contains("win") -> {
                    // Windows: Launch detached
                    listOf("cmd", "/c", "start", "/b", scriptFile.absolutePath)
                }
                else -> {
                    println("Unknown OS, attempting direct execution")
                    listOf("sh", scriptFile.absolutePath)
                }
            }

            println("Launching update script: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD)
            processBuilder.start()

            println("✅ Update script launched successfully")

        } catch (e: Exception) {
            println("❌ Failed to launch update script: ${e.message}")
            throw e
        }
    }

    /**
     * Make a file executable (Unix-like systems)
     *
     * @param file The file to make executable
     */
    fun makeExecutable(file: File) {
        try {
            val path = file.toPath()
            val permissions = mutableSetOf<PosixFilePermission>()
            permissions.add(PosixFilePermission.OWNER_READ)
            permissions.add(PosixFilePermission.OWNER_WRITE)
            permissions.add(PosixFilePermission.OWNER_EXECUTE)
            permissions.add(PosixFilePermission.GROUP_READ)
            permissions.add(PosixFilePermission.GROUP_EXECUTE)
            permissions.add(PosixFilePermission.OTHERS_READ)
            permissions.add(PosixFilePermission.OTHERS_EXECUTE)

            Files.setPosixFilePermissions(path, permissions)
            println("Set executable permissions on: ${file.name}")
        } catch (e: Exception) {
            // Not a POSIX system (Windows) - ignore
            println("Could not set POSIX permissions (probably Windows): ${e.message}")
        }
    }
}
