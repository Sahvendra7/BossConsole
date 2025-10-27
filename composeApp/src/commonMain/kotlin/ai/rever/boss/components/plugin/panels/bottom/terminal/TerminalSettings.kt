package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.ui.graphics.Color

// Global settings object - now in commonMain
object TerminalSettings {
    var fontFamily: String = "MesloLGS NF"
    var fontSize: Int = 14
    var colorScheme: String = "iTerm2 Default"
    var shell: String = "/bin/zsh"
    var startupCommand: String = ""
    
    // Color scheme colors
    fun getBackgroundColor(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_FFFFFF)
        "Solarized Dark" -> Color(0xFF_002B36)
        "Solarized Light" -> Color(0xFF_FDF6E3)
        "Dracula" -> Color(0xFF_282A36)
        "Tomorrow Night" -> Color(0xFF_1D1F21)
        "iTerm2 Default" -> Color(0xFF_000000)
        else -> Color(0xFF_1E1E1E) // BOSS Dark
    }
    
    fun getForegroundColor(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_000000)
        "Solarized Dark" -> Color(0xFF_839496)
        "Solarized Light" -> Color(0xFF_657B83)
        "Dracula" -> Color(0xFF_F8F8F2)
        "Tomorrow Night" -> Color(0xFF_C5C8C6)
        "iTerm2 Default" -> Color(0xFF_FFFFFF)
        else -> Color(0xFF_D4D4D4) // BOSS Dark
    }
    
    fun getCursorColor(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_000000)
        "Solarized Dark" -> Color(0xFF_93A1A1)
        "Solarized Light" -> Color(0xFF_586E75)
        "Dracula" -> Color(0xFF_F8F8F2)
        "Tomorrow Night" -> Color(0xFF_C5C8C6)
        "iTerm2 Default" -> Color(0xFF_FFFFFF)
        else -> Color(0xFF_FFFFFF) // BOSS Dark
    }
    
    fun getSelectionColor(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_B3D9FF)
        "Solarized Dark" -> Color(0xFF_586E75)
        "Solarized Light" -> Color(0xFF_EEE8D5)
        "Dracula" -> Color(0xFF_44475A)
        "Tomorrow Night" -> Color(0xFF_373B41)
        "iTerm2 Default" -> Color(0xFF_264F78)
        else -> Color(0xFF_264F78) // BOSS Dark
    }
    
    // ANSI color palette
    fun getAnsiBlack(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_000000)
        "Solarized Dark" -> Color(0xFF_073642)
        "Solarized Light" -> Color(0xFF_073642)
        "Dracula" -> Color(0xFF_21222C)
        "Tomorrow Night" -> Color(0xFF_1D1F21)
        "iTerm2 Default" -> Color(0xFF_000000)
        else -> Color(0xFF_000000) // BOSS Dark
    }
    
    fun getAnsiRed(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_CD3131)
        "Solarized Dark" -> Color(0xFF_DC322F)
        "Solarized Light" -> Color(0xFF_DC322F)
        "Dracula" -> Color(0xFF_FF5555)
        "Tomorrow Night" -> Color(0xFF_CC6666)
        "iTerm2 Default" -> Color(0xFF_FF5555)
        else -> Color(0xFF_CD3131) // BOSS Dark
    }
    
    fun getAnsiGreen(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_0DBC79)
        "Solarized Dark" -> Color(0xFF_859900)
        "Solarized Light" -> Color(0xFF_859900)
        "Dracula" -> Color(0xFF_50FA7B)
        "Tomorrow Night" -> Color(0xFF_B5BD68)
        "iTerm2 Default" -> Color(0xFF_55FF55)
        else -> Color(0xFF_0DBC79) // BOSS Dark
    }
    
    fun getAnsiYellow(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_E5E510)
        "Solarized Dark" -> Color(0xFF_B58900)
        "Solarized Light" -> Color(0xFF_B58900)
        "Dracula" -> Color(0xFF_F1FA8C)
        "Tomorrow Night" -> Color(0xFF_F0C674)
        "iTerm2 Default" -> Color(0xFF_FFFF55)
        else -> Color(0xFF_E5E510) // BOSS Dark
    }
    
    fun getAnsiBlue(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_2472C8)
        "Solarized Dark" -> Color(0xFF_268BD2)
        "Solarized Light" -> Color(0xFF_268BD2)
        "Dracula" -> Color(0xFF_BD93F9)
        "Tomorrow Night" -> Color(0xFF_81A2BE)
        "iTerm2 Default" -> Color(0xFF_5555FF)
        else -> Color(0xFF_2472C8) // BOSS Dark
    }
    
    fun getAnsiMagenta(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_BC3FBC)
        "Solarized Dark" -> Color(0xFF_D33682)
        "Solarized Light" -> Color(0xFF_D33682)
        "Dracula" -> Color(0xFF_FF79C6)
        "Tomorrow Night" -> Color(0xFF_B294BB)
        "iTerm2 Default" -> Color(0xFF_FF55FF)
        else -> Color(0xFF_BC3FBC) // BOSS Dark
    }
    
    fun getAnsiCyan(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_11A8CD)
        "Solarized Dark" -> Color(0xFF_2AA198)
        "Solarized Light" -> Color(0xFF_2AA198)
        "Dracula" -> Color(0xFF_8BE9FD)
        "Tomorrow Night" -> Color(0xFF_8ABEB7)
        "iTerm2 Default" -> Color(0xFF_55FFFF)
        else -> Color(0xFF_11A8CD) // BOSS Dark
    }
    
    fun getAnsiWhite(): Color = when (colorScheme) {
        "BOSS Light" -> Color(0xFF_E5E5E5)
        "Solarized Dark" -> Color(0xFF_EEE8D5)
        "Solarized Light" -> Color(0xFF_EEE8D5)
        "Dracula" -> Color(0xFF_F8F8F2)
        "Tomorrow Night" -> Color(0xFF_C5C8C6)
        "iTerm2 Default" -> Color(0xFF_FFFFFF)
        else -> Color(0xFF_E5E5E5) // BOSS Dark
    }
    
    // Bright ANSI colors (8-15)
    fun getAnsiBrightBlack(): Color = when (colorScheme) {
        "iTerm2 Default" -> Color(0xFF_686868)
        else -> Color(0xFF_666666) // Default bright black
    }
    
    fun getAnsiBrightRed(): Color = when (colorScheme) {
        "iTerm2 Default" -> Color(0xFF_FF6E68)
        else -> Color(0xFF_F14C4C) // Default bright red
    }
    
    fun getAnsiBrightGreen(): Color = when (colorScheme) {
        "iTerm2 Default" -> Color(0xFF_60FA68)
        else -> Color(0xFF_23D18B) // Default bright green
    }
    
    fun getAnsiBrightYellow(): Color = when (colorScheme) {
        "iTerm2 Default" -> Color(0xFF_FFFC67)
        else -> Color(0xFF_F5F543) // Default bright yellow
    }
    
    fun getAnsiBrightBlue(): Color = when (colorScheme) {
        "iTerm2 Default" -> Color(0xFF_6871FF)
        else -> Color(0xFF_3B8EEA) // Default bright blue
    }
    
    fun getAnsiBrightMagenta(): Color = when (colorScheme) {
        "iTerm2 Default" -> Color(0xFF_FF77FF)
        else -> Color(0xFF_D670D6) // Default bright magenta
    }
    
    fun getAnsiBrightCyan(): Color = when (colorScheme) {
        "iTerm2 Default" -> Color(0xFF_60FDFF)
        else -> Color(0xFF_29B8DB) // Default bright cyan
    }
    
    fun getAnsiBrightWhite(): Color = when (colorScheme) {
        "iTerm2 Default" -> Color(0xFF_FFFFFF)
        else -> Color(0xFF_E5E5E5) // Default bright white
    }
}