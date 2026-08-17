package ai.rever.boss.plugin.browser

import com.teamdev.jxbrowser.browser.callback.BrowserCallback
import com.teamdev.jxbrowser.browser.callback.OpenFileCallback
import com.teamdev.jxbrowser.browser.callback.OpenFilesCallback
import com.teamdev.jxbrowser.callback.Advisable
import com.teamdev.jxbrowser.callback.Callback
import com.teamdev.jxbrowser.callback.internal.DefaultCallbacks
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the two things [NativeFileDialogs] rests on.
 *
 * The dialogs themselves cannot be asserted here - they are native panels, and two of the
 * three CI legs have no display - so what is tested is the contract that decides whether
 * they are ever reached, plus the filter the page controls.
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
     * The whole fix is "set ours first and JxBrowser's view will not replace it".
     *
     * That is `DefaultCallbacks.register()` skipping any type already present - library
     * behaviour, not ours, and invisible from our own code. A JxBrowser upgrade that made
     * defaults overwrite would silently put the Swing `JFileChooser` back on every page
     * with an upload field, and nothing else in the tree would notice.
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
     * And the other half: a default we never claimed *is* installed, so leaving a callback
     * out of `installOn` means the Swing chooser, not a dead file input.
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
}
