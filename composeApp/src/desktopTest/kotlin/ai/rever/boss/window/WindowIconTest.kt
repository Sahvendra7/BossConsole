package ai.rever.boss.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins that the icon actually loads.
 *
 * [BossWindowIcon] degrades to an empty list rather than throwing when `/boss_icon.png` cannot be
 * read, because [ai.rever.boss.crash.CrashHandler] is one of its callers and a missing icon must not
 * be the reason a crash report goes unshown. That fallback is also how this whole feature could
 * become a silent no-op: every window would carry on showing the Java icon and nothing would say so.
 * Hence a test on the resource, not just on the call sites.
 */
class WindowIconTest {
    @Test
    fun `the window icon resource is on the classpath`() {
        assertTrue(
            BossWindowIcon.images.isNotEmpty(),
            "/boss_icon.png did not load - every window would silently fall back to the Java icon",
        )
    }

    @Test
    fun `the icon list is square, distinct and covers the sizes Windows asks for`() {
        val sizes = BossWindowIcon.images.map { it.width }

        BossWindowIcon.images.forEach { image ->
            assertEquals(image.width, image.height, "icon variants must be square")
        }
        assertEquals(sizes.distinct(), sizes, "duplicate icon sizes waste memory and help nothing")
        // 16 for the title bar and 32 for the taskbar button and Alt-Tab card are the two Windows
        // actually asks for; the source is kept so a high-DPI request has something to work from.
        assertTrue(16 in sizes, "no 16px variant, which is what the Windows title bar asks for")
        assertTrue(32 in sizes, "no 32px variant, which is what the taskbar and Alt-Tab ask for")
        assertTrue(sizes.max() >= 256, "the full-size source should be kept for high-DPI requests")
    }

    @Test
    fun `the compose painter resolves from the same decode`() {
        assertTrue(
            BossWindowIcon.composeBitmap != null,
            "bossWindowIcon() would return null and every Compose window would go unbranded",
        )
    }
}
