package ai.rever.boss.plugin.browser

import com.teamdev.jxbrowser.browser.callback.BrowserCallback
import com.teamdev.jxbrowser.browser.callback.OpenFileCallback
import com.teamdev.jxbrowser.browser.callback.OpenFilesCallback
import com.teamdev.jxbrowser.browser.callback.OpenFolderCallback
import com.teamdev.jxbrowser.browser.callback.SaveAsPdfCallback
import com.teamdev.jxbrowser.browser.callback.SaveFileCallback
import com.teamdev.jxbrowser.callback.Advisable
import com.teamdev.jxbrowser.callback.Callback
import com.teamdev.jxbrowser.callback.internal.DefaultCallbacks
import java.nio.file.Paths
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins what [NativeFileDialogs] rests on.
 *
 * The panels themselves cannot be asserted here - they are native, and two of the three CI
 * legs have no display - so what is tested is everything that decides whether they are
 * reached and answered correctly, plus the pure helpers the page's input feeds.
 */
class NativeFileDialogsTest {
    /**
     * A stand-in for `Browser`, which is the `Advisable` JxBrowser actually registers into.
     */
    private class RecordingAdvisable : Advisable<BrowserCallback> {
        val callbacks = mutableMapOf<Class<out Callback>, BrowserCallback>()

        // set/remove return the *previous* callback, which is null the first time - hence
        // the nullable returns against the unannotated Java signatures.
        @Suppress("UNCHECKED_CAST")
        override fun <C : BrowserCallback> set(
            type: Class<C>,
            callback: C,
        ): C? = callbacks.put(type, callback) as C?

        @Suppress("UNCHECKED_CAST")
        override fun <C : BrowserCallback> get(type: Class<C>): Optional<C> = Optional.ofNullable(callbacks[type] as C?)

        @Suppress("UNCHECKED_CAST")
        override fun <C : BrowserCallback> remove(type: Class<C>): C? = callbacks.remove(type) as C?
    }

    /**
     * Every dialog the page can raise has to be claimed, because an unclaimed one silently
     * gets JxBrowser's Swing `JFileChooser` back and nothing else in the tree notices.
     *
     * Asserted against `registerOn` rather than `installOn`: the latter is gated on macOS, so
     * this would assert nothing on two of the three CI legs and sleep through the regression.
     */
    @Test
    fun `every page-driven file dialog is claimed`() {
        val advisable = RecordingAdvisable()

        NativeFileDialogs.registerOn(advisable)

        val expected =
            listOf(
                OpenFileCallback::class.java,
                OpenFilesCallback::class.java,
                OpenFolderCallback::class.java,
                SaveFileCallback::class.java,
                SaveAsPdfCallback::class.java,
            ).map { it.simpleName }.sorted()
        assertEquals(
            expected,
            advisable.callbacks.keys
                .map { it.simpleName }
                .sorted(),
            "a missing one falls back to the Swing chooser",
        )
    }

    /**
     * The whole fix is "set ours first and JxBrowser's view will not replace it".
     *
     * That is `DefaultCallbacks.register()` skipping any type already present - library
     * behaviour, not ours, and invisible from our own code. A JxBrowser upgrade that made
     * defaults overwrite would silently put the Swing `JFileChooser` back on every page
     * with an upload field.
     */
    @Test
    fun `a default callback never replaces one already set`() {
        val advisable = RecordingAdvisable()
        val ours = OpenFileCallback { _, action -> action.cancel() }
        advisable.set(OpenFileCallback::class.java, ours)

        val theirs = OpenFileCallback { _, action -> action.cancel() }
        val untouched = OpenFilesCallback { _, action -> action.cancel() }
        DefaultCallbacks
            .of(advisable)
            .add(theirs)
            .add(untouched)
            .build()
            .register()

        assertSame(ours, advisable.callbacks[OpenFileCallback::class.java], "ours must survive registration")
        assertSame(
            untouched,
            advisable.callbacks[OpenFilesCallback::class.java],
            "a type we did not claim must still get the default",
        )
    }

