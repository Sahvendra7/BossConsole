package ai.rever.boss.utils

import androidx.compose.ui.graphics.Color as ComposeColor
import java.awt.Color as AwtColor

/**
 * Extension functions for converting between Compose and AWT colors.
 * Used for Swing/AWT interop.
 */

/**
 * Converts a Compose Color to an AWT Color.
 */
fun ComposeColor.toAwtColor(): AwtColor {
    return AwtColor(
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
        (alpha * 255).toInt().coerceIn(0, 255)
    )
}

/**
 * Converts an AWT Color to a Compose Color.
 */
fun AwtColor.toComposeColor(): ComposeColor {
    return ComposeColor(
        red = red / 255f,
        green = green / 255f,
        blue = blue / 255f,
        alpha = alpha / 255f
    )
}

/**
 * Creates an AWT Color from a hex color value (0xAARRGGBB or 0xRRGGBB format).
 */
fun awtColorFromHex(hex: Long): AwtColor {
    val hasAlpha = hex > 0xFFFFFF
    return if (hasAlpha) {
        AwtColor(
            ((hex shr 16) and 0xFF).toInt(),
            ((hex shr 8) and 0xFF).toInt(),
            (hex and 0xFF).toInt(),
            ((hex shr 24) and 0xFF).toInt()
        )
    } else {
        AwtColor(
            ((hex shr 16) and 0xFF).toInt(),
            ((hex shr 8) and 0xFF).toInt(),
            (hex and 0xFF).toInt()
        )
    }
}
