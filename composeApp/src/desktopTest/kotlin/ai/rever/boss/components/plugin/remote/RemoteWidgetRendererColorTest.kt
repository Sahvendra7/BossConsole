package ai.rever.boss.components.plugin.remote

import ai.rever.boss.plugin.ui.BossDarkColorScheme
import ai.rever.boss.plugin.ui.BossLightColorScheme
import ai.rever.boss.ui.sdk.ThemeToken
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `WidgetModifier.background_color` resolution (issue #34 item 5).
 *
 * `ui_protocol.proto` promises "hex color string … or theme token", but the renderer only ever parsed
 * hex, so every token value a plugin sent was dropped on the floor.
 */
class RemoteWidgetRendererColorTest {
    @Test
    fun `every theme token resolves to a color`() {
        // A token the table forgets would silently fall back to "no background".
        assertEquals(ThemeToken.entries.toSet(), themeTokenColors.keys)
        for (token in ThemeToken.entries) {
            assertNotNull(
                resolveBackgroundColor(token.tokenName, BossDarkColorScheme),
                "token ${token.tokenName} must resolve",
            )
        }
    }

    @Test
    fun `tokens resolve against the active scheme`() {
        assertEquals(BossDarkColorScheme.panel, resolveBackgroundColor("panel", BossDarkColorScheme))
        assertEquals(BossLightColorScheme.panel, resolveBackgroundColor("panel", BossLightColorScheme))
        assertEquals(BossDarkColorScheme.signalWash, resolveBackgroundColor("signal_wash", BossDarkColorScheme))
    }

    @Test
    fun `hex specs still resolve`() {
        assertEquals(Color(0xFFFF0000), resolveBackgroundColor("#FF0000", BossDarkColorScheme))
        assertEquals(Color(0x80FF0000), resolveBackgroundColor("#80FF0000", BossDarkColorScheme))
    }

    @Test
    fun `unknown specs resolve to no background`() {
        assertNull(resolveBackgroundColor("", BossDarkColorScheme))
        assertNull(resolveBackgroundColor("rebeccapurple", BossDarkColorScheme))
        assertNull(resolveBackgroundColor("#F00", BossDarkColorScheme))
    }
}
