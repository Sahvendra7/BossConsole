package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.proto.services.CellAttr
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Host-side theme palette for the OOP terminal renderer. Maps proto RGBA
 * values + CellAttr bitmasks onto Compose graphics primitives. Lives
 * host-side so themes can change without round-tripping the child.
 */
@Immutable
data class ThemePalette(
    val defaultForeground: Color,
    val defaultBackground: Color,
    val cursorForeground: Color,
    val cursorBackground: Color,
    val selectionForeground: Color,
    val selectionBackground: Color,
    /** 16 ANSI colors (0..7 normal, 8..15 bright). */
    val ansiColors: List<Color>,
) {
    init {
        require(ansiColors.size == ANSI_PALETTE_SIZE) {
            "ANSI palette must have exactly $ANSI_PALETTE_SIZE entries (got ${ansiColors.size})"
        }
    }

    /**
     * Resolve a packed 0xRRGGBBAA value from [ResolvedStyle] into a
     * Compose color. The sentinel 0 (== fully transparent black) maps
     * to [fallback] so the child can defer to the host theme by
     * leaving the cell style's fg/bg unset.
     */
    fun resolve(rgba: Int, fallback: Color): Color =
        if (rgba == 0) fallback else rgbaToColor(rgba)

    fun foregroundFor(style: ResolvedStyle): Color {
        val base = resolve(style.fgRgba, defaultForeground)
        return if (hasAttr(style.attrs, CellAttr.CELL_ATTR_DIM)) base.copy(alpha = 0.6f) else base
    }

    fun backgroundFor(style: ResolvedStyle): Color =
        resolve(style.bgRgba, defaultBackground)

    /** Foreground/background after applying CELL_ATTR_REVERSE. */
    fun resolvedFgBg(style: ResolvedStyle): Pair<Color, Color> {
        val fg = foregroundFor(style)
        val bg = backgroundFor(style)
        return if (hasAttr(style.attrs, CellAttr.CELL_ATTR_REVERSE)) bg to fg else fg to bg
    }

    fun fontWeightFor(style: ResolvedStyle): FontWeight =
        if (hasAttr(style.attrs, CellAttr.CELL_ATTR_BOLD)) FontWeight.Bold else FontWeight.Normal

    fun fontStyleFor(style: ResolvedStyle): FontStyle =
        if (hasAttr(style.attrs, CellAttr.CELL_ATTR_ITALIC)) FontStyle.Italic else FontStyle.Normal

    fun textDecorationFor(style: ResolvedStyle): TextDecoration {
        val underline = hasAttr(style.attrs, CellAttr.CELL_ATTR_UNDERLINE) ||
            hasAttr(style.attrs, CellAttr.CELL_ATTR_DOUBLE_UNDERLINE) ||
            hasAttr(style.attrs, CellAttr.CELL_ATTR_CURLY_UNDERLINE)
        val strike = hasAttr(style.attrs, CellAttr.CELL_ATTR_STRIKETHROUGH)
        return when {
            underline && strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            underline -> TextDecoration.Underline
            strike -> TextDecoration.LineThrough
            else -> TextDecoration.None
        }
    }

    fun isInvisible(style: ResolvedStyle): Boolean =
        hasAttr(style.attrs, CellAttr.CELL_ATTR_INVISIBLE)

    companion object {
        const val ANSI_PALETTE_SIZE = 16

        /** Default dark palette modeled on the BOSS terminal theme. */
        val Default: ThemePalette = ThemePalette(
            defaultForeground = Color(0xFFE0E0E0),
            defaultBackground = Color(0xFF1E1E1E),
            cursorForeground = Color(0xFF1E1E1E),
            cursorBackground = Color(0xFFE0E0E0),
            selectionForeground = Color(0xFFFFFFFF),
            selectionBackground = Color(0xFF264F78),
            ansiColors = listOf(
                Color(0xFF000000), // black
                Color(0xFFCD3131), // red
                Color(0xFF0DBC79), // green
                Color(0xFFE5E510), // yellow
                Color(0xFF2472C8), // blue
                Color(0xFFBC3FBC), // magenta
                Color(0xFF11A8CD), // cyan
                Color(0xFFE5E5E5), // white
                Color(0xFF666666), // bright black
                Color(0xFFF14C4C), // bright red
                Color(0xFF23D18B), // bright green
                Color(0xFFF5F543), // bright yellow
                Color(0xFF3B8EEA), // bright blue
                Color(0xFFD670D6), // bright magenta
                Color(0xFF29B8DB), // bright cyan
                Color(0xFFE5E5E5), // bright white
            ),
        )

        private fun hasAttr(bitmask: Int, attr: CellAttr): Boolean =
            (bitmask and attr.number) != 0

        /** 0xRRGGBBAA → Compose Color. */
        fun rgbaToColor(rgba: Int): Color {
            val r = (rgba ushr 24) and 0xFF
            val g = (rgba ushr 16) and 0xFF
            val b = (rgba ushr 8) and 0xFF
            val a = rgba and 0xFF
            return Color(red = r, green = g, blue = b, alpha = a)
        }
    }
}
