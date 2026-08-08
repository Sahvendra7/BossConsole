package ai.rever.boss.app

import ai.rever.boss.components.overlays.OverlayConfig
import ai.rever.boss.plugin.sandbox.notification.PluginToastState
import ai.rever.boss.plugin.sandbox.notification.ToastDuration
import ai.rever.boss.plugin.sandbox.notification.ToastMessage
import ai.rever.boss.plugin.sandbox.notification.ToastType
import ai.rever.boss.plugin.ui.LocalHeavyweightOverlays
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Pins the empty check in [ToastOverlay].
 *
 * Without it the overlay is composed for every loaded plugin for the entire session, because
 * `PluginToastHost` composes its padded `Column` unconditionally and `DefaultPlugin.pluginToastState`
 * is never null. Since a non-focusable AWT window still receives mouse events and there is no
 * portable click-through, that is a permanently dead region of the app - and, being always-on-top, of
 * whatever other application is in front. The guard is one line, and nothing else in the build would
 * notice it going missing.
 */
class ToastOverlayTest {
    @get:Rule
    val rule = createComposeRule()

    private val previousRenderer = OverlayConfig.heavyweightCorner
    private val previousUseHeavyweight = OverlayConfig.useHeavyweightPopups

    @After
    fun restore() {
        // OverlayConfig is a process-global registry; leaving a fake in it would leak into any
        // other test that routes an overlay.
        OverlayConfig.heavyweightCorner = previousRenderer
        OverlayConfig.useHeavyweightPopups = previousUseHeavyweight
    }

    /** How many times the heavyweight renderer is asked for a window, for the given toasts. */
    private fun windowsOpenedFor(messages: List<String>): Int {
        var opened = 0
        OverlayConfig.useHeavyweightPopups = true
        OverlayConfig.heavyweightCorner = { _, _, _ ->
            // Counted, not composed: composing a real Window needs a display.
            opened++
        }
        val state = PluginToastState(CoroutineScope(Job()))
        messages.forEach { title ->
            // INDEFINITE so no auto-dismiss timer can race the assertion.
            state.show(
                ToastMessage(
                    type = ToastType.INFO,
                    title = title,
                    message = title,
                    duration = ToastDuration.INDEFINITE,
                ),
            )
        }

        rule.setContent {
            CompositionLocalProvider(LocalHeavyweightOverlays provides true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ToastOverlay(toastState = state)
                }
            }
        }
        rule.waitForIdle()
        return opened
    }

    @Test
    fun `no overlay window is opened when there are no toasts`() {
        assertEquals(
            0,
            windowsOpenedFor(emptyList()),
            "an empty toast host still measures 32x32 from its own padding, so an unguarded " +
                "overlay holds a click-eating always-on-top window open for the whole session",
        )
    }

    @Test
    fun `an overlay window is opened once there is a toast`() {
        assertEquals(1, windowsOpenedFor(listOf("Something happened")))
    }
}