    /**
     * And the other half: a default we never claimed *is* installed, so the test above
     * cannot pass just because `register()` does nothing at all.
     */
    @Test
    fun `a default callback is installed when nothing claimed the type`() {
        val advisable = RecordingAdvisable()
        val theirs = OpenFileCallback { _, action -> action.cancel() }

        DefaultCallbacks
            .of(advisable)
            .add(theirs)
            .build()
            .register()

        assertSame(theirs, advisable.callbacks[OpenFileCallback::class.java])
    }

    /**
     * A page's file input stays pending until something answers, so a dialog that throws
     * must still cancel rather than wedge the input for the life of the tab.
     */
    @Test
    fun `a throw while picking still cancels`() {
        val cancels = AtomicInteger()
        val opens = AtomicInteger()
        val done = CountDownLatch(1)

        NativeFileDialogs.answerOnce<String>(
            pick = { error("dialog blew up") },
            open = { opens.incrementAndGet() },
            cancel = {
                cancels.incrementAndGet()
                done.countDown()
            },
        )

        assertTrue(done.await(5, TimeUnit.SECONDS), "the callback was never answered")
        assertEquals(1, cancels.get())
        assertEquals(0, opens.get())
    }

    /** Answering twice is the other way to break the page, so a pick answers once only. */
    @Test
    fun `a successful pick answers exactly once`() {
        val cancels = AtomicInteger()
        val opens = AtomicInteger()
        val done = CountDownLatch(1)

        NativeFileDialogs.answerOnce(
            pick = { "chosen" },
            open = {
                opens.incrementAndGet()
                done.countDown()
            },
            cancel = { cancels.incrementAndGet() },
        )

        assertTrue(done.await(5, TimeUnit.SECONDS), "the callback was never answered")
        // Drain the EDT so a second answer posted behind ours would have landed by now.
        javax.swing.SwingUtilities.invokeAndWait { }
        assertEquals(1, opens.get())
        assertEquals(0, cancels.get())
    }

    @Test
    fun `suffixes are normalised and mime types dropped`() {
        assertEquals(listOf("png", "jpeg"), fileDialogSuffixes(listOf(".png", "jpeg")))
        assertEquals(listOf("pdf"), fileDialogSuffixes(listOf(" pdf ")))
        // An accept attribute of "image/*" or "*" names no suffix. Dropping them leaves an
        // empty list, which the caller reads as "no filter" - a panel showing everything,
        // rather than one where every file is greyed out.
        assertEquals(emptyList(), fileDialogSuffixes(listOf("image/*", "*", "", ".")))
    }

    @Test
    fun `suffix matching ignores case and requires the dot`() {
        val suffixes = listOf("png")
        assertTrue(matchesSuffix("Photo.PNG", suffixes))
        assertTrue(matchesSuffix("a.b.png", suffixes))
        assertFalse(matchesSuffix("png", suffixes), "a bare name is not a match")
        assertFalse(matchesSuffix("notapng", suffixes))
    }

    @Test
    fun `a save-as-pdf name keeps its extension without doubling it`() {
        // Relative and built from segments on purpose: a literal "/tmp/report" has the parent
        // "\\tmp" on the Windows CI leg, which is the separator's business, not this rule's.
        val bare = Paths.get("reports", "report")
        assertEquals("report.pdf", bare.withExtension("pdf").fileName.toString())
        assertEquals(
            "report.pdf",
            Paths
                .get("reports", "report.pdf")
                .withExtension("pdf")
                .fileName
                .toString(),
        )
        assertEquals(
            "report.PDF",
            Paths
                .get("reports", "report.PDF")
                .withExtension("pdf")
                .fileName
                .toString(),
        )
        assertEquals(bare.parent, bare.withExtension("pdf").parent, "the file must not move directory")
    }

    @Test
    fun `a prefilled save name keeps unicode but loses any path`() {
        // FileNameSanitizer would turn this into underscores; the name field is read by a
        // person, and the path we return comes from the panel, not from this string.
        assertEquals("報告書.pdf", safePrefill("報告書.pdf"))
        assertEquals("x", safePrefill("../../x"))
        assertEquals("b.txt", safePrefill("/etc/a/b.txt"))
        assertEquals("my report.txt", safePrefill("my report.txt"), "a space is not a control character")
        assertEquals("ab.txt", safePrefill("a\u0001b.txt"), "control characters are dropped")
    }
}
