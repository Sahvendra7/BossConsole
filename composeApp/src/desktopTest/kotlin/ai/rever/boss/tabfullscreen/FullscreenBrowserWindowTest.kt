package ai.rever.boss.tabfullscreen

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenBrowserWindowTest {
    private val screen = Rectangle(0, 0, 1728, 1117)

    @Test
    fun `matching screen bounds are fullscreen`() {
        assertTrue(fillsScreen(Rectangle(0, 0, 1728, 1117), screen))
    }

    @Test
    fun `native fullscreen bounds larger than the display are accepted`() {
        assertTrue(fillsScreen(Rectangle(0, 0, 1728, 1118), screen))
    }

    @Test
    fun `maximized window leaving menu bar space is not fullscreen`() {
        assertFalse(fillsScreen(Rectangle(0, 25, 1728, 1092), screen))
    }

    @Test
    fun `partial screen window is not fullscreen`() {
        assertFalse(fillsScreen(Rectangle(200, 100, 1200, 800), screen))
    }
}
