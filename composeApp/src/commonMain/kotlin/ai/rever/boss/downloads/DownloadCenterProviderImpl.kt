package ai.rever.boss.downloads

import ai.rever.boss.plugin.api.DownloadCenterProvider
import ai.rever.boss.plugin.api.TransferHandle
import ai.rever.boss.plugin.api.TransferInfo
import ai.rever.boss.plugin.api.TransferKind
import ai.rever.boss.plugin.api.TransferPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The plugin-facing view of [DownloadCenter].
 *
 * Plain common code rather than the `createXxxProvider` expect/actual other
 * providers use: there is nothing platform-specific to reach for, the center is
 * already common, and an expect/actual pair here would only be ceremony.
 *
 * @param scope a long-lived scope (the plugin layer's) for the derived flow;
 *   the mapping is stateful so every plugin observing it shares one collector.
 */
class DownloadCenterProviderImpl(
    scope: CoroutineScope,
) : DownloadCenterProvider {
    override val transfers: StateFlow<List<TransferInfo>> =
        DownloadCenter.transfers
            .map { list -> list.map { it.info } }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DownloadCenter.transfers.value.map { it.info },
            )

    override fun begin(
        id: String,
        title: String,
        kind: TransferKind,
        detail: String?,
        onCancel: (() -> Unit)?,
    ): TransferHandle {
        val created = DownloadCenter.begin(id, title, kind, detail, onCancel)
        return CenterHandle(id, ownsEntry = created)
    }

    /**
     * A handle over one center entry.
     *
     * [ownsEntry] is the nested-begin rule: a caller that joined an existing
     * transfer reports into it but must not remove it, or an inner fallback path
     * would tear down the row its caller is still filling.
     */
    private class CenterHandle(
        private val id: String,
        private val ownsEntry: Boolean,
    ) : TransferHandle {
        override fun progress(fraction: Float) = DownloadCenter.progress(id, fraction)

        override fun phase(phase: TransferPhase) = DownloadCenter.phase(id, phase)

        override fun done() {
            if (ownsEntry) DownloadCenter.end(id)
        }
    }
}
