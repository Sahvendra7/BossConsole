package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.proto.services.CellAttr
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ThemePaletteTest {

    private val palette = ThemePalette.Default

    @Test
    fun `sentinel 0 fg falls back to default foreground`() {
        val style = ResolvedStyle(fgRgba = 0, bgRgba = 0, attrs = 0)
        assertEquals(palette.defaultForeground, palette.foregroundFor(style))
        assertEquals(palette.defaultBackground, palette.backgroundFor(style))
    }

    @Test
    fun `non-zero rgba is decoded as Compose Color`() {
        // 0xRRGGBBAA — opaque red.
        val style = ResolvedStyle(fgRgba = 0xFF0000FF.toInt(), bgRgba = 0, attrs = 0)
        val fg = palette.foregroundFor(style)
        // Compose Color stores channels as 0..1 floats; compare round-tripped ints.
        assertEquals(0xFF, (fg.red * 255).toInt())
        assertEquals(0x00, (fg.green * 255).toInt())
        assertEquals(0x00, (fg.blue * 255).toInt())
    }

    @Test
    fun `DIM applies an alpha multiplier to the foreground`() {
        val plain = ResolvedStyle(fgRgba = 0, bgRgba = 0, attrs = 0)
        val dim = ResolvedStyle(fgRgba = 0, bgRgba = 0, attrs = CellAttr.CELL_ATTR_DIM_VALUE)
        val plainColor = palette.foregroundFor(plain)
        val dimColor = palette.foregroundFor(dim)
        assertNotEquals(plainColor.alpha, dimColor.alpha)
        assertTrue(dimColor.alpha < plainColor.alpha)
    }

    @Test
    fun `REVERSE swaps the resolved foreground and background`() {
        val style = ResolvedStyle(
            fgRgba = 0xFF0000FF.toInt(),
            bgRgba = 0x00FF00FF.toInt(),
            attrs = CellAttr.CELL_ATTR_REVERSE_VALUE,
        )
        val (fg, bg) = palette.resolvedFgBg(style)
        // Foreground after swap = original background, and vice versa.
        assertEquals(palette.foregroundFor(style.copy(attrs = 0)), bg)
        assertEquals(palette.backgroundFor(style.copy(attrs = 0)), fg)
    }

    @Test
    fun `BOLD maps to FontWeight Bold and ITALIC maps to FontStyle Italic`() {
        val bold = ResolvedStyle(0, 0, CellAttr.CELL_ATTR_BOLD_VALUE)
        val italic = ResolvedStyle(0, 0, CellAttr.CELL_ATTR_ITALIC_VALUE)
        val plain = ResolvedStyle(0, 0, 0)

        assertEquals(FontWeight.Bold, palette.fontWeightFor(bold))
        assertEquals(FontWeight.Normal, palette.fontWeightFor(plain))
        assertEquals(FontStyle.Italic, palette.fontStyleFor(italic))
        assertEquals(FontStyle.Normal, palette.fontStyleFor(plain))
    }

    @Test
    fun `INVISIBLE flag reports invisible so renderer can skip the glyph`() {
        val invisible = ResolvedStyle(0, 0, CellAttr.CELL_ATTR_INVISIBLE_VALUE)
        val visible = ResolvedStyle(0, 0, 0)
        assertTrue(palette.isInvisible(invisible))
        assertFalse(palette.isInvisible(visible))
    }

    @Test
    fun `underline and strikethrough combine via TextDecoration`() {
        val underline = ResolvedStyle(0, 0, CellAttr.CELL_ATTR_UNDERLINE_VALUE)
        val strike = ResolvedStyle(0, 0, CellAttr.CELL_ATTR_STRIKETHROUGH_VALUE)
        val both = ResolvedStyle(
            0,
            0,
            CellAttr.CELL_ATTR_UNDERLINE_VALUE or CellAttr.CELL_ATTR_STRIKETHROUGH_VALUE,
        )
        val plain = ResolvedStyle(0, 0, 0)

        assertEquals(TextDecoration.Underline, palette.textDecorationFor(underline))
        assertEquals(TextDecoration.LineThrough, palette.textDecorationFor(strike))
        assertEquals(TextDecoration.None, palette.textDecorationFor(plain))
        // Combined returns a single decoration value carrying both.
        val combined = palette.textDecorationFor(both)
        assertTrue(combined.contains(TextDecoration.Underline))
        assertTrue(combined.contains(TextDecoration.LineThrough))
    }

    @Test
    fun `double underline and curly underline both register as underlined`() {
        val double = ResolvedStyle(0, 0, CellAttr.CELL_ATTR_DOUBLE_UNDERLINE_VALUE)
        val curly = ResolvedStyle(0, 0, CellAttr.CELL_ATTR_CURLY_UNDERLINE_VALUE)
        assertTrue(palette.textDecorationFor(double).contains(TextDecoration.Underline))
        assertTrue(palette.textDecorationFor(curly).contains(TextDecoration.Underline))
    }

    @Test
    fun `default palette has 16 ANSI colors`() {
        assertEquals(16, palette.ansiColors.size)
        // First entry conventionally black; eighth (index 7) is conventionally white.
        assertEquals(Color(0xFF000000), palette.ansiColors[0])
    }
}
