package ai.rever.boss.plugin.browser

import ai.rever.boss.platform.FileNameSanitizer
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.browser.callback.OpenFileCallback
import com.teamdev.jxbrowser.browser.callback.OpenFilesCallback
import com.teamdev.jxbrowser.browser.callback.OpenFolderCallback
import com.teamdev.jxbrowser.browser.callback.SaveAsPdfCallback
import com.teamdev.jxbrowser.browser.callback.SaveFileCallback
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/**
 * Native OS file pickers for the page's own file dialogs.
 *
 * JxBrowser does not leave `<input type="file">` unanswered: both the Compose and the Swing
 * `BrowserView` install a set of *default* callbacks when they are created, and every one of
 * them shows a `javax.swing.JFileChooser`. On macOS that renders through `AquaFileChooserUI` -
 * a Swing re-creation of the pre-10.7 Open panel, with a "Where:" popup and a "File Format:"
 * combo - so uploading a file from BOSS looked a decade older than doing it from Safari or
 * Chrome, which both open the real `NSOpenPanel` (sidebar, column view, search, Recents).
 *
 * `java.awt.FileDialog` *is* that panel: its macOS peer (`sun.lwawt.macosx.CFileDialog`) is a
 * thin wrapper over `NSOpenPanel`/`NSSavePanel`, and its Windows peer is the Win32 common
 * dialog. So the fix is not to restyle anything, it is to answer these callbacks ourselves
 * before JxBrowser installs its own.
 *
 * **Registration has to happen before the view is created.** `DefaultCallbacks.register()`
 * checks `advisable.get(type).isPresent()` and *skips* any callback already set, so setting
 * ours first wins and JxBrowser never installs the Swing chooser. [installOn] is therefore
 * called from browser construction, not from composition - and because the view's matching
 * `unregister()` only removes what it actually registered, ours survives a view being
 * detached and re-attached (tab switch, fullscreen, a popup adopted into a window).
 *
 * **Linux keeps the JxBrowser default on purpose.** AWT there resolves to `GtkFileDialogPeer`
 * only when GTK loads, and falls back to `XFileDialogPeer` - a Motif-era dialog that is worse
 * than the Swing chooser it would be replacing. macOS and Windows have no such fallback.
 */
object NativeFileDialogs {
    private val logger = BossLogger.forComponent("NativeFileDialogs")

    private const val MAC_DIRECTORY_MODE = "apple.awt.fileDialogForDirectories"

    private val osName = System.getProperty("os.name").orEmpty().lowercase()
    private val isMacOs = osName.contains("mac")
    private val isWindows = osName.contains("win")

    /** True when [installOn] does anything. See the class KDoc for why Linux is excluded. */
    val isSupported: Boolean = isMacOs || isWindows

    /**
     * Point [browser]'s page-driven file dialogs at the OS ones.
     *
     * Safe to call more than once for the same browser - `Browser.set` replaces.
     */
    // Deliberately broad: a browser closed between creation and here throws rather than
    // no-ops, and losing the native panel is cosmetic. It must never fail browser creation.
    @Suppress("TooGenericExceptionCaught")
    fun installOn(browser: Browser) {
        if (!isSupported) return
        try {
            browser.set(OpenFileCallback::class.java, OpenFileCallback(::onOpenFile))
            browser.set(OpenFilesCallback::class.java, OpenFilesCallback(::onOpenFiles))
            browser.set(OpenFolderCallback::class.java, OpenFolderCallback(::onOpenFolder))
            browser.set(SaveFileCallback::class.java, SaveFileCallback(::onSaveFile))
            browser.set(SaveAsPdfCallback::class.java, SaveAsPdfCallback(::onSaveAsPdf))
        } catch (e: Exception) {
            logger.warn(LogCategory.BROWSER, "Could not install native file dialogs", error = e)
        }
    }

    // ============================================================
    // CALLBACKS
    // ============================================================

    private fun onOpenFile(
        params: OpenFileCallback.Params,
        action: OpenFileCallback.Action,
    ) = answerOnce(
        pick = {
            showOpen(
                suggestedDirectory = params.suggestedDirectory(),
                extensions = params.acceptableExtensions(),
                acceptAll = params.acceptAll(),
                multiple = false,
            ).firstOrNull()
        },
        open = action::open,
        cancel = action::cancel,
    )

