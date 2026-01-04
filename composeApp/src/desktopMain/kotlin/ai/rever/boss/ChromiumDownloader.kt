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

    try {
        val options = EngineOptions.newBuilder(RenderingMode.OFF_SCREEN)
            .licenseKey(licenseKey)
            .chromiumDir(chromiumDir)
            .build()

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
        System.err.println("ChromiumDownloader: Error - ${e.message}")
        e.printStackTrace()
        System.exit(1)
    }
}
