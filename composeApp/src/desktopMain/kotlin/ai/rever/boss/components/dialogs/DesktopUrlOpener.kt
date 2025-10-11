package ai.rever.boss.components.dialogs

import java.awt.Desktop
import java.net.URI

/**
 * Desktop implementation of URL opening
 * Opens URL in system browser (later can be enhanced to open in Fluck)
 */
actual fun openUrlInBrowser(url: String) {
    try {
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(URI.create(url))
                if (url.contains("/register/mobile")) {
                    println("Opened WebAuthn registration URL in system browser: $url")
                } else if (url.contains("/auth/mobile")) {
                    println("Opened WebAuthn authentication URL in system browser: $url")
                } else {
                    println("Opened URL in system browser: $url")
                }
            } else {
                println("Desktop browse not supported, URL: $url")
            }
        } else {
            println("Desktop not supported, URL: $url")
        }
    } catch (e: Exception) {
        println("Failed to open URL in browser: ${e.message}")
        e.printStackTrace()
    }
}