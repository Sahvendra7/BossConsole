package ai.rever.boss.components.dialogs

import ai.rever.boss.platform.rememberFilePicker
import ai.rever.boss.plugin.api.BookmarkDataProvider
import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.services.auth.AuthStateManager
import ai.rever.boss.services.bookmarks.BookmarkAPIAccess
import ai.rever.boss.services.importer.ImportFileReader
import ai.rever.boss.services.importer.ImportPreview
import ai.rever.boss.services.importer.ImportResult
import ai.rever.boss.services.importer.ImportService
import ai.rever.boss.services.importer.SkipReason
import ai.rever.boss.services.importer.browser.BrowserImportService
import ai.rever.boss.services.importer.browser.DetectedBrowser
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where the import flow currently is. */
private sealed interface ImportStage {
    data object ChooseSource : ImportStage

    data class Failed(
        val message: String,
    ) : ImportStage

    data class Review(
        val preview: ImportPreview,
        /** Why part of the source could not be read, when applicable. */
        val note: String? = null,
    ) : ImportStage

    data class Running(
        val done: Int,
        val total: Int,
    ) : ImportStage

    data class Finished(
        val passwords: ImportResult?,
        val bookmarks: ImportResult?,
    ) : ImportStage
}

/**
 * Imports passwords and bookmarks from a browser export.
 *
 * Passwords need a signed-in user (they go into the encrypted vault) and
 * bookmarks need the bookmarks plugin. Either half can be unavailable without
 * blocking the other, so the dialog reports what it can and cannot do rather
 * than refusing to open.
 */
@Composable
fun ImportDataDialog(onDismiss: () -> Unit) {
    var stage by remember { mutableStateOf<ImportStage>(ImportStage.ChooseSource) }
    var runningJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val canImportPasswords = AuthStateManager.currentUser.value != null
    val bookmarkProvider = remember { BookmarkAPIAccess.getProvider() }

    var browsers by remember { mutableStateOf<List<DetectedBrowser>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }

    // Scan on open. Counts only — nothing is decrypted here, so this cannot
    // trigger a keychain prompt before the user has chosen anything.
    LaunchedEffect(Unit) {
        browsers = runCatching { BrowserImportService.scan() }.getOrDefault(emptyList())
        scanning = false
    }

    val picker = rememberImportPicker { stage = it }

    // Dropping the parsed data as the dialog closes keeps plaintext credentials
    // from outliving the flow that needed them.
    fun close() {
        runningJob?.cancel()
        stage = ImportStage.ChooseSource
        onDismiss()
    }

    // Closing mid-import would leave half the entries written, so the scrim and
    // Escape are inert while it runs; Cancel inside the progress stage is the
    // way out.
    DialogShell(dismissable = stage !is ImportStage.Running, onDismiss = ::close) {
        StageBody(
            stage = stage,
            canImportPasswords = canImportPasswords,
            bookmarkProvider = bookmarkProvider,
            browsers = browsers,
            scanning = scanning,
            onPickBrowser = { detected ->
                stage = ImportStage.Running(0, 0)
                runningJob =
                    scope.launch {
                        stage = readBrowser(detected, canImportPasswords)
                    }
            },
            onChooseFile = { picker.pickFile() },
            onRestart = { stage = ImportStage.ChooseSource },
            onCancel = ::close,
            onStart = { preview ->
                stage = ImportStage.Running(0, preview.total)
                runningJob =
                    scope.launch {
                        runImport(
                            preview = preview,
                            canImportPasswords = canImportPasswords,
                            bookmarkProvider = bookmarkProvider,
                            onStage = { stage = it },
                        )
                    }
            },
            onCancelRunning = {
                runningJob?.cancel()
                stage = ImportStage.Finished(null, null)
            },
        )
    }

    // Nothing to import means nothing to hold on to.
    LaunchedEffect(stage) {
        if (stage is ImportStage.ChooseSource) runningJob = null
    }
}

/** Card scaffold shared by every stage of the import dialog. */
@Composable
private fun DialogShell(
    dismissable: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = { if (dismissable) onDismiss() }) {
        Card(
            modifier = Modifier.width(DIALOG_WIDTH).padding(BossTheme.space.lg),
            elevation = BossTheme.elevation.popover,
            shape = BossTheme.radius.dialogShape,
            backgroundColor = BossTheme.colors.raised,
        ) {
            Column(modifier = Modifier.padding(BossTheme.space.xl)) {
                Text(
                    "Import Passwords & Bookmarks",
                    style = BossTheme.type.title,
                    color = BossTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(BossTheme.space.lg))
                content()
            }
        }
    }
}

