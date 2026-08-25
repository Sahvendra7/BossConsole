package ai.rever.boss.downloads

import ai.rever.boss.plugin.api.TransferKind
import ai.rever.boss.plugin.api.TransferPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules that keep one row honest: who owns it, when Cancel is offered, and
 * what a nested operation is allowed to do to somebody else's entry.
 *
 * Each of these is a way the status bar can lie - a row that never leaves, a
 * Cancel that corrupts a half-swapped plugin, or a fallback path removing the
 * row its caller is still filling.
 */
class DownloadCenterTest {
    @BeforeEach
    @AfterEach
    fun clean() = DownloadCenter.reset()

    @Test
    fun `begin creates one entry and nested begin joins it`() {
        val outer = DownloadCenter.begin("p", "Plugin", TransferKind.PLUGIN_INSTALL)
        val inner = DownloadCenter.begin("p", "Plugin again", TransferKind.PLUGIN_UPDATE)

        assertTrue(outer, "the first begin owns the entry")
        assertFalse(inner, "a nested begin must not claim ownership")
        assertEquals(1, DownloadCenter.transfers.value.size)
        // The outer call's description wins: the inner one is a fallback inside it.
        assertEquals(
            "Plugin",
            DownloadCenter.transfers.value
                .single()
                .info.title,
        )
    }

    @Test
    fun `progress moves the row into the downloading phase`() {
        DownloadCenter.begin("p", "Plugin", TransferKind.PLUGIN_INSTALL)
        DownloadCenter.progress("p", 0.42f)

        val info =
            DownloadCenter.transfers.value
                .single()
                .info
        assertEquals(TransferPhase.DOWNLOADING, info.phase)
        assertEquals(0.42f, info.progress)
    }

    @Test
    fun `progress is clamped`() {
        DownloadCenter.begin("p", "Plugin", TransferKind.PLUGIN_INSTALL)
        DownloadCenter.progress("p", 3f)
        assertEquals(
            1f,
            DownloadCenter.transfers.value
                .single()
                .info.progress,
        )
    }

    @Test
    fun `leaving the download phase drops the fraction`() {
        DownloadCenter.begin("p", "Plugin", TransferKind.PLUGIN_INSTALL)
        DownloadCenter.progress("p", 0.9f)
        DownloadCenter.phase("p", TransferPhase.INSTALLING)

        // Otherwise the row reads "Installing 90%" and sits there.
        assertNull(
            DownloadCenter.transfers.value
                .single()
                .info.progress,
        )
    }

    @Test
    fun `installing is the one phase that cannot be cancelled`() {
        DownloadCenter.begin("p", "Plugin", TransferKind.PLUGIN_UPDATE, onCancel = {})

        DownloadCenter.progress("p", 0.5f)
        assertTrue(
            DownloadCenter.transfers.value
                .single()
                .info.cancellable,
        )

        DownloadCenter.phase("p", TransferPhase.INSTALLING)
        assertFalse(
            DownloadCenter.transfers.value
                .single()
                .info.cancellable,
            "cancelling a jar swap would leave the plugin unloaded",
        )

        DownloadCenter.phase("p", TransferPhase.READY_TO_INSTALL)
        assertTrue(
            DownloadCenter.transfers.value
                .single()
                .info.cancellable,
            "a downloaded update is a file to delete, so Cancel is offered again",
        )
    }

    @Test
    fun `a transfer with no cancel action is never cancellable`() {
        DownloadCenter.begin("p", "Plugin", TransferKind.PLUGIN_INSTALL)
        DownloadCenter.progress("p", 0.5f)
        assertFalse(
            DownloadCenter.transfers.value
                .single()
                .info.cancellable,
        )
    }

    @Test
    fun `cancel refuses while installing and runs otherwise`() {
        var cancelled = 0
        DownloadCenter.begin("p", "Plugin", TransferKind.PLUGIN_UPDATE, onCancel = { cancelled++ })

        DownloadCenter.phase("p", TransferPhase.INSTALLING)
        DownloadCenter.cancel("p")
        assertEquals(0, cancelled, "the gate is enforced in the center, not only in the dialog")

        DownloadCenter.progress("p", 0.1f)
        DownloadCenter.cancel("p")
        assertEquals(1, cancelled)
    }

    @Test
    fun `cancel leaves the row for its owner to remove`() {
        DownloadCenter.begin("p", "Plugin", TransferKind.PLUGIN_UPDATE, onCancel = {})
        DownloadCenter.progress("p", 0.1f)
        DownloadCenter.cancel("p")

        // The cancelled work removes it through the same finally a completed one
        // uses; removing it here would race a transfer that finished first.
        assertEquals(1, DownloadCenter.transfers.value.size)
    }

    @Test
    fun `an Install action arrives after the row exists`() {
        var installed = 0
        DownloadCenter.begin("boss", "BOSS", TransferKind.APP_UPDATE)
        // Not a begin parameter: an Install only exists once something has
        // finished downloading, which is a later fact about an existing row.
        DownloadCenter.setActions("boss", onCancel = null, onInstall = { installed++ })
        DownloadCenter.phase("boss", TransferPhase.READY_TO_INSTALL)

        DownloadCenter.install("boss")
        assertEquals(1, installed)
    }

    @Test
    fun `end is idempotent and untargeted calls are no-ops`() {
        DownloadCenter.begin("p", "Plugin", TransferKind.PLUGIN_INSTALL)
        DownloadCenter.end("p")
        DownloadCenter.end("p")
        // Safe in a finally, and safe after a transfer someone else already ended.
        DownloadCenter.progress("p", 0.5f)
        DownloadCenter.phase("p", TransferPhase.INSTALLING)
        DownloadCenter.cancel("p")
        DownloadCenter.install("p")

        assertTrue(DownloadCenter.transfers.value.isEmpty())
    }

    @Test
    fun `rows keep their insertion order as progress arrives`() {
        DownloadCenter.begin("a", "A", TransferKind.PLUGIN_INSTALL)
        DownloadCenter.begin("b", "B", TransferKind.PLUGIN_INSTALL)
        DownloadCenter.progress("a", 0.5f)

        // A map would let the dialog's rows swap places under the pointer.
        assertEquals(listOf("a", "b"), DownloadCenter.transfers.value.map { it.info.id })
    }
}
