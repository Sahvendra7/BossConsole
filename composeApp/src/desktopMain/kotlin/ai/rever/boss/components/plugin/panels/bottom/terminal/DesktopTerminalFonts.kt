package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import boss_kotlin.composeapp.generated.resources.Res
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_bold
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_bold_italic
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_italic
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_regular
import org.jetbrains.compose.resources.Font

@Composable
actual fun rememberTerminalFontFamily(): FontFamily {
    // Load the bundled MesloLGS NF font
    val mesloLGSFonts = listOf(
        Font(Res.font.meslolgs_nf_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.meslolgs_nf_bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.meslolgs_nf_italic, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.meslolgs_nf_bold_italic, FontWeight.Bold, FontStyle.Italic)
    )
    
    println("✅ MesloLGS NF font loaded from resources")
    
    // Always use bundled fonts as primary to ensure consistent glyph support
    // The bundled MesloLGS NF has full powerline and nerd font symbol support
    return FontFamily(mesloLGSFonts)
} 