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

    @Test
    fun `full-size window offset from the display is not fullscreen`() {
        assertFalse(fillsScreen(Rectangle(1, 0, 1728, 1117), screen))
    }

    @Test
    fun `compose fallback requires signal and fullscreen geometry`() {
        assertTrue(
            shouldUseComposeFullscreenOverlay(
                composeSignalActive = true,
                isShowing = true,
                isMaximized = false,
                windowBounds = screen,
                screenBounds = screen,
            ),
        )
        assertFalse(
            shouldUseComposeFullscreenOverlay(
                composeSignalActive = false,
                isShowing = true,
                isMaximized = false,
                windowBounds = screen,
                screenBounds = screen,
            ),
        )
        assertFalse(
            shouldUseComposeFullscreenOverlay(
                composeSignalActive = true,
                isShowing = true,
                isMaximized = true,
                windowBounds = screen,
                screenBounds = screen,
            ),
        )
        assertFalse(
            shouldUseComposeFullscreenOverlay(
                composeSignalActive = true,
                isShowing = true,
                isMaximized = false,
                windowBounds = Rectangle(200, 100, 1200, 800),
                screenBounds = screen,
            ),
        )
    }

    @Test
    fun `delayed fullscreen work is rejected after a newer lifecycle starts`() {
        assertTrue(
            isCurrentFullscreenLifecycle(
                expectedEpoch = 4,
                currentEpoch = 4,
                isInFullscreenMode = true,
            ),
        )
        assertFalse(
            isCurrentFullscreenLifecycle(
                expectedEpoch = 4,
                currentEpoch = 5,
                isInFullscreenMode = true,
            ),
        )
        assertFalse(
            isCurrentFullscreenLifecycle(
                expectedEpoch = 4,
                currentEpoch = 4,
                isInFullscreenMode = false,
            ),
        )
    }

    @Test
    fun `old cleanup cannot clear a newer fullscreen tab state`() {
        assertTrue(
            shouldRestoreFullscreenTabState(
                cleanupEpoch = 5,
                currentEpoch = 5,
                isInFullscreenMode = false,
            ),
        )
        assertFalse(
            shouldRestoreFullscreenTabState(
                cleanupEpoch = 5,
                currentEpoch = 6,
                isInFullscreenMode = true,
            ),
        )
    }
}
