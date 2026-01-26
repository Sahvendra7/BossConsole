package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.panel.codebase.DirectoryPickerProvider
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Desktop implementation of DirectoryPickerProvider.
 * Uses native file dialogs (AWT FileDialog on macOS, JFileChooser on Windows/Linux).
 */
actual class DirectoryPickerProviderImpl : DirectoryPickerProvider {

    override fun pickDirectory(onResult: (String?) -> Unit) {
        SwingUtilities.invokeLater {
            // Set system look and feel for native appearance
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            } catch (e: Exception) {
                // If setting system L&F fails, continue with default
            }

            // Use native file dialog for better macOS integration
            val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

            if (isMacOS) {
                // Use AWT FileDialog for native macOS look
                System.setProperty("apple.awt.fileDialogForDirectories", "true")

                val dialog = FileDialog(null as Frame?, "Select Project Directory", FileDialog.LOAD)
                dialog.isVisible = true

                val directory = dialog.directory
                val file = dialog.file

                System.setProperty("apple.awt.fileDialogForDirectories", "false")

                if (directory != null && file != null) {
                    onResult("$directory$file")
                } else if (directory != null) {
                    onResult(directory)
                } else {
                    onResult(null)
                }
            } else {
                // For Windows/Linux, use JFileChooser
                val fileChooser = javax.swing.JFileChooser().apply {
                    fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Select Project Directory"
                    isAcceptAllFileFilterUsed = false
                    currentDirectory = File(System.getProperty("user.home"))
                }

                val result = fileChooser.showOpenDialog(null)

                if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                    val selectedFile = fileChooser.selectedFile
                    onResult(selectedFile?.absolutePath)
                } else {
                    onResult(null)
                }
            }
        }
    }
}
