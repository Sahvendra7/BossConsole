package ai.rever.boss.downloads

import ai.rever.boss.plugin.api.TransferInfo
import ai.rever.boss.plugin.api.TransferKind
import ai.rever.boss.plugin.api.TransferPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One in-flight transfer plus the actions only the host can perform.
 *
 * [info] is exactly what a plugin sees through `DownloadCenterProvider`; the two
 * lambdas stay here because they close over host state (a download job, the
 * update installer) that has no meaning across a plugin boundary.
 */
data class Transfer(
    val info: TransferInfo,
    val onCancel: (() -> Unit)? = null,
    val onInstall: (() -> Unit)? = null,
)

/**
 * Every long-running transfer in the app, in one place: plugin installs and
 * updates (started by the host's own prompts or by the Toolbox), and the BOSS
 * application update.
 *
 * This is the single source the bottom-bar progress item and its dialog read,
 * which is the point of it - before it existed, the only visible progress was
 * a widget the Toolbox plugin owned, so every download the host itself started
 * happened silently.
 *
 * Not to be confused with the browser's file downloads (`DownloadDataProvider`
 * and the `downloads` plugin). This one is about plugin jars and the app.
 *
 * Process-wide, like [ai.rever.boss.updater.UpdateManager]: a transfer outlives
 * the window whose button started it, and every window's status bar shows the
 * same set.
 */
object DownloadCenter {
    /** The id the application's own update is tracked under. */
    const val APP_UPDATE_ID = "boss-app-update"

    // Insertion-ordered: rows must not jump around in the dialog as progress ticks.
    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()

    /**
     * Start tracking [id], or join the entry already tracking it.
     *
     * There is deliberately no `onInstall` here: an Install action only exists
     * once something has finished downloading, which is a later fact about a row
     * that already exists. [setActions] is where it arrives.
     *
     * @return true when this call created the entry. A caller that gets false is
     *   nested inside an operation someone else owns and must NOT [end] it - the
     *   fallback paths inside an install would otherwise tear down the row their
     *   caller is still using.
     */
    fun begin(
        id: String,
        title: String,
        kind: TransferKind,
        detail: String? = null,
        onCancel: (() -> Unit)? = null,
    ): Boolean {
        var created = false
        _transfers.update { list ->
            // ASSIGNED on every pass, never only set: `update` is a compare-and-set
            // retry loop, so a caller that built the row and then lost the CAS runs
            // again with the row present - and a stale `true` would hand ownership to
            // two callers, whose `finally` blocks then delete each other's rows.
            val exists = list.any { it.info.id == id }
            created = !exists
            if (exists) {
                list
            } else {
                list +
                    Transfer(
                        info =
                            TransferInfo(
                                id = id,
                                title = title,
                                detail = detail,
                                kind = kind,
                                phase = TransferPhase.PREPARING,
                                progress = null,
                                cancellable = onCancel != null,
                            ),
                        onCancel = onCancel,
                    )
            }
        }
        return created
    }

    /**
     * Report download progress for [id]. Also moves the transfer into
     * [TransferPhase.DOWNLOADING]: bytes arriving is what that phase means, and
     * making it implicit means no caller can report progress on a row that still
     * reads "Preparing" (and so refuses to offer Cancel).
     */
    fun progress(
        id: String,
        fraction: Float,
    ) = mutate(id) { t ->
        t.copy(
            info =
                t.info.copy(
                    phase = TransferPhase.DOWNLOADING,
                    progress = fraction.coerceIn(0f, 1f),
                ),
        )
    }

    /** Move [id] to [phase], dropping the progress fraction once it stops downloading. */
    fun phase(
        id: String,
        phase: TransferPhase,
    ) = mutate(id) { t ->
        t.copy(
            info =
                t.info.copy(
                    phase = phase,
                    progress = if (phase == TransferPhase.DOWNLOADING) t.info.progress else null,
                ),
        )
    }

    /** Replace [id]'s actions, e.g. when a finished download gains an Install. */
    fun setActions(
        id: String,
        onCancel: (() -> Unit)?,
        onInstall: (() -> Unit)?,
    ) = mutate(id) { it.copy(onCancel = onCancel, onInstall = onInstall) }

    /** Stop tracking [id]. Idempotent, so it is safe in a `finally`. */
    fun end(id: String) {
        _transfers.update { list -> list.filterNot { it.info.id == id } }
    }

    /**
     * Run [id]'s cancel action, if it currently has one.
     *
     * The row is NOT removed here: the cancelled work removes it through the
     * same `finally` that a completed one does, so a cancel that loses a race
     * with completion cannot leave a phantom row behind.
     */
    fun cancel(id: String) {
        _transfers.value
            .firstOrNull { it.info.id == id }
            ?.takeIf { it.info.cancellable }
            ?.onCancel
            ?.invoke()
    }

    /** Run [id]'s install action, if it has one (a downloaded app update). */
    fun install(id: String) {
        _transfers.value
            .firstOrNull { it.info.id == id }
            ?.onInstall
            ?.invoke()
    }

    /** Drop everything. Test-only; production rows are ended by their owners. */
    internal fun reset() {
        _transfers.value = emptyList()
    }

    /**
     * Apply [block] to [id] and re-derive `cancellable` from the result, so that
     * flag has exactly one definition: an action exists AND the transfer is not
     * being installed.
     *
     * [TransferPhase.INSTALLING] is the one phase that cannot be abandoned -
     * cancelling a jar swap mid-flight leaves the plugin unloaded, and an app
     * install is file moves plus an elevated helper. Everything else can:
     * a download is bytes, and a downloaded-but-uninstalled app update is a file
     * to delete, which is exactly the "Install / Cancel" pair the dialog offers.
     */
    private fun mutate(
        id: String,
        block: (Transfer) -> Transfer,
    ) {
        _transfers.update { list ->
            list.map { t ->
                if (t.info.id != id) {
                    t
                } else {
                    val next = block(t)
                    next.copy(
                        info =
                            next.info.copy(
                                cancellable = next.onCancel != null && next.info.phase != TransferPhase.INSTALLING,
                            ),
                    )
                }
            }
        }
    }
}