    // The spread is over a user's selection, so a handful of paths at most, and
    // Action.open is Java varargs with no collection overload to prefer.
    @Suppress("SpreadOperator")
    private fun onOpenFiles(
        params: OpenFilesCallback.Params,
        action: OpenFilesCallback.Action,
    ) = answerOnce(
        pick = {
            // OpenFilesCallback.Params carries no suggestedDirectory and no acceptAll,
            // unlike the single-file one. Extensions still narrow the panel.
            showOpen(
                suggestedDirectory = "",
                extensions = params.acceptableExtensions(),
                acceptAll = false,
                multiple = true,
            ).takeIf { it.isNotEmpty() }
        },
        open = { paths -> action.open(*paths.toTypedArray()) },
        cancel = action::cancel,
    )

    private fun onOpenFolder(
        params: OpenFolderCallback.Params,
        action: OpenFolderCallback.Action,
    ) = answerOnce(
        pick = { showOpenFolder(params.suggestedDirectory()) },
        open = action::open,
        cancel = action::cancel,
    )

    private fun onSaveFile(
        params: SaveFileCallback.Params,
        action: SaveFileCallback.Action,
    ) = answerOnce(
        pick = {
            showSave(
                suggestedFileName = params.suggestedFileName(),
                suggestedDirectory = params.suggestedDirectory(),
                extensions = params.acceptableExtensions(),
                acceptAll = params.acceptAll(),
            )
        },
        open = action::save,
        cancel = action::cancel,
    )

    private fun onSaveAsPdf(
        params: SaveAsPdfCallback.Params,
        action: SaveAsPdfCallback.Action,
    ) = answerOnce(
        pick = {
            showSave(
                suggestedFileName = params.suggestedFileName(),
                // The only Params here that hands back a Path rather than a String.
                suggestedDirectory = params.suggestedDirectory()?.toString().orEmpty(),
                extensions = listOf("pdf"),
                acceptAll = false,
            )
        },
        open = action::save,
        cancel = action::cancel,
    )

    // ============================================================
    // DIALOGS
    // ============================================================

    private fun showOpen(
        suggestedDirectory: String,
        extensions: List<String>,
        acceptAll: Boolean,
        multiple: Boolean,
    ): List<Path> {
        val dialog = newDialog("Open", FileDialog.LOAD, suggestedDirectory)
        dialog.isMultipleMode = multiple
        dialog.narrowTo(extensions, acceptAll)
        dialog.isVisible = true
        // getFiles() is populated in both modes; getFile()/getDirectory() are just the
        // single-selection view of the same result. Empty means cancelled.
        return dialog.files.orEmpty().map { it.toPath() }
    }

    private fun showOpenFolder(suggestedDirectory: String): Path? {
        // The only way to ask NSOpenPanel for a directory from AWT. It is a process-wide
        // property, which is safe here only because it is set and cleared around a modal
        // show on the EDT, so no other file dialog can be opening in between.
        val previous = System.getProperty(MAC_DIRECTORY_MODE)
        if (isMacOs) System.setProperty(MAC_DIRECTORY_MODE, "true")
        try {
            val dialog = newDialog("Open", FileDialog.LOAD, suggestedDirectory)
            dialog.isVisible = true
            val name = dialog.file
            val directory = dialog.directory
            // In directory mode the chosen folder comes back split: directory is its
            // parent, file is its own name. Cancelling leaves both null.
            return when {
                directory == null -> null
                name == null -> File(directory).toPath()
                else -> File(directory, name).toPath()
            }
        } finally {
            if (isMacOs) System.setProperty(MAC_DIRECTORY_MODE, previous ?: "false")
        }
    }

