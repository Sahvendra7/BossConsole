package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
expect fun rememberTerminalFontFamily(): FontFamily

// Common terminal font names that support powerline
val NERD_FONT_NAMES = listOf(
    "MesloLGS NF",
    "MesloLGS Nerd Font",
    "Hack Nerd Font",
    "FiraCode Nerd Font", 
    "JetBrainsMono Nerd Font",
    "CaskaydiaCove Nerd Font",
    "SauceCodePro Nerd Font",
    "Meslo LG S for Powerline",
    "Hack",
    "Fira Code",
    "JetBrains Mono"
) 