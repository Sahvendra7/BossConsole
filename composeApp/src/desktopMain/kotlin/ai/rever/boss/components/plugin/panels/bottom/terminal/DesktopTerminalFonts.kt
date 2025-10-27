package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.awt.Font as AwtFont
import androidx.compose.ui.text.platform.FontLoader
import boss_kotlin.composeapp.generated.resources.Res
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_bold
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_bold_italic
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_italic
import boss_kotlin.composeapp.generated.resources.meslolgs_nf_regular
import boss_kotlin.composeapp.generated.resources.noto_color_emoji
import org.jetbrains.compose.resources.Font

@Composable
actual fun rememberTerminalFontFamily(): FontFamily {
    // Use the font family from settings, with system fonts for emoji support
    return when (TerminalSettings.fontFamily) {
        "Monaco" -> {
            // Use system Monaco font with emoji fallback
            FontFamily(
                listOf(
                    Font("Monaco", FontWeight.Normal, FontStyle.Normal),
                    Font(Res.font.noto_color_emoji, FontWeight.Normal, FontStyle.Normal)
                )
            )
        }
        "Menlo" -> {
            // Use system Menlo font with emoji fallback
            FontFamily(
                listOf(
                    Font("Menlo", FontWeight.Normal, FontStyle.Normal),
                    Font(Res.font.noto_color_emoji, FontWeight.Normal, FontStyle.Normal)
                )
            )
        }
        "MesloLGS NF" -> {
            // Use original MesloLGS NF - it has good glyph coverage
            FontFamily(
                Font(Res.font.meslolgs_nf_regular, FontWeight.Normal, FontStyle.Normal),
                Font(Res.font.meslolgs_nf_bold, FontWeight.Bold, FontStyle.Normal),
                Font(Res.font.meslolgs_nf_italic, FontWeight.Normal, FontStyle.Italic),
                Font(Res.font.meslolgs_nf_bold_italic, FontWeight.Bold, FontStyle.Italic)
            )
        }
        "MesloLGS NF + Emoji" -> {
            // MesloLGS NF with emoji support - use this for emoji support
            FontFamily(
                Font(Res.font.meslolgs_nf_regular, FontWeight.Normal, FontStyle.Normal),
                Font(Res.font.meslolgs_nf_bold, FontWeight.Bold, FontStyle.Normal),
                Font(Res.font.meslolgs_nf_italic, FontWeight.Normal, FontStyle.Italic),
                Font(Res.font.meslolgs_nf_bold_italic, FontWeight.Bold, FontStyle.Italic),
                Font(Res.font.noto_color_emoji, FontWeight.Normal, FontStyle.Normal) // Emoji support!
            )
        }
        "JetBrains Mono" -> {
            // JetBrains Mono with emoji support
            FontFamily(
                listOf(
                    Font("JetBrains Mono", FontWeight.Normal, FontStyle.Normal),
                    Font("JetBrains Mono", FontWeight.Bold, FontStyle.Normal),
                    Font(Res.font.noto_color_emoji, FontWeight.Normal, FontStyle.Normal)
                )
            )
        }
        "Fira Code" -> {
            // Fira Code with emoji support
            FontFamily(
                listOf(
                    Font("Fira Code", FontWeight.Normal, FontStyle.Normal),
                    Font("Fira Code", FontWeight.Bold, FontStyle.Normal),
                    Font(Res.font.noto_color_emoji, FontWeight.Normal, FontStyle.Normal)
                )
            )
        }
        "Source Code Pro" -> {
            // Source Code Pro with emoji support
            FontFamily(
                listOf(
                    Font("Source Code Pro", FontWeight.Normal, FontStyle.Normal),
                    Font("Source Code Pro", FontWeight.Bold, FontStyle.Normal),
                    Font(Res.font.noto_color_emoji, FontWeight.Normal, FontStyle.Normal)
                )
            )
        }
        "Consolas" -> {
            // Consolas with emoji support
            FontFamily(
                listOf(
                    Font("Consolas", FontWeight.Normal, FontStyle.Normal),
                    Font("Consolas", FontWeight.Bold, FontStyle.Normal),
                    Font(Res.font.noto_color_emoji, FontWeight.Normal, FontStyle.Normal)
                )
            )
        }
        else -> {
            // Default fallback to bundled MesloLGS NF - prioritize symbol support
            FontFamily(
                Font(Res.font.meslolgs_nf_regular, FontWeight.Normal, FontStyle.Normal),
                Font(Res.font.meslolgs_nf_bold, FontWeight.Bold, FontStyle.Normal),
                Font(Res.font.meslolgs_nf_italic, FontWeight.Normal, FontStyle.Italic),
                Font(Res.font.meslolgs_nf_bold_italic, FontWeight.Bold, FontStyle.Italic)
            )
        }
    }
}
