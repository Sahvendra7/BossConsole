package ai.rever.boss.window

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.AWTEvent
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * The BOSS icon, as every window BOSS opens should wear it.
 *
 * `jpackage` puts `boss_icon.ico` on the launcher `.exe` (see `composeApp/build.gradle.kts`), which
 * is why the Start-menu entry and the executable have always looked right - but that icon never
 * reaches a live AWT window. A Compose `Window` given no `icon` leaves `Frame.iconImages` empty, and
 * Windows then draws the JDK's default Java icon in the title bar, the taskbar button and the
 * Alt-Tab card. macOS takes its icon from the `.app` bundle and ignores per-window icons entirely,
 * and Linux takes it from the `.desktop` file - which is why this only ever showed up on Windows,
 * and why it went unnoticed: every window except the main one was affected.
 *
 * Two shapes, because the two families of window in this app take icons differently:
 *
 * - [bossWindowIcon] hands a Compose `Window` / `DialogWindow` its `icon` parameter.
 * - [images] hands a raw AWT [Frame] its `iconImages`, which is what the Swing frames
 *   (crash dialog, fullscreen browser, browser popups) and [DefaultWindowIcon] need.
 *
 * Both read the same single decode.
 */
object BossWindowIcon {
    private val logger = BossLogger.forComponent("BossWindowIcon")

    /**
     * Sizes Windows actually asks for: 16 for the title bar, 32 for the taskbar button and
     * Alt-Tab, the rest for high-DPI variants of those two. AWT picks the nearest and scales, so
     * handing it pre-scaled variants beats letting it squeeze one 256px bitmap into 16px.
     */
    private val derivedSizes = intArrayOf(16, 20, 24, 32, 40, 48, 64, 128)

    /** The 256x256 source, decoded once. Null only if the resource is missing from the classpath. */
    private val source: BufferedImage? by lazy { decodeSource() }

    /**
     * Multi-resolution icon list for `Frame.iconImages`.
     *
     * Empty rather than throwing when the resource cannot be read. Callers include
     * [ai.rever.boss.crash.CrashHandler], which is already handling a crash - a missing icon must
     * not become the reason the crash report never gets shown. `iconImages = emptyList()` is
     * exactly the state every window is in today, so the fallback is the current behaviour.
     */
    val images: List<BufferedImage> by lazy {
        val original = source ?: return@lazy emptyList()
        buildList {
            derivedSizes.forEach { size -> add(original.scaledTo(size)) }
            add(original)
        }
    }

    private fun decodeSource(): BufferedImage? {
        // `/boss_icon.png` rather than the Compose resource: this has to be readable from plain AWT
        // code and from a crash handler, neither of which can call into a composition, and one
        // decode shared by both families beats two loaders.
        val decoded =
            runCatching {
                javaClass.getResourceAsStream("/boss_icon.png")?.use(ImageIO::read)
            }.getOrElse { e ->
                logger.warn(LogCategory.UI, "Could not decode /boss_icon.png", error = e)
                return null
            }
        if (decoded == null) {
            logger.warn(LogCategory.UI, "/boss_icon.png is not on the classpath - windows will be unbranded")
        }
        return decoded
    }

    private fun BufferedImage.scaledTo(size: Int): BufferedImage {
        val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(this, 0, 0, size, size, null)
        } finally {
            g.dispose()
        }
        return scaled
    }

    /** The source as a Compose bitmap, decoded and converted once for every window that asks. */
    internal val composeBitmap by lazy { source?.toComposeImageBitmap() }
}

/**
 * The `icon` for a Compose `Window` or `DialogWindow`.
 *
 * Deliberately not `painterResource(Res.drawable.boss_icon)` at each call site. The five
 * `Heavyweight*` overlay windows are created and torn down constantly, and `painterResource` plus
 * the `Painter.toAwtImage` conversion Compose does for the icon would re-decode and re-rasterise a
 * 256px image on every one. This shares [BossWindowIcon]'s single decode.
 *
 * Returns null when the resource could not be read, which is what `Window(icon = null)` already
 * means - see [BossWindowIcon.images] for why this degrades rather than throws.
 *
 * Note the asymmetry with [BossWindowIcon.images], which is deliberate: Compose rasterises this
 * painter at a fixed 192dp and calls the single-image `Window.setIconImage`, so a Compose window
 * gets one bitmap that Windows downscales for the 16px title bar, not the multi-resolution list. The
 * main window has worked exactly this way since it was written, so it is good enough; the AWT sites
 * take the list because there the API allows it. The Compose `icon` parameter is still what the
 * Compose sites use, because it is applied before the window is shown - assigning `iconImages` from
 * inside the window body would leave one frame of Java icon on screen first.
 */
@Composable
fun bossWindowIcon(): Painter? {
    val bitmap = BossWindowIcon.composeBitmap ?: return null
    return remember(bitmap) { BitmapPainter(bitmap) }
}

/**
 * Brands windows BOSS does not create itself.
 *
 * JxBrowser raises Swing dialogs of its own, `JFileChooser` makes its own frames, a plugin can open
 * one, and the next window added to this app can forget its `icon`. This catches all of them.
 *
 * A net, not the fix. `WINDOW_OPENED` is delivered after `setVisible(true)`, so a window caught here
 * shows the Java icon for one frame first - invisible for a foreign dialog, but not good enough for
 * ours, which is why every window in this repo sets its icon explicitly and is held to it by
 * `WindowIconConventionTest`.
 *
 * Only [Frame] is touched: `java.awt.Dialog` has no icon of its own and takes its owner frame's, so
 * fixing the frames fixes the dialogs over them.
 */
object DefaultWindowIcon {
    private val logger = BossLogger.forComponent("DefaultWindowIcon")

    /**
     * Registers the listener. Call once, before any window exists - `main` does this next to
     * `setLinuxWMClass`, which has the same ordering requirement.
     */
    fun install() {
        val icons = BossWindowIcon.images
        if (icons.isEmpty()) return

        runCatching {
            Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
                if (event.id != WindowEvent.WINDOW_OPENED) return@addAWTEventListener
                val frame = (event as? WindowEvent)?.window as? Frame ?: return@addAWTEventListener
                // Only when nothing set one: an explicit icon - including a deliberately different
                // one - is never overwritten here.
                if (frame.iconImages.isEmpty()) {
                    frame.iconImages = icons
                }
            }, AWTEvent.WINDOW_EVENT_MASK)
        }.onFailure { e ->
            // A SecurityManager can refuse the listener. Losing the net is survivable; every
            // window this app opens sets its own icon.
            logger.warn(LogCategory.UI, "Could not install the default window icon listener", error = e)
        }
    }
}
