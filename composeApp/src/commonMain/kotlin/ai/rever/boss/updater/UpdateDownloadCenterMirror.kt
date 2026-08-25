package ai.rever.boss.updater

import ai.rever.boss.downloads.DownloadCenter
import ai.rever.boss.plugin.api.TransferKind
import ai.rever.boss.plugin.api.TransferPhase
import kotlinx.coroutines.Job

/**
 * Publishes the application's own update into [DownloadCenter], so it shares the
 * bottom-bar progress item and the dialog with every plugin transfer.
 *
 * A mirror rather than a second source: [UpdateManager.updateState] stays the one
 * place the update's progress lives (the top banner reads the same flow), and this
 * translates it. Two states writing the same row from different places is how a
 * banner and a bar come to disagree about whether an install has started.
 *
 * The row carries the actions the state allows, and only those:
 *
 * | State | Row |
 * |---|---|
 * | `Downloading` | progress, Cancel |
 * | `ReadyToInstall` | Install, Cancel (discards the staged file) |
 * | `Installing` | no actions - file moves and an elevated helper, past abandoning |
 * | anything else | no row |
 */
object UpdateDownloadCenterMirror {
    /**
     * Start mirroring. Idempotent: a second call is ignored, so every window may
     * ask for it without N collectors racing to write one row.
     *
     * Runs on the manager's own scope, the same one the download runs on - a
     * window's scope dies with its composition and would stop mirroring a
     * download that is still going.
     */
    fun start(coordinator: UpdateCoordinator) {
        if (job?.isActive == true) return
        val manager = coordinator.manager
        job =
            manager.launchInBackground {
                manager.updateState.collect { state -> publish(state, manager) }
            }
    }

    @Volatile
    private var job: Job? = null

    /** Internal for the mapping test: the table above is the whole behaviour. */
    internal fun publish(
        state: UpdateState,
        manager: UpdateManager,
    ) {
        val id = DownloadCenter.APP_UPDATE_ID
        when (state) {
            is UpdateState.Downloading -> {
                DownloadCenter.begin(
                    id = id,
                    title = title(manager),
                    kind = TransferKind.APP_UPDATE,
                    detail = "Application update",
                    onCancel = { manager.cancelDownload() },
                )
                DownloadCenter.progress(id, state.progress)
            }

            is UpdateState.ReadyToInstall -> {
                DownloadCenter.begin(id, title(manager), TransferKind.APP_UPDATE, "Application update")
                // Set after begin, not through it: a download that just finished
                // already has a row, and begin leaves an existing one alone.
                val staged = state.downloadPath
                DownloadCenter.setActions(
                    id = id,
                    onCancel = { manager.launchInBackground { manager.discardDownload() } },
                    onInstall = { manager.launchInBackground { manager.installUpdate(staged) } },
                )
                DownloadCenter.phase(id, TransferPhase.READY_TO_INSTALL)
            }

            is UpdateState.Installing -> {
                DownloadCenter.begin(id, title(manager), TransferKind.APP_UPDATE, "Application update")
                DownloadCenter.phase(id, TransferPhase.INSTALLING)
            }

            // Idle, UpToDate, CheckingForUpdates, UpdateAvailable, RestartRequired, Error.
            // None of them is work in flight: an available update is an offer the banner
            // and the dialog make, and a failure is reported there too - a row that
            // stayed would be a progress bar for something that is not happening.
            else -> {
                DownloadCenter.end(id)
            }
        }
    }

    /** "BOSS v9.4.33" when a version is known, and just "BOSS" before any check named one. */
    private fun title(manager: UpdateManager): String {
        val version = manager.updateInfo.value?.latestVersion ?: return "BOSS"
        return "BOSS v$version"
    }
}