    private fun showSave(
        suggestedFileName: String,
        suggestedDirectory: String,
        extensions: List<String>,
        acceptAll: Boolean,
    ): Path? {
        val dialog = newDialog("Save", FileDialog.SAVE, suggestedDirectory)
        // The page picks this name and it becomes a real path, so it goes through the same
        // sanitizer the download handler uses. Only when the page suggested something:
        // sanitize("") is the string "download", which would pre-fill a name nobody asked for.
        if (suggestedFileName.isNotBlank()) {
            dialog.file = FileNameSanitizer.sanitize(suggestedFileName)
        }
        dialog.narrowTo(extensions, acceptAll)
        dialog.isVisible = true
        val name = dialog.file
        val directory = dialog.directory
        return if (name != null && directory != null) File(directory, name).toPath() else null
    }

    // ============================================================
    // PLUMBING
    // ============================================================

    /**
     * Run [pick] on the EDT and answer the callback exactly once, with [open] or [cancel].
     *
     * These are async callbacks: JxBrowser hands over an action and returns, and the page's
     * file input stays pending until something answers. Dropping the answer wedges that input
     * for the life of the tab, so a throw inside the dialog still has to produce a cancel.
     * Answering *twice* is the other failure, hence the flag rather than two exit paths.
     */
    // Deliberately broad, both of them: the point is that no failure, whatever its type,
    // leaves the page waiting. Error still propagates, as elsewhere in this package.
    @Suppress("TooGenericExceptionCaught")
    private fun <T : Any> answerOnce(
        pick: () -> T?,
        open: (T) -> Unit,
        cancel: () -> Unit,
    ) {
        val answered = AtomicBoolean(false)

        fun answer(picked: T?) {
            if (!answered.compareAndSet(false, true)) return
            try {
                if (picked != null) open(picked) else cancel()
            } catch (e: Exception) {
                logger.warn(LogCategory.BROWSER, "Could not answer a file dialog", error = e)
            }
        }

        try {
            SwingUtilities.invokeLater {
                val picked =
                    try {
                        pick()
                    } catch (e: Exception) {
                        logger.warn(LogCategory.BROWSER, "Native file dialog failed", error = e)
                        null
                    }
                answer(picked)
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.BROWSER, "Could not show a native file dialog", error = e)
            answer(null)
        }
    }
}

/**
 * An OS file panel, owned by the window the user is actually looking at.
 *
 * The owner matters: with a null one the native panel can open BEHIND the Compose window, so
 * the click that opened it looks like it did nothing. Same fix as `DirectoryPickerProviderImpl`.
 *
 * (Top level rather than a member, with the helpers below, so [NativeFileDialogs] stays under
 * detekt's per-object function count.)
 */
private fun newDialog(
    title: String,
    mode: Int,
    suggestedDirectory: String,
): FileDialog {
    val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
    return FileDialog(owner, title, mode).apply {
        isAlwaysOnTop = true
        val suggested = File(suggestedDirectory)
        if (suggestedDirectory.isNotBlank() && suggested.isDirectory) {
            directory = suggested.absolutePath
        }
    }
}

/**
 * Narrow the panel to [extensions], unless the page said any file will do.
 *
 * The macOS peer asks this filter per URL and answers yes for anything that is not a regular
 * file, so directories stay navigable. Windows applies it the same way.
 *
 *  */
private fun FileDialog.narrowTo(
    extensions: List<String>,
    acceptAll: Boolean,
) {
    if (acceptAll) return
    val suffixes = fileDialogSuffixes(extensions)
    if (suffixes.isEmpty()) return
    setFilenameFilter { _, name -> matchesSuffix(name, suffixes) }
}

/**
 * The suffixes an `accept` attribute actually names, normalised for [matchesSuffix].
 *
 * Chromium hands these through however the page wrote them. A leading dot is common and
 * would double up in the `.ext` comparison; **MIME types and wildcards are dropped**, because
 * a filter built from a MIME pattern matches no filename at all - and an empty result means
 * no filter, which shows everything, rather than a panel where nothing is selectable.
 */
internal fun fileDialogSuffixes(extensions: List<String>): List<String> =
    extensions
        .mapNotNull { it.trim().removePrefix(".").takeIf(String::isNotEmpty) }
        .filterNot { it.contains('/') || it.contains('*') }

/** Whether [name] carries one of [suffixes]. Case-insensitive: the disk is not. */
internal fun matchesSuffix(
    name: String,
    suffixes: List<String>,
): Boolean = suffixes.any { name.endsWith(".$it", ignoreCase = true) }