/**
 * File picker wired to the importer's sniffing, reporting the resulting stage.
 *
 * Parses the text the picker already read rather than opening the file a second
 * time — for a password CSV that content is plaintext in memory.
 */
@Composable
private fun rememberImportPicker(onStage: (ImportStage) -> Unit) =
    rememberFilePicker(
        onFileSelected = { path, content ->
            if (path != null && content != null) {
                onStage(
                    ImportFileReader.parseContent(path, content).fold(
                        onSuccess = { ImportStage.Review(it) },
                        onFailure = { ImportStage.Failed(it.message ?: "That file could not be read.") },
                    ),
                )
            }
        },
        fileExtensions = listOf("csv", "html", "htm"),
        title = "Choose a passwords or bookmarks export",
    )

/**
 * Pull everything importable out of a detected browser.
 *
 * On macOS this is where the keychain prompt appears, which is why it runs only
 * after the user picks a browser rather than during the initial scan.
 */
private suspend fun readBrowser(
    detected: DetectedBrowser,
    canImportPasswords: Boolean,
): ImportStage {
    val result = BrowserImportService.read(detected.profile, canImportPasswords)
    return if (result.preview.hasAnything) {
        ImportStage.Review(result.preview, note = result.note)
    } else {
        ImportStage.Failed(
            result.note ?: "Nothing importable was found in ${detected.profile.displayName}.",
        )
    }
}

/** Total number of items an import will attempt. */
private val ImportPreview.total: Int
    get() = passwords.size + bookmarks.size

/**
 * Run both halves of an import, reporting progress as a single combined count.
 *
 * A half is skipped when it has nothing to do or is unavailable; the other half
 * still runs. Kept out of the composable so the dialog is a state machine and
 * nothing else.
 */
private suspend fun runImport(
    preview: ImportPreview,
    canImportPasswords: Boolean,
    bookmarkProvider: BookmarkDataProvider?,
    onStage: (ImportStage) -> Unit,
) {
    val total = preview.total

    val passwordResult =
        if (canImportPasswords && preview.passwords.isNotEmpty()) {
            ImportService.importPasswords(preview.passwords) { done, _ ->
                onStage(ImportStage.Running(done, total))
            }
        } else {
            null
        }

    // Bookmarks continue the same progress bar rather than restarting it.
    val offset = if (passwordResult != null) preview.passwords.size else 0

    val bookmarkResult =
        if (preview.bookmarks.isNotEmpty()) {
            ImportService.importBookmarks(preview.bookmarks, bookmarkProvider) { done, _ ->
                onStage(ImportStage.Running(offset + done, total))
            }
        } else {
            null
        }

    onStage(ImportStage.Finished(passwordResult, bookmarkResult))
}

/** Renders whichever stage the flow is currently in. */
@Composable
private fun StageBody(
    stage: ImportStage,
    canImportPasswords: Boolean,
    bookmarkProvider: BookmarkDataProvider?,
    browsers: List<DetectedBrowser>,
    scanning: Boolean,
    onPickBrowser: (DetectedBrowser) -> Unit,
    onChooseFile: () -> Unit,
    onRestart: () -> Unit,
    onCancel: () -> Unit,
    onStart: (ImportPreview) -> Unit,
    onCancelRunning: () -> Unit,
) {
    when (stage) {
        is ImportStage.ChooseSource -> {
            ChooseSourceContent(
                browsers = browsers,
                scanning = scanning,
                onPickBrowser = onPickBrowser,
                onChoose = onChooseFile,
                onCancel = onCancel,
            )
        }

        is ImportStage.Failed -> {
            FailedContent(message = stage.message, onRetry = onRestart, onCancel = onCancel)
        }

        is ImportStage.Review -> {
            ReviewContent(
                preview = stage.preview,
                canImportPasswords = canImportPasswords,
                bookmarksAvailable = bookmarkProvider != null,
                // Only warn when the slow path would actually be noticeable.
                slowBookmarkPath =
                    bookmarkProvider != null &&
                        !ImportService.supportsBulkBookmarkInsert(bookmarkProvider) &&
                        stage.preview.bookmarks.size > ImportService.FALLBACK_WARNING_THRESHOLD,
                sourceNote = stage.note,
                onCancel = onCancel,
                onStart = { onStart(stage.preview) },
            )
        }

        is ImportStage.Running -> {
            RunningContent(done = stage.done, total = stage.total, onCancel = onCancelRunning)
        }

        is ImportStage.Finished -> {
            FinishedContent(
                passwords = stage.passwords,
                bookmarks = stage.bookmarks,
                onClose = onCancel,
            )
        }
    }
}

/** Wide enough for a browser row plus its explanatory note on one line. */
private val DIALOG_WIDTH = 560.dp
