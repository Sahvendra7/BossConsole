package ai.rever.boss.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowFocusManagerTest {
    @Test
    fun `native macOS fullscreen events map to authoritative state`() {
        assertEquals(true, nativeMacOSFullscreenStateForEvent("windowEnteringFullScreen"))
        assertEquals(true, nativeMacOSFullscreenStateForEvent("windowEnteredFullScreen"))
        assertNull(nativeMacOSFullscreenStateForEvent("windowExitingFullScreen"))
        assertEquals(false, nativeMacOSFullscreenStateForEvent("windowExitedFullScreen"))
        assertNull(nativeMacOSFullscreenStateForEvent("toString"))
        assertTrue(isNativeMacOSFullscreenExitStarting("windowExitingFullScreen"))
        assertFalse(isNativeMacOSFullscreenExitStarting("windowExitedFullScreen"))
    }

    @Test
    fun `native tracking is authoritative and does not leak across windows`() {
        assertTrue(
            hasFullscreenSignal(
                nativeStateAvailable = false,
                nativeFullscreen = false,
                composeFullscreen = true,
            ),
        )
        assertTrue(
            hasFullscreenSignal(
                nativeStateAvailable = true,
                nativeFullscreen = true,
                composeFullscreen = false,
            ),
        )
        assertFalse(
            hasFullscreenSignal(
                nativeStateAvailable = true,
                nativeFullscreen = false,
                composeFullscreen = true,
            ),
        )
        assertFalse(
            hasFullscreenSignal(
                nativeStateAvailable = false,
                nativeFullscreen = true,
                composeFullscreen = false,
            ),
        )
        assertFalse(
            hasFullscreenSignal(
                nativeStateAvailable = false,
                nativeFullscreen = false,
                composeFullscreen = false,
            ),
        )
    }

    @Test
    fun `fullscreen exit notifications require a real transition`() {
        assertFalse(shouldNotifyComposeFullscreenExit(wasComposeFullscreen = false, isNativeFullscreen = false))
        assertFalse(shouldNotifyComposeFullscreenExit(wasComposeFullscreen = true, isNativeFullscreen = true))
        assertTrue(shouldNotifyComposeFullscreenExit(wasComposeFullscreen = true, isNativeFullscreen = false))
    }

    @Test
    fun `fullscreen exit listener stops firing after removal`() {
        val notifier = FullscreenExitNotifier()
        val exitedWindows = mutableListOf<String>()
        val listener: (String) -> Unit = exitedWindows::add

        notifier.notify("before-registration")
        notifier.add(listener)
        notifier.notify("window-a")
        notifier.remove(listener)
        notifier.notify("after-removal")

        assertEquals(listOf("window-a"), exitedWindows)
    }

    @Test
    fun `registration snapshots an already focused window`() {
        val tracker = AwtWindowFocusTracker()

        tracker.snapshotRegistration("window-a", isFocused = false)
        assertFalse(tracker.isFocused("window-a"))

        tracker.snapshotRegistration("window-a", isFocused = true)
        assertTrue(tracker.isFocused("window-a"))
    }

    @Test
    fun `late loss from previous window does not clear newer focus gain`() {
        val tracker = AwtWindowFocusTracker()
        val windowAListener = tracker.createListener("window-a")
        val windowBListener = tracker.createListener("window-b")

        windowAListener.windowGainedFocus(null)
        windowBListener.windowGainedFocus(null)
        windowAListener.windowLostFocus(null)

        assertFalse(tracker.isFocused("window-a"))
        assertTrue(tracker.isFocused("window-b"))
    }

    @Test
    fun `losing the focused window clears the snapshot`() {
        val tracker = AwtWindowFocusTracker()
        val listener = tracker.createListener("window-a")

        listener.windowGainedFocus(null)
        listener.windowLostFocus(null)

        assertFalse(tracker.isFocused("window-a"))
    }

    @Test
    fun `unregister clears only the matching focused window`() {
        val tracker = AwtWindowFocusTracker()
        val listener = tracker.createListener("window-b")

        listener.windowGainedFocus(null)
        tracker.onUnregistered("window-a")
        assertTrue(tracker.isFocused("window-b"))

        tracker.onUnregistered("window-b")
        assertFalse(tracker.isFocused("window-b"))
    }
}
