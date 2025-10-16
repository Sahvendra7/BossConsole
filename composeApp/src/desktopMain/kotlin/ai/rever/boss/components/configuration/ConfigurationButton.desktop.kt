package ai.rever.boss.components.configuration

import java.awt.Desktop
import java.io.File

/**
 * Desktop implementation to open configuration directory
 */
actual fun openConfigurationDirectory(path: String) {
    try {
        val directory = File(path)
        if (directory.exists() && directory.isDirectory) {
            Desktop.getDesktop().open(directory)
        }
    } catch (e: Exception) {
    }
}
