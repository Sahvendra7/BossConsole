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
    /**
     * Prefix for ids this instance reports, or null for the host's own transfers.
     *
     * Without it the id space is one unnamespaced namespace shared by the host and
     * every plugin, which is two bugs rather than one: two plugins both choosing
     * `"update"` collide and hit the nested-begin rule by accident (the second is
     * bound to a row it cannot remove, and its transfer never appears), and any
     * plugin can address another's transfer - or the host's app update - to withdraw
     * its Cancel, fake its progress, or fabricate a row. The prefix is stripped on
     * the way out, so a plugin still matches its own work by the id it passed in.
     */
    private val idPrefix: String? = null,
) : DownloadCenterProvider {
    override val transfers: StateFlow<List<TransferInfo>> =
        DownloadCenter.transfers
            .map { list -> list.map { it.info.withoutPrefix(idPrefix) } }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DownloadCenter.transfers.value.map { it.info.withoutPrefix(idPrefix) },
            )

    override fun begin(
        id: String,
        title: String,
        kind: TransferKind,
        detail: String?,
        onCancel: (() -> Unit)?,
    ): TransferHandle {
        val key = qualify(id)
        val created = DownloadCenter.begin(key, title, kind, detail, onCancel)
        return CenterHandle(key, ownsEntry = created)
    }

    private fun qualify(id: String): String = idPrefix?.let { "$it:$id" } ?: id

    /**
     * Show a plugin its own ids unprefixed, and leave everyone else's alone.
     *
     * A plugin asking "is this one busy?" compares against the id it passed to
     * [begin]; it should not have to know the host qualified it. Other plugins' rows
     * stay qualified, which is what makes them unmistakable for your own.
     */
    private fun TransferInfo.withoutPrefix(prefix: String?): TransferInfo {
        val head = prefix?.plus(":") ?: return this
        return if (id.startsWith(head)) copy(id = id.removePrefix(head)) else this
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
