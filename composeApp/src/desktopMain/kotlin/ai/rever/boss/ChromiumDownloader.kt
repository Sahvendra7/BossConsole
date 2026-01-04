package ai.rever.boss

import com.teamdev.jxbrowser.engine.Engine
import com.teamdev.jxbrowser.engine.EngineOptions
import com.teamdev.jxbrowser.engine.RenderingMode
import java.nio.file.Paths

/**
 * Utility to download JxBrowser's Chromium binaries.
 *
 * This is used by CI to pre-download Chromium for branding.
 * The license is bound to the ai.rever.boss package, so this must run
 * within the context of this project.
 */
fun main(args: Array<String>) {
    val chromiumDir = if (args.isNotEmpty()) {
        Paths.get(args[0])
    } else {
        Paths.get(System.getProperty("user.home"), "chromium-binaries")
    }

    println("ChromiumDownloader: Downloading to $chromiumDir")

    val licenseKey = System.getenv("JXBROWSER_LICENSE_KEY")
        ?: System.getProperty("jxbrowser.license.key")
        ?: error("JXBROWSER_LICENSE_KEY environment variable or jxbrowser.license.key property not set")

    // Check if we should disable sandbox (needed on Linux CI where user namespaces aren't available)
    val disableSandbox = System.getenv("JXBROWSER_DISABLE_SANDBOX")?.toBoolean() ?: false
    val isLinux = System.getProperty("os.name").lowercase().contains("linux")

    try {
        val optionsBuilder = EngineOptions.newBuilder(RenderingMode.OFF_SCREEN)
            .licenseKey(licenseKey)
            .chromiumDir(chromiumDir)

        // Disable sandbox on Linux CI environments where user namespaces aren't supported
        if (disableSandbox || (isLinux && System.getenv("CI") != null)) {
            println("ChromiumDownloader: Disabling Chromium sandbox for CI environment")
            optionsBuilder.disableSandbox()
        }

        val options = optionsBuilder.build()

        println("ChromiumDownloader: Creating engine (this triggers download)...")
        val engine = Engine.newInstance(options)
        println("ChromiumDownloader: Chromium downloaded successfully!")

        // List contents to verify
        val files = chromiumDir.toFile().listFiles()
        println("ChromiumDownloader: Directory contents:")
        files?.forEach { println("  - ${it.name}") }

        engine.close()
        println("ChromiumDownloader: Done!")
    } catch (e: Exception) {
        // On headless Linux CI, the engine may fail to start even after downloading
        // Check if binaries were downloaded successfully
        val files = chromiumDir.toFile().listFiles()
        val hasChromium = files?.any {
            it.name.contains("chromium", ignoreCase = true) ||
            it.name.contains("chrome", ignoreCase = true) ||
            it.name == "jxbrowser-chromium" ||
            it.isDirectory
        } == true

        if (hasChromium && isLinux) {
            println("ChromiumDownloader: Engine failed to start but binaries were downloaded!")
            println("ChromiumDownloader: Directory contents:")
            files?.forEach { println("  - ${it.name}") }
            println("ChromiumDownloader: Done (download-only mode on Linux CI)")
        } else {
            System.err.println("ChromiumDownloader: Error - ${e.message}")
            e.printStackTrace()
            System.exit(1)
        }
    }
}
