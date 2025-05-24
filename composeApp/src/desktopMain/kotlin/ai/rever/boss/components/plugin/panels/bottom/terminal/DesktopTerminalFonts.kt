package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle as SkiaFontStyle

@Composable
actual fun rememberTerminalFontFamily(): FontFamily = remember {
    val fontManager = FontMgr.default
    
    // Try to find a Nerd Font
    for (fontName in NERD_FONT_NAMES) {
        val typeface = fontManager.matchFamilyStyle(fontName, SkiaFontStyle.NORMAL)
        if (typeface != null) {
            // Found a Nerd Font, use it
            println("Terminal: Using Nerd Font: $fontName")
            return@remember FontFamily(Typeface(typeface))
        }
    }
    
    // No Nerd Font found, use default monospace
    println("Terminal: No Nerd Font found. Using default monospace font.")
    println("Terminal: To get powerline symbols, install one of: ${NERD_FONT_NAMES.take(5).joinToString(", ")}")
    println("Terminal: Download from: https://www.nerdfonts.com/")
    FontFamily.Monospace
} 