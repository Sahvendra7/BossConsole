package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

data class TerminalCell(
    val char: Char = ' ',
    val foregroundColor: Color = Color.Unspecified,
    val backgroundColor: Color = Color.Unspecified,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false
)

class TerminalEmulator(
    private var columns: Int = 120,
    private var rows: Int = 24,
    private val maxBufferSize: Int = 10000 // Configurable buffer size
) {
    // Callback to send responses back to the terminal process
    var responseCallback: ((String) -> Unit)? = null
    
    // iTerm2-style optimized buffer management
    private var primaryGrid = Array(rows) { Array(columns) { TerminalCell() } }
    private var alternateGrid: Array<Array<TerminalCell>>? = null // Lazy allocation!
    private var currentGrid = primaryGrid // Points to active grid
    private var usingAlternateBuffer = false
    
    // Saved primary buffer cursor position
    private var savedPrimaryCursorRow = 0
    private var savedPrimaryCursorCol = 0
    
    // Scrolling region
    private var scrollTop = 0
    private var scrollBottom = rows - 1
    
    // Cursor position (0-based)
    private var cursorRow = 0
    private var cursorCol = 0
    
    // Cursor visibility
    private var cursorVisible = true
    
    // Saved cursor position for save/restore operations
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    
    // Current attributes for new characters
    private var currentForeground = Color.Unspecified
    private var currentBackground = Color.Unspecified
    private var currentBold = false
    private var currentItalic = false
    private var currentUnderline = false
    
    // Character sets (G0, G1, G2, G3)
    private enum class CharacterSet {
        ASCII,      // US ASCII
        GRAPHICS,   // Line drawing/special graphics
        UK,         // UK charset
        UNCHANGED   // Keep as-is
    }
    
    private var g0CharSet = CharacterSet.ASCII
    private var g1CharSet = CharacterSet.ASCII
    private var g2CharSet = CharacterSet.ASCII
    private var g3CharSet = CharacterSet.ASCII
    
    // Currently active character set (GL)
    private var currentCharSet = 0 // 0=G0, 1=G1, 2=G2, 3=G3
    
    // Single shift state
    private var singleShift = -1 // -1=none, 2=SS2(G2), 3=SS3(G3)
    
    // Parser state
    private val escapeSequence = StringBuilder()
    private var inEscapeSequence = false
    
    // Scrollback buffer with limit
    // Use ArrayDeque for efficient removal from both ends (O(1) instead of O(n))
    private val scrollbackLines = ArrayDeque<Array<TerminalCell>>()
    // maxBufferSize is now a constructor parameter - removed duplicate declaration
    private val maxScrollback: Int
        get() = maxBufferSize - rows // Dynamically calculate based on current rows
    
    // Window title
    private var windowTitle = ""
    private var iconTitle = ""
    
    fun processInput(input: String) {
        for (char in input) {
            processChar(char)
        }
    }
    
    private fun processChar(char: Char) {
        when {
            inEscapeSequence -> handleEscapeSequence(char)
            char == '\u001B' -> {
                inEscapeSequence = true
                escapeSequence.clear()
            }
            else -> handleRegularChar(char)
        }
    }
    
    private fun handleEscapeSequence(char: Char) {
        escapeSequence.append(char)
        
        // Check if we have a complete sequence
        when {
            // CSI sequences: ESC [ ... letter (@ through ~)
            escapeSequence.startsWith("[") && escapeSequence.length > 1 && char >= '@' && char <= '~' -> {
                // Extract parameters (everything between [ and the command char)
                val params = if (escapeSequence.length > 2) {
                    escapeSequence.substring(1, escapeSequence.length - 1)
                } else {
                    "" // No parameters, just ESC[<command>
                }
                processCsiSequence(params, char)
                inEscapeSequence = false
            }
            // OSC sequences: ESC ] ... BEL or ESC \
            escapeSequence.startsWith("]") && (char == '\u0007' || 
                (escapeSequence.length > 1 && escapeSequence.endsWith("\\"))) -> {
                val content = if (escapeSequence.length > 1) {
                    escapeSequence.substring(1)
                } else {
                    ""
                }
                processOscSequence(content)
                inEscapeSequence = false
            }
            // DCS sequences: ESC P ... ESC \
            escapeSequence.startsWith("P") && escapeSequence.length > 1 && 
                escapeSequence.endsWith("\\") && escapeSequence[escapeSequence.length - 2] == '\u001B' -> {
                // Device Control String - ignore for now
                inEscapeSequence = false
            }
            // PM sequences: ESC ^ ... ESC \
            escapeSequence.startsWith("^") && escapeSequence.length > 1 && 
                escapeSequence.endsWith("\\") && escapeSequence[escapeSequence.length - 2] == '\u001B' -> {
                // Privacy Message - ignore for now
                inEscapeSequence = false
            }
            // APC sequences: ESC _ ... ESC \
            escapeSequence.startsWith("_") && escapeSequence.length > 1 && 
                escapeSequence.endsWith("\\") && escapeSequence[escapeSequence.length - 2] == '\u001B' -> {
                // Application Program Command - ignore for now
                inEscapeSequence = false
            }
            // Two-character sequences (ESC followed by a single character)
            escapeSequence.length == 1 -> {
                when (char) {
                    '[', ']', 'P', '^', '_' -> {
                        // Start of multi-character sequence, continue building
                    }
                    '(', ')', '*', '+' -> {
                        // Character set selection - start of three-character sequence, continue building
                    }
                    else -> {
                        // Other two-character sequences
                        processTwoCharSequence(char)
                        inEscapeSequence = false
                    }
                }
            }
            // Character set designation: ESC ( A, ESC ) 0, etc.
            (escapeSequence.length == 2 && 
                (escapeSequence[0] == '(' || escapeSequence[0] == ')' || 
                 escapeSequence[0] == '*' || escapeSequence[0] == '+')) -> {
                // Character set designation complete
                processCharacterSetDesignation(escapeSequence[0], char)
                inEscapeSequence = false
            }
            // Handle malformed sequences - if we get too long, just abort
            escapeSequence.length > 100 -> {
                // Abort processing of malformed escape sequences that exceed maximum length
                inEscapeSequence = false
            }
        }
    }
    
    private fun processCsiSequence(params: String, command: Char) {
        // Extract intermediate characters (?, >, =, etc.) from the beginning of params
        var intermediates = ""
        var actualParams = params
        
        if (params.isNotEmpty()) {
            // Check for intermediate characters at the start
            val firstChar = params[0]
            val isCsiIntermediateChar = firstChar == '?' || firstChar == '>' ||
                firstChar == '=' || firstChar == '<' || firstChar == '!'
            if (isCsiIntermediateChar) {
                intermediates = firstChar.toString()
                actualParams = params.substring(1)
            }
        }
        
        // Parse parameters more carefully - handle empty params and non-numeric values
        val args = if (actualParams.isEmpty()) {
            emptyList()
        } else {
            actualParams.split(';').map { param ->
                param.toIntOrNull() ?: 0  // Default to 0 for invalid params
            }
        }
        
        when (command) {
            'A' -> moveCursorUp(args.getOrElse(0) { 1 }) // Cursor up
            'B' -> moveCursorDown(args.getOrElse(0) { 1 }) // Cursor down
            'C' -> moveCursorRight(args.getOrElse(0) { 1 }) // Cursor forward
            'D' -> moveCursorLeft(args.getOrElse(0) { 1 }) // Cursor back
            'E' -> { // Cursor next line
                cursorCol = 0
                moveCursorDown(args.getOrElse(0) { 1 })
            }
            'F' -> { // Cursor previous line
                cursorCol = 0
                moveCursorUp(args.getOrElse(0) { 1 })
            }
            'G' -> cursorCol = minOf(columns - 1, maxOf(0, args.getOrElse(0) { 1 } - 1)) // Cursor horizontal absolute
            'H', 'f' -> setCursorPosition(args.getOrElse(0) { 1 }, args.getOrElse(1) { 1 }) // Cursor position
            'J' -> clearScreen(args.getOrElse(0) { 0 }) // Clear screen
            'K' -> clearLine(args.getOrElse(0) { 0 }) // Clear line
            'L' -> insertLines(args.getOrElse(0) { 1 }) // Insert lines
            'M' -> deleteLines(args.getOrElse(0) { 1 }) // Delete lines
            'P' -> deleteCharacters(args.getOrElse(0) { 1 }) // Delete characters
            'S' -> scrollUp(args.getOrElse(0) { 1 }) // Scroll up
            'T' -> scrollDown(args.getOrElse(0) { 1 }) // Scroll down
            'X' -> eraseCharacters(args.getOrElse(0) { 1 }) // Erase characters
            'd' -> cursorRow = minOf(rows - 1, maxOf(0, args.getOrElse(0) { 1 } - 1)) // Line position absolute
            'm' -> processColorAndStyle(args) // SGR - Select Graphic Rendition
            'n' -> handleDeviceStatusReport(args) // Device status report
            'r' -> setScrollingRegion(args.getOrElse(0) { 1 }, args.getOrElse(1) { rows }) // Set scrolling region
            's' -> saveCursor() // Save cursor position
            'u' -> restoreCursor() // Restore cursor position
            'h' -> handleModeSet(params) // Set mode - pass original params with intermediates
            'l' -> handleModeReset(params) // Reset mode - pass original params with intermediates
            '!' -> {} // Soft reset - ignore for now
            'c' -> {} // Device attributes - ignore for now
            '@' -> insertCharacters(args.getOrElse(0) { 1 }) // Insert characters
            'q' -> {} // Load LEDs - ignore for now  
            'p' -> {} // Soft reset - ignore for now
            'g' -> {} // Tab clear - ignore for now
            't' -> {} // Window manipulation - ignore for now
            'I' -> moveCursorRight(args.getOrElse(0) { 1 }) // Cursor horizontal tabulation
            'Z' -> moveCursorLeft(args.getOrElse(0) { 1 }) // Cursor backward tabulation  
            'b' -> {} // Repeat preceding character - ignore for now
            'a' -> moveCursorRight(args.getOrElse(0) { 1 }) // Cursor right (same as C)
            'e' -> moveCursorDown(args.getOrElse(0) { 1 }) // Cursor down (same as B)
            'R' -> {} // Cursor position report - ignore for now
            'i' -> {} // Media copy - ignore for now
            'y' -> {} // Invoke confidence test - ignore for now
            'z' -> {} // Invoke macro - ignore for now
            '`' -> cursorCol = minOf(columns - 1, maxOf(0, args.getOrElse(0) { 1 } - 1)) // Character position absolute
            'j' -> moveCursorUp(args.getOrElse(0) { 1 }) // Cursor up (legacy)
            'k' -> moveCursorDown(args.getOrElse(0) { 1 }) // Cursor down (legacy)
            'w' -> {} // Tab set - ignore for now
            'x' -> {} // Request attributes - ignore for now
            // Additional modern sequences commonly used by Claude Code and similar apps:
            'W' -> {} // Tab character - ignore for now
            'V' -> {} // Page down - ignore for now
            'v' -> {} // Page up - ignore for now
            'o' -> {} // Reset to initial state - ignore for now
            'Q' -> {} // Cursor character position - ignore for now
            'O' -> {} // Set active position - ignore for now
            'N' -> {} // Single shift 2 - ignore for now
            'Y' -> setCursorPosition(args.getOrElse(0) { 1 }, args.getOrElse(1) { 1 }) // Direct cursor addressing (alternative to H)
            // Claude Code specific sequences - removed duplicate 'K' handler
            else -> {
                // Ignore unrecognized CSI command sequences
            }
        }
    }
    
    private fun processOscSequence(content: String) {
        // Handle OSC sequences (e.g., setting window title)
        // OSC sequences have the format: OSC number ; text ST
        // Where ST can be BEL (\u0007) or ESC \
        
        // Remove the terminator if present
        val cleanContent = content.removeSuffix("\\").removeSuffix("\u0007")
        
        // Split into command number and parameters
        val parts = cleanContent.split(';', limit = 2)
        if (parts.isEmpty()) return
        
        val command = parts[0].toIntOrNull() ?: return
        val text = parts.getOrElse(1) { "" }
        
        when (command) {
            0 -> {
                // Set icon name and window title
                iconTitle = text
                windowTitle = text
            }
            1 -> {
                // Set icon name
                iconTitle = text
            }
            2 -> {
                // Set window title
                windowTitle = text
            }
            8 -> {
                // Hyperlink (modern terminal feature)
                // Format: OSC 8 ; params ; uri ST
                processHyperlink(text)
            }
            9 -> {
                // iTerm2: Growl notification
                // Format: OSC 9 ; message ST
                // Handle iTerm2 Growl notification (ignored)
            }
            133 -> {
                // FinalTerm/iTerm2: Command started
                // Handle FinalTerm/iTerm2 command started marker for shell integration
            }
            134 -> {
                // FinalTerm/iTerm2: Command finished
                // Handle FinalTerm/iTerm2 command finished marker for shell integration
            }
            1337 -> {
                // iTerm2 proprietary sequences
                processItermSequence(text)
            }
            4 -> {
                // Set/change color palette
                // Format: OSC 4 ; index ; color ST
                val colorParts = text.split(';', limit = 2)
                if (colorParts.size == 2) {
                    val index = colorParts[0].toIntOrNull()
                    val colorSpec = colorParts[1]
                    if (index != null) {
                        setColorPalette(index, colorSpec)
                    }
                }
            }
            7 -> {
                // Set current working directory (used by some terminals)
                // We'll store but not use this for now
            }
            // Hyperlink handling moved to case 8 above - removed duplicate
            10 -> {
                // Set foreground color
                setDefaultForeground(text)
            }
            11 -> {
                // Set background color
                setDefaultBackground(text)
            }
            12 -> {
                // Set cursor color
                // We could implement this by adding a cursor color state
            }
            52 -> {
                // Clipboard operations
                // Format: OSC 52 ; clipboard ; base64-data ST
                // For security reasons, we'll ignore clipboard operations for now
            }
            104 -> {
                // Reset color palette
                // Format: OSC 104 ; index ST or OSC 104 ST (reset all)
                if (text.isEmpty()) {
                    resetColorPalette()
                } else {
                    val index = text.toIntOrNull()
                    if (index != null) {
                        resetColorPaletteEntry(index)
                    }
                }
            }
            110 -> {
                // Reset foreground color
                resetDefaultForeground()
            }
            111 -> {
                // Reset background color
                resetDefaultBackground()
            }
            112 -> {
                // Reset cursor color
            }
        }
    }
    
    // Color palette for custom colors
    private val customColorPalette = mutableMapOf<Int, Color>()
    private var defaultForegroundColor: Color? = null
    private var defaultBackgroundColor: Color? = null
    
    private fun setColorPalette(index: Int, colorSpec: String) {
        // Parse color specification (e.g., "rgb:ff/00/00" or "#ff0000")
        val color = parseColorSpec(colorSpec)
        if (color != null && index in 0..255) {
            customColorPalette[index] = color
        }
    }
    
    private fun setDefaultForeground(colorSpec: String) {
        defaultForegroundColor = parseColorSpec(colorSpec)
    }
    
    private fun setDefaultBackground(colorSpec: String) {
        defaultBackgroundColor = parseColorSpec(colorSpec)
    }
    
    private fun resetColorPalette() {
        customColorPalette.clear()
    }
    
    private fun resetColorPaletteEntry(index: Int) {
        customColorPalette.remove(index)
    }
    
    private fun resetDefaultForeground() {
        defaultForegroundColor = null
    }
    
    private fun resetDefaultBackground() {
        defaultBackgroundColor = null
    }
    
    private fun processHyperlink(text: String) {
        // Modern terminal hyperlink support: OSC 8 ; params ; uri ST
        // Store hyperlink info with text cells (currently ignored)
    }
    
    private fun processItermSequence(text: String) {
        // iTerm2 proprietary escape sequences: OSC 1337 ; command ST
        // Parse the command and parameters
        val parts = text.split('=', limit = 2)
        val command = parts.getOrElse(0) { "" }
        val params = parts.getOrElse(1) { "" }
        
        when {
            command.startsWith("CursorShape") -> {
                // Change cursor shape: OSC 1337 ; CursorShape=N ST
                val shape = params.toIntOrNull() ?: 0
                // Handle cursor shape change: 0=block, 1=vertical bar, 2=underline
            }
            command == "ClearScrollback" -> {
                // Clear scrollback buffer: OSC 1337 ; ClearScrollback ST
                scrollbackLines.clear()
            }
            command.startsWith("CurrentDir") -> {
                // Set current directory: OSC 1337 ; CurrentDir=path ST
                // Store current directory path (currently ignored)
            }
            command.startsWith("SetColors") -> {
                // Change colors: OSC 1337 ; SetColors=key=value ST
                // Handle color palette changes (currently ignored)
            }
            else -> {
                // Handle unknown iTerm2 proprietary sequence (ignored)
            }
        }
    }
    
    private fun parseColorSpec(spec: String): Color? {
        return when {
            // RGB format: rgb:rr/gg/bb or rgb:rrrr/gggg/bbbb
            spec.startsWith("rgb:") -> {
                val rgb = spec.substring(4).split('/')
                if (rgb.size == 3) {
                    try {
                        val r = rgb[0].padEnd(4, rgb[0].last()).substring(0, 2).toInt(16)
                        val g = rgb[1].padEnd(4, rgb[1].last()).substring(0, 2).toInt(16)
                        val b = rgb[2].padEnd(4, rgb[2].last()).substring(0, 2).toInt(16)
                        Color(r, g, b)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
            // Hex format: #rrggbb
            spec.startsWith("#") && spec.length == 7 -> {
                try {
                    val r = spec.substring(1, 3).toInt(16)
                    val g = spec.substring(3, 5).toInt(16)
                    val b = spec.substring(5, 7).toInt(16)
                    Color(r, g, b)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
    
    private fun handleRegularChar(char: Char) {
        when (char) {
            '\n' -> {
                cursorCol = 0
                cursorRow++
                if (cursorRow >= rows) {
                    scrollUp()
                    cursorRow = rows - 1
                }
            }
            '\r' -> cursorCol = 0
            '\b' -> if (cursorCol > 0) cursorCol--
            '\t' -> {
                // Move to next tab stop (every 8 columns)
                val nextTab = ((cursorCol / 8) + 1) * 8
                cursorCol = minOf(nextTab, columns - 1)
            }
            '\u000E' -> currentCharSet = 1 // SO - Shift Out (use G1)
            '\u000F' -> currentCharSet = 0 // SI - Shift In (use G0)
            else -> {
                if (char.code >= 32) {
                    // Handle all printable characters including ASCII, Unicode, and emojis
                    val displayChar = translateCharacter(char)
                    
                    // Place character at cursor position
                    if (cursorCol < columns) {
                        currentGrid[cursorRow][cursorCol] = TerminalCell(
                            char = displayChar,
                            foregroundColor = currentForeground,
                            backgroundColor = currentBackground,
                            bold = currentBold,
                            italic = currentItalic,
                            underline = currentUnderline
                        )
                        
                        
                        cursorCol++
                        if (cursorCol >= columns) {
                            cursorCol = 0
                            cursorRow++
                            if (cursorRow >= rows) {
                                scrollUp()
                                cursorRow = rows - 1
                            }
                        }
                    }
                    
                    // Reset single shift if used
                    if (singleShift >= 0) {
                        singleShift = -1
                    }
                }
            }
        }
    }
    
    private fun translateCharacter(char: Char): Char {
        // Determine which character set to use
        val activeSet = when {
            singleShift >= 0 -> singleShift
            else -> currentCharSet
        }
        
        val charSet = when (activeSet) {
            0 -> g0CharSet
            1 -> g1CharSet
            2 -> g2CharSet
            3 -> g3CharSet
            else -> CharacterSet.ASCII
        }
        
        // Translate character based on character set
        return when (charSet) {
            CharacterSet.GRAPHICS -> translateGraphicsChar(char)
            CharacterSet.UK -> translateUKChar(char)
            else -> char // ASCII or UNCHANGED
        }
    }
    
    private fun translateGraphicsChar(char: Char): Char {
        // DEC Special Graphics character set (line drawing)
        return when (char) {
            '`' -> '◆' // Diamond
            'a' -> '▒' // Checkerboard
            'b' -> '␉' // HT
            'c' -> '␌' // FF
            'd' -> '␍' // CR
            'e' -> '␊' // LF
            'f' -> '°' // Degree
            'g' -> '±' // Plus/minus
            'h' -> '␤' // NL
            'i' -> '␋' // VT
            'j' -> '┘' // Lower right corner
            'k' -> '┐' // Upper right corner
            'l' -> '┌' // Upper left corner
            'm' -> '└' // Lower left corner
            'n' -> '┼' // Crossing lines
            'o' -> '⎺' // Scan line 1
            'p' -> '⎻' // Scan line 3
            'q' -> '─' // Horizontal line
            'r' -> '⎼' // Scan line 5
            's' -> '⎽' // Scan line 7
            't' -> '├' // T pointing right
            'u' -> '┤' // T pointing left
            'v' -> '┴' // T pointing up
            'w' -> '┬' // T pointing down
            'x' -> '│' // Vertical line
            'y' -> '≤' // Less than or equal
            'z' -> '≥' // Greater than or equal
            '{' -> 'π' // Pi
            '|' -> '≠' // Not equal
            '}' -> '£' // UK pound
            '~' -> '·' // Centered dot
            else -> char // Keep unchanged
        }
    }
    
    private fun translateUKChar(char: Char): Char {
        // UK character set - only difference from US ASCII is # -> £
        return when (char) {
            '#' -> '£' // UK pound symbol
            else -> char
        }
    }
    
    private fun processColorAndStyle(args: List<Int>) {
        // Handle extended color modes with proper parsing
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                // 256-color foreground: ESC[38;5;nnn
                arg == 38 && i + 2 < args.size && args[i + 1] == 5 -> {
                    currentForeground = xterm256ColorToCompose(args[i + 2])
                    i += 3
                }
                // 256-color background: ESC[48;5;nnn  
                arg == 48 && i + 2 < args.size && args[i + 1] == 5 -> {
                    currentBackground = xterm256ColorToCompose(args[i + 2])
                    i += 3
                }
                // True color foreground: ESC[38;2;r;g;b
                arg == 38 && i + 4 < args.size && args[i + 1] == 2 -> {
                    val r = args[i + 2].coerceIn(0, 255)
                    val g = args[i + 3].coerceIn(0, 255)
                    val b = args[i + 4].coerceIn(0, 255)
                    currentForeground = Color(r, g, b)
                    i += 5
                }
                // True color background: ESC[48;2;r;g;b
                arg == 48 && i + 4 < args.size && args[i + 1] == 2 -> {
                    val r = args[i + 2].coerceIn(0, 255)
                    val g = args[i + 3].coerceIn(0, 255)
                    val b = args[i + 4].coerceIn(0, 255)
                    currentBackground = Color(r, g, b)
                    i += 5
                }
                else -> {
                    // Process single SGR codes
                    when (arg) {
                        0 -> resetAttributes()
                        1 -> currentBold = true
                        2 -> {} // Dim/faint - ignore for now
                        3 -> currentItalic = true
                        4 -> currentUnderline = true
                        5 -> {} // Slow blink - ignore
                        6 -> {} // Rapid blink - ignore
                        7 -> {} // Reverse video - ignore for now
                        8 -> {} // Conceal - ignore
                        9 -> {} // Strikethrough - ignore for now
                        21 -> {} // Double underline - ignore
                        22 -> currentBold = false
                        23 -> currentItalic = false
                        24 -> currentUnderline = false
                        25 -> {} // No blink
                        27 -> {} // No reverse
                        28 -> {} // No conceal
                        29 -> {} // No strikethrough
                        in 30..37 -> currentForeground = ansiColorToCompose(arg - 30)
                        39 -> currentForeground = Color.Unspecified
                        in 40..47 -> currentBackground = ansiColorToCompose(arg - 40)
                        49 -> currentBackground = Color.Unspecified
                        in 90..97 -> currentForeground = ansiColorToCompose(arg - 90, bright = true)
                        in 100..107 -> currentBackground = ansiColorToCompose(arg - 100, bright = true)
                    }
                    i++
                }
            }
        }
    }
    
    private fun ansiColorToCompose(code: Int, bright: Boolean = false): Color {
        return if (bright) {
            when (code) {
                0 -> TerminalSettings.getAnsiBrightBlack()
                1 -> TerminalSettings.getAnsiBrightRed()
                2 -> TerminalSettings.getAnsiBrightGreen()
                3 -> TerminalSettings.getAnsiBrightYellow()
                4 -> TerminalSettings.getAnsiBrightBlue()
                5 -> TerminalSettings.getAnsiBrightMagenta()
                6 -> TerminalSettings.getAnsiBrightCyan()
                7 -> TerminalSettings.getAnsiBrightWhite()
                else -> Color.Unspecified
            }
        } else {
            when (code) {
                0 -> TerminalSettings.getAnsiBlack()
                1 -> TerminalSettings.getAnsiRed()
                2 -> TerminalSettings.getAnsiGreen()
                3 -> TerminalSettings.getAnsiYellow()
                4 -> TerminalSettings.getAnsiBlue()
                5 -> TerminalSettings.getAnsiMagenta()
                6 -> TerminalSettings.getAnsiCyan()
                7 -> TerminalSettings.getAnsiWhite()
                else -> Color.Unspecified
            }
        }
    }
    
    private fun xterm256ColorToCompose(code: Int): Color {
        // Check custom palette first
        customColorPalette[code]?.let { return it }
        
        // Implement 256-color palette
        return when (code) {
            in 0..15 -> {
                // Standard 16 colors
                if (code < 8) ansiColorToCompose(code) 
                else ansiColorToCompose(code - 8, bright = true)
            }
            in 16..231 -> {
                // 216-color cube
                val index = code - 16
                val r = (index / 36) * 51
                val g = ((index % 36) / 6) * 51
                val b = (index % 6) * 51
                Color((r shl 16) or (g shl 8) or b or 0xFF000000.toInt())
            }
            in 232..255 -> {
                // Grayscale
                val gray = 8 + (code - 232) * 10
                Color((gray shl 16) or (gray shl 8) or gray or 0xFF000000.toInt())
            }
            else -> Color.Unspecified
        }
    }
    
    private fun resetAttributes() {
        currentForeground = Color.Unspecified
        currentBackground = Color.Unspecified
        currentBold = false
        currentItalic = false
        currentUnderline = false
    }
    
    private fun moveCursorUp(n: Int) {
        cursorRow = maxOf(0, cursorRow - n)
    }
    
    private fun moveCursorDown(n: Int) {
        cursorRow = minOf(rows - 1, cursorRow + n)
    }
    
    private fun moveCursorRight(n: Int) {
        cursorCol = minOf(columns - 1, cursorCol + n)
    }
    
    private fun moveCursorLeft(n: Int) {
        cursorCol = maxOf(0, cursorCol - n)
    }
    
    private fun setCursorPosition(row: Int, col: Int) {
        cursorRow = minOf(rows - 1, maxOf(0, row - 1))
        cursorCol = minOf(columns - 1, maxOf(0, col - 1))
    }
    
    private fun clearScreen(mode: Int) {
        when (mode) {
            0 -> {
                // Clear from cursor to end
                for (row in cursorRow until rows) {
                    val startCol = if (row == cursorRow) cursorCol else 0
                    for (col in startCol until columns) {
                        currentGrid[row][col] = TerminalCell()
                    }
                }
            }
            1 -> {
                // Clear from beginning to cursor
                for (row in 0..cursorRow) {
                    val endCol = if (row == cursorRow) cursorCol else columns - 1
                    for (col in 0..endCol) {
                        currentGrid[row][col] = TerminalCell()
                    }
                }
            }
            2 -> {
                // Clear entire screen
                for (row in 0 until rows) {
                    for (col in 0 until columns) {
                        currentGrid[row][col] = TerminalCell()
                    }
                }
            }
        }
    }
    
    private fun clearLine(mode: Int) {
        when (mode) {
            0 -> {
                // Clear from cursor to end of line
                for (col in cursorCol until columns) {
                    currentGrid[cursorRow][col] = TerminalCell()
                }
            }
            1 -> {
                // Clear from beginning to cursor
                for (col in 0..cursorCol) {
                    currentGrid[cursorRow][col] = TerminalCell()
                }
            }
            2 -> {
                // Clear entire line
                for (col in 0 until columns) {
                    currentGrid[cursorRow][col] = TerminalCell()
                }
            }
        }
    }
    
    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
    }
    
    private fun restoreCursor() {
        cursorRow = savedCursorRow
        cursorCol = savedCursorCol
    }
    
    private fun scrollUp(count: Int = 1) {
        repeat(count) {
            if (usingAlternateBuffer || scrollTop > 0 || scrollBottom < rows - 1) {
                // In alternate buffer or with custom scroll region, only scroll within the region
                if (scrollBottom > scrollTop) {
                    // Shift lines up within scroll region
                    for (row in scrollTop until scrollBottom) {
                        currentGrid[row] = currentGrid[row + 1]
                    }
                    // Clear the bottom line of scroll region
                    currentGrid[scrollBottom] = Array(columns) { TerminalCell() }
                }
            } else {
                // Normal scrolling in primary buffer - save to scrollback
                // Reuse removed line if available to reduce allocations
                val lineToSave = if (scrollbackLines.size >= maxScrollback && scrollbackLines.isNotEmpty()) {
                    val reusedLine = scrollbackLines.removeFirst()
                    // Copy current top line into the reused array
                    currentGrid[0].copyInto(reusedLine)
                    reusedLine
                } else {
                    currentGrid[0].copyOf()
                }
                scrollbackLines.addLast(lineToSave)
                
                // Ensure total buffer doesn't exceed limit
                trimBuffer()
                
                // Shift all lines up
                for (row in 0 until rows - 1) {
                    currentGrid[row] = currentGrid[row + 1]
                }
                
                // Clear the bottom line
                currentGrid[rows - 1] = Array(columns) { TerminalCell() }
            }
        }
    }
    
    private fun scrollDown(count: Int = 1) {
        repeat(count) {
            if (scrollBottom > scrollTop) {
                // Shift lines down within scroll region
                for (row in scrollBottom downTo scrollTop + 1) {
                    currentGrid[row] = currentGrid[row - 1]
                }
                
                // Clear the top line of scroll region
                currentGrid[scrollTop] = Array(columns) { TerminalCell() }
            }
        }
    }
    
    private fun insertLines(count: Int) {
        repeat(count) {
            // Shift lines down from cursor position
            for (row in rows - 1 downTo cursorRow + 1) {
                currentGrid[row] = currentGrid[row - 1]
            }
            // Clear the line at cursor position
            currentGrid[cursorRow] = Array(columns) { TerminalCell() }
        }
    }
    
    private fun deleteLines(count: Int) {
        repeat(count) {
            // Shift lines up from cursor position
            for (row in cursorRow until rows - 1) {
                currentGrid[row] = currentGrid[row + 1]
            }
            // Clear the bottom line
            currentGrid[rows - 1] = Array(columns) { TerminalCell() }
        }
    }
    
    private fun insertCharacters(count: Int) {
        // Insert blank characters at cursor position, shift rest of line right
        val actualCount = minOf(count, columns - cursorCol)
        if (actualCount > 0) {
            // Shift existing characters to the right
            for (col in columns - 1 downTo cursorCol + actualCount) {
                if (col - actualCount >= cursorCol) {
                    currentGrid[cursorRow][col] = currentGrid[cursorRow][col - actualCount]
                }
            }
            // Insert blank characters
            for (col in cursorCol until minOf(cursorCol + actualCount, columns)) {
                currentGrid[cursorRow][col] = TerminalCell()
            }
        }
    }
    
    private fun deleteCharacters(count: Int) {
        // Delete characters at cursor position, shift rest of line left
        minOf(cursorCol + count, columns)
        for (col in cursorCol until columns - count) {
            currentGrid[cursorRow][col] = if (col + count < columns) {
                currentGrid[cursorRow][col + count]
            } else {
                TerminalCell()
            }
        }
    }
    
    private fun eraseCharacters(count: Int) {
        // Erase characters at cursor position without shifting
        val endCol = minOf(cursorCol + count, columns)
        for (col in cursorCol until endCol) {
            currentGrid[cursorRow][col] = TerminalCell()
        }
    }
    
    fun getAnnotatedLines(): List<AnnotatedString> {
        val allLines = mutableListOf<AnnotatedString>()
        
        // Ensure we don't exceed buffer limit
        trimBuffer()
        
        // Add scrollback lines ONLY when not in alternate screen buffer
        if (!usingAlternateBuffer) {
            scrollbackLines.forEach { row ->
                val lineText = buildAnnotatedString {
                    for (cell in row) {
                        // Only add non-space characters or spaces that have styling
                        if (cell.char != ' ' || cell.backgroundColor != Color.Unspecified) {
                            val style = SpanStyle(
                                color = if (cell.foregroundColor == Color.Unspecified) 
                                    defaultForegroundColor ?: Color(0xFFE0E0E0) else cell.foregroundColor,
                                background = if (cell.backgroundColor != Color.Unspecified) 
                                    cell.backgroundColor else defaultBackgroundColor ?: Color.Unspecified,
                                fontWeight = if (cell.bold) FontWeight.Bold else null,
                                fontStyle = if (cell.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
                                textDecoration = if (cell.underline) 
                                    androidx.compose.ui.text.style.TextDecoration.Underline else null
                            )
                            
                            append(AnnotatedString(cell.char.toString(), style))
                        }
                    }
                    // Ensure line has at least one character for proper rendering
                    if (length == 0) {
                        append(" ")
                    }
                }
                
                
                allLines.add(lineText)
            }
        }
        
        // Add current grid lines - only up to the actual terminal height to prevent excess empty lines
        for (rowIndex in 0 until minOf(currentGrid.size, rows)) {
            val row = currentGrid[rowIndex]
            val lineText = buildAnnotatedString {
                for (cell in row) {
                    val style = SpanStyle(
                        color = if (cell.foregroundColor == Color.Unspecified) 
                            defaultForegroundColor ?: Color(0xFFE0E0E0) else cell.foregroundColor,
                        background = if (cell.backgroundColor != Color.Unspecified) 
                            cell.backgroundColor else defaultBackgroundColor ?: Color.Unspecified,
                        fontWeight = if (cell.bold) FontWeight.Bold else null,
                        fontStyle = if (cell.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
                        textDecoration = if (cell.underline) 
                            androidx.compose.ui.text.style.TextDecoration.Underline else null
                    )
                    
                    append(AnnotatedString(cell.char.toString(), style))
                }
                // Ensure line has at least one character for proper rendering
                if (length == 0) {
                    append(" ")
                }
            }
            
            
            allLines.add(lineText)
        }
        
        return allLines
    }
    
    fun getCursorPosition(): Pair<Int, Int> {
        // Ensure cursor is within bounds and return validated position
        val safeRow = minOf(cursorRow, rows - 1).coerceAtLeast(0)
        val safeCol = minOf(cursorCol, columns - 1).coerceAtLeast(0)
        
        // Validate cursor position is within terminal bounds
        
        // Update internal cursor to safe values if they were out of bounds
        if (cursorRow != safeRow || cursorCol != safeCol) {
            cursorRow = safeRow
            cursorCol = safeCol
        }
        
        return safeRow to safeCol  // Return validated cursor position
    }
    
    fun isCursorVisible(): Boolean = cursorVisible
    
    private fun trimBuffer() {
        // Ensure total lines don't exceed maxBufferSize
        val totalLines = scrollbackLines.size + rows
        if (totalLines > maxBufferSize) {
            val linesToRemove = totalLines - maxBufferSize
            repeat(linesToRemove) {
                if (scrollbackLines.isNotEmpty()) {
                    scrollbackLines.removeFirst() // O(1) instead of O(n)
                }
            }
        }
    }
    
    fun resize(newColumns: Int, newRows: Int) {
        if (newColumns == columns && newRows == rows) {
            return // No change needed
        }

        // Resizing terminal buffer

        // Resize grids (iTerm2-style optimized)
        val newPrimaryGrid = Array(newRows) { Array(newColumns) { TerminalCell() } }

        // Copy existing content to new grids
        val rowsToCopy = minOf(rows, newRows)
        val colsToCopy = minOf(columns, newColumns)

        // Copy primary grid
        for (row in 0 until rowsToCopy) {
            for (col in 0 until colsToCopy) {
                newPrimaryGrid[row][col] = primaryGrid[row][col]
            }
        }
        
        // Update primary grid
        primaryGrid = newPrimaryGrid
        
        // Resize alternate grid if it exists (lazy allocation advantage)
        alternateGrid?.let { altGrid ->
            val newAlternateGrid = Array(newRows) { Array(newColumns) { TerminalCell() } }
            for (row in 0 until rowsToCopy) {
                for (col in 0 until colsToCopy) {
                    newAlternateGrid[row][col] = altGrid[row][col]
                }
            }
            alternateGrid = newAlternateGrid
        }
        
        // Update current grid pointer and dimensions
        currentGrid = if (usingAlternateBuffer) alternateGrid!! else primaryGrid
        columns = newColumns
        rows = newRows
        
        // Adjust cursor position if necessary
        cursorRow = minOf(cursorRow, newRows - 1)
        cursorCol = minOf(cursorCol, newColumns - 1)
        
        // Adjust saved cursor position
        savedCursorRow = minOf(savedCursorRow, newRows - 1)
        savedCursorCol = minOf(savedCursorCol, newColumns - 1)
        savedPrimaryCursorRow = minOf(savedPrimaryCursorRow, newRows - 1)
        savedPrimaryCursorCol = minOf(savedPrimaryCursorCol, newColumns - 1)
        
        // Adjust scrolling region
        scrollBottom = minOf(scrollBottom, newRows - 1)
        scrollTop = minOf(scrollTop, scrollBottom)
        
        // Update max scrollback based on new rows
        // Scrollback only applies to primary buffer
        val newMaxScrollback = maxBufferSize - newRows
        
        // Trim scrollback if necessary
        while (scrollbackLines.size > newMaxScrollback && scrollbackLines.isNotEmpty()) {
            scrollbackLines.removeFirst() // O(1) instead of O(n)
        }
    }
    
    private fun handleModeSet(params: String) {
        when (params) {
            "?25" -> cursorVisible = true // Show cursor
            "?1049" -> switchToAlternateBuffer() // Use alternate screen buffer
            "?47" -> switchToAlternateBuffer() // Alternate screen (older version)
            "?1047" -> switchToAlternateBuffer() // Alternate screen
            "?1" -> {} // Application cursor keys - ignore for now
            "?3" -> {} // 132 column mode - ignore for now
            "?4" -> {} // Smooth scroll - ignore for now
            "?5" -> {} // Reverse screen - ignore for now
            "?6" -> {} // Origin mode - ignore for now
            "?7" -> {} // Auto wrap - ignore for now
            "?8" -> {} // Auto repeat - ignore for now
            "?9" -> {} // X10 mouse tracking - ignore for now
            "?12" -> {} // Cursor blink - ignore for now
            "?40" -> {} // Allow 132 columns - ignore for now
            "?1000" -> {} // VT200 mouse tracking - ignore for now
            "?1002" -> {} // Cell motion mouse tracking - ignore for now
            "?1003" -> {} // All motion mouse tracking - ignore for now
            "?1006" -> {} // Extended mouse mode - ignore for now
            "?2004" -> {} // Bracketed paste mode - ignore for now
            else -> {
                // Handle unknown terminal mode set command (ignored)
            }
        }
    }
    
    private fun handleModeReset(params: String) {
        when (params) {
            "?25" -> cursorVisible = false // Hide cursor
            "?1049" -> switchToPrimaryBuffer() // Use normal screen buffer
            "?47" -> switchToPrimaryBuffer() // Normal screen (older version)
            "?1047" -> switchToPrimaryBuffer() // Normal screen
            "?1" -> {} // Normal cursor keys
            "?3" -> {} // 80 column mode
            "?4" -> {} // Jump scroll
            "?5" -> {} // Normal screen
            "?6" -> {} // Normal cursor mode
            "?7" -> {} // No auto wrap
            "?8" -> {} // No auto repeat
            "?9" -> {} // No X10 mouse tracking
            "?12" -> {} // No cursor blink
            "?40" -> {} // Disallow 132 columns
            "?1000" -> {} // No VT200 mouse tracking
            "?1002" -> {} // No cell motion mouse tracking
            "?1003" -> {} // No all motion mouse tracking
            "?1006" -> {} // No extended mouse mode
            "?2004" -> {} // No bracketed paste mode
            else -> {
                // Handle unknown terminal mode reset command (ignored)
            }
        }
    }
    
    private fun switchToAlternateBuffer() {
        if (!usingAlternateBuffer) {
            // Save current cursor position in primary buffer with bounds validation
            savedPrimaryCursorRow = minOf(cursorRow, rows - 1).coerceAtLeast(0)
            savedPrimaryCursorCol = minOf(cursorCol, columns - 1).coerceAtLeast(0)
            
            // Lazy allocation of alternate buffer (iTerm2 style)
            if (alternateGrid == null) {
                alternateGrid = Array(rows) { Array(columns) { TerminalCell() } }
            }
            
            // Switch to alternate grid
            currentGrid = alternateGrid!!
            usingAlternateBuffer = true
            
            // Clear the alternate buffer
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    currentGrid[row][col] = TerminalCell()
                }
            }
            
            // Reset cursor position to top-left in alternate buffer
            cursorRow = 0
            cursorCol = 0
            
            // Reset scrolling region
            scrollTop = 0
            scrollBottom = rows - 1
        }
    }
    
    private fun switchToPrimaryBuffer() {
        if (usingAlternateBuffer) {
            // Switch back to primary grid (alternateGrid stays allocated)
            currentGrid = primaryGrid
            usingAlternateBuffer = false
            
            // Restore cursor position with bounds validation
            cursorRow = minOf(savedPrimaryCursorRow, rows - 1).coerceAtLeast(0)
            cursorCol = minOf(savedPrimaryCursorCol, columns - 1).coerceAtLeast(0)
            
            // Reset scrolling region
            scrollTop = 0
            scrollBottom = rows - 1
        }
    }
    
    private fun setScrollingRegion(top: Int, bottom: Int) {
        // Convert 1-based to 0-based and validate
        scrollTop = maxOf(0, minOf(rows - 1, top - 1))
        scrollBottom = maxOf(scrollTop, minOf(rows - 1, bottom - 1))
        
        // Move cursor to home position when scrolling region is set
        cursorRow = scrollTop
        cursorCol = 0
    }
    
    private fun handleDeviceStatusReport(args: List<Int>) {
        val command = args.getOrElse(0) { 0 }
        when (command) {
            5 -> {
                // Device Status Report - respond that terminal is OK
                responseCallback?.invoke("\u001B[0n")
            }
            6 -> {
                // Cursor Position Report - respond with current cursor position (1-based)
                // Ensure cursor is within bounds before reporting
                val safeRow = minOf(cursorRow, rows - 1).coerceAtLeast(0)
                val safeCol = minOf(cursorCol, columns - 1).coerceAtLeast(0)
                val row = safeRow + 1
                val col = safeCol + 1
                responseCallback?.invoke("\u001B[${row};${col}R")
                // Send cursor position report to requesting application
            }
            else -> {
                // Handle unknown device status report command (ignored)
            }
        }
    }
    
    private fun processCharacterSetDesignation(intermediate: Char, final: Char) {
        // Character set designation sequences like ESC(B, ESC)0, etc.
        val charSet = when (final) {
            'A' -> CharacterSet.UK
            'B' -> CharacterSet.ASCII
            '0' -> CharacterSet.GRAPHICS
            '1' -> CharacterSet.ASCII // Alternate character ROM - treat as ASCII
            '2' -> CharacterSet.GRAPHICS // Alternate character ROM special graphics
            else -> CharacterSet.UNCHANGED
        }
        
        if (charSet != CharacterSet.UNCHANGED) {
            when (intermediate) {
                '(' -> g0CharSet = charSet // Designate G0
                ')' -> g1CharSet = charSet // Designate G1
                '*' -> g2CharSet = charSet // Designate G2
                '+' -> g3CharSet = charSet // Designate G3
            }
        }
    }
    
    private fun performFullReset() {
        // Reset to initial state
        
        // Switch to primary buffer if in alternate
        if (usingAlternateBuffer) {
            switchToPrimaryBuffer()
        }
        
        // Clear the entire screen
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                currentGrid[row][col] = TerminalCell()
            }
        }
        
        // Reset cursor position
        cursorRow = 0
        cursorCol = 0
        savedCursorRow = 0
        savedCursorCol = 0
        savedPrimaryCursorRow = 0
        savedPrimaryCursorCol = 0
        
        // Reset attributes
        resetAttributes()
        
        // Reset cursor visibility
        cursorVisible = true
        
        // Reset scrolling region
        scrollTop = 0
        scrollBottom = rows - 1
        
        // Clear scrollback in primary buffer
        scrollbackLines.clear()
        
        // Reset character sets to defaults
        g0CharSet = CharacterSet.ASCII
        g1CharSet = CharacterSet.ASCII
        g2CharSet = CharacterSet.ASCII
        g3CharSet = CharacterSet.ASCII
        currentCharSet = 0
        singleShift = -1
        
        // Reset window titles
        windowTitle = ""
        iconTitle = ""
        
        // Reset custom colors
        customColorPalette.clear()
        defaultForegroundColor = null
        defaultBackgroundColor = null
    }

    private fun processTwoCharSequence(char: Char) {
        when (char) {
            '7' -> saveCursor() // Save cursor
            '8' -> restoreCursor() // Restore cursor
            'D' -> scrollUp() // Index
            'M' -> scrollDown() // Reverse index
            'E' -> { // Next line
                cursorCol = 0
                moveCursorDown(1)
            }
            'H' -> {} // Tab set - ignore for now
            '=' -> {} // Application keypad mode - ignore for now
            '>' -> {} // Normal keypad mode - ignore for now
            'c' -> performFullReset() // Reset
            'N' -> singleShift = 2 // SS2 - Single shift to G2
            'O' -> singleShift = 3 // SS3 - Single shift to G3
            'n' -> currentCharSet = 2 // LS2 - Locking shift to G2
            'o' -> currentCharSet = 3 // LS3 - Locking shift to G3
            'Z' -> {} // DEC Identification - ignore for now
            '\u009B' -> {} // CSI - Control Sequence Introducer (8-bit)
            '\u0090' -> {} // DCS - Device Control String (8-bit)
            '\u009D' -> {} // OSC - Operating System Command (8-bit)
            '\u009E' -> {} // PM - Privacy Message (8-bit)
            '\u009F' -> {} // APC - Application Program Command (8-bit)
            else -> {
                // Handle unknown two-character escape sequence (ignored)
            }
        }
    }

    // Add proper cleanup to prevent memory leaks
    fun dispose() {
        // Clear all buffers to free memory
        scrollbackLines.clear()

        // Clear primary grid
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                primaryGrid[row][col] = TerminalCell()
            }
        }

        // Clear alternate grid if it exists and free memory (iTerm2 optimization)
        alternateGrid?.let { altGrid ->
            for (row in 0 until altGrid.size) {
                for (col in 0 until altGrid[row].size) {
                    altGrid[row][col] = TerminalCell()
                }
            }
            alternateGrid = null // Completely free alternate grid memory
        }

        // Clear escape sequence builder
        escapeSequence.clear()

        // Reset callback to prevent memory references
        responseCallback = null
    }
}
